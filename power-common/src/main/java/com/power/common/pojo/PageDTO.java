package com.power.common.pojo;

import lombok.Data;

/**
 * 分页查询参数 DTO
 * <p>封装前端传递的分页和排序参数，提供合理的默认值：
 * 默认第1页、每页10条、按创建时间降序排列。</p>
 */
@Data
public class PageDTO {
    /** 当前页码（从1开始），默认1 */
    private int page = 1;
    /** 每页记录数，默认10 */
    private int size = 10;
    /** 排序字段名（驼峰格式），默认 createTime */
    private String sortField = "createTime";
    /** 排序方向：asc-升序，desc-降序，默认 asc */
    private String sortOrder = "asc";
}
