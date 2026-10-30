package com.power.billing.pojo;

import com.power.common.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日用电量实体，映射 electricity_daily_usage 表
 *
 * <p>一块电表一天一行，按尖/峰/平/谷四段记录电量。
 * {@code totalUsage} 是数据库的生成列（STORED GENERATED），插入时不传，
 * 由 MySQL 按四段之和自动算出——避免应用层与数据库两份口径。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DailyUsage extends BaseEntity {
    /** 电表 ID（power_device 库） */
    private Long meterId;
    /** 用电客户 ID */
    private Long customerId;
    /** 用电日期 */
    private LocalDate usageDate;
    /** 尖峰段电量(kWh) */
    private BigDecimal peakUsage;
    /** 高峰段电量(kWh) */
    private BigDecimal highUsage;
    /** 平段电量(kWh) */
    private BigDecimal flatUsage;
    /** 低谷段电量(kWh) */
    private BigDecimal valleyUsage;
    /** 合计电量(kWh)，数据库生成列，只读 */
    private BigDecimal totalUsage;
}
