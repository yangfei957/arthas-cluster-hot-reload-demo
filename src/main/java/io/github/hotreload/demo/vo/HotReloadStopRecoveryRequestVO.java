package io.github.hotreload.demo.vo;

import com.alibaba.fastjson.annotation.JSONField;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 停止重启自动恢复请求。
 * <p>
 * 页面选择目标节点后提交该请求，服务端会生成任务并通过 Redis 通知目标节点删除本地恢复文件。
 * 恢复文件删除后，服务下次重启时不会再自动恢复对应范围内的热重载内容。
 */
@Data
@ApiModel(value = "HotReloadStopRecoveryRequestVO", description = "停止重启自动恢复请求")
public class HotReloadStopRecoveryRequestVO implements Serializable {

    private static final long serialVersionUID = -4242021958459240478L;

    /**
     * 目标应用名称。
     */
    @ApiModelProperty(value = "目标应用名称，需要与服务注册到 Redis 的 appName 一致", example = "arthas-cluster-hot-reload-demo", required = true)
    private String appName;

    /**
     * 停止恢复范围，空或 * 表示全部。
     */
    @ApiModelProperty(value = "停止恢复范围，空或 * 表示全部，SPRING_BEAN 表示 Spring Bean，COMMON_CLASS 表示普通类，MYBATIS_XML 表示 MyBatis XML", example = "*")
    private String fileType;

    /**
     * 页面选中的目标节点 IP。
     */
    @JSONField(alternateNames = {"ipList"})
    @ApiModelProperty(value = "页面选择的目标节点 IP 列表，服务端只给这些节点创建停止恢复实例", example = "[\"10.0.0.11\", \"10.0.0.12\"]", required = true)
    private List<String> ips;

    /**
     * 任务备注。
     */
    @ApiModelProperty(value = "任务备注，用于说明本次停止重启自动恢复的原因", example = "停止非容器部署环境遗留热重载的重启自动恢复")
    private String taskRemark;
}
