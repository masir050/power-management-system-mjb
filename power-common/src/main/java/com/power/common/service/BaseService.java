package com.power.common.service;

import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;

import java.util.Map;

/**
 * 基础业务接口
 * <p>定义通用的 CRUD 方法，所有业务 Service 均实现此接口。
 * 泛型 T 为对应的实体类型。</p>
 *
 * @param <T> 实体类型
 */
public interface BaseService<T> {

    /** 分页查询（支持多条件过滤和排序） */
    PageResult<T> pageList(PageDTO pageDTO, Map<String, String> conditions);

    /** 根据 ID 查询 */
    T getById(Long id);

    /** 新增 */
    boolean create(T entity);

    /** 更新 */
    boolean update(T entity);

    /** 删除（逻辑删除） */
    boolean delete(Long id);
}
