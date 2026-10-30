package com.power.billing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 日用电量 Mapper
 *
 * <p><b>所有方法都带 {@code scopeCustomerId}</b>：客户只能查自己的日用电，
 * 管理员传 null 查全站。这是本项目"同一页面不同人看到不同数据"的落地位置，
 * 前端不参与判断。</p>
 */
@Mapper
public interface DailyUsageMapper {

    /**
     * 日用电明细：某客户（或全站）最近 N 天的每日电量
     *
     * @param scopeCustomerId null 表示全站合计
     * @param days            查询天数，从今天往前推
     */
    List<Map<String, Object>> selectDailyTrend(@Param("scopeCustomerId") Long scopeCustomerId,
                                               @Param("days") int days);

    /**
     * 按电表分组的日用电：同一天多块表时，客户看到的是分表明细
     */
    List<Map<String, Object>> selectMeterDailyTrend(@Param("scopeCustomerId") Long scopeCustomerId,
                                                    @Param("meterId") Long meterId,
                                                    @Param("days") int days);

    /** 单个客户的汇总：近 N 天总用电、日均、峰值日 */
    Map<String, Object> selectCustomerSummary(@Param("scopeCustomerId") Long scopeCustomerId,
                                              @Param("days") int days);

    /** 尖峰平谷构成：最近 N 天四段电量之和 */
    Map<String, Object> selectTouSplit(@Param("scopeCustomerId") Long scopeCustomerId,
                                       @Param("days") int days);

    /** 按月汇总近 N 个月的用电量（首页"月度用电趋势"用） */
    List<Map<String, Object>> selectMonthlyTrend(@Param("scopeCustomerId") Long scopeCustomerId,
                                                 @Param("months") int months);

    /** 是否有某天的数据（定时采集任务的幂等判断） */
    int countByDate(@Param("usageDate") LocalDate usageDate);
}
