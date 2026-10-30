package com.power.alert.pojo;

import com.power.common.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 告警实体，映射 alert 表
 *
 * <p>告警是独立的业务对象，不从检修记录派生：检修记录描述"已经处理完的一次维修"，
 * 告警描述"发生过一次需要人注意的异常"，二者是 1:N（一条告警可能被多次检修）。
 * 把两者混在一张表里，是单体时代勉强能跑、拆分后立刻打架的做法。</p>
 *
 * <p>状态机：{@code PENDING}（待处理）→ {@code ACKNOWLEDGED}（已确认）→ {@code RESOLVED}（已处理）。
 * 只允许前向流转，不允许回退，避免已处理告警被误改回待处理导致重复派单。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AlertRecord extends BaseEntity {

    /** 待处理 */
    public static final String STATUS_PENDING = "PENDING";
    /** 已确认（有人接了） */
    public static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    /** 已处理 */
    public static final String STATUS_RESOLVED = "RESOLVED";

    /** 关联设备 ID（设备数据在 power_device 库，这里只存 ID，不冗余设备名） */
    private Long equipmentId;

    /** 设备名称快照：告警发生时立刻从 device 服务取一次并落库，设备改名后历史告警仍显示当时的名字 */
    private String equipmentName;

    /** 故障类型（如设备过热、开关跳闸、绝缘降低） */
    private String faultType;

    /** 告警描述 */
    private String alertDescription;

    /** 告警级别：紧急、高、中、低 */
    private String alertLevel;

    /** 告警状态：PENDING / ACKNOWLEDGED / RESOLVED */
    private String alertStatus;

    /** 告警触发时间 */
    private LocalDateTime occurrenceTime;

    /** 确认时间 */
    private LocalDateTime acknowledgeTime;

    /** 处理完成时间 */
    private LocalDateTime resolveTime;

    /** 处理人 ID（运维人员） */
    private Long handlerId;

    /** 处理结果 */
    private String handleResult;
}
