package io.github.hotreload.demo.controller;

import com.alibaba.fastjson.JSONObject;
import io.github.hotreload.demo.service.HotReloadClusterService;
import io.github.hotreload.demo.vo.PageRequestVO;
import io.github.hotreload.demo.vo.PageResultVO;
import io.github.hotreload.demo.vo.HotReloadAppVO;
import io.github.hotreload.demo.vo.HotReloadCreateTaskRequestVO;
import io.github.hotreload.demo.vo.HotReloadNodeVO;
import io.github.hotreload.demo.vo.HotReloadRetryRequestVO;
import io.github.hotreload.demo.vo.HotReloadStopRecoveryRequestVO;
import io.github.hotreload.demo.vo.HotReloadTaskLogQueryVO;
import io.github.hotreload.demo.vo.HotReloadTaskQueryVO;
import io.github.hotreload.demo.vo.HotReloadTaskVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 集群热重载接口。
 * <p>
 * 该控制器覆盖页面端的完整流程：查询服务、刷新节点、创建热重载任务、停止重启自动恢复、查看详情、分页查看日志和失败节点重试。
 */
@Api(tags = "集群热重载")
@RestController
@RequestMapping("/hotReloadCluster")
public class HotReloadClusterController {

    private final HotReloadClusterService hotReloadClusterService;

    /**
     * 构造集群热重载接口。
     *
     * @param hotReloadClusterService 集群热重载业务服务
     */
    public HotReloadClusterController(HotReloadClusterService hotReloadClusterService) {
        this.hotReloadClusterService = hotReloadClusterService;
    }

    /**
     * 查询可发起热重载的应用列表。
     *
     * @return 应用配置列表
     */
    @ApiOperation("查询可热重载应用")
    @PostMapping("/apps")
    public ResponseEntity<List<HotReloadAppVO>> apps() {
        return ResponseEntity.ok(hotReloadClusterService.apps());
    }

    /**
     * 向 Redis 发布节点发现消息。
     *
     * @param appName 目标应用名称
     * @return 空响应，节点会异步刷新 Redis 注册信息
     */
    @ApiOperation("刷新目标应用节点信息")
    @PostMapping("/discover")
    public ResponseEntity<Void> discover(
            @ApiParam(value = "目标应用名称", required = true) @RequestParam("appName") String appName) {
        hotReloadClusterService.discover(appName);
        return ResponseEntity.ok().build();
    }

    /**
     * 查询 Redis 中的节点列表。
     *
     * @param appName 目标应用名称
     * @return 节点状态列表
     */
    @ApiOperation("查询目标应用节点列表")
    @PostMapping("/nodes")
    public ResponseEntity<List<HotReloadNodeVO>> nodes(
            @ApiParam(value = "目标应用名称", required = true) @RequestParam("appName") String appName) {
        return ResponseEntity.ok(hotReloadClusterService.nodes(appName));
    }

    /**
     * 查询当前服务实例的本地节点信息。
     *
     * @return 当前节点信息
     */
    @ApiOperation("查询当前节点信息")
    @PostMapping("/currentNode")
    public ResponseEntity<HotReloadNodeVO> currentNode() {
        return ResponseEntity.ok(hotReloadClusterService.currentNode());
    }

    /**
     * 创建集群热重载任务。
     *
     * @param file        上传的 class 或 MyBatis XML 文件
     * @param requestJson JSON 格式任务参数
     * @return 空响应，页面可随后调用任务详情接口查看执行情况
     */
    @ApiOperation("创建集群热重载任务")
    @PostMapping("/task/create")
    public ResponseEntity<Void> createTask(
            @ApiParam(value = "上传的 .class 或 .xml 文件", required = true) @RequestParam("file") MultipartFile file,
            @ApiParam(value = "任务创建请求 JSON", required = true) @RequestParam("request") String requestJson) {
        HotReloadCreateTaskRequestVO requestVO =
                JSONObject.parseObject(requestJson, HotReloadCreateTaskRequestVO.class);
        hotReloadClusterService.createTask(file, requestVO);
        return ResponseEntity.ok().build();
    }

    /**
     * 创建停止重启自动恢复任务。
     *
     * @param requestVO 停止恢复请求
     * @return 空响应，页面可通过任务详情或日志接口查询执行结果
     */
    @ApiOperation("停止目标节点重启自动恢复")
    @PostMapping("/restartRecovery/stop")
    public ResponseEntity<Void> stopRestartRecovery(@RequestBody HotReloadStopRecoveryRequestVO requestVO) {
        hotReloadClusterService.stopRestartRecovery(requestVO);
        return ResponseEntity.ok().build();
    }

    /**
     * 查询任务详情和节点实例执行结果。
     *
     * @param queryVO 查询条件
     * @return 任务详情
     */
    @ApiOperation("查询热重载相关任务详情")
    @PostMapping("/task/get")
    public ResponseEntity<HotReloadTaskVO> getTask(@RequestBody HotReloadTaskQueryVO queryVO) {
        return ResponseEntity.ok(hotReloadClusterService.getTask(queryVO.getTaskId()));
    }

    /**
     * 分页查询历史热重载相关任务日志。
     *
     * @param page 分页参数和查询条件
     * @return 任务日志分页结果
     */
    @ApiOperation("分页查询热重载相关任务日志")
    @PostMapping("/task/log/page")
    public ResponseEntity<PageResultVO<HotReloadTaskVO>> pageTaskLogs(
            @RequestBody PageRequestVO<HotReloadTaskLogQueryVO> page) {
        return ResponseEntity.ok(hotReloadClusterService.pageTaskLogs(page));
    }

    /**
     * 重试指定任务下的失败节点实例。
     *
     * @param requestVO 重试请求
     * @return 重试后的任务详情
     */
    @ApiOperation("重试失败节点")
    @PostMapping("/task/retry")
    public ResponseEntity<HotReloadTaskVO> retry(@RequestBody HotReloadRetryRequestVO requestVO) {
        return ResponseEntity.ok(hotReloadClusterService.retry(requestVO));
    }
}
