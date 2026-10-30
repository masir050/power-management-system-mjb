package com.power.device.pojo;

import com.power.common.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDate;
import java.math.BigDecimal;

/**
 * 智能电表实体，映射 smart_meter 表
 * <p>记录安装在客户处的智能电表信息，包括型号、安装日期、检定周期等。
 * 通过 customer_id 关联到用电客户。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SmartMeter extends BaseEntity {
    /** 电表型号 */
    private String model;
    /** 安装日期 */
    private LocalDate installDate;
    /** 检定周期（月） */
    private Integer verificationCycle;
    /** 运行状态：正常、故障、待检定 */
    private String meterStatus;
    /** 安装位置 */
    private String installLocation;
    /** 绑定客户日期 */
    private LocalDate bindDate;
    /** 表计倍率 */
    private BigDecimal ratio;
    /** 所属客户 ID，关联 electricity_customer 表 */
    private Long customerId;
}
