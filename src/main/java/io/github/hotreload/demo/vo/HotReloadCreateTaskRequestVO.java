package io.github.hotreload.demo.vo;

import com.alibaba.fastjson.annotation.JSONField;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 集群热重载任务创建请求。
 * <p>
 * 页面上传 class/xml 文件时使用该模型描述目标服务、目标节点和执行策略。
 */
@Data
@ApiModel(value = "HotReloadCreateTaskRequestVO", description = "集群热重载任务创建请求")
public class HotReloadCreateTaskRequestVO implements Serializable {

    private static final long serialVersionUID = 7845910664413924211L;

    /**
     * 目标应用名称。
     */
    @ApiModelProperty(value = "目标应用名称，需要与服务注册到 Redis 的 appName 一致", example = "arthas-cluster-hot-reload-demo", required = true)
    private String appName;

    /**
     * 容器重启后是否继续保持本次重载。
     */
    @ApiModelProperty(value = "容器重启后是否继续保持本次重载，Y 表示保存恢复文件，N 表示只在当前进程生效", example = "N")
    private String persistOnRestart;

    /**
     * 任务备注。
     */
    @ApiModelProperty(value = "任务备注，用于说明本次热重载原因或变更内容", example = "修复测试服务返回内容")
    private String taskRemark;

    /**
     * 页面选中的目标节点 IP，兼容 ips 和 ipList 两种入参。
     */
    @JSONField(alternateNames = {"ipList"})
    @ApiModelProperty(value = "页面选择的目标节点 IP 列表，服务端只给这些节点创建实例", example = "[\"10.0.0.11\", \"10.0.0.12\"]", required = true)
    private List<String> ips;
}
