package io.github.hotreload.demo.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.hotreload.demo.config.exception.HotReloadException;
import io.github.hotreload.demo.config.redis.RedisPublisher;
import io.github.hotreload.demo.config.reload.HotReloadProperties;
import io.github.hotreload.demo.core.recovery.HotReloadRecoverFileMeta;
import io.github.hotreload.demo.entity.HotReloadFileEntity;
import io.github.hotreload.demo.entity.HotReloadTaskEntity;
import io.github.hotreload.demo.entity.HotReloadTaskInstanceEntity;
import io.github.hotreload.demo.entity.SysConfigDetailEntity;
import io.github.hotreload.demo.mapper.HotReloadFileMapper;
import io.github.hotreload.demo.mapper.HotReloadTaskInstanceMapper;
import io.github.hotreload.demo.mapper.HotReloadTaskMapper;
import io.github.hotreload.demo.mapper.SysConfigDetailMapper;
import io.github.hotreload.demo.core.cluster.HotReloadConstants;
import io.github.hotreload.demo.core.message.HotReloadDiscoverMessage;
import io.github.hotreload.demo.core.cluster.HotReloadLocalNode;
import io.github.hotreload.demo.core.message.HotReloadTaskMessage;
import io.github.hotreload.demo.core.recovery.HotReloadRecoveryFileStore;
import io.github.hotreload.demo.core.runtime.HotReloadRuntimeExecutor;
import io.github.hotreload.demo.util.BooleanUtils;
import io.github.hotreload.demo.util.HotReloadUtils;
import io.github.hotreload.demo.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PreDestroy;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 集群热重载业务实现。
 * <p>
 * 该类负责把页面请求转成数据库任务和 Redis 广播，并在节点收到通知后执行本机 Arthas/MyBatis 热重载。
 */
@Slf4j
@Service
public class HotReloadClusterServiceImpl implements HotReloadClusterService {

    private static final Set<String> RUNNING_INSTANCE_STATUS = new HashSet<>(Arrays.asList(
            HotReloadConstants.INSTANCE_STATUS_PENDING,
            HotReloadConstants.INSTANCE_STATUS_RECEIVED,
            HotReloadConstants.INSTANCE_STATUS_PRECHECKING,
            HotReloadConstants.INSTANCE_STATUS_RELOADING,
            HotReloadConstants.INSTANCE_STATUS_VERIFYING
    ));

    private static final Set<String> RETRYABLE_INSTANCE_STATUS = new HashSet<>(Arrays.asList(
            HotReloadConstants.INSTANCE_STATUS_PRECHECK_FAILED,
            HotReloadConstants.INSTANCE_STATUS_FAILED,
            HotReloadConstants.INSTANCE_STATUS_TIMEOUT
    ));

    private static final Set<String> SUPPORTED_STOP_RECOVERY_FILE_TYPES = new HashSet<>(Arrays.asList(
            HotReloadConstants.FILE_TYPE_ALL,
            HotReloadConstants.FILE_TYPE_CLASS,
            HotReloadConstants.FILE_TYPE_MYBATIS_XML
    ));

    private static final long NODE_STATUS_EXPIRE_MILLIS = 30 * 60 * 1000L;
    private static final String RELOAD_CONFIG_CODE = "RELOAD_CONFIG";
    private static final String RELOAD_SERVICE_DETAIL_CODE = "RELOAD_SERVICE";
    private static final String RELOAD_SWITCH_DETAIL_CODE = "RELOAD_SWITCH";

    private final HotReloadProperties hotReloadProperties;
    private final RedisPublisher redisPublisher;
    private final RedisTemplate<String, Object> hotReloadRedisTemplate;
    private final HotReloadTaskMapper hotReloadTaskMapper;
    private final HotReloadTaskInstanceMapper hotReloadTaskInstanceMapper;
    private final HotReloadFileMapper hotReloadFileMapper;
    private final SysConfigDetailMapper sysConfigDetailMapper;
    private final HotReloadLocalNode localNode;
    private final HotReloadRuntimeExecutor hotReloadRuntimeExecutor;
    private final HotReloadRecoveryFileStore recoverFileStore;
    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(2);
    private final ReentrantLock localReloadLock = new ReentrantLock(true);

    private volatile String localHotReloadStatus = HotReloadConstants.HOT_RELOAD_STATUS_IDLE;
    private volatile String localLastTaskId;

    /**
     * 构造集群热重载业务实现。
     *
     * @param hotReloadProperties          热重载配置
     * @param redisPublisher               Redis 消息发布器
     * @param hotReloadRedisTemplate       热重载 RedisTemplate
     * @param hotReloadTaskMapper          任务主表 Mapper
     * @param hotReloadTaskInstanceMapper  任务实例 Mapper
     * @param hotReloadFileMapper          任务文件 Mapper
     * @param sysConfigDetailMapper        配置明细 Mapper
     * @param localNode                    当前节点信息
     * @param hotReloadRuntimeExecutor     本机热重载执行器
     * @param recoverFileStore             恢复文件存储组件
     */
    public HotReloadClusterServiceImpl(HotReloadProperties hotReloadProperties,
                                       RedisPublisher redisPublisher,
                                       RedisTemplate<String, Object> hotReloadRedisTemplate,
                                       HotReloadTaskMapper hotReloadTaskMapper,
                                       HotReloadTaskInstanceMapper hotReloadTaskInstanceMapper,
                                       HotReloadFileMapper hotReloadFileMapper,
                                       SysConfigDetailMapper sysConfigDetailMapper,
                                       HotReloadLocalNode localNode,
                                       HotReloadRuntimeExecutor hotReloadRuntimeExecutor,
                                       HotReloadRecoveryFileStore recoverFileStore) {
        this.hotReloadProperties = hotReloadProperties;
        this.redisPublisher = redisPublisher;
        this.hotReloadRedisTemplate = hotReloadRedisTemplate;
        this.hotReloadTaskMapper = hotReloadTaskMapper;
        this.hotReloadTaskInstanceMapper = hotReloadTaskInstanceMapper;
        this.hotReloadFileMapper = hotReloadFileMapper;
        this.sysConfigDetailMapper = sysConfigDetailMapper;
        this.localNode = localNode;
        this.hotReloadRuntimeExecutor = hotReloadRuntimeExecutor;
        this.recoverFileStore = recoverFileStore;
    }

    /**
     * 查询可热重载的应用配置。
     *
     * @return 按 appName 排序后的应用列表
     */
    @Override
    public List<HotReloadAppVO> apps() {
        SysConfigDetailEntity configDetail = selectReloadConfigDetail(RELOAD_SERVICE_DETAIL_CODE);
        if (configDetail == null || StringUtils.isBlank(configDetail.getDetailValue())) {
            return defaultAppConfig();
        }
        try {
            List<HotReloadAppVO> configs = JSONObject.parseArray(configDetail.getDetailValue(), HotReloadAppVO.class);
            if (CollectionUtils.isEmpty(configs)) {
                return defaultAppConfig();
            }
            Map<String, HotReloadAppVO> distinct = new LinkedHashMap<>();
            for (HotReloadAppVO config : configs) {
                if (config != null && StringUtils.isNotBlank(config.getAppName())) {
                    distinct.put(config.getAppName(), config);
                }
            }
            return distinct.isEmpty() ? defaultAppConfig() : distinct.values().stream()
                    .sorted((a, b) -> StringUtils.defaultString(a.getAppName())
                            .compareTo(StringUtils.defaultString(b.getAppName())))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("解析热重载服务配置失败，detailValue={}", configDetail.getDetailValue(), e);
            throw new HotReloadException("热重载服务配置格式错误");
        }
    }

    /**
     * 发布节点发现消息，并在发布前清空当前环境下目标应用旧节点列表。
     *
     * @param appName 目标应用名称
     */
    @Override
    public void discover(String appName) {
        String targetAppName = resolveRequiredAppName(appName);
        if (!isHotReloadEnabled()) {
            return;
        }
        String env = localNode.getEnv();
        hotReloadRedisTemplate.delete(discoverNodeKey(env, targetAppName));

        HotReloadDiscoverMessage message = new HotReloadDiscoverMessage();
        message.setMessageType(HotReloadConstants.MESSAGE_TYPE_DISCOVER_REQUEST);
        message.setAppName(targetAppName);
        message.setEnv(env);
        message.setRequestTime(new Date());
        redisPublisher.pushMessage(hotReloadProperties.getRedisTopic(), (JSONObject) JSONObject.toJSON(message));
        log.info("热重载节点发现消息已发布，appName={}，env={}", targetAppName, env);
    }

    /**
     * 查询目标应用在 Redis 中的节点注册信息。
     *
     * @param appName 目标应用名称
     * @return 节点列表
     */
    @Override
    public List<HotReloadNodeVO> nodes(String appName) {
        String targetAppName = resolveRequiredAppName(appName);
        return readDiscoverNodes(discoverNodeKey(localNode.getEnv(), targetAppName));
    }

    /**
     * 当前节点响应 discover 广播，并把自己的节点信息写入 Redis。
     *
     * @param message Redis 节点发现消息
     */
    @Override
    public void reportDiscover(HotReloadDiscoverMessage message) {
        if (!isHotReloadEnabled()) {
            return;
        }
        if (message == null || !HotReloadConstants.MESSAGE_TYPE_DISCOVER_REQUEST.equals(message.getMessageType())) {
            return;
        }
        if (!matchesLocalNode(message.getAppName(), message.getEnv())) {
            return;
        }
        registerCurrentNode();
        log.info("热重载节点信息已上报，appName={}，env={}，ip={}",
                message.getAppName(), message.getEnv(), localNode.getIp());
    }

    /**
     * 创建热重载任务、保存上传文件、拆分目标节点实例，并在事务提交后发布 Redis 任务通知。
     *
     * @param file      上传的 class 或 XML 文件
     * @param requestVO 创建任务请求
     */
    @Override
    @Transactional
    public void createTask(MultipartFile file, HotReloadCreateTaskRequestVO requestVO) {
        assertHotReloadEnabled();
        validateCreateTask(file, requestVO);
        fillCreateTaskRequest(requestVO);

        String env = localNode.getEnv();
        String appName = requestVO.getAppName();
        String fileName = normalizeUploadFileName(file.getOriginalFilename());
        byte[] fileBytes = readFileBytes(file);
        String reloadType = resolveReloadType(fileName);
        validateReloadFile(reloadType, fileBytes, fileName);
        String className = parseClassNameQuietly(fileBytes);
        String taskId = newId("TASK");
        String fileSha256 = HotReloadUtils.sha256(fileBytes);

        HotReloadTaskEntity taskEntity = new HotReloadTaskEntity();
        taskEntity.setTaskId(taskId);
        taskEntity.setAppName(appName);
        taskEntity.setEnv(env);
        taskEntity.setPatchName(fileName);
        taskEntity.setPatchSha256(fileSha256);
        taskEntity.setReloadType(reloadType);
        taskEntity.setPersistOnRestart(requestVO.getPersistOnRestart());
        taskEntity.setTaskRemark(requestVO.getTaskRemark());
        hotReloadTaskMapper.insert(taskEntity);

        HotReloadFileEntity fileEntity = new HotReloadFileEntity();
        fileEntity.setFileId(newId("FILE"));
        fileEntity.setTaskId(taskId);
        fileEntity.setFileName(fileName);
        fileEntity.setFileType(reloadType);
        fileEntity.setClassName(className);
        fileEntity.setFileSha256(fileSha256);
        fileEntity.setFileContent(fileBytes);
        hotReloadFileMapper.insert(fileEntity);

        List<HotReloadTaskInstanceEntity> createdInstances = new ArrayList<>();
        for (String ip : requestVO.getIps()) {
            HotReloadTaskInstanceEntity instanceEntity = new HotReloadTaskInstanceEntity();
            instanceEntity.setTaskInstanceId(newId("INST"));
            instanceEntity.setTaskId(taskId);
            instanceEntity.setAppName(appName);
            instanceEntity.setEnv(env);
            instanceEntity.setIp(ip);
            instanceEntity.setExecuteType(HotReloadConstants.EXECUTE_TYPE_NORMAL);
            instanceEntity.setStatus(HotReloadConstants.INSTANCE_STATUS_PENDING);
            instanceEntity.setRetryCount(0);
            hotReloadTaskInstanceMapper.insert(instanceEntity);
            createdInstances.add(instanceEntity);
        }

        registerAfterCommit(() -> {
            for (HotReloadTaskInstanceEntity instance : createdInstances) {
                updateRedisNodeStatus(instance.getEnv(), instance.getAppName(), instance.getIp(),
                        HotReloadConstants.INSTANCE_STATUS_PENDING, instance.getTaskId());
            }
            publishTask(taskEntity, HotReloadConstants.MESSAGE_TYPE_RELOAD_TASK_NOTIFY);
        });
    }

    /**
     * 创建停止重启自动恢复任务、拆分目标节点实例，并在事务提交后发布 Redis 任务通知。
     *
     * @param requestVO 停止恢复任务请求
     */
    @Override
    @Transactional
    public void stopRestartRecovery(HotReloadStopRecoveryRequestVO requestVO) {
        assertHotReloadEnabled();
        validateStopRecoveryTask(requestVO);

        String env = localNode.getEnv();
        String appName = requestVO.getAppName();
        String fileType = requestVO.getFileType();
        String taskId = newId("STOP");

        HotReloadTaskEntity taskEntity = new HotReloadTaskEntity();
        taskEntity.setTaskId(taskId);
        taskEntity.setAppName(appName);
        taskEntity.setEnv(env);
        taskEntity.setPatchName("停止重启自动恢复");
        taskEntity.setReloadType(fileType);
        taskEntity.setPersistOnRestart(HotReloadConstants.FLAG_N);
        taskEntity.setTaskRemark(StringUtils.defaultIfBlank(requestVO.getTaskRemark(), "停止本地热重载重启自动恢复"));
        hotReloadTaskMapper.insert(taskEntity);

        List<HotReloadTaskInstanceEntity> createdInstances = new ArrayList<>();
        for (String ip : requestVO.getIps()) {
            HotReloadTaskInstanceEntity instanceEntity = new HotReloadTaskInstanceEntity();
            instanceEntity.setTaskInstanceId(newId("INST"));
            instanceEntity.setTaskId(taskId);
            instanceEntity.setAppName(appName);
            instanceEntity.setEnv(env);
            instanceEntity.setIp(ip);
            instanceEntity.setExecuteType(HotReloadConstants.EXECUTE_TYPE_STOP_RECOVERY);
            instanceEntity.setStatus(HotReloadConstants.INSTANCE_STATUS_PENDING);
            instanceEntity.setRetryCount(0);
            hotReloadTaskInstanceMapper.insert(instanceEntity);
            createdInstances.add(instanceEntity);
        }

        registerAfterCommit(() -> {
            for (HotReloadTaskInstanceEntity instance : createdInstances) {
                updateRedisNodeStatus(instance.getEnv(), instance.getAppName(), instance.getIp(),
                        HotReloadConstants.INSTANCE_STATUS_PENDING, instance.getTaskId());
            }
            publishTask(taskEntity, HotReloadConstants.MESSAGE_TYPE_STOP_RECOVERY_TASK_NOTIFY);
        });
    }

    /**
     * 查询热重载相关任务详情，并附带节点实例和动态汇总状态。
     *
     * @param taskId 任务 ID
     * @return 任务详情
     */
    @Override
    public HotReloadTaskVO getTask(String taskId) {
        if (StringUtils.isBlank(taskId)) {
            throw new HotReloadException("taskId 不能为空");
        }
        HotReloadTaskEntity taskEntity = hotReloadTaskMapper.selectById(taskId);
        if (taskEntity == null) {
            throw new HotReloadException("热重载相关任务不存在");
        }
        HotReloadTaskVO taskVO = new HotReloadTaskVO();
        BeanUtils.copyProperties(taskEntity, taskVO);

        LambdaQueryWrapper<HotReloadTaskInstanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotReloadTaskInstanceEntity::getTaskId, taskId);
        wrapper.orderByAsc(HotReloadTaskInstanceEntity::getIp);
        wrapper.orderByAsc(HotReloadTaskInstanceEntity::getExecuteType);
        wrapper.orderByAsc(HotReloadTaskInstanceEntity::getStartTime);
        List<HotReloadTaskInstanceEntity> instanceEntities = hotReloadTaskInstanceMapper.selectList(wrapper);

        List<HotReloadTaskInstanceVO> instances = new ArrayList<>();
        for (HotReloadTaskInstanceEntity entity : instanceEntities) {
            HotReloadTaskInstanceVO vo = new HotReloadTaskInstanceVO();
            BeanUtils.copyProperties(entity, vo);
            instances.add(vo);
        }
        applyTaskSummary(taskVO, instanceEntities.stream()
                .filter(this::isUserTaskInstance)
                .collect(Collectors.toList()));
        taskVO.setInstances(instances);
        return taskVO;
    }

    /**
     * 分页查询热重载相关历史任务日志，支持按任务字段和实例字段过滤。
     *
     * @param page 分页参数和查询条件
     * @return 分页任务日志
     */
    @Override
    public PageResultVO<HotReloadTaskVO> pageTaskLogs(PageRequestVO<HotReloadTaskLogQueryVO> page) {
        if (page == null) {
            throw new HotReloadException("日志查询分页参数不能为空");
        }
        HotReloadTaskLogQueryVO queryVO = page.getRequestVo() == null
                ? new HotReloadTaskLogQueryVO() : page.getRequestVo();
        Set<String> filteredTaskIds = selectTaskIdsByInstanceLog(queryVO);
        if (filteredTaskIds != null && filteredTaskIds.isEmpty()) {
            return emptyTaskLogGrid(page);
        }

        LambdaQueryWrapper<HotReloadTaskEntity> wrapper = buildTaskLogQueryWrapper(queryVO, filteredTaskIds);
        if (StringUtils.isBlank(queryVO.getStatus())) {
            Page<HotReloadTaskEntity> queryPage = new Page<>(page.getPageStart(), page.getPageNums());
            Page<HotReloadTaskEntity> entityPage = hotReloadTaskMapper.selectPage(queryPage, wrapper);
            PageResultVO<HotReloadTaskVO> result = new PageResultVO<>();
            result.setPageStart(page.getPageStart());
            result.setPageNums(page.getPageNums());
            result.setTotal(entityPage.getTotal());
            result.setRows(buildTaskLogRows(entityPage.getRecords()));
            return result;
        }

        List<HotReloadTaskVO> rows = buildTaskLogRows(hotReloadTaskMapper.selectList(wrapper)).stream()
                .filter(taskVO -> StringUtils.equals(queryVO.getStatus(), taskVO.getStatus()))
                .collect(Collectors.toList());
        return pageTaskLogRows(page, rows);
    }

    /**
     * 把失败、超时或预检查失败的节点实例重置为待执行，并重新广播任务。
     *
     * @param requestVO 重试请求
     * @return 重试后的任务详情
     */
    @Override
    @Transactional
    public HotReloadTaskVO retry(HotReloadRetryRequestVO requestVO) {
        if (requestVO == null || StringUtils.isBlank(requestVO.getTaskId())
                || CollectionUtils.isEmpty(requestVO.getIps())) {
            throw new HotReloadException("重试请求参数无效");
        }
        HotReloadTaskEntity taskEntity = hotReloadTaskMapper.selectById(requestVO.getTaskId());
        if (taskEntity == null) {
            throw new HotReloadException("热重载相关任务不存在");
        }

        LambdaQueryWrapper<HotReloadTaskInstanceEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(HotReloadTaskInstanceEntity::getTaskId, requestVO.getTaskId());
        queryWrapper.in(HotReloadTaskInstanceEntity::getExecuteType,
                Arrays.asList(HotReloadConstants.EXECUTE_TYPE_NORMAL, HotReloadConstants.EXECUTE_TYPE_STOP_RECOVERY));
        queryWrapper.in(HotReloadTaskInstanceEntity::getIp, requestVO.getIps());
        List<HotReloadTaskInstanceEntity> instances = hotReloadTaskInstanceMapper.selectList(queryWrapper);
        List<HotReloadTaskInstanceEntity> retriedInstances = instances.stream()
                .filter(instance -> RETRYABLE_INSTANCE_STATUS.contains(instance.getStatus()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(retriedInstances)) {
            throw new HotReloadException("没有可重试的目标节点");
        }
        String retryMessageType = resolveTaskMessageType(retriedInstances);

        for (HotReloadTaskInstanceEntity instance : retriedInstances) {
            LambdaUpdateWrapper<HotReloadTaskInstanceEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(HotReloadTaskInstanceEntity::getTaskInstanceId, instance.getTaskInstanceId());
            updateWrapper.set(HotReloadTaskInstanceEntity::getStatus, HotReloadConstants.INSTANCE_STATUS_PENDING);
            updateWrapper.set(HotReloadTaskInstanceEntity::getErrorCode, null);
            updateWrapper.set(HotReloadTaskInstanceEntity::getErrorMessage, null);
            updateWrapper.set(HotReloadTaskInstanceEntity::getReloadResult, null);
            updateWrapper.set(HotReloadTaskInstanceEntity::getReceiveTime, null);
            updateWrapper.set(HotReloadTaskInstanceEntity::getStartTime, null);
            updateWrapper.set(HotReloadTaskInstanceEntity::getFinishTime, null);
            updateWrapper.set(HotReloadTaskInstanceEntity::getRetryCount,
                    instance.getRetryCount() == null ? 1 : instance.getRetryCount() + 1);
            hotReloadTaskInstanceMapper.update(null, updateWrapper);
            instance.setStatus(HotReloadConstants.INSTANCE_STATUS_PENDING);
        }

        registerAfterCommit(() -> {
            for (HotReloadTaskInstanceEntity instance : retriedInstances) {
                updateRedisNodeStatus(instance.getEnv(), instance.getAppName(), instance.getIp(),
                        HotReloadConstants.INSTANCE_STATUS_PENDING, instance.getTaskId());
            }
            publishTask(taskEntity, retryMessageType);
        });
        return getTask(requestVO.getTaskId());
    }

    /**
     * 查询当前节点在热重载系统中的状态。
     *
     * @return 当前节点信息
     */
    @Override
    public HotReloadNodeVO currentNode() {
        HotReloadNodeVO nodeVO = localNode.currentNode();
        nodeVO.setHotReloadStatus(StringUtils.defaultIfBlank(localHotReloadStatus,
                HotReloadConstants.HOT_RELOAD_STATUS_IDLE));
        nodeVO.setLastTaskId(localLastTaskId);
        applyNodeStatus(nodeVO);
        return nodeVO;
    }

    /**
     * 收到热重载任务通知后提交异步执行，避免阻塞 Redis 监听线程。
     *
     * @param taskId 任务 ID
     */
    @Override
    public void receiveReloadTask(String taskId) {
        if (!isHotReloadEnabled() || StringUtils.isBlank(taskId)) {
            return;
        }
        taskExecutor.submit(() -> executeReloadTask(taskId));
    }

    /**
     * 收到停止重启自动恢复任务通知后提交异步执行，避免阻塞 Redis 监听线程。
     *
     * @param taskId 任务 ID
     */
    @Override
    public void receiveStopRecoveryTask(String taskId) {
        if (!isHotReloadEnabled() || StringUtils.isBlank(taskId)) {
            return;
        }
        taskExecutor.submit(() -> executeStopRecoveryTask(taskId));
    }

    /**
     * 拉取热重载任务并判断是否需要由当前节点执行。
     *
     * @param taskId 任务 ID
     */
    private void executeReloadTask(String taskId) {
        String ip = localNode.getIp();
        HotReloadTaskEntity taskEntity = selectExecutableTask(taskId);
        if (taskEntity == null) {
            return;
        }
        HotReloadTaskInstanceEntity instanceEntity = selectLocalReloadTaskInstance(taskId, ip);
        if (instanceEntity == null) {
            log.debug("忽略非本机热重载任务，taskId={}，ip={}", taskId, ip);
            return;
        }

        localReloadLock.lock();
        try {
            executeLocalReloadTask(taskEntity, taskId, ip);
        } finally {
            localReloadLock.unlock();
        }
    }

    /**
     * 拉取停止重启自动恢复任务并判断是否需要由当前节点执行。
     *
     * @param taskId 任务 ID
     */
    private void executeStopRecoveryTask(String taskId) {
        String ip = localNode.getIp();
        HotReloadTaskEntity taskEntity = selectExecutableTask(taskId);
        if (taskEntity == null) {
            return;
        }
        HotReloadTaskInstanceEntity instanceEntity = selectLocalStopRecoveryTaskInstance(taskId, ip);
        if (instanceEntity == null) {
            log.debug("忽略非本机停止重启自动恢复任务，taskId={}，ip={}", taskId, ip);
            return;
        }

        localReloadLock.lock();
        try {
            executeLocalStopRecoveryTask(taskEntity, taskId, ip);
        } finally {
            localReloadLock.unlock();
        }
    }

    /**
     * 查询可由当前节点处理的任务主表记录。
     *
     * @param taskId 任务 ID
     * @return 可执行任务，不存在或不属于当前节点所在应用环境时返回 null
     */
    private HotReloadTaskEntity selectExecutableTask(String taskId) {
        HotReloadTaskEntity taskEntity = hotReloadTaskMapper.selectById(taskId);
        if (taskEntity == null || !matchesLocalNode(taskEntity.getAppName(), taskEntity.getEnv())) {
            return null;
        }
        return taskEntity;
    }

    /**
     * 执行当前节点对应的热重载任务实例。
     * <p>
     * 本方法按“接收、预检查、重载、保存恢复文件、成功/失败回写”的顺序推进实例状态。
     *
     * @param taskEntity 任务主表记录
     * @param taskId     任务 ID
     * @param ip         当前节点 IP
     */
    private void executeLocalReloadTask(HotReloadTaskEntity taskEntity, String taskId, String ip) {
        String executeType = HotReloadConstants.EXECUTE_TYPE_NORMAL;
        HotReloadTaskInstanceEntity instanceEntity = null;
        try {
            instanceEntity = selectLocalReloadTaskInstance(taskId, ip);
            if (instanceEntity == null) {
                return;
            }
            if (!HotReloadConstants.INSTANCE_STATUS_PENDING.equals(instanceEntity.getStatus())) {
                log.info("热重载任务已处理，taskId={}，ip={}，status={}", taskId, ip, instanceEntity.getStatus());
                return;
            }
            if (!markReceived(taskId, ip, executeType)) {
                return;
            }
            updateHotReloadExecutionStatus(taskEntity, instanceEntity, HotReloadConstants.INSTANCE_STATUS_RECEIVED);

            updateInstanceStatus(taskId, ip, executeType, HotReloadConstants.INSTANCE_STATUS_PRECHECKING, null, null);
            updateHotReloadExecutionStatus(taskEntity, instanceEntity, HotReloadConstants.INSTANCE_STATUS_PRECHECKING);
            List<HotReloadFileEntity> files = selectTaskFiles(taskId);
            try {
                precheck(files);
            } catch (Exception e) {
                log.error("热重载任务预检查失败，taskId={}，ip={}", taskId, ip, e);
                markPrecheckFailed(taskId, ip, executeType, e.getMessage());
                updateHotReloadExecutionStatus(taskEntity, instanceEntity,
                        HotReloadConstants.INSTANCE_STATUS_PRECHECK_FAILED);
                return;
            }

            updateInstanceStatus(taskId, ip, executeType, HotReloadConstants.INSTANCE_STATUS_RELOADING, null, null);
            updateHotReloadExecutionStatus(taskEntity, instanceEntity, HotReloadConstants.INSTANCE_STATUS_RELOADING);
            markStartTime(taskId, ip, executeType);

            List<String> reloadResults = new ArrayList<>();
            boolean persistOnRestart = isPersistOnRestart(taskEntity.getPersistOnRestart());
            for (HotReloadFileEntity fileEntity : files) {
                reloadResults.add(executeFile(fileEntity));
            }

            updateInstanceStatus(taskId, ip, executeType, HotReloadConstants.INSTANCE_STATUS_VERIFYING, null, null);
            updateHotReloadExecutionStatus(taskEntity, instanceEntity, HotReloadConstants.INSTANCE_STATUS_VERIFYING);
            saveOrDeleteRecoverFiles(taskEntity, files, persistOnRestart);
            reloadResults.add("恢复文件策略：persistOnRestart=" + persistOnRestart);
            markSuccess(taskId, ip, executeType, StringUtils.join(reloadResults, "\n"));
            updateHotReloadExecutionStatus(taskEntity, instanceEntity, HotReloadConstants.INSTANCE_STATUS_SUCCESS);
        } catch (Exception e) {
            log.error("热重载任务执行失败，taskId={}，ip={}", taskId, ip, e);
            if (instanceEntity != null) {
                markFailed(taskId, ip, executeType, "RELOAD_FAILED", e.getMessage());
                updateHotReloadExecutionStatus(taskEntity, instanceEntity, HotReloadConstants.INSTANCE_STATUS_FAILED);
            }
        }
    }

    /**
     * 执行当前节点对应的停止重启自动恢复任务实例。
     *
     * @param taskEntity 任务主表记录
     * @param taskId     任务 ID
     * @param ip         当前节点 IP
     */
    private void executeLocalStopRecoveryTask(HotReloadTaskEntity taskEntity, String taskId, String ip) {
        String executeType = HotReloadConstants.EXECUTE_TYPE_STOP_RECOVERY;
        HotReloadTaskInstanceEntity instanceEntity = null;
        try {
            instanceEntity = selectLocalStopRecoveryTaskInstance(taskId, ip);
            if (instanceEntity == null) {
                return;
            }
            if (!HotReloadConstants.INSTANCE_STATUS_PENDING.equals(instanceEntity.getStatus())) {
                log.info("停止重启自动恢复任务已处理，taskId={}，ip={}，status={}", taskId, ip, instanceEntity.getStatus());
                return;
            }
            if (!markReceived(taskId, ip, executeType)) {
                return;
            }
            updateHotReloadExecutionStatus(taskEntity, instanceEntity, HotReloadConstants.INSTANCE_STATUS_RECEIVED);

            updateInstanceStatus(taskId, ip, executeType, HotReloadConstants.INSTANCE_STATUS_RELOADING, null, null);
            updateHotReloadExecutionStatus(taskEntity, instanceEntity, HotReloadConstants.INSTANCE_STATUS_RELOADING);
            markStartTime(taskId, ip, executeType);

            String stopResult = recoverFileStore.deleteRecoverFiles(taskEntity.getReloadType());
            markSuccess(taskId, ip, executeType, stopResult);
            updateHotReloadExecutionStatus(taskEntity, instanceEntity, HotReloadConstants.INSTANCE_STATUS_SUCCESS);
        } catch (Exception e) {
            log.error("停止重启自动恢复任务执行失败，taskId={}，ip={}", taskId, ip, e);
            if (instanceEntity != null) {
                markFailed(taskId, ip, executeType, "STOP_RECOVERY_FAILED", e.getMessage());
                updateHotReloadExecutionStatus(taskEntity, instanceEntity, HotReloadConstants.INSTANCE_STATUS_FAILED);
            }
        }
    }

    /**
     * 执行前检查任务文件是否存在。
     *
     * @param files 任务文件列表
     */
    private void precheck(List<HotReloadFileEntity> files) {
        if (CollectionUtils.isEmpty(files)) {
            throw new HotReloadException("任务补丁文件不存在");
        }
    }

    /**
     * 按文件类型执行单个补丁文件。
     *
     * @param fileEntity 任务文件记录
     * @return 单个文件的热重载结果
     * @throws Exception 本机热重载失败时抛出
     */
    private String executeFile(HotReloadFileEntity fileEntity) throws Exception {
        String fileType = fileEntity.getFileType();
        byte[] fileContent = fileEntity.getFileContent();
        if (HotReloadConstants.FILE_TYPE_CLASS.equals(fileType)) {
            return hotReloadRuntimeExecutor.reloadClassRuntime(fileContent);
        }
        if (HotReloadConstants.FILE_TYPE_MYBATIS_XML.equals(fileType)) {
            return hotReloadRuntimeExecutor.reloadMyBatisXmlRuntime(fileContent);
        }
        throw new HotReloadException("不支持的热重载类型：" + fileType);
    }

    /**
     * 根据任务策略同步所有补丁文件的重启恢复文件。
     *
     * @param taskEntity       任务主表记录
     * @param files            任务文件列表
     * @param persistOnRestart 是否需要重启恢复
     * @throws Exception 恢复文件写入或删除失败时抛出
     */
    private void saveOrDeleteRecoverFiles(HotReloadTaskEntity taskEntity, List<HotReloadFileEntity> files,
                                          boolean persistOnRestart) throws Exception {
        for (HotReloadFileEntity fileEntity : files) {
            saveOrDeleteRecoverFile(taskEntity, fileEntity, persistOnRestart);
        }
    }

    /**
     * 根据单个补丁文件类型同步对应目录下的恢复文件。
     *
     * @param taskEntity       任务主表记录
     * @param fileEntity       任务文件记录
     * @param persistOnRestart 是否需要重启恢复
     * @throws Exception 恢复文件写入或删除失败时抛出
     */
    private void saveOrDeleteRecoverFile(HotReloadTaskEntity taskEntity, HotReloadFileEntity fileEntity,
                                         boolean persistOnRestart) throws Exception {
        String fileType = fileEntity.getFileType();
        byte[] fileContent = fileEntity.getFileContent();
        HotReloadRecoverFileMeta recoverMeta = buildRecoverFileMeta(taskEntity, fileEntity);
        if (HotReloadConstants.FILE_TYPE_CLASS.equals(fileType)) {
            recoverFileStore.syncClassRecoverFile(fileEntity.getFileName(), fileContent,
                    persistOnRestart, recoverMeta);
        } else if (HotReloadConstants.FILE_TYPE_MYBATIS_XML.equals(fileType)) {
            recoverFileStore.syncMyBatisXmlRecoverFile(fileContent, persistOnRestart, recoverMeta);
        } else {
            throw new HotReloadException("不支持的热重载类型：" + fileType);
        }
    }

    /**
     * 构建恢复文件元数据，便于服务重启后恢复执行并写回任务实例日志。
     *
     * @param taskEntity 任务主表记录
     * @param fileEntity 任务文件记录
     * @return 恢复文件元数据
     */
    private HotReloadRecoverFileMeta buildRecoverFileMeta(HotReloadTaskEntity taskEntity,
                                                          HotReloadFileEntity fileEntity) {
        HotReloadRecoverFileMeta recoverMeta = new HotReloadRecoverFileMeta();
        recoverMeta.setTaskId(taskEntity.getTaskId());
        recoverMeta.setAppName(taskEntity.getAppName());
        recoverMeta.setEnv(taskEntity.getEnv());
        recoverMeta.setIp(localNode.getIp());
        recoverMeta.setFileId(fileEntity.getFileId());
        recoverMeta.setFileName(fileEntity.getFileName());
        recoverMeta.setFileType(fileEntity.getFileType());
        recoverMeta.setClassName(fileEntity.getClassName());
        recoverMeta.setFileSha256(fileEntity.getFileSha256());
        return recoverMeta;
    }

    /**
     * 把当前节点实例从 PENDING 原子更新为 RECEIVED。
     *
     * @param taskId      任务 ID
     * @param ip          节点 IP
     * @param executeType 执行类型
     * @return true 表示当前节点成功抢到该实例执行权
     */
    private boolean markReceived(String taskId, String ip, String executeType) {
        HotReloadTaskInstanceEntity update = new HotReloadTaskInstanceEntity();
        update.setStatus(HotReloadConstants.INSTANCE_STATUS_RECEIVED);
        update.setReceiveTime(new Date());
        LambdaUpdateWrapper<HotReloadTaskInstanceEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HotReloadTaskInstanceEntity::getTaskId, taskId);
        wrapper.eq(HotReloadTaskInstanceEntity::getIp, ip);
        wrapper.eq(HotReloadTaskInstanceEntity::getExecuteType, executeType);
        wrapper.eq(HotReloadTaskInstanceEntity::getStatus, HotReloadConstants.INSTANCE_STATUS_PENDING);
        return hotReloadTaskInstanceMapper.update(update, wrapper) == 1;
    }

    /**
     * 更新当前节点实例状态和错误信息。
     *
     * @param taskId       任务 ID
     * @param ip           节点 IP
     * @param executeType  执行类型
     * @param status       实例状态
     * @param errorCode    错误编码
     * @param errorMessage 错误消息
     */
    private void updateInstanceStatus(String taskId, String ip, String executeType, String status,
                                      String errorCode, String errorMessage) {
        HotReloadTaskInstanceEntity update = new HotReloadTaskInstanceEntity();
        update.setStatus(status);
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        LambdaUpdateWrapper<HotReloadTaskInstanceEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HotReloadTaskInstanceEntity::getTaskId, taskId);
        wrapper.eq(HotReloadTaskInstanceEntity::getIp, ip);
        wrapper.eq(HotReloadTaskInstanceEntity::getExecuteType, executeType);
        hotReloadTaskInstanceMapper.update(update, wrapper);
    }

    /**
     * 记录当前节点实例开始执行时间。
     *
     * @param taskId      任务 ID
     * @param ip          节点 IP
     * @param executeType 执行类型
     */
    private void markStartTime(String taskId, String ip, String executeType) {
        HotReloadTaskInstanceEntity update = new HotReloadTaskInstanceEntity();
        update.setStartTime(new Date());
        LambdaUpdateWrapper<HotReloadTaskInstanceEntity> wrapper = taskInstanceWrapper(taskId, ip, executeType);
        hotReloadTaskInstanceMapper.update(update, wrapper);
    }

    /**
     * 标记当前节点实例执行成功。
     *
     * @param taskId       任务 ID
     * @param ip           节点 IP
     * @param executeType  执行类型
     * @param reloadResult 执行结果
     */
    private void markSuccess(String taskId, String ip, String executeType, String reloadResult) {
        HotReloadTaskInstanceEntity update = new HotReloadTaskInstanceEntity();
        update.setStatus(HotReloadConstants.INSTANCE_STATUS_SUCCESS);
        update.setReloadResult(reloadResult);
        update.setFinishTime(new Date());
        hotReloadTaskInstanceMapper.update(update, taskInstanceWrapper(taskId, ip, executeType));
    }

    /**
     * 标记当前节点实例执行失败。
     *
     * @param taskId       任务 ID
     * @param ip           节点 IP
     * @param executeType  执行类型
     * @param errorCode    错误编码
     * @param errorMessage 错误消息
     */
    private void markFailed(String taskId, String ip, String executeType, String errorCode, String errorMessage) {
        HotReloadTaskInstanceEntity update = new HotReloadTaskInstanceEntity();
        update.setStatus(HotReloadConstants.INSTANCE_STATUS_FAILED);
        update.setErrorCode(errorCode);
        update.setErrorMessage(StringUtils.abbreviate(errorMessage, 1900));
        update.setFinishTime(new Date());
        hotReloadTaskInstanceMapper.update(update, taskInstanceWrapper(taskId, ip, executeType));
    }

    /**
     * 标记当前节点实例预检查失败。
     *
     * @param taskId       任务 ID
     * @param ip           节点 IP
     * @param executeType  执行类型
     * @param errorMessage 预检查错误消息
     */
    private void markPrecheckFailed(String taskId, String ip, String executeType, String errorMessage) {
        HotReloadTaskInstanceEntity update = new HotReloadTaskInstanceEntity();
        update.setStatus(HotReloadConstants.INSTANCE_STATUS_PRECHECK_FAILED);
        update.setErrorCode("PRECHECK_FAILED");
        update.setErrorMessage(StringUtils.abbreviate(errorMessage, 1900));
        update.setFinishTime(new Date());
        hotReloadTaskInstanceMapper.update(update, taskInstanceWrapper(taskId, ip, executeType));
    }

    /**
     * 构造指定执行类型实例的更新条件。
     *
     * @param taskId      任务 ID
     * @param ip          节点 IP
     * @param executeType 执行类型
     * @return MyBatis-Plus 更新条件
     */
    private LambdaUpdateWrapper<HotReloadTaskInstanceEntity> taskInstanceWrapper(String taskId, String ip,
                                                                                String executeType) {
        LambdaUpdateWrapper<HotReloadTaskInstanceEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HotReloadTaskInstanceEntity::getTaskId, taskId);
        wrapper.eq(HotReloadTaskInstanceEntity::getIp, ip);
        wrapper.eq(HotReloadTaskInstanceEntity::getExecuteType, executeType);
        return wrapper;
    }

    /**
     * 查询当前节点对应的热重载任务实例。
     *
     * @param taskId 任务 ID
     * @param ip     当前节点 IP
     * @return 节点任务实例，不存在时返回 null
     */
    private HotReloadTaskInstanceEntity selectLocalReloadTaskInstance(String taskId, String ip) {
        return selectLocalTaskInstance(taskId, ip, HotReloadConstants.EXECUTE_TYPE_NORMAL);
    }

    /**
     * 查询当前节点对应的停止重启自动恢复任务实例。
     *
     * @param taskId 任务 ID
     * @param ip     当前节点 IP
     * @return 节点任务实例，不存在时返回 null
     */
    private HotReloadTaskInstanceEntity selectLocalStopRecoveryTaskInstance(String taskId, String ip) {
        return selectLocalTaskInstance(taskId, ip, HotReloadConstants.EXECUTE_TYPE_STOP_RECOVERY);
    }

    /**
     * 查询当前节点对应的指定执行类型实例。
     *
     * @param taskId      任务 ID
     * @param ip          当前节点 IP
     * @param executeType 执行类型
     * @return 节点任务实例，不存在时返回 null
     */
    private HotReloadTaskInstanceEntity selectLocalTaskInstance(String taskId, String ip, String executeType) {
        LambdaQueryWrapper<HotReloadTaskInstanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotReloadTaskInstanceEntity::getTaskId, taskId);
        wrapper.eq(HotReloadTaskInstanceEntity::getIp, ip);
        wrapper.eq(HotReloadTaskInstanceEntity::getExecuteType, executeType);
        return hotReloadTaskInstanceMapper.selectOne(wrapper);
    }

    /**
     * 查询任务包含的补丁文件。
     *
     * @param taskId 任务 ID
     * @return 补丁文件列表
     */
    private List<HotReloadFileEntity> selectTaskFiles(String taskId) {
        LambdaQueryWrapper<HotReloadFileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotReloadFileEntity::getTaskId, taskId);
        return hotReloadFileMapper.selectList(wrapper);
    }

    /**
     * 构造任务日志主表查询条件。
     *
     * @param queryVO         日志查询条件
     * @param filteredTaskIds 已由实例条件筛选出的任务 ID，可为空
     * @return 任务主表查询条件
     */
    private LambdaQueryWrapper<HotReloadTaskEntity> buildTaskLogQueryWrapper(HotReloadTaskLogQueryVO queryVO,
                                                                             Set<String> filteredTaskIds) {
        LambdaQueryWrapper<HotReloadTaskEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotReloadTaskEntity::getEnv, StringUtils.defaultIfBlank(queryVO.getEnv(), localNode.getEnv()));
        if (StringUtils.isNotBlank(queryVO.getTaskId())) {
            wrapper.like(HotReloadTaskEntity::getTaskId, StringUtils.trim(queryVO.getTaskId()));
        }
        if (StringUtils.isNotBlank(queryVO.getAppName())) {
            wrapper.eq(HotReloadTaskEntity::getAppName, StringUtils.trim(queryVO.getAppName()));
        }
        if (StringUtils.isNotBlank(queryVO.getPatchName())) {
            wrapper.like(HotReloadTaskEntity::getPatchName, StringUtils.trim(queryVO.getPatchName()));
        }
        if (StringUtils.isNotBlank(queryVO.getReloadType())) {
            wrapper.eq(HotReloadTaskEntity::getReloadType, StringUtils.trim(queryVO.getReloadType()).toUpperCase());
        }
        if (queryVO.getCreatedStartTime() != null) {
            wrapper.ge(HotReloadTaskEntity::getCreatedTime, queryVO.getCreatedStartTime());
        }
        if (queryVO.getCreatedEndTime() != null) {
            wrapper.le(HotReloadTaskEntity::getCreatedTime, queryVO.getCreatedEndTime());
        }
        if (filteredTaskIds != null) {
            wrapper.in(HotReloadTaskEntity::getTaskId, filteredTaskIds);
        }
        wrapper.orderByDesc(HotReloadTaskEntity::getCreatedTime);
        return wrapper;
    }

    /**
     * 根据实例维度条件反查任务 ID。
     *
     * @param queryVO 日志查询条件
     * @return 匹配的任务 ID 集合；未提供实例条件时返回 null
     */
    private Set<String> selectTaskIdsByInstanceLog(HotReloadTaskLogQueryVO queryVO) {
        boolean hasInstanceCondition = StringUtils.isNotBlank(queryVO.getIp())
                || StringUtils.isNotBlank(queryVO.getInstanceStatus())
                || StringUtils.isNotBlank(queryVO.getExecuteType());
        if (!hasInstanceCondition) {
            return null;
        }
        LambdaQueryWrapper<HotReloadTaskInstanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotReloadTaskInstanceEntity::getEnv, StringUtils.defaultIfBlank(queryVO.getEnv(), localNode.getEnv()));
        if (StringUtils.isNotBlank(queryVO.getAppName())) {
            wrapper.eq(HotReloadTaskInstanceEntity::getAppName, StringUtils.trim(queryVO.getAppName()));
        }
        if (StringUtils.isNotBlank(queryVO.getIp())) {
            wrapper.eq(HotReloadTaskInstanceEntity::getIp, StringUtils.trim(queryVO.getIp()));
        }
        if (StringUtils.isNotBlank(queryVO.getInstanceStatus())) {
            wrapper.eq(HotReloadTaskInstanceEntity::getStatus, StringUtils.trim(queryVO.getInstanceStatus()));
        }
        if (StringUtils.isNotBlank(queryVO.getExecuteType())) {
            wrapper.eq(HotReloadTaskInstanceEntity::getExecuteType, StringUtils.trim(queryVO.getExecuteType()).toUpperCase());
        }
        return hotReloadTaskInstanceMapper.selectList(wrapper).stream()
                .map(HotReloadTaskInstanceEntity::getTaskId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    /**
     * 把任务主表记录转换为日志行，并填充动态任务汇总状态。
     *
     * @param taskEntities 任务主表记录
     * @return 日志行列表
     */
    private List<HotReloadTaskVO> buildTaskLogRows(List<HotReloadTaskEntity> taskEntities) {
        if (CollectionUtils.isEmpty(taskEntities)) {
            return new ArrayList<>();
        }
        List<String> taskIds = taskEntities.stream().map(HotReloadTaskEntity::getTaskId).collect(Collectors.toList());
        LambdaQueryWrapper<HotReloadTaskInstanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(HotReloadTaskInstanceEntity::getTaskId, taskIds);
        wrapper.in(HotReloadTaskInstanceEntity::getExecuteType,
                Arrays.asList(HotReloadConstants.EXECUTE_TYPE_NORMAL, HotReloadConstants.EXECUTE_TYPE_STOP_RECOVERY));
        Map<String, List<HotReloadTaskInstanceEntity>> instanceMap = hotReloadTaskInstanceMapper.selectList(wrapper)
                .stream().collect(Collectors.groupingBy(HotReloadTaskInstanceEntity::getTaskId));

        List<HotReloadTaskVO> rows = new ArrayList<>();
        for (HotReloadTaskEntity taskEntity : taskEntities) {
            HotReloadTaskVO taskVO = new HotReloadTaskVO();
            BeanUtils.copyProperties(taskEntity, taskVO);
            applyTaskSummary(taskVO, instanceMap.get(taskEntity.getTaskId()));
            rows.add(taskVO);
        }
        return rows;
    }

    /**
     * 判断实例是否属于页面触发的任务。
     *
     * @param entity 节点实例
     * @return true 表示普通热重载或停止恢复任务实例
     */
    private boolean isUserTaskInstance(HotReloadTaskInstanceEntity entity) {
        return entity != null && !HotReloadConstants.EXECUTE_TYPE_RECOVER.equals(entity.getExecuteType());
    }

    /**
     * 对内存中的任务日志行做分页。
     * <p>
     * 任务状态由实例动态计算，因此按任务状态过滤时需要先查出候选任务再分页。
     *
     * @param page 分页参数
     * @param rows 已过滤的任务日志行
     * @return 分页结果
     */
    private PageResultVO<HotReloadTaskVO> pageTaskLogRows(PageRequestVO<HotReloadTaskLogQueryVO> page,
                                                          List<HotReloadTaskVO> rows) {
        long pageStart = Math.max(page.getPageStart(), 1);
        long pageNums = page.getPageNums() <= 0 ? 10 : page.getPageNums();
        int fromIndex = (int) Math.min((pageStart - 1) * pageNums, rows.size());
        int toIndex = (int) Math.min(fromIndex + pageNums, rows.size());
        PageResultVO<HotReloadTaskVO> result = new PageResultVO<>();
        result.setPageStart(pageStart);
        result.setPageNums(pageNums);
        result.setTotal(rows.size());
        result.setRows(new ArrayList<>(rows.subList(fromIndex, toIndex)));
        return result;
    }

    /**
     * 构造空分页结果。
     *
     * @param page 分页参数
     * @return 空分页结果
     */
    private PageResultVO<HotReloadTaskVO> emptyTaskLogGrid(PageRequestVO<HotReloadTaskLogQueryVO> page) {
        PageResultVO<HotReloadTaskVO> result = new PageResultVO<>();
        result.setPageStart(page.getPageStart());
        result.setPageNums(page.getPageNums());
        result.setTotal(0L);
        result.setRows(new ArrayList<>());
        return result;
    }

    /**
     * 根据实例列表计算并写入任务汇总字段。
     *
     * @param taskVO    任务 VO
     * @param instances 普通热重载实例列表
     */
    private void applyTaskSummary(HotReloadTaskVO taskVO, List<HotReloadTaskInstanceEntity> instances) {
        TaskSummary summary = buildTaskSummary(instances);
        taskVO.setStatus(summary.taskStatus);
        taskVO.setTotalCount(summary.totalCount);
        taskVO.setSuccessCount(summary.successCount);
        taskVO.setFailedCount(summary.failedCount);
        taskVO.setTimeoutCount(summary.timeoutCount);
    }

    /**
     * 从节点实例状态动态计算任务状态和数量统计。
     *
     * @param instances 普通热重载实例列表
     * @return 任务汇总结果
     */
    private TaskSummary buildTaskSummary(List<HotReloadTaskInstanceEntity> instances) {
        TaskSummary summary = new TaskSummary();
        if (CollectionUtils.isEmpty(instances)) {
            summary.taskStatus = HotReloadConstants.TASK_STATUS_FAILED;
            return summary;
        }
        summary.totalCount = instances.size();
        for (HotReloadTaskInstanceEntity instance : instances) {
            if (HotReloadConstants.INSTANCE_STATUS_SUCCESS.equals(instance.getStatus())) {
                summary.successCount++;
            } else if (HotReloadConstants.INSTANCE_STATUS_TIMEOUT.equals(instance.getStatus())) {
                summary.timeoutCount++;
                summary.failedCount++;
            } else if (RUNNING_INSTANCE_STATUS.contains(instance.getStatus())) {
                summary.running = true;
            } else {
                summary.failedCount++;
            }
        }
        if (summary.successCount == summary.totalCount) {
            summary.taskStatus = HotReloadConstants.TASK_STATUS_SUCCESS;
        } else if (summary.running) {
            summary.taskStatus = HotReloadConstants.TASK_STATUS_RUNNING;
        } else if (summary.successCount > 0) {
            summary.taskStatus = HotReloadConstants.TASK_STATUS_PARTIAL_SUCCESS;
        } else {
            summary.taskStatus = HotReloadConstants.TASK_STATUS_FAILED;
        }
        return summary;
    }

    /**
     * 任务汇总中间结果。
     */
    private static class TaskSummary {
        private String taskStatus = HotReloadConstants.TASK_STATUS_RUNNING;
        private int totalCount;
        private int successCount;
        private int failedCount;
        private int timeoutCount;
        private boolean running;
    }

    /**
     * 根据被重试的实例类型决定 Redis 任务通知类型。
     *
     * @param instances 被重试的任务实例
     * @return Redis 消息类型
     */
    private String resolveTaskMessageType(List<HotReloadTaskInstanceEntity> instances) {
        Set<String> executeTypes = instances.stream()
                .map(HotReloadTaskInstanceEntity::getExecuteType)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        if (executeTypes.size() != 1) {
            throw new HotReloadException("同一次重试不能混合不同执行类型的任务实例");
        }
        String executeType = executeTypes.iterator().next();
        if (HotReloadConstants.EXECUTE_TYPE_STOP_RECOVERY.equals(executeType)) {
            return HotReloadConstants.MESSAGE_TYPE_STOP_RECOVERY_TASK_NOTIFY;
        }
        return HotReloadConstants.MESSAGE_TYPE_RELOAD_TASK_NOTIFY;
    }

    /**
     * 发布任务通知。
     *
     * @param taskEntity  任务主表记录
     * @param messageType Redis 消息类型
     */
    private void publishTask(HotReloadTaskEntity taskEntity, String messageType) {
        HotReloadTaskMessage message = new HotReloadTaskMessage();
        message.setMessageType(messageType);
        message.setAppName(taskEntity.getAppName());
        message.setEnv(taskEntity.getEnv());
        message.setTaskId(taskEntity.getTaskId());
        message.setPublishTime(new Date());
        redisPublisher.pushMessage(hotReloadProperties.getRedisTopic(), (JSONObject) JSONObject.toJSON(message));
        log.info("热重载相关任务消息已发布，taskId={}，messageType={}", taskEntity.getTaskId(), messageType);
    }

    /**
     * 把当前节点信息写入 Redis 节点发现结果。
     */
    private void registerCurrentNode() {
        HotReloadNodeVO nodeVO = currentNode();
        nodeVO.setUpdateTime(new Date());
        nodeVO.setNodeStatus(null);
        hotReloadRedisTemplate.opsForHash().put(discoverNodeKey(nodeVO.getEnv(), nodeVO.getAppName()),
                nodeVO.getIp(), JSONObject.toJSONString(nodeVO));
    }

    /**
     * 从 Redis 读取节点发现结果并计算节点在线状态。
     *
     * @param nodeKey Redis hash key
     * @return 节点列表
     */
    private List<HotReloadNodeVO> readDiscoverNodes(String nodeKey) {
        List<Object> values = hotReloadRedisTemplate.opsForHash().values(nodeKey);
        if (CollectionUtils.isEmpty(values)) {
            return new ArrayList<>();
        }
        List<HotReloadNodeVO> nodes = new ArrayList<>();
        for (Object value : values) {
            try {
                String json = value instanceof String ? (String) value : JSONObject.toJSONString(value);
                HotReloadNodeVO nodeVO = JSONObject.parseObject(json, HotReloadNodeVO.class);
                applyNodeStatus(nodeVO);
                nodes.add(nodeVO);
            } catch (Exception e) {
                log.error("解析热重载节点发现结果失败，value={}", value, e);
            }
        }
        return nodes.stream()
                .sorted((a, b) -> StringUtils.defaultString(a.getIp()).compareTo(StringUtils.defaultString(b.getIp())))
                .collect(Collectors.toList());
    }

    /**
     * 根据节点更新时间计算页面展示的在线状态。
     *
     * @param nodeVO 节点信息
     */
    private void applyNodeStatus(HotReloadNodeVO nodeVO) {
        if (nodeVO == null) {
            return;
        }
        Date updateTime = nodeVO.getUpdateTime();
        if (updateTime == null || System.currentTimeMillis() - updateTime.getTime() > NODE_STATUS_EXPIRE_MILLIS) {
            nodeVO.setNodeStatus(HotReloadConstants.NODE_STATUS_EXPIRED);
            return;
        }
        nodeVO.setNodeStatus(HotReloadConstants.NODE_STATUS_ONLINE);
    }

    /**
     * 同步更新 Redis 中对应节点的最近热重载相关操作状态。
     *
     * @param taskEntity     任务主表记录
     * @param instanceEntity 任务实例记录
     * @param status         最近热重载相关操作状态
     */
    private void updateHotReloadExecutionStatus(HotReloadTaskEntity taskEntity,
                                                HotReloadTaskInstanceEntity instanceEntity,
                                                String status) {
        if (taskEntity == null || instanceEntity == null) {
            return;
        }
        updateRedisNodeStatus(taskEntity.getEnv(), taskEntity.getAppName(), instanceEntity.getIp(),
                status, taskEntity.getTaskId());
    }

    /**
     * 更新 Redis 节点记录中的热重载相关操作状态和最近任务 ID。
     *
     * @param env        运行环境
     * @param appName    应用名称
     * @param ip         节点 IP
     * @param status     最近热重载相关操作状态
     * @param lastTaskId 最近任务 ID
     */
    private void updateRedisNodeStatus(String env, String appName, String ip, String status, String lastTaskId) {
        if (StringUtils.isBlank(env) || StringUtils.isBlank(appName) || StringUtils.isBlank(ip)) {
            return;
        }
        HotReloadNodeVO nodeVO = readRedisNode(env, appName, ip);
        if (nodeVO == null) {
            nodeVO = new HotReloadNodeVO();
            nodeVO.setEnv(env);
            nodeVO.setAppName(appName);
            nodeVO.setIp(ip);
        }
        nodeVO.setUpdateTime(new Date());
        nodeVO.setNodeStatus(null);
        nodeVO.setHotReloadStatus(StringUtils.defaultIfBlank(status, HotReloadConstants.HOT_RELOAD_STATUS_IDLE));
        nodeVO.setLastTaskId(lastTaskId);
        hotReloadRedisTemplate.opsForHash().put(discoverNodeKey(env, appName), ip, JSONObject.toJSONString(nodeVO));
        if (matchesLocalNode(appName, env) && StringUtils.equals(ip, localNode.getIp())) {
            localHotReloadStatus = nodeVO.getHotReloadStatus();
            localLastTaskId = lastTaskId;
        }
    }

    /**
     * 从 Redis 节点 hash 中读取指定节点。
     *
     * @param env     运行环境
     * @param appName 应用名称
     * @param ip      节点 IP
     * @return Redis 节点记录，不存在或解析失败时返回 null
     */
    private HotReloadNodeVO readRedisNode(String env, String appName, String ip) {
        Object value = hotReloadRedisTemplate.opsForHash().get(discoverNodeKey(env, appName), ip);
        if (value == null) {
            return null;
        }
        try {
            String json = value instanceof String ? (String) value : JSONObject.toJSONString(value);
            return JSONObject.parseObject(json, HotReloadNodeVO.class);
        } catch (Exception e) {
            log.warn("解析 Redis 热重载节点信息失败，appName={}，env={}，ip={}", appName, env, ip, e);
            return null;
        }
    }

    /**
     * 查询热重载配置明细。
     *
     * @param detailCode 配置明细编码
     * @return 配置明细记录
     */
    private SysConfigDetailEntity selectReloadConfigDetail(String detailCode) {
        LambdaQueryWrapper<SysConfigDetailEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfigDetailEntity::getConfigCode, RELOAD_CONFIG_CODE);
        wrapper.eq(SysConfigDetailEntity::getDetailCode, detailCode);
        return sysConfigDetailMapper.selectOne(wrapper);
    }

    /**
     * 判断数据库中的热重载开关是否开启。
     *
     * @return true 表示允许创建和执行热重载任务
     */
    private boolean isHotReloadEnabled() {
        SysConfigDetailEntity switchConfig = selectReloadConfigDetail(RELOAD_SWITCH_DETAIL_CODE);
        return switchConfig != null && BooleanUtils.isTrue(switchConfig.getDetailValue());
    }

    /**
     * 校验热重载开关，未开启时直接阻断页面创建任务。
     */
    private void assertHotReloadEnabled() {
        if (!isHotReloadEnabled()) {
            throw new HotReloadException("热重载开关未开启");
        }
    }

    /**
     * 构造默认应用配置。
     * <p>
     * 数据库未配置服务列表时，默认只返回当前应用，便于本地 demo 快速启动。
     *
     * @return 默认应用配置
     */
    private List<HotReloadAppVO> defaultAppConfig() {
        HotReloadAppVO appVO = new HotReloadAppVO();
        appVO.setAppName(localNode.getAppName());
        return Arrays.asList(appVO);
    }

    /**
     * 解析必填应用名称。
     *
     * @param appName 页面传入应用名称
     * @return 去除前后空格后的应用名称
     */
    private String resolveRequiredAppName(String appName) {
        if (StringUtils.isBlank(appName)) {
            throw new HotReloadException("appName 不能为空");
        }
        return StringUtils.trim(appName);
    }

    /**
     * 补齐创建任务请求的默认值。
     *
     * @param requestVO 创建任务请求
     */
    private void fillCreateTaskRequest(HotReloadCreateTaskRequestVO requestVO) {
        requestVO.setPersistOnRestart(normalizePersistOnRestart(requestVO.getPersistOnRestart()));
    }

    /**
     * 规范化重启恢复开关。
     *
     * @param persistOnRestart 页面传入开关值
     * @return Y 或 N
     */
    private String normalizePersistOnRestart(String persistOnRestart) {
        if (StringUtils.equalsIgnoreCase(HotReloadConstants.FLAG_Y, persistOnRestart)
                || StringUtils.equalsIgnoreCase("true", persistOnRestart)
                || StringUtils.equals("1", persistOnRestart)) {
            return HotReloadConstants.FLAG_Y;
        }
        return HotReloadConstants.FLAG_N;
    }

    /**
     * 判断任务是否需要保存重启恢复文件。
     *
     * @param persistOnRestart Y/N 开关值
     * @return true 表示需要保存恢复文件
     */
    private boolean isPersistOnRestart(String persistOnRestart) {
        return StringUtils.equalsIgnoreCase(HotReloadConstants.FLAG_Y, persistOnRestart);
    }

    /**
     * 校验创建任务请求和目标节点。
     *
     * @param file      上传文件
     * @param requestVO 创建任务请求
     */
    private void validateCreateTask(MultipartFile file, HotReloadCreateTaskRequestVO requestVO) {
        if (file == null || file.isEmpty()) {
            throw new HotReloadException("热重载文件不能为空");
        }
        normalizeUploadFileName(file.getOriginalFilename());
        if (requestVO == null) {
            throw new HotReloadException("创建热重载任务请求不能为空");
        }
        if (CollectionUtils.isEmpty(requestVO.getIps())) {
            throw new HotReloadException("请选择热重载目标节点");
        }
        String targetAppName = resolveRequiredAppName(requestVO.getAppName());
        String targetEnv = localNode.getEnv();
        requestVO.setAppName(targetAppName);
        requestVO.setIps(normalizeTargetIps(targetEnv, targetAppName, requestVO.getIps()));
    }

    /**
     * 校验停止重启自动恢复任务请求。
     *
     * @param requestVO 停止恢复任务请求
     */
    private void validateStopRecoveryTask(HotReloadStopRecoveryRequestVO requestVO) {
        if (requestVO == null) {
            throw new HotReloadException("停止重启自动恢复请求不能为空");
        }
        String targetAppName = resolveRequiredAppName(requestVO.getAppName());
        String targetEnv = localNode.getEnv();
        requestVO.setAppName(targetAppName);
        requestVO.setFileType(normalizeStopRecoveryFileType(requestVO.getFileType()));
        requestVO.setIps(normalizeTargetIps(targetEnv, targetAppName, requestVO.getIps()));
    }

    /**
     * 校验并规范化目标节点 IP 列表。
     *
     * @param targetEnv     目标运行环境
     * @param targetAppName 目标应用名称
     * @param ips           页面选择的目标 IP
     * @return 去重校验后的目标 IP
     */
    private List<String> normalizeTargetIps(String targetEnv, String targetAppName, List<String> ips) {
        if (CollectionUtils.isEmpty(ips)) {
            throw new HotReloadException("请选择目标节点");
        }
        Set<String> nodeIps = new HashSet<>();
        List<String> normalizedIps = new ArrayList<>();
        for (String ip : ips) {
            String targetIp = StringUtils.trim(ip);
            if (StringUtils.isBlank(targetIp)) {
                throw new HotReloadException("目标节点 IP 不能为空");
            }
            if (!nodeIps.add(targetIp)) {
                throw new HotReloadException("目标节点 IP 不能重复");
            }
            if (readRedisNode(targetEnv, targetAppName, targetIp) == null) {
                throw new HotReloadException("目标节点未在当前发现结果中：" + targetIp);
            }
            normalizedIps.add(targetIp);
        }
        return normalizedIps;
    }

    /**
     * 规范化停止重启自动恢复范围。
     *
     * @param fileType 页面传入的文件类型
     * @return 规范化后的停止恢复范围
     */
    private String normalizeStopRecoveryFileType(String fileType) {
        String targetType = StringUtils.defaultIfBlank(StringUtils.trim(fileType), HotReloadConstants.FILE_TYPE_ALL)
                .toUpperCase();
        if (!SUPPORTED_STOP_RECOVERY_FILE_TYPES.contains(targetType)) {
            throw new HotReloadException("不支持的停止恢复文件类型：" + fileType);
        }
        return targetType;
    }

    /**
     * 根据上传文件后缀识别热重载类型。
     *
     * @param fileName 上传文件名
     * @return CLASS 或 MYBATIS_XML
     */
    private String resolveReloadType(String fileName) {
        if (StringUtils.endsWithIgnoreCase(fileName, ".class")) {
            return HotReloadConstants.FILE_TYPE_CLASS;
        }
        if (StringUtils.endsWithIgnoreCase(fileName, ".xml")) {
            return HotReloadConstants.FILE_TYPE_MYBATIS_XML;
        }
        throw new HotReloadException("热重载文件只允许上传 .class 或 .xml 文件：" + fileName);
    }

    /**
     * 校验后缀识别出的热重载文件内容。
     *
     * @param reloadType 根据文件后缀识别出的热重载类型
     * @param fileBytes  上传文件内容
     * @param fileName   上传文件名
     */
    private void validateReloadFile(String reloadType, byte[] fileBytes, String fileName) {
        if (HotReloadConstants.FILE_TYPE_CLASS.equals(reloadType)) {
            validateClassFile(fileBytes, fileName);
            return;
        }
        if (HotReloadConstants.FILE_TYPE_MYBATIS_XML.equals(reloadType) && !isMyBatisXml(fileBytes)) {
            throw new HotReloadException("热重载文件不是 MyBatis XML：" + fileName);
        }
    }

    /**
     * 校验文件内容是否为 class 文件。
     *
     * @param fileBytes 上传文件内容
     * @param fileName  上传文件名
     */
    private void validateClassFile(byte[] fileBytes, String fileName) {
        if (!isClassFile(fileBytes)) {
            throw new HotReloadException("热重载文件不是 class 文件：" + fileName);
        }
    }

    /**
     * 校验上传文件名后缀。
     *
     * @param fileName 上传文件名
     */
    private void validateUploadFileName(String fileName) {
        String targetFileName = StringUtils.trim(fileName);
        if (StringUtils.isBlank(targetFileName)
                || !(StringUtils.endsWithIgnoreCase(targetFileName, ".class")
                || StringUtils.endsWithIgnoreCase(targetFileName, ".xml"))) {
            throw new HotReloadException("热重载文件只允许上传 .class 或 .xml 文件：" + fileName);
        }
    }

    /**
     * 规范化上传文件名并执行后缀校验。
     *
     * @param fileName 上传文件名
     * @return 去除前后空格后的文件名
     */
    private String normalizeUploadFileName(String fileName) {
        String targetFileName = StringUtils.trim(fileName);
        validateUploadFileName(targetFileName);
        return targetFileName;
    }

    /**
     * 判断文件内容是否为 JVM class 文件。
     *
     * @param fileBytes 上传文件内容
     * @return true 表示文件头为 CAFEBABE
     */
    private boolean isClassFile(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length < 4) {
            return false;
        }
        int magic = ((fileBytes[0] & 0xFF) << 24)
                | ((fileBytes[1] & 0xFF) << 16)
                | ((fileBytes[2] & 0xFF) << 8)
                | (fileBytes[3] & 0xFF);
        return magic == 0xCAFEBABE;
    }

    /**
     * 判断文件内容是否为 MyBatis Mapper XML。
     *
     * @param fileBytes 上传文件内容
     * @return true 表示包含 mapper 节点和 namespace
     */
    private boolean isMyBatisXml(byte[] fileBytes) {
        for (String charset : new String[]{"UTF-8", "GBK"}) {
            String content = new String(fileBytes, Charset.forName(charset));
            if (content.contains("<mapper") && content.contains("namespace=\"")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 尝试从 class 文件解析类名。
     *
     * @param fileBytes 上传文件内容
     * @return class 完整类名，非 class 文件或解析失败时返回 null
     */
    private String parseClassNameQuietly(byte[] fileBytes) {
        try {
            return isClassFile(fileBytes) ? HotReloadUtils.parseClassName(fileBytes) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 读取上传文件内容。
     *
     * @param file 上传文件
     * @return 文件字节内容
     */
    private byte[] readFileBytes(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return HotReloadUtils.readBytes(inputStream);
        } catch (Exception e) {
            throw new HotReloadException("读取热重载文件失败：" + e.getMessage(), e);
        }
    }

    /**
     * 判断消息或任务是否属于当前服务实例所在的应用和环境。
     *
     * @param appName 应用名称
     * @param env     运行环境
     * @return true 表示当前节点需要处理
     */
    private boolean matchesLocalNode(String appName, String env) {
        return StringUtils.equals(appName, localNode.getAppName())
                && StringUtils.equals(env, localNode.getEnv());
    }

    /**
     * 构造 Redis 节点发现 hash key。
     *
     * @param env     运行环境
     * @param appName 应用名称
     * @return Redis key
     */
    private String discoverNodeKey(String env, String appName) {
        return String.format(HotReloadConstants.REDIS_DISCOVER_NODE_KEY, env, appName);
    }

    /**
     * 生成任务、文件或实例 ID。
     *
     * @param prefix ID 前缀
     * @return 带时间戳和随机后缀的 ID
     */
    private String newId(String prefix) {
        return prefix + "-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 在当前事务提交后执行动作；没有事务时立即执行。
     *
     * @param runnable 事务提交后要执行的动作
     */
    private void registerAfterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 事务提交后发布 Redis 消息或更新 Redis 节点状态。
                 */
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
        } else {
            runnable.run();
        }
    }

    /**
     * 应用关闭前释放异步任务线程池。
     */
    @PreDestroy
    public void destroy() {
        taskExecutor.shutdown();
    }
}
