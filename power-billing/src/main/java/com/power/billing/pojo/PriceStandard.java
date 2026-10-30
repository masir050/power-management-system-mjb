package com.power.billing.pojo;

import com.power.common.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDate;
import java.math.BigDecimal;

/**
 * 电价标准实体，映射 price_standard 表
 * <p>按用电类别（居民/商业/工业/农业）定义电价标准，
 * 支持峰谷分时电价，记录每次调价的执行日期和优惠政策。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PriceStandard extends BaseEntity {
    /** 用电类别：居民、商业、工业、农业 */
    private String electricityCategory;
    /** 基础电价（元/kWh） */
    private BigDecimal basePrice;
    /** 高峰电价（元/kWh） */
    private BigDecimal peakPrice;
    /** 低谷电价（元/kWh） */
    private BigDecimal valleyPrice;
    /** 执行日期 */
    private LocalDate effectiveDate;
    /** 执行方案名称 */
    private String implementationPlan;
    /** 优惠政策说明 */
    private String preferentialPolicy;
    /** 计价方式：单一制、两部制 */
    private String pricingMethod;
}
