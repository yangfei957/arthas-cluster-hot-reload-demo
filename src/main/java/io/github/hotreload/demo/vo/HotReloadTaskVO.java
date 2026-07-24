package io.github.hotreload.demo.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 集群操作任务详情。
 * <p>
 * 任务主表记录一次页面提交的集群操作，节点级执行结果通过 instances 返回。
 */
@Data
@ApiModel(value = "HotReloadTaskVO", description = "集群热重载操作任务详情")
public class HotReloadTaskVO implements Serializable {

    private static final long serialVersionUID = -5020630043057651677L;

    @ApiModelProperty(value = "任务 ID，一次页面提交生成一个任务", example = "HR20260629120000001")
    private String taskId;
    @ApiModelProperty(value = "目标应用名称", example = "cluster-hot-reload-demo")
    private String appName;
    @ApiModelProperty(value = "运行环境", example = "local")
    private String env;
    @ApiModelProperty(value = "操作对象名称，正常热重载时为上传补丁文件名，停止重启自动恢复时为操作名称", example = "TestHotReloadServiceImpl.class")
    private String patchName;
    @ApiModelProperty(value = "上传补丁文件 SHA-256 摘要，用于识别文件内容是否一致；非文件类任务为空")
    private String patchSha256;
    @ApiModelProperty(value = "文件类型或停止恢复范围，CLASS/MYBATIS_XML/*", example = "CLASS")
    private String reloadType;
    @ApiModelProperty(value = "正常热重载是否保存恢复文件，Y 表示重启后自动恢复，N 表示只在当前进程生效", example = "N")
    private String persistOnRestart;
    @ApiModelProperty(value = "任务汇总状态，由实例状态动态计算", example = "SUCCESS")
    private String status;
    @ApiModelProperty(value = "任务实例总数")
    private Integer totalCount;
    @ApiModelProperty(value = "执行成功实例数")
    private Integer successCount;
    @ApiModelProperty(value = "执行失败实例数")
    private Integer failedCount;
    @ApiModelProperty(value = "超时或长时间未回写实例数")
    private Integer timeoutCount;
    @ApiModelProperty(value = "任务备注")
    private String taskRemark;
    @ApiModelProperty(value = "任务创建时间")
    private Date createdTime;
    @ApiModelProperty(value = "任务更新时间")
    private Date updatedTime;
    @ApiModelProperty(value = "节点级任务实例列表")
    private List<HotReloadTaskInstanceVO> instances;
}
