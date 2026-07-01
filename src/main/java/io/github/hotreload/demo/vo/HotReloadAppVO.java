package io.github.hotreload.demo.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 可发起热重载的应用配置。
 * <p>
 * 服务下拉框来自数据库配置表，该模型用于把配置项展示给页面。
 */
@Data
@ApiModel(value = "HotReloadAppVO", description = "可发起热重载的应用配置")
public class HotReloadAppVO implements Serializable {

    private static final long serialVersionUID = -1771250279979202738L;

    /**
     * 服务应用名称。
     */
    @ApiModelProperty(value = "服务应用名称，用于区分不同后端服务模块", example = "arthas-cluster-hot-reload-demo")
    private String appName;

    /**
     * 预期节点数量。
     */
    @ApiModelProperty(value = "预期节点数量，页面可用它辅助判断发现到的节点是否完整", example = "3")
    private String nodeTotal;
}
