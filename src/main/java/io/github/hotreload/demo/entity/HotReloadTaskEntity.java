package io.github.hotreload.demo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 热重载操作任务实体。
 * <p>
 * 对应 T_HOT_RELOAD_TASK 表，一条记录表示页面发起的一次集群操作，包括正常热重载和停止重启自动恢复。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("T_HOT_RELOAD_TASK")
public class HotReloadTaskEntity extends BaseEntity {

    private static final long serialVersionUID = -551244399831270025L;

    /**
     * 任务 ID，用于 Redis 广播、页面查询和节点执行。
     */
    @TableId("TASK_ID")
    private String taskId;

    /**
     * 应用名称。
     */
    @TableField("APP_NAME")
    private String appName;

    /**
     * 运行环境。
     */
    @TableField("ENV")
    private String env;

    /**
     * 操作对象名称，正常热重载时为上传补丁文件名，停止重启自动恢复时为操作名称。
     */
    @TableField("PATCH_NAME")
    private String patchName;

    /**
     * 上传补丁文件 SHA-256 摘要，非文件类任务为空。
     */
    @TableField("PATCH_SHA256")
    private String patchSha256;

    /**
     * 文件类型或停止恢复范围。
     */
    @TableField("RELOAD_TYPE")
    private String reloadType;

    /**
     * 正常热重载是否保存恢复文件，Y 表示重启后自动恢复，N 表示只在当前进程生效。
     */
    @TableField("PERSIST_ON_RESTART")
    private String persistOnRestart;

    /**
     * 页面展示用的任务备注。
     */
    @TableField("TASK_REMARK")
    private String taskRemark;
}
