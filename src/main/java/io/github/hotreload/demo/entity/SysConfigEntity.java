package io.github.hotreload.demo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统配置主表实体。
 * <p>
 * 对应 T_SYS_CONFIG 表，用于描述一组配置项；热重载配置组编码为 RELOAD_CONFIG。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("T_SYS_CONFIG")
public class SysConfigEntity extends BaseEntity {

    private static final long serialVersionUID = -1792524701288242376L;

    /**
     * 配置编码。
     */
    @TableId("CONFIG_CODE")
    private String configCode;

    /**
     * 配置名称。
     */
    @TableField("CONFIG_NAME")
    private String configName;

    /**
     * 备注。
     */
    @TableField("REMARK")
    private String remark;
}
