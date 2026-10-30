package com.power.billing.mapper;

import com.power.common.mapper.BaseMapper;
import com.power.billing.pojo.PriceStandard;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 电价标准 Mapper 接口
 * <p>自定义方法对应 PriceStandardMapper.xml 中的手写 SQL，
 * 包含最新生效电价查询（子查询）和按类别的聚合统计。</p>
 */
public interface PriceStandardMapper extends BaseMapper<PriceStandard> {

    /** 分页查询电价（支持按用电类别、计价方式动态过滤） */
    List<Map<String, Object>> selectPricePage(@Param("electricityCategory") String electricityCategory,
                                               @Param("pricingMethod") String pricingMethod,
                                               @Param("effectiveDate") LocalDate effectiveDate);

    /** 查询指定日期各用电类别下最新生效的电价（子查询取 MAX 日期） */
    List<Map<String, Object>> selectEffectivePrices(@Param("queryDate") LocalDate queryDate);

    /** 统计各用电类别的电价版本数、平均/最高/最低价格 */
    List<Map<String, Object>> selectPriceStatistics();

    /** 带条件的分页查询（返回实体类型，支持排序） */
    List<PriceStandard> selectByPageWithConditions(@Param("electricityCategory") String electricityCategory,
                                                    @Param("effectiveDate") LocalDate effectiveDate,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit,
                                                    @Param("sortField") String sortField,
                                                    @Param("sortOrder") String sortOrder);

    /** 带条件的计数查询 */
    long countByPageWithConditions(@Param("electricityCategory") String electricityCategory,
                                    @Param("effectiveDate") LocalDate effectiveDate);

    /** 动态更新电价标准 */
    int updatePriceSelective(PriceStandard price);
}
