package com.power.billing.pojo;

import com.power.common.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;

/**
 * 电费账单实体，映射 electricity_bill 表
 * <p>记录每个计费周期内智能电表的用电量和费用信息，
 * 包含止码、用电量、欠费金额和缴费状态。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ElectricityBill extends BaseEntity {
    /** 计费年份 */
    private Integer billingYear;
    /** 总用电量（kWh） */
    private BigDecimal totalConsumption;
    /** 本期应缴电费（元），出账时按「用电量 × 电价」定死，缴费后不变 */
    private BigDecimal billAmount;
    /** 累计已缴（元） */
    private BigDecimal paidAmount;
    /** 最近一次缴费时间 */
    private LocalDateTime paidTime;
    /** 最近一次支付方式：微信、支付宝、银行卡、现金 */
    private String paymentMethod;
    /** 尚欠金额（元）= billAmount - paidAmount */
    private BigDecimal arrearsAmount;
    /** 缴费状态：已缴、未缴、部分缴纳、逾期 */
    private String paymentStatus;
    /** 账单生成时间 */
    private LocalDateTime generateTime;
    /** 催缴次数 */
    private Integer reminderCount;
    /** 缴费截止日期 */
    private LocalDate paymentDeadline;
    /** 本期止码 */
    private BigDecimal currentReading;
    /** 上期止码 */
    private BigDecimal previousReading;
    /** 抄表时间 */
    private LocalDateTime meterReadingTime;
    /** 关联电表 ID，关联 smart_meter 表 */
    private Long meterId;
    /**
     * 用电户 ID
     *
     * <p>这是<b>有意冗余</b>的字段：账单本来可以通过 meter_id 反查电表、
     * 再反查客户，但电表在 power_device 库、客户在本库，跨库反查既慢又会让
     * 分页的 total 算不准。账单生成时客户归属就是确定的，直接落一列最省事。</p>
     */
    private Long customerId;
}
