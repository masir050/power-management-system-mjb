package com.power.device.mapper;

import com.power.common.mapper.BaseMapper;
import com.power.device.pojo.PowerEquipment;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 电力设备 Mapper 接口
 * <p>自定义方法对应 PowerEquipmentMapper.xml 中的手写 SQL，
 * 包含设备与变电站关联查询、按类型/危害等级的分组统计。</p>
 */
public interface PowerEquipmentMapper extends BaseMapper<PowerEquipment> {

    /** 查询设备详情（LEFT JOIN substation 获取变电站名称和电压等级） */
    Map<String, Object> selectEquipmentWithSubstation(@Param("id") Long id);

    /** 分页查询设备（支持按名称、类型、危害等级、变电站等多条件过滤） */
    List<Map<String, Object>> selectEquipmentPage(@Param("equipmentName") String equipmentName,
                                                   @Param("equipmentType") String equipmentType,
                                                   @Param("hazardLevel") String hazardLevel,
                                                   @Param("substationId") Long substationId,
                                                   @Param("installBatch") String installBatch);

    /** 带条件的分页查询（返回实体类型，支持排序） */
    List<PowerEquipment> selectByPageWithConditions(@Param("equipmentName") String equipmentName,
                                                    @Param("equipmentType") String equipmentType,
                                                    @Param("hazardLevel") String hazardLevel,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit,
                                                    @Param("sortField") String sortField,
                                                    @Param("sortOrder") String sortOrder);

    /** 带条件的计数查询 */
    long countByPageWithConditions(@Param("equipmentName") String equipmentName,
                                    @Param("equipmentType") String equipmentType,
                                    @Param("hazardLevel") String hazardLevel);

    /** 统计设备概况（总数、总数量、各类型/等级分布） */
    Map<String, Object> selectEquipmentStatistics();

    /** 按危害等级分组统计设备（使用 FIELD() 自定义排序） */
    List<Map<String, Object>> selectEquipmentGroupByHazard();

    /** 动态更新设备信息（仅更新非空字段） */
    int updateEquipmentSelective(PowerEquipment equipment);
}
