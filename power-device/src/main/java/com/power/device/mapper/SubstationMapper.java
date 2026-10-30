package com.power.device.mapper;

import com.power.common.mapper.BaseMapper;
import com.power.device.pojo.Substation;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 变电站 Mapper 接口
 * <p>自定义方法对应 SubstationMapper.xml 中的手写 SQL，
 * 包含多表关联查询和聚合统计。</p>
 */
public interface SubstationMapper extends BaseMapper<Substation> {

    /** 查询变电站详情（LEFT JOIN power_area 获取区域名称和负责人） */
    Map<String, Object> selectSubstationWithArea(@Param("id") Long id);

    /** 分页查询变电站（支持按名称、电压等级、状态等多条件动态过滤） */
    List<Map<String, Object>> selectSubstationPage(@Param("name") String name,
                                                    @Param("voltageLevel") String voltageLevel,
                                                    @Param("runStatus") String runStatus,
                                                    @Param("areaId") Long areaId,
                                                    @Param("managementLevel") String managementLevel);

    /** 带条件的分页查询（返回实体类型，支持排序） */
    List<Substation> selectByPageWithConditions(@Param("name") String name,
                                                @Param("voltageLevel") String voltageLevel,
                                                @Param("runStatus") String runStatus,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit,
                                                @Param("sortField") String sortField,
                                                @Param("sortOrder") String sortOrder);

    /** 带条件的计数查询 */
    long countByPageWithConditions(@Param("name") String name,
                                    @Param("voltageLevel") String voltageLevel,
                                    @Param("runStatus") String runStatus);

    /** 统计变电站整体概况（总数、各状态数量、平均管辖年限） */
    Map<String, Object> selectSubstationStatistics();

    /** 按电压等级分组统计变电站数量 */
    List<Map<String, Object>> selectSubstationGroupByVoltage();

    /** 动态更新变电站信息（<set> 标签实现非空字段更新） */
    int updateSubstationSelective(Substation substation);
}
