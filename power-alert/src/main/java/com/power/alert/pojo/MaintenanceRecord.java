package com.power.alert.pojo;

import com.power.common.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 检修记录实体，映射 maintenance_record 表
 * <p>记录每次设备故障的检修全过程：故障发生时间、类型、描述、
 * 检修结果、工时、优先级以及责任人员。通过 equipment_id 和 staff_id
 * 分别关联设备和运维人员。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaintenanceRecord extends BaseEntity {
    /** 故障发生时间 */
    private LocalDateTime occurrenceTime;
    /** 故障类型（如设备过热、开关跳闸、绝缘降低） */
    private String faultType;
    /** 故障详细描述 */
    private String faultDescription;
    /** 检修结果 */
    private String repairResult;
    /** 检修完成时间 */
    private LocalDateTime completionTime;
    /** 检修工时（小时） */
    private BigDecimal repairHours;
    /** 检修优先级：紧急、高、中、低 */
    private String repairPriority;
    /** 责任分工描述 */
    private String responsibilityDivision;
    /** 关联设备 ID，关联 power_equipment 表 */
    private Long equipmentId;
    /** 关联运维人员 ID，关联 maintenance_staff 表 */
    private Long staffId;
}
