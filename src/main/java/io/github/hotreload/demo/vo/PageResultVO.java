package io.github.hotreload.demo.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用分页查询结果。
 * <p>
 * 接口返回总数、当前页信息和当前页数据列表，便于前端直接渲染表格。
 */
@Data
@ApiModel(value = "PageResultVO", description = "通用分页查询结果")
public class PageResultVO<T> implements Serializable {

    private static final long serialVersionUID = 6758298051285039896L;

    @ApiModelProperty(value = "符合查询条件的总记录数")
    private long total;
    @ApiModelProperty(value = "当前页码，从 1 开始")
    private long pageStart;
    @ApiModelProperty(value = "每页条数")
    private long pageNums;
    @ApiModelProperty(value = "当前页数据列表")
    private List<T> rows = new ArrayList<>();
}
