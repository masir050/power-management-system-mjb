package com.power.alert.pojo;

import com.power.common.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 本地消息表实体，映射 local_message 表
 *
 * <p><b>为什么需要它</b>：告警落库和发 MQ 是两件事，无法放进同一个事务。
 * 若先落库再发 MQ，发消息失败则告警永远推不出去；若先发 MQ 再落库，消息消费方可能查不到对应告警。
 * 本地消息表把"要发的消息"和业务数据写在<b>同一个本地事务</b>里，再由后台任务把消息投递出去，
 * 从而保证"业务成功 ⇒ 消息最终一定被投递"（最终一致性）。</p>
 *
 * <p><b>投递次数</b>：至少一次。后台任务可能把同一条消息投多次，
 * 因此消费端必须用 {@code messageId} 做幂等。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LocalMessage extends BaseEntity {

    /** 待投递 */
    public static final String STATUS_PENDING = "PENDING";
    /** 已投递成功 */
    public static final String STATUS_SENT = "SENT";
    /** 投递失败（已超过最大重试次数，需人工介入） */
    public static final String STATUS_FAILED = "FAILED";

    /** 消息唯一 ID，同时作为消费端幂等键 */
    private String messageId;

    /** 交换机 */
    private String exchangeName;

    /** 路由键 */
    private String routingKey;

    /** 消息体（JSON） */
    private String payload;

    /** 状态：PENDING / SENT / FAILED */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最近一次投递失败原因 */
    private String lastError;

    /** 下次可重试时间（用于退避） */
    private LocalDateTime nextRetryTime;

    /** 投递成功时间 */
    private LocalDateTime sentTime;
}
