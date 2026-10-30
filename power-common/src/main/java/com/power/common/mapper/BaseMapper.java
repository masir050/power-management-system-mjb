package com.power.common.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BaseMapper<T> {
    int insert(T entity);
    T selectById(Long id);
    List<T> selectByPage(@Param("offset") int offset, @Param("limit") int limit,
                         @Param("sortField") String sortField, @Param("sortOrder") String sortOrder);
    long countByPage();
    int updateById(T entity);
    int deleteById(Long id);
}
