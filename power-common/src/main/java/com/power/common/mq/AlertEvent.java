package com.power.common.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警事件消息体
 *
 * <p>在服务之间通过 RabbitMQ 传递时序列化为 JSON。字段设计上刻意带上
 * {@code messageId}——它同时是<b>本地消息表的业务主键</b>与<b>消费端的幂等键</b>：
 * RabbitMQ 只保证"至少一次"投递，网络抖动或消费者重启都会造成重复投递，
 * 消费端凭 messageId 做去重才能保证业务不重复执行。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息唯一标识（= 本地消息表主键），消费端据此幂等去重 */
    private String messageId;

    /** 事件类型：ALERT_CREATED / ALERT_RESOLVED */
    private String eventType;

    /** 告警业务 ID（maintenance_record.id 或 alert.id） */
    private Long alertId;

    /** 关联设备 ID */
    private Long equipmentId;

    /** 关联设备名称（冗余，避免消费端再远程调用） */
    private String equipmentName;

    /** 故障类型 */
    private String faultType;

    /** 故障描述 */
    private String faultDescription;

    /** 优先级：紧急/高/中/低 */
    private String priority;

    /** 事件发生时间 */
    private LocalDateTime occurredAt;
}
