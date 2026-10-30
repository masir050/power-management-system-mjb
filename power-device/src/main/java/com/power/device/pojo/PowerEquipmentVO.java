package com.power.device.pojo;

import lombok.Data;
import java.time.LocalDate;

/**
 * 电力设备视图对象
 * <p>用于自定义 SQL 查询返回的设备信息，不含审计字段。</p>
 */
@Data
public class PowerEquipmentVO {
    private Long id;
    private String equipmentName;
    private String equipmentType;
    private String ratedParams;
    private String installLocation;
    private LocalDate manufactureDate;
    private String impactScope;
    private String hazardLevel;
    private String installBatch;
    private Integer quantity;
    private String commissionBatch;
    private Long substationId;
}
