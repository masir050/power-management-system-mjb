package com.power.device.mapper;

import com.power.common.mapper.BaseMapper;
import com.power.device.pojo.PowerArea;
import com.power.device.pojo.PowerAreaVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 供电区域 Mapper 接口
 * <p>继承 BaseMapper 获得基础的 CRUD 方法，
 * 自定义方法对应 PowerAreaMapper.xml 中的手写 SQL。</p>
 */
public interface PowerAreaMapper extends BaseMapper<PowerArea> {

    /** 分页查询供电区域（支持按名称、级别、负责人模糊过滤） */
    List<PowerAreaVO> selectAreaPage(@Param("areaName") String areaName,
                                     @Param("adminLevel") String adminLevel,
                                     @Param("leader") String leader);

    /** 带条件的分页查询（返回实体类型，支持排序） */
    List<PowerArea> selectByPageWithConditions(@Param("areaName") String areaName,
                                                @Param("adminLevel") String adminLevel,
                                                @Param("leader") String leader,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit,
                                                @Param("sortField") String sortField,
                                                @Param("sortOrder") String sortOrder);

    /** 带条件的计数查询 */
    long countByPageWithConditions(@Param("areaName") String areaName,
                                    @Param("adminLevel") String adminLevel,
                                    @Param("leader") String leader);

    /** 查询各区域及其下辖变电站数量（LEFT JOIN + GROUP BY） */
    List<Map<String, Object>> selectAreaWithSubstationCount(@Param("areaName") String areaName);

    /** 统计各行政级别的区域数量（CASE WHEN 分组聚合） */
    Map<String, Object> selectAreaStatistics();

    /** 动态更新区域（仅更新非空字段，使用 <set> 标签） */
    int updateAreaSelective(PowerArea area);

    /** 批量逻辑删除区域（使用 <foreach> 实现 IN 查询） */
    int deleteAreaByIds(@Param("ids") List<Long> ids);
}
