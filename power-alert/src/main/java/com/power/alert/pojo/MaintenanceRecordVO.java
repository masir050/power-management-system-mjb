package com.power.alert.pojo;

import lombok.Data;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 检修记录视图对象
 * <p>用于自定义 SQL 查询返回的检修记录信息，不含审计字段。</p>
 */
@Data
public class MaintenanceRecordVO {
    private Long id;
    private LocalDateTime occurrenceTime;
    private String faultType;
    private String faultDescription;
    private String repairResult;
    private LocalDateTime completionTime;
    private BigDecimal repairHours;
    private String repairPriority;
    private String responsibilityDivision;
    private Long equipmentId;
    private Long staffId;
}
