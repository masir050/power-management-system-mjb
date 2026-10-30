package com.power.device.pojo;

import lombok.Data;
import java.time.LocalDate;
import java.math.BigDecimal;

/**
 * 智能电表视图对象
 * <p>用于自定义 SQL 查询返回的电表信息，不含审计字段。</p>
 */
@Data
public class SmartMeterVO {
    private Long id;
    private String model;
    private LocalDate installDate;
    private Integer verificationCycle;
    private String meterStatus;
    private String installLocation;
    private LocalDate bindDate;
    private BigDecimal ratio;
    private Long customerId;
}
