package io.github.hotreload.demo.service;

import io.github.hotreload.demo.vo.PageRequestVO;
import io.github.hotreload.demo.vo.PageResultVO;
import io.github.hotreload.demo.vo.HotReloadAppVO;
import io.github.hotreload.demo.vo.HotReloadCreateTaskRequestVO;
import io.github.hotreload.demo.core.message.HotReloadDiscoverMessage;
import io.github.hotreload.demo.vo.HotReloadNodeVO;
import io.github.hotreload.demo.vo.HotReloadRetryRequestVO;
import io.github.hotreload.demo.vo.HotReloadStopRecoveryRequestVO;
import io.github.hotreload.demo.vo.HotReloadTaskLogQueryVO;
import io.github.hotreload.demo.vo.HotReloadTaskVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 集群热重载业务接口。
 * <p>
 * 该接口定义页面、Redis 监听器和本地节点之间的协作入口，覆盖服务发现、任务创建、任务执行和日志查询。
 */
public interface HotReloadClusterService {

    /**
     * 查询配置表中允许热重载的应用列表。
     *
     * @return 可发起热重载的应用配置
     */
    List<HotReloadAppVO> apps();

    /**
     * 向 Redis 发布节点发现消息，通知目标应用的所有节点刷新注册信息。
     *
     * @param appName 目标应用名称
     */
    void discover(String appName);

    /**
     * 查询 Redis 中指定应用的节点注册信息。
     *
     * @param appName 目标应用名称
     * @return 节点列表
     */
    List<HotReloadNodeVO> nodes(String appName);

    /**
     * 处理当前节点收到的节点发现消息，并把本节点信息写入 Redis。
     *
     * @param message Redis 节点发现消息
     */
    void reportDiscover(HotReloadDiscoverMessage message);

    /**
     * 创建集群热重载任务并发布 Redis 任务通知。
     *
     * @param file      上传的 class 或 MyBatis XML 文件
     * @param requestVO 任务创建参数
     */
    void createTask(MultipartFile file, HotReloadCreateTaskRequestVO requestVO);

    /**
     * 创建停止重启自动恢复任务并发布 Redis 任务通知。
     *
     * @param requestVO 停止恢复任务参数
     */
    void stopRestartRecovery(HotReloadStopRecoveryRequestVO requestVO);

    /**
     * 查询热重载相关任务详情和节点实例执行结果。
     *
     * @param taskId 任务 ID
     * @return 任务详情
     */
    HotReloadTaskVO getTask(String taskId);

    /**
     * 分页查询热重载相关历史任务日志。
     *
     * @param page 分页参数和查询条件
     * @return 分页任务日志
     */
    PageResultVO<HotReloadTaskVO> pageTaskLogs(PageRequestVO<HotReloadTaskLogQueryVO> page);

    /**
     * 重试指定任务下的失败节点实例。
     *
     * @param requestVO 重试请求
     * @return 重试后的任务详情
     */
    HotReloadTaskVO retry(HotReloadRetryRequestVO requestVO);

    /**
     * 返回当前服务实例的节点信息。
     *
     * @return 当前节点信息
     */
    HotReloadNodeVO currentNode();

    /**
     * 当前节点收到热重载任务通知后，从数据库拉取任务并执行本机热重载。
     *
     * @param taskId 任务 ID
     */
    void receiveReloadTask(String taskId);

    /**
     * 当前节点收到停止重启自动恢复任务通知后，从数据库拉取任务并删除本机恢复文件，使下次重启不再自动恢复对应热重载内容。
     *
     * @param taskId 任务 ID
     */
    void receiveStopRecoveryTask(String taskId);
}
