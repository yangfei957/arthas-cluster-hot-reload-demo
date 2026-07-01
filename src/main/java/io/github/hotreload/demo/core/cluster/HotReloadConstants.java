package io.github.hotreload.demo.core.cluster;

/**
 * 热重载常量定义。
 * <p>
 * 统一维护 Redis 消息类型、任务状态、实例状态、文件类型和通用开关值，避免协议字段散落在业务代码中。
 */
public final class HotReloadConstants {

    /**
     * 工具类不允许实例化。
     */
    private HotReloadConstants() {
    }

    public static final String MESSAGE_TYPE_DISCOVER_REQUEST = "DISCOVER_REQUEST";
    public static final String MESSAGE_TYPE_RELOAD_TASK_NOTIFY = "RELOAD_TASK_NOTIFY";
    public static final String MESSAGE_TYPE_STOP_RECOVERY_TASK_NOTIFY = "STOP_RECOVERY_TASK_NOTIFY";

    public static final String REDIS_DISCOVER_NODE_KEY = "hotreload:%s:%s:nodes";

    public static final String NODE_STATUS_ONLINE = "ONLINE";
    public static final String NODE_STATUS_EXPIRED = "EXPIRED";
    public static final String HOT_RELOAD_STATUS_IDLE = "IDLE";

    public static final String TASK_STATUS_RUNNING = "RUNNING";
    public static final String TASK_STATUS_SUCCESS = "SUCCESS";
    public static final String TASK_STATUS_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    public static final String TASK_STATUS_FAILED = "FAILED";

    public static final String INSTANCE_STATUS_PENDING = "PENDING";
    public static final String INSTANCE_STATUS_RECEIVED = "RECEIVED";
    public static final String INSTANCE_STATUS_PRECHECKING = "PRECHECKING";
    public static final String INSTANCE_STATUS_PRECHECK_FAILED = "PRECHECK_FAILED";
    public static final String INSTANCE_STATUS_RELOADING = "RELOADING";
    public static final String INSTANCE_STATUS_VERIFYING = "VERIFYING";
    public static final String INSTANCE_STATUS_SUCCESS = "SUCCESS";
    public static final String INSTANCE_STATUS_FAILED = "FAILED";
    public static final String INSTANCE_STATUS_TIMEOUT = "TIMEOUT";

    public static final String EXECUTE_TYPE_NORMAL = "NORMAL";
    public static final String EXECUTE_TYPE_RECOVER = "RECOVER";
    public static final String EXECUTE_TYPE_STOP_RECOVERY = "STOP_RECOVERY";

    public static final String FILE_TYPE_ALL = "*";
    public static final String FILE_TYPE_AUTO = "AUTO";
    public static final String FILE_TYPE_SPRING_BEAN = "SPRING_BEAN";
    public static final String FILE_TYPE_COMMON_CLASS = "COMMON_CLASS";
    public static final String FILE_TYPE_MYBATIS_XML = "MYBATIS_XML";

    public static final String FLAG_Y = "Y";
    public static final String FLAG_N = "N";
}
