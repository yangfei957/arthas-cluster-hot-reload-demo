package io.github.hotreload.demo.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 热重载相关任务详情查询请求。
 * <p>
 * 页面创建任务后可通过任务 ID 查询任务主表和节点实例汇总结果。
 */
@Data
@ApiModel(value = "HotReloadTaskQueryVO", description = "热重载相关任务详情查询请求")
public class HotReloadTaskQueryVO implements Serializable {

    private static final long serialVersionUID = -6040679196941703448L;

    /**
     * 任务 ID。
     */
    @ApiModelProperty(value = "任务 ID", example = "HR20260629120000001", required = true)
    private String taskId;
}
