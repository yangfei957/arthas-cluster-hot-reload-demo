package io.github.hotreload.demo.core.message;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Redis 任务通知消息。
 * <p>
 * 创建页面任务后只广播任务 ID。节点根据 messageType 进入对应处理入口，再从数据库拉取任务明细，
 * 避免 Redis 承载补丁文件内容或恢复文件操作细节。
 */
@Data
public class HotReloadTaskMessage implements Serializable {

    private static final long serialVersionUID = -2043531359365558617L;

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
     * 节点需要拉取的任务 ID。
     */
    private String taskId;

    /**
     * 任务通知发布时间。
     */
    private Date publishTime;
}
