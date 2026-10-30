package com.power.alert.mq;

import com.power.alert.mapper.LocalMessageMapper;
import com.power.alert.pojo.LocalMessage;
import com.power.common.mq.AlertEvent;
import com.power.common.mq.AlertMqConstants;
import com.power.common.util.JsonSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 告警事件发件箱 —— 事务性发件箱的"写入侧"
 *
 * <p>职责只有一个：在<b>业务事务内</b>把"要发的消息"写进 {@code local_message} 表。
 * 真正的 MQ 投递由 {@link LocalMessageDispatcher} 异步完成。</p>
 *
 * <h3>为什么必须先写库而不是直接发 MQ</h3>
 * <p>直接 {@code rabbitTemplate.convertAndSend()} 的问题：消息发出去了，但随后业务事务回滚
 * （比如唯一约束冲突、数据库连接断），消费者收到了一条"对应告警并不存在"的消息，
 * 消费端去查 alert 表查不到，只能丢弃或报错。反过来，如果先提交事务再发消息，
 * 发消息时进程崩了，告警就永远推不出去。</p>
 *
 * <p>写进同一张库的同一个事务后，二者要么一起成功要么一起回滚，
 * 再由后台任务异步补偿投递，天然满足"业务成功 ⇒ 消息最终送达"。</p>
 *
 * <h3>调用约定（很重要）</h3>
 * <p>本方法<b>必须</b>在调用方的 {@code @Transactional} 方法内被调用，
 * 且调用方事务的隔离级别要能读到本条 {@code local_message} 记录。
 * 如果调用方没有事务，每条语句各自提交，就退化成"先写消息再写业务"，
 * 一致性保证不成立。当前唯一调用方是 {@code AlertService#createAlert}。</p>
 */
@Slf4j
@Component
public class AlertEventPublisher {

    private final LocalMessageMapper localMessageMapper;

    public AlertEventPublisher(LocalMessageMapper localMessageMapper) {
        this.localMessageMapper = localMessageMapper;
    }

    /**
     * 在本地事务内登记一条待投递的告警事件
     *
     * @param event     告警事件（messageId 为空时自动生成）
     * @param routingKey 路由键，见 {@link AlertMqConstants}
     * @return 登记后的 messageId，便于调用方回写或日志追踪
     */
    public String enqueue(AlertEvent event, String routingKey) {
        if (event.getMessageId() == null || event.getMessageId().isBlank()) {
            // UUID 而非自增 ID：messageId 要跨服务传递，自增 ID 在不同库可能重复
            event.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(LocalDateTime.now());
        }

        LocalMessage msg = new LocalMessage();
        msg.setMessageId(event.getMessageId());
        msg.setExchangeName(AlertMqConstants.ALERT_EXCHANGE);
        msg.setRoutingKey(routingKey);
        msg.setPayload(JsonSupport.toJson(event));
        msg.setStatus(LocalMessage.STATUS_PENDING);
        msg.setRetryCount(0);
        // 立即可投递：补偿任务每轮扫 next_retry_time <= now() 的记录
        msg.setNextRetryTime(LocalDateTime.now());

        localMessageMapper.insert(msg);
        log.debug("告警事件已登记待投递 messageId={} routingKey={}", event.getMessageId(), routingKey);
        return event.getMessageId();
    }
}
