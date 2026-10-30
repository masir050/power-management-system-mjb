package com.power.common.pojo;

import lombok.Data;
import java.util.List;

/**
 * 分页查询结果封装类
 * <p>包含总记录数、总页数、当前页码、每页大小和当前页数据列表。</p>
 *
 * @param <T> 记录类型
 */
@Data
public class PageResult<T> {
    /** 总记录数 */
    private long total;
    /** 总页数 */
    private long pages;
    /** 当前页码 */
    private long current;
    /** 每页大小 */
    private long size;
    /** 当前页数据列表 */
    private List<T> records;
}
