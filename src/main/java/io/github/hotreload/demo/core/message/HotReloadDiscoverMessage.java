package io.github.hotreload.demo.core.message;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Redis 节点发现消息。
 * <p>
 * 页面调用 discover 接口后发布该消息，目标应用的节点收到后把自己的节点信息写入 Redis。
 */
@Data
public class HotReloadDiscoverMessage implements Serializable {

    private static final long serialVersionUID = -4859610070129028617L;

    /**
     * Redis 消息类型。
     */
    private String messageType;

    /**
     * 目标应用名称。
     */
    private String appName;

    /**
     * 目标运行环境。
     */
    private String env;

    /**
     * 发现请求发布时间。
     */
    private Date requestTime;
}
