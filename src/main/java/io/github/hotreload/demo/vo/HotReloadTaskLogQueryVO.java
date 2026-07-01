package io.github.hotreload.demo.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 热重载相关任务日志分页查询条件。
 * <p>
 * 页面按任务、服务、节点和执行状态筛选正常热重载、停止重启自动恢复等历史记录时使用该模型。
 */
@Data
@ApiModel(value = "HotReloadTaskLogQueryVO", description = "热重载相关任务日志分页查询条件")
public class HotReloadTaskLogQueryVO implements Serializable {

    private static final long serialVersionUID = -2324987961750391896L;

    @ApiModelProperty(value = "任务 ID，精确查询某一次热重载相关任务", example = "HR20260629120000001")
    private String taskId;
    @ApiModelProperty(value = "应用名称", example = "arthas-cluster-hot-reload-demo")
    private String appName;
    @ApiModelProperty(value = "运行环境", example = "local")
    private String env;
    @ApiModelProperty(value = "操作对象名称，正常热重载时可按上传文件名定位任务", example = "TestHotReloadServiceImpl.class")
    private String patchName;
    @ApiModelProperty(value = "文件类型或停止恢复范围，SPRING_BEAN/COMMON_CLASS/MYBATIS_XML/*", example = "SPRING_BEAN")
    private String reloadType;
    @ApiModelProperty(value = "任务汇总状态", example = "FAILED")
    private String status;
    @ApiModelProperty(value = "目标节点 IP", example = "10.0.0.11")
    private String ip;
    @ApiModelProperty(value = "节点实例状态", example = "FAILED")
    private String instanceStatus;
    @ApiModelProperty(value = "执行类型，NORMAL 表示正常热重载，STOP_RECOVERY 表示停止重启自动恢复，RECOVER 表示重启恢复", example = "NORMAL")
    private String executeType;
    @ApiModelProperty(value = "任务创建开始时间")
    private Date createdStartTime;
    @ApiModelProperty(value = "任务创建结束时间")
    private Date createdEndTime;
}
