package io.github.hotreload.demo.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 热重载节点执行实例。
 * <p>
 * 一个任务会按目标 IP 拆成多个实例，每个实例记录一个节点的执行状态、错误信息和 Arthas 返回结果。
 */
@Data
@ApiModel(value = "HotReloadTaskInstanceVO", description = "热重载节点执行实例")
public class HotReloadTaskInstanceVO implements Serializable {

    private static final long serialVersionUID = 5472954031318065170L;

    @ApiModelProperty(value = "任务实例 ID，一个目标节点对应一个实例", example = "HRI202606291200000010001")
    private String taskInstanceId;
    @ApiModelProperty(value = "所属任务 ID", example = "HR20260629120000001")
    private String taskId;
    @ApiModelProperty(value = "目标应用名称", example = "cluster-hot-reload-demo")
    private String appName;
    @ApiModelProperty(value = "运行环境", example = "local")
    private String env;
    @ApiModelProperty(value = "目标节点 IP", example = "10.0.0.11")
    private String ip;
    @ApiModelProperty(value = "执行类型，NORMAL 表示页面热重载，STOP_RECOVERY 表示停止重启自动恢复，RECOVER 表示服务启动恢复", example = "NORMAL")
    private String executeType;
    @ApiModelProperty(value = "实例执行状态，PENDING/RECEIVED/PRECHECKING/RELOADING/VERIFYING/SUCCESS/FAILED/TIMEOUT", example = "SUCCESS")
    private String status;
    @ApiModelProperty(value = "错误编码，执行失败时用于快速分类")
    private String errorCode;
    @ApiModelProperty(value = "错误消息，执行失败时记录异常摘要")
    private String errorMessage;
    @ApiModelProperty(value = "热重载执行结果，保存 Arthas 或 MyBatis 刷新返回信息")
    private String reloadResult;
    @ApiModelProperty(value = "重试次数")
    private Integer retryCount;
    @ApiModelProperty(value = "节点收到 Redis 任务通知时间")
    private Date receiveTime;
    @ApiModelProperty(value = "节点开始执行热重载时间")
    private Date startTime;
    @ApiModelProperty(value = "节点完成执行热重载时间")
    private Date finishTime;
}
