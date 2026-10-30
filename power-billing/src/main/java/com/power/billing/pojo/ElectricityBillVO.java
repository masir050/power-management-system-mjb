package com.power.billing.pojo;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;

/**
 * 电费账单视图对象
 * <p>用于自定义 SQL 查询返回的账单信息，不含审计字段。</p>
 */
@Data
public class ElectricityBillVO {
    private Long id;
    private Integer billingYear;
    private BigDecimal totalConsumption;
    private BigDecimal arrearsAmount;
    private String paymentStatus;
    private LocalDateTime generateTime;
    private Integer reminderCount;
    private LocalDate paymentDeadline;
    private BigDecimal currentReading;
    private BigDecimal previousReading;
    private LocalDateTime meterReadingTime;
    private Long meterId;
}
