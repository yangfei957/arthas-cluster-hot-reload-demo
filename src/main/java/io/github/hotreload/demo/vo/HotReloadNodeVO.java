package io.github.hotreload.demo.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Redis 中记录的热重载节点信息。
 * <p>
 * 节点信息由 discover 广播触发刷新，页面根据该模型选择目标节点。
 */
@Data
@ApiModel(value = "HotReloadNodeVO", description = "Redis 中记录的热重载节点信息")
public class HotReloadNodeVO implements Serializable {

    private static final long serialVersionUID = 1823033989168654159L;

    /**
     * 应用名称。
     */
    @ApiModelProperty(value = "应用名称，同一套 Redis 中用 appName 区分不同服务模块", example = "arthas-cluster-hot-reload-demo")
    private String appName;

    /**
     * 运行环境。
     */
    @ApiModelProperty(value = "运行环境，默认取 Spring active profile 的第一个值", example = "local")
    private String env;

    /**
     * 节点 IP。
     */
    @ApiModelProperty(value = "服务实例 IP，容器部署时通常是 Pod IP", example = "10.0.0.11")
    private String ip;

    /**
     * Redis 节点信息最近更新时间。
     */
    @ApiModelProperty(value = "Redis 节点信息最近更新时间，用于判断节点信息是否过期")
    private Date updateTime;

    /**
     * 页面展示用节点在线状态。
     */
    @ApiModelProperty(value = "页面展示用节点在线状态，ONLINE 表示未超过过期时间，EXPIRED 表示超过 30 分钟未刷新", example = "ONLINE")
    private String nodeStatus;

    /**
     * 最近一次热重载相关操作状态。
     */
    @ApiModelProperty(value = "节点最近一次热重载相关操作状态，来自 Redis 节点记录", example = "SUCCESS")
    private String hotReloadStatus;

    /**
     * 最近一次热重载相关操作任务 ID。
     */
    @ApiModelProperty(value = "节点最近一次执行的热重载相关操作任务 ID，便于跳转查询任务详情", example = "HR20260629120000001")
    private String lastTaskId;
}
