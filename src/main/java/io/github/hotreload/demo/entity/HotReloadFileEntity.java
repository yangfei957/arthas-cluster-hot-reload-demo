package io.github.hotreload.demo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 热重载补丁文件实体。
 * <p>
 * 对应 T_HOT_RELOAD_FILE 表，用于保存上传文件内容、文件类型和 class 解析信息。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("T_HOT_RELOAD_FILE")
public class HotReloadFileEntity extends BaseEntity {

    private static final long serialVersionUID = 6637186693775522038L;

    /**
     * 上传补丁文件主键。
     */
    @TableId("FILE_ID")
    private String fileId;

    /**
     * 所属任务 ID。
     */
    @TableField("TASK_ID")
    private String taskId;

    /**
     * 操作人上传时的原始文件名。
     */
    @TableField("FILE_NAME")
    private String fileName;

    /**
     * 识别后的文件类型。
     */
    @TableField("FILE_TYPE")
    private String fileType;

    /**
     * 从 class 文件解析出的类名。
     */
    @TableField("CLASS_NAME")
    private String className;

    /**
     * 文件 SHA-256 摘要。
     */
    @TableField("FILE_SHA256")
    private String fileSha256;

    /**
     * 原始补丁文件内容。
     */
    @TableField("FILE_CONTENT")
    private byte[] fileContent;
}
