package io.github.hotreload.demo.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 热重载失败实例重试请求。
 * <p>
 * 页面在任务详情中选择失败节点后，用该模型提交需要重新执行的目标节点。
 */
@Data
@ApiModel(value = "HotReloadRetryRequestVO", description = "热重载失败实例重试请求")
public class HotReloadRetryRequestVO implements Serializable {

    private static final long serialVersionUID = -6180088993627840877L;

    /**
     * 要重试的任务 ID。
     */
    @ApiModelProperty(value = "要重试的任务 ID", example = "HR20260629120000001", required = true)
    private String taskId;

    /**
     * 需要重试的目标节点 IP。
     */
    @ApiModelProperty(value = "需要重试的目标节点 IP，只会重试这些节点对应的任务实例", example = "[\"10.0.0.11\"]", required = true)
    private List<String> ips;
}
