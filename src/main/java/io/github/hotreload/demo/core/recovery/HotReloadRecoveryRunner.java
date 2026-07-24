package io.github.hotreload.demo.core.recovery;

import io.github.hotreload.demo.core.cluster.HotReloadConstants;
import io.github.hotreload.demo.core.cluster.HotReloadLocalNode;
import io.github.hotreload.demo.core.runtime.HotReloadRuntimeExecutor;
import io.github.hotreload.demo.entity.HotReloadTaskInstanceEntity;
import io.github.hotreload.demo.mapper.HotReloadTaskInstanceMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 热重载重启恢复运行器。
 * <p>
 * 服务启动完成后，该组件会读取本地恢复目录中的 class/xml 文件，
 * 直接重新执行热重载，并把恢复执行结果写入任务实例表。
 */
@Slf4j
@Component
public class HotReloadRecoveryRunner implements ApplicationContextAware {

    private final HotReloadRuntimeExecutor hotReloadRuntimeExecutor;
    private final HotReloadRecoveryFileStore recoverFileStore;
    private final HotReloadTaskInstanceMapper hotReloadTaskInstanceMapper;
    private final HotReloadLocalNode localNode;

    private ApplicationContext applicationContext;

    /**
     * 构造热重载重启恢复运行器。
     *
     * @param hotReloadRuntimeExecutor   本机热重载运行时执行器
     * @param recoverFileStore           恢复文件存储组件
     * @param hotReloadTaskInstanceMapper 任务实例 Mapper
     * @param localNode                  当前节点信息
     */
    public HotReloadRecoveryRunner(HotReloadRuntimeExecutor hotReloadRuntimeExecutor,
                                    HotReloadRecoveryFileStore recoverFileStore,
                                    HotReloadTaskInstanceMapper hotReloadTaskInstanceMapper,
                                    HotReloadLocalNode localNode) {
        this.hotReloadRuntimeExecutor = hotReloadRuntimeExecutor;
        this.recoverFileStore = recoverFileStore;
        this.hotReloadTaskInstanceMapper = hotReloadTaskInstanceMapper;
        this.localNode = localNode;
    }

    /**
     * 保存当前 Spring 应用上下文，用于过滤父子容器重复刷新事件。
     *
     * @param applicationContext Spring 应用上下文
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 监听 Spring 容器刷新完成事件，并异步启动热重载恢复流程。
     *
     * @param event 容器刷新完成事件
     */
    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!event.getApplicationContext().equals(applicationContext)) {
            return;
        }
        Thread thread = new Thread(this::recoverSafely, "hot-reload-recover");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 捕获恢复流程中的异常，避免恢复失败影响主应用启动。
     */
    private void recoverSafely() {
        try {
            List<File> classFiles = recoverFileStore.listClassFiles();
            List<File> myBatisXmlFiles = recoverFileStore.listMyBatisXmlFiles();
            if (classFiles.isEmpty() && myBatisXmlFiles.isEmpty()) {
                log.info("未发现热重载恢复文件");
                return;
            }
            log.info("本地检测到恢复文件，热重载自动恢复开始运行");
            recoverClasses(classFiles);
            recoverMyBatisXml(myBatisXmlFiles);
            log.info("热重载自动恢复已完成");
        } catch (Exception e) {
            log.error("热重载自动恢复失败", e);
        }
    }

    /**
     * 恢复 JVM class 热重载文件。
     */
    private void recoverClasses(List<File> files) {
        for (File file : files) {
            Date startTime = new Date();
            try {
                String reloadResult = hotReloadRuntimeExecutor.reloadClassRuntime(recoverFileStore.readFile(file));
                recordRecoverSuccess(file, reloadResult, startTime);
            } catch (Exception e) {
                log.error("恢复 class 失败，file={}，原因={}", file.getName(), e.getMessage());
                recordRecoverFailed(file, e, startTime);
            }
        }
    }

    /**
     * 恢复 MyBatis Mapper XML 热重载文件。
     * <p>
     * 同一个 namespace 只恢复最后一个文件，避免旧 XML 覆盖新 SQL。
     */
    private void recoverMyBatisXml(List<File> files) {
        Map<String, File> latestByNamespace = new LinkedHashMap<>();
        for (File file : files) {
            Date startTime = new Date();
            try {
                String namespace = recoverFileStore.parseMyBatisNamespace(recoverFileStore.readFile(file));
                latestByNamespace.put(namespace, file);
            } catch (Exception e) {
                log.error("解析 Mapper XML namespace 失败，file={}，原因={}", file.getName(), e.getMessage());
                recordRecoverFailed(file, e, startTime);
            }
        }
        for (Map.Entry<String, File> entry : latestByNamespace.entrySet()) {
            File file = entry.getValue();
            Date startTime = new Date();
            try {
                String reloadResult = hotReloadRuntimeExecutor.reloadMyBatisXmlRuntime(recoverFileStore.readFile(file));
                recordRecoverSuccess(file, reloadResult, startTime);
            } catch (Exception e) {
                log.error("恢复 Mapper XML 失败，file={}，原因={}", file.getName(), e.getMessage());
                recordRecoverFailed(file, e, startTime);
            }
        }
    }

    /**
     * 记录恢复成功结果。
     *
     * @param file         恢复文件
     * @param reloadResult 热重载返回结果
     * @param startTime    恢复开始时间
     */
    private void recordRecoverSuccess(File file, String reloadResult, Date startTime) {
        recordRecoverResult(file, HotReloadConstants.INSTANCE_STATUS_SUCCESS,
                null, null, reloadResult, startTime);
    }

    /**
     * 记录恢复失败结果。
     *
     * @param file      恢复文件
     * @param e         恢复异常
     * @param startTime 恢复开始时间
     */
    private void recordRecoverFailed(File file, Exception e, Date startTime) {
        recordRecoverResult(file, HotReloadConstants.INSTANCE_STATUS_FAILED,
                "RECOVER_FAILED", e.getMessage(), null, startTime);
    }

    /**
     * 把重启恢复执行结果写入任务实例表。
     *
     * @param file         恢复文件
     * @param status       执行状态
     * @param errorCode    错误编码
     * @param errorMessage 错误消息
     * @param reloadResult 热重载返回结果
     * @param startTime    恢复开始时间
     */
    private void recordRecoverResult(File file, String status, String errorCode, String errorMessage,
                                     String reloadResult, Date startTime) {
        HotReloadRecoverFileMeta recoverMeta = recoverFileStore.readRecoverMeta(file);
        if (recoverMeta == null || StringUtils.isBlank(recoverMeta.getTaskId())) {
            return;
        }
        try {
            HotReloadTaskInstanceEntity entity = new HotReloadTaskInstanceEntity();
            entity.setTaskInstanceId(newId("INST"));
            entity.setTaskId(recoverMeta.getTaskId());
            entity.setAppName(defaultText(recoverMeta.getAppName(), localNode.getAppName()));
            entity.setEnv(defaultText(recoverMeta.getEnv(), localNode.getEnv()));
            entity.setIp(defaultText(localNode.getIp(), recoverMeta.getIp()));
            entity.setExecuteType(HotReloadConstants.EXECUTE_TYPE_RECOVER);
            entity.setStatus(status);
            entity.setErrorCode(errorCode);
            entity.setErrorMessage(abbreviate(errorMessage, 1900));
            entity.setReloadResult(reloadResult);
            entity.setRetryCount(0);
            entity.setReceiveTime(startTime);
            entity.setStartTime(startTime);
            entity.setFinishTime(new Date());
            hotReloadTaskInstanceMapper.insert(entity);
        } catch (Exception e) {
            log.error("记录热重载恢复结果失败，file={}", file.getName(), e);
        }
    }

    /**
     * 生成恢复任务实例 ID。
     *
     * @param prefix ID 前缀
     * @return 唯一 ID
     */
    private String newId(String prefix) {
        return prefix + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 取非空文本，原值为空时使用默认值。
     *
     * @param value        原值
     * @param defaultValue 默认值
     * @return 非空文本
     */
    private String defaultText(String value, String defaultValue) {
        return StringUtils.isNotBlank(value) ? value : defaultValue;
    }

    /**
     * 截断过长文本，避免数据库错误消息字段超长。
     *
     * @param value     原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
