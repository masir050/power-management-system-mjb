package com.power.alert.mq;

import com.power.alert.ws.AlertWebSocketEndpoint;
import com.power.common.mq.AlertEvent;
import com.power.common.mq.AlertMqConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警事件消费者
 *
 * <h3>职责边界</h3>
 * <p>消费端<b>只做"推送"这一件事</b>，不再改数据库。原因：告警的落库已经在生产端
 * 的事务里完成，消费端再改一次没有意义，反而引入"消费端改库失败要不要回滚消息"的麻烦。
 * 这样消费逻辑变成纯转发，失败重投也不会造成数据副作用。</p>
 *
 * <h3>幂等</h3>
 * <p>本地消息表的投递语义是 at-least-once（见 {@link LocalMessageDispatcher}），
 * 重复投递必然发生。这里用 messageId 做去重：同一 messageId 只处理一次。</p>
 *
 * <p><b>关于去重实现的选择</b>：当前用带 TTL 的本地 Map。它在单实例下完全够用；
 * 多实例时每个实例各有一份，同一条消息会被每个实例各推一次（前端收到重复弹窗）。
 * 生产上应改为 Redis {@code SETNX alert:dedup:{messageId} EX 3600}——
 * 接口已留好，只需替换 {@link #markProcessed} / {@link #isDuplicate} 两处实现。</p>
 */
@Slf4j
@Component
public class AlertEventConsumer {

    /** 去重窗口：10 分钟足够覆盖所有重投（补偿任务最多重试 5 次，间隔封顶 60s） */
    private static final long DEDUP_TTL_MILLIS = 10 * 60 * 1000L;

    /** messageId -> 处理时间。用 ConcurrentHashMap，消费线程池是多线程的 */
    private final Map<String, Long> processedMessages = new ConcurrentHashMap<>();

    // 拓扑与绑定由 AlertMqConfig 统一声明（那里带了死信参数）。
    // 这里若再用 @QueueBinding 声明同名队列，参数与 Config 不一致会让 broker 抛
    // 406 PRECONDITION_FAILED，监听器起不来，所以只引用队列名。
    @RabbitListener(queues = AlertMqConstants.ALERT_QUEUE)
    public void onAlertEvent(AlertEvent event) {
        if (event == null || event.getMessageId() == null) {
            // 空消息体通常是序列化失败，重投也没用，直接丢弃（不抛异常即视为 ack）
            log.warn("收到空告警事件，丢弃");
            return;
        }
        if (isDuplicate(event.getMessageId())) {
            log.info("重复消息，跳过推送 messageId={}", event.getMessageId());
            return;
        }

        try {
            Map<String, Object> push = new LinkedHashMap<>();
            push.put("type", "ALERT");
            push.put("eventType", event.getEventType());
            push.put("messageId", event.getMessageId());
            push.put("alertId", event.getAlertId());
            push.put("equipmentId", event.getEquipmentId());
            push.put("equipmentName", event.getEquipmentName());
            push.put("faultType", event.getFaultType());
            push.put("faultDescription", event.getFaultDescription());
            push.put("priority", event.getPriority());
            push.put("occurredAt", event.getOccurredAt());
            push.put("pushedAt", LocalDateTime.now());
            // 让前端能据此渲染不同颜色：ALERT_CREATED 红色进列表，ALERT_RESOLVED 置灰归档
            push.put("title", "ALERT_RESOLVED".equals(event.getEventType())
                    ? "告警已处理" : "新增告警");

            AlertWebSocketEndpoint.broadcast(push);
            markProcessed(event.getMessageId());

            log.info("告警事件已推送 messageId={} alertId={} 在线数={}",
                    event.getMessageId(), event.getAlertId(), AlertWebSocketEndpoint.onlineCount());
        } catch (Exception e) {
            // 抛异常 -> Spring AMQP 按 retry 配置重试，超过次数后进死信队列
            log.error("告警事件推送失败 messageId={}", event.getMessageId(), e);
            throw e;
        }
    }

    /**
     * 判断是否重复
     * <p>同时顺手清理过期记录，避免 Map 无限增长——
     * 用"读时清理"而不是定时任务，因为消费频率本身就足够触发清理。</p>
     */
    private boolean isDuplicate(String messageId) {
        long now = System.currentTimeMillis();
        Long last = processedMessages.get(messageId);
        if (last != null && now - last < DEDUP_TTL_MILLIS) {
            return true;
        }
        // 兜底清理：超过 10000 条时扫一遍，防止无流量时从不清理
        if (processedMessages.size() > 10000) {
            processedMessages.entrySet().removeIf(e -> now - e.getValue() > DEDUP_TTL_MILLIS);
        }
        return false;
    }

    private void markProcessed(String messageId) {
        processedMessages.put(messageId, System.currentTimeMillis());
    }
}
