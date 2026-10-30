package com.power.common.mq;

/**
 * RabbitMQ 告警事件的交换机/队列/路由键常量
 *
 * <p>集中定义，避免生产端与消费端各写一套字符串导致路由对不上（这种错通常只能在运行期暴露）。
 * 声明关系见 {@code power-alert} 模块的 {@code AlertMqConfig}。</p>
 */
public final class AlertMqConstants {

    private AlertMqConstants() {
    }

    /** 告警事件主交换机（topic 类型，便于后续按严重级别扩展路由） */
    public static final String ALERT_EXCHANGE = "power.alert.exchange";

    /** 告警事件死信交换机：消费失败且重试耗尽的消息进入这里，便于人工排查 */
    public static final String ALERT_DLX_EXCHANGE = "power.alert.dlx.exchange";

    /** 告警事件队列 */
    public static final String ALERT_QUEUE = "power.alert.queue";

    /** 告警死信队列 */
    public static final String ALERT_DLQ = "power.alert.dlq";

    /** 告警事件路由键：新增告警 */
    public static final String RK_ALERT_CREATED = "alert.created";

    /** 告警事件路由键：告警状态变更（确认/处理完成） */
    public static final String RK_ALERT_RESOLVED = "alert.resolved";

    /** 死信路由键 */
    public static final String RK_DEAD = "dead";
}
