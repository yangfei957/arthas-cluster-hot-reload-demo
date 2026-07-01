package io.github.hotreload.demo.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用分页查询请求。
 * <p>
 * pageStart 从 1 开始，requestVo 承载具体业务查询条件。
 */
@Data
@ApiModel(value = "PageRequestVO", description = "通用分页查询请求")
public class PageRequestVO<T> implements Serializable {

    private static final long serialVersionUID = -1252801088582386915L;

    @ApiModelProperty(value = "当前页码，从 1 开始", example = "1")
    private long pageStart = 1L;
    @ApiModelProperty(value = "每页条数", example = "10")
    private long pageNums = 10L;
    @ApiModelProperty(value = "具体业务查询条件")
    private T requestVo;
}
