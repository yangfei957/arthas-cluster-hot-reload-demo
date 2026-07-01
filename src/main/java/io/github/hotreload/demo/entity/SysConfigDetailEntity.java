package io.github.hotreload.demo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统配置明细实体。
 * <p>
 * 对应 T_SYS_CONFIG_DETAIL 表，热重载用它保存开关和服务下拉列表配置。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("T_SYS_CONFIG_DETAIL")
public class SysConfigDetailEntity extends BaseEntity {

    private static final long serialVersionUID = 8502144386423500602L;

    /**
     * 配置编码。
     */
    @TableField("CONFIG_CODE")
    private String configCode;

    /**
     * 明细编码。
     */
    @TableField("DETAIL_CODE")
    private String detailCode;

    /**
     * 明细值。
     */
    @TableField("DETAIL_VALUE")
    private String detailValue;

    /**
     * 备注。
     */
    @TableField("REMARK")
    private String remark;
}
