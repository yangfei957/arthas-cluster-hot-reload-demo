package io.github.hotreload.demo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 热重载相关任务实例实体。
 * <p>
 * 对应 T_HOT_RELOAD_TASK_INSTANCE 表，一条记录表示一个节点对某个热重载相关任务的一次执行结果。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("T_HOT_RELOAD_TASK_INSTANCE")
public class HotReloadTaskInstanceEntity extends BaseEntity {

    private static final long serialVersionUID = -1938353763717176307L;

    /**
     * 任务实例 ID。
     */
    @TableId("TASK_INSTANCE_ID")
    private String taskInstanceId;

    /**
     * 所属任务 ID。
     */
    @TableField("TASK_ID")
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
     * 节点 IP。
     */
    @TableField("IP")
    private String ip;

    /**
     * 执行类型，NORMAL 表示页面发起热重载，STOP_RECOVERY 表示停止重启自动恢复，RECOVER 表示服务启动恢复。
     */
    @TableField("EXECUTE_TYPE")
    private String executeType;

    /**
     * 节点执行状态。
     */
    @TableField("STATUS")
    private String status;

    /**
     * 错误编码。
     */
    @TableField("ERROR_CODE")
    private String errorCode;

    /**
     * 错误信息。
     */
    @TableField("ERROR_MESSAGE")
    private String errorMessage;

    /**
     * 重载执行结果。
     */
    @TableField("RELOAD_RESULT")
    private String reloadResult;

    /**
     * 重试次数。
     */
    @TableField("RETRY_COUNT")
    private Integer retryCount;

    /**
     * 任务接收时间。
     */
    @TableField("RECEIVE_TIME")
    private Date receiveTime;

    /**
     * 重载开始时间。
     */
    @TableField("START_TIME")
    private Date startTime;

    /**
     * 重载完成时间。
     */
    @TableField("FINISH_TIME")
    private Date finishTime;
}
