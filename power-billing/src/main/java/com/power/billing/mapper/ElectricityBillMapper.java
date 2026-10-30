package com.power.billing.mapper;

import com.power.common.mapper.BaseMapper;
import com.power.billing.pojo.ElectricityBill;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 电费账单 Mapper 接口
 * <p>自定义方法对应 ElectricityBillMapper.xml 中的手写 SQL，
 * 包含三表关联查询、欠费统计、年度聚合等复杂业务查询。</p>
 */
public interface ElectricityBillMapper extends BaseMapper<ElectricityBill> {

    /** 查询账单详情（bill JOIN customer；电表信息由 deviceClient 补齐） */
    Map<String, Object> selectBillWithDetails(@Param("id") Long id,
                                              @Param("scopeCustomerId") Long scopeCustomerId);

    /**
     * 分页查询账单（支持年份、状态、客户、用电量区间等多条件动态过滤）
     *
     * @param scopeCustomerId 数据范围：客户角色传自己的 customerId，其余传 null 表示不限
     */
    List<Map<String, Object>> selectBillPage(@Param("billingYear") Integer billingYear,
                                              @Param("paymentStatus") String paymentStatus,
                                              @Param("meterId") Long meterId,
                                              @Param("customerName") String customerName,
                                              @Param("minConsumption") Double minConsumption,
                                              @Param("maxConsumption") Double maxConsumption,
                                              @Param("scopeCustomerId") Long scopeCustomerId);

    /** 带条件的分页查询（返回实体类型，支持排序） */
    List<ElectricityBill> selectByPageWithConditions(@Param("billingYear") Integer billingYear,
                                                     @Param("paymentStatus") String paymentStatus,
                                                     @Param("offset") int offset,
                                                     @Param("limit") int limit,
                                                     @Param("sortField") String sortField,
                                                     @Param("sortOrder") String sortOrder);

    /** 带条件的计数查询 */
    long countByPageWithConditions(@Param("billingYear") Integer billingYear,
                                    @Param("paymentStatus") String paymentStatus);

    /**
     * 客户视图分页：只查该客户自己的账单
     * <p>与 {@link #selectByPageWithConditions} 的区别是强制 {@code customer_id = ?}，
     * 且 total 也按同一条件统计，保证分页条数与列表一致。</p>
     */
    List<ElectricityBill> selectCustomerBillPage(@Param("customerId") Long customerId,
                                                 @Param("billingYear") Integer billingYear,
                                                 @Param("paymentStatus") String paymentStatus,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    /** 客户视图分页的总数（条件与列表查询严格一致） */
    long countCustomerBillPage(@Param("customerId") Long customerId,
                               @Param("billingYear") Integer billingYear,
                               @Param("paymentStatus") String paymentStatus);

    /** 查询未缴/逾期账单列表（按欠费金额降序） */
    List<Map<String, Object>> selectUnpaidBills(@Param("billingYear") Integer billingYear,
                                                 @Param("customerName") String customerName,
                                                 @Param("scopeCustomerId") Long scopeCustomerId);

    /** 按年度统计账单数据（总用电量、总欠费、各状态数量） */
    List<Map<String, Object>> selectBillStatistics(@Param("scopeCustomerId") Long scopeCustomerId);

    /** 欠费金额分布统计（CASE WHEN 范围分组） */
    List<Map<String, Object>> selectArrearsDistribution(@Param("scopeCustomerId") Long scopeCustomerId);

    /** 动态更新账单信息 */
    int updateBillSelective(ElectricityBill bill);

    /** 缴费前取账单（含应缴 / 已缴 / 欠费三列），不走缓存，保证看到的是最新余额 */
    ElectricityBill selectForPay(@Param("id") Long id);

    /**
     * 扣减欠费并累加已缴，返回影响行数
     *
     * <p>条件里同时做了两道守卫：</p>
     * <ul>
     *   <li>{@code payment_status IN (...)} —— 已缴清的账单不允许重复缴；</li>
     *   <li>{@code arrears_amount >= #{amount}} —— 同一笔账单被并发缴纳时，
     *       后到的请求余额已不足，更新 0 行，服务层据此报「请刷新后重试」，
     *       不需要额外加锁或版本号。</li>
     * </ul>
     */
    int applyPayment(@Param("id") Long id,
                     @Param("amount") java.math.BigDecimal amount,
                     @Param("status") String status,
                     @Param("paymentMethod") String paymentMethod,
                     @Param("payTime") java.time.LocalDateTime payTime);
}
