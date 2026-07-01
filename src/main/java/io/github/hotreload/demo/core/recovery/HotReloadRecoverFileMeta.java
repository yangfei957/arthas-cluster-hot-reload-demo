package io.github.hotreload.demo.core.recovery;

import lombok.Data;

import java.io.Serializable;

/**
 * 热重载恢复文件元数据。
 * <p>
 * 每个需要重启恢复的 zip 恢复包中会保存一份 meta.json，
 * 记录原任务、应用、节点和文件摘要，便于服务重启后恢复执行并写回任务实例日志。
 */
@Data
public class HotReloadRecoverFileMeta implements Serializable {

    private static final long serialVersionUID = -2175863968458078460L;

    /**
     * 原热重载任务 ID。
     */
    private String taskId;

    /**
     * 应用名称。
     */
    private String appName;

    /**
     * 运行环境。
     */
    private String env;

    /**
     * 写入恢复文件时的节点 IP。
     */
    private String ip;

    /**
     * 原补丁文件 ID。
     */
    private String fileId;

    /**
     * 原上传文件名。
     */
    private String fileName;

    /**
     * 文件类型。
     */
    private String fileType;

    /**
     * Spring Bean 热重载使用的 BeanName。
     */
    private String beanName;

    /**
     * class 文件解析出的类名。
     */
    private String className;

    /**
     * 补丁文件 SHA-256 摘要。
     */
    private String fileSha256;
}
