package com.power.device.mapper;

import com.power.common.mapper.BaseMapper;
import com.power.device.pojo.SmartMeter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 智能电表 Mapper 接口
 * <p>自定义方法对应 SmartMeterMapper.xml 中的手写 SQL，
 * 包含电表与客户关联查询、按型号的状态分组统计。</p>
 */
public interface SmartMeterMapper extends BaseMapper<SmartMeter> {

    /** 查询电表详情（LEFT JOIN electricity_customer 获取客户信息） */
    Map<String, Object> selectMeterWithCustomer(@Param("id") Long id);

    /** 分页查询电表（支持按型号、状态、客户等多条件动态过滤） */
    List<Map<String, Object>> selectMeterPage(@Param("model") String model,
                                               @Param("meterStatus") String meterStatus,
                                               @Param("customerId") Long customerId,
                                               @Param("installLocation") String installLocation);

    /** 带条件的分页查询（返回实体类型，支持排序） */
    List<SmartMeter> selectByPageWithConditions(@Param("model") String model,
                                                @Param("meterStatus") String meterStatus,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit,
                                                @Param("sortField") String sortField,
                                                @Param("sortOrder") String sortOrder);

    /** 带条件的计数查询 */
    long countByPageWithConditions(@Param("model") String model,
                                    @Param("meterStatus") String meterStatus);

    /** 统计电表概况（总数、正常/故障/待检定数量） */
    Map<String, Object> selectMeterStatistics();

    /** 按型号分组统计各运行状态的电表数量 */
    List<Map<String, Object>> selectMetersByStatusGroupByModel();

    /** 动态更新电表信息 */
    int updateMeterSelective(SmartMeter meter);
}
