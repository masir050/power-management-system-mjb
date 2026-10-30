package com.power.common.service;

import com.power.common.mapper.BaseMapper;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 基础业务实现
 *
 * <p>把原本在各个 Service 里逐份复制的分页逻辑（参数归一化、排序字段白名单、
 * 总数与页数计算）收敛到这里，子类只需覆写真正有差异的部分。</p>
 *
 * <p><b>安全关键点</b>：排序字段最终会拼进 SQL 的 {@code ORDER BY ${sortField}}，
 * 因此必须做白名单校验。原实现的 {@code sortField.matches("[a-zA-Z_]+")} 只挡住了注入字符，
 * 但当表里没有这个列时 SQL 会直接报错（暴露表结构）；这里改成<b>与允许排序的列集合比对</b>，
 * 不合法的字段一律回落到默认列。</p>
 */
public abstract class BaseServiceImpl<T> implements BaseService<T> {

    /** 每页最大条数，防止前端传 size=100000 拖垮数据库 */
    protected static final int MAX_PAGE_SIZE = 500;

    /** 默认排序字段（数据库列名形式） */
    protected static final String DEFAULT_SORT_FIELD = "create_time";

    protected final BaseMapper<T> mapper;

    protected BaseServiceImpl(BaseMapper<T> mapper) {
        this.mapper = mapper;
    }

    @Override
    public PageResult<T> pageList(PageDTO pageDTO, Map<String, String> conditions) {
        PageQuery query = resolvePageQuery(pageDTO);

        List<T> records = mapper.selectByPage(query.offset(), query.size(),
                query.sortField(), query.sortOrder());
        long total = mapper.countByPage();

        PageResult<T> result = new PageResult<>();
        result.setTotal(total);
        result.setPages((long) Math.ceil((double) total / query.size()));
        result.setCurrent(query.page());
        result.setSize(query.size());
        result.setRecords(records);
        return result;
    }

    @Override
    public T getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public boolean create(T entity) {
        return mapper.insert(entity) > 0;
    }

    @Override
    public boolean update(T entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean delete(Long id) {
        return mapper.deleteById(id) > 0;
    }

    /**
     * 归一化分页参数并校验排序字段
     *
     * @param pageDTO 前端传入的分页参数（可能为 null 或含非法值）
     * @return 可直接用于 SQL 的分页参数
     */
    protected PageQuery resolvePageQuery(PageDTO pageDTO) {
        if (pageDTO == null) {
            pageDTO = new PageDTO();
        }
        int page = Math.max(1, pageDTO.getPage());
        int size = Math.min(Math.max(1, pageDTO.getSize()), MAX_PAGE_SIZE);
        String order = "desc".equalsIgnoreCase(pageDTO.getSortOrder()) ? "desc" : "asc";
        return new PageQuery(page, size, (page - 1) * size,
                resolveSortField(pageDTO.getSortField()), order);
    }

    /**
     * 排序字段白名单校验：允许子类覆写以开放自己表的可排序列
     *
     * @param sortField 前端传入的驼峰字段名
     * @return 合法的数据库列名；不合法时返回默认列
     */
    protected String resolveSortField(String sortField) {
        if (sortField == null || sortField.isBlank()) {
            return DEFAULT_SORT_FIELD;
        }
        String column = camelToSnake(sortField.trim());
        return isSortableColumn(column) ? column : DEFAULT_SORT_FIELD;
    }

    /**
     * 判断某列是否允许排序
     * <p>默认只允许基础审计列，子类按需追加（如按投运日期、按欠费金额排序）。</p>
     */
    protected boolean isSortableColumn(String column) {
        return "create_time".equals(column) || "update_time".equals(column) || "id".equals(column);
    }

    /** 驼峰转下划线，便于前端直接传 Java 字段名而不用关心表结构 */
    protected static String camelToSnake(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        result.append(Character.toLowerCase(str.charAt(0)));
        for (int i = 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('_').append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 归一化后的分页参数
     *
     * @param page 页码（从 1 开始）
     * @param size 每页条数
     * @param offset SQL 的 OFFSET
     * @param sortField 已校验的数据库列名
     * @param sortOrder asc / desc
     */
    protected record PageQuery(int page, int size, int offset, String sortField, String sortOrder) {
    }
}
