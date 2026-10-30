package com.power.alert.mq;

import com.power.alert.mapper.LocalMessageMapper;
import com.power.alert.pojo.LocalMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表投递器 —— 事务性发件箱的"投递侧"
 *
 * <h3>为什么需要这一层</h3>
 * <p>告警落库（写 {@code alert} 表）与发 RabbitMQ 是两件独立的事，没有跨资源的事务：
 * 先落库再发消息，发消息失败则告警永远推不出去；先发消息再落库，消费方可能查不到告警。
 * 本地消息表把"要发的消息"和业务数据放进<b>同一个本地数据库事务</b>，
 * 再由本类的定时任务把消息搬到 MQ，保证"业务提交成功 ⇒ 消息最终一定被投递"。</p>
 *
 * <h3>投递语义：至少一次（at-least-once）</h3>
 * <p>投递成功后、标记 SENT 前若进程崩溃，消息会在下一轮被重复投递。
 * 这是<b>刻意</b>的取舍——宁可重复也不能丢；重复由消费端的 messageId 幂等去重兜住。</p>
 *
 * <h3>三个关键动作</h3>
 * <ol>
 *   <li><b>抢占</b>：{@code FOR UPDATE SKIP LOCKED} 让多实例并行补偿互不阻塞，见 LocalMessageMapper.xml；</li>
 *   <li><b>确认</b>：投递成功必须等 broker 确认（publisher-confirm）后才标 SENT，
 *       否则只是"写进了客户端缓冲区"就标成功，进程一崩消息就丢；</li>
 *   <li><b>退避</b>：失败不立即重试，按指数退避（1s、2s、4s……封顶 60s），
 *       避免 broker 短暂抖动时被几千次重试打垮。</li>
 * </ol>
 */
@Slf4j
@Component
public class LocalMessageDispatcher {

    /** 单轮最多处理多少条，防止一次捞太多把内存打满 */
    private static final int BATCH_SIZE = 100;

    /** 最大重试次数：超过则转 FAILED，留待人工处理 */
    private static final int MAX_RETRY = 5;

    /** 退避基数（秒） */
    private static final long BASE_BACKOFF_SECONDS = 1L;

    /** 退避上限（秒） */
    private static final long MAX_BACKOFF_SECONDS = 60L;

    private final LocalMessageMapper localMessageMapper;
    private final RabbitTemplate rabbitTemplate;
    /** 自身代理：用于让 grabPending 的 @Transactional 真正生效 */
    private final LocalMessageDispatcher self;

    public LocalMessageDispatcher(LocalMessageMapper localMessageMapper,
                                  RabbitTemplate rabbitTemplate,
                                  @Lazy LocalMessageDispatcher self) {
        this.localMessageMapper = localMessageMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.self = self;
    }

    /**
     * 定时扫描并投递待发送消息
     *
     * <p>间隔 5 秒：告警场景对延迟不敏感（秒级足够），但也不能太长，
     * 否则 MQ 恢复后积压要等很久才清空。</p>
     *
     * <p><b>注意事务边界</b>：本方法刻意<b>不</b>加 {@code @Transactional}。
     * 抢占用的行锁必须在方法返回时立即释放，否则整个投递过程（含网络 IO）都占着锁，
     * 多实例就退化成串行。因此这里把抢占与投递放在同一事务里、事务只包住"读取+标记"，
     * 网络发送在事务内完成但锁不会跨轮次持有——见 {@link #dispatchOne}。</p>
     */
    @Scheduled(fixedDelayString = "${power.alert.dispatch-interval-ms:5000}")
    public void dispatchPending() {
        // 通过 self 代理调用，确保 @Transactional 生效（同类自调用不走 Spring 代理）
        List<LocalMessage> pending = self.grabPending();
        if (pending.isEmpty()) {
            return;
        }
        log.info("本地消息补偿：本轮抢占 {} 条待投递消息", pending.size());
        for (LocalMessage msg : pending) {
            try {
                dispatchOne(msg);
            } catch (Exception e) {
                // 单条失败不能影响同批其它消息
                log.error("投递消息异常 messageId={}", msg.getMessageId(), e);
            }
        }
    }

    /**
     * 抢占一批消息（短事务，只做读取）
     *
     * <p>用单独的 {@code @Transactional} 方法包住，确保行锁在读完立刻释放，
     * 后续的网络投递不再持有任何数据库锁。</p>
     *
     * <p><b>为什么要 public + 自注入</b>：Spring 的 {@code @Transactional} 依赖代理，
     * 同一个类内部直接调用 {@code this.grabPending()} 不经过代理，事务注解会被静默忽略——
     * 结果就是 {@code FOR UPDATE} 在一个自动提交的连接上执行，加锁形同虚设。
     * 这里通过 {@code @Lazy} 注入自身的代理对象规避。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public List<LocalMessage> grabPending() {
        return localMessageMapper.selectPending(LocalDateTime.now(), BATCH_SIZE);
    }

    /**
     * 投递单条消息
     *
     * <p>投递失败不抛异常，而是落库记录失败原因与下次重试时间——补偿任务的原则是
     * "失败也是状态的一部分"，不能让异常把整轮扫描打断。</p>
     */
    private void dispatchOne(LocalMessage msg) {
        try {
            Message amqpMessage = MessageBuilder
                    .withBody(msg.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType("application/json")
                    .setContentEncoding("UTF-8")
                    .setMessageId(msg.getMessageId())
                    // 持久化：broker 重启后消息不丢
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();

            rabbitTemplate.send(msg.getExchangeName(), msg.getRoutingKey(), amqpMessage);

            // 成功：标记 SENT。这里依赖 publisher-confirm 已开启，
            // 只有 broker 确认接收后 send 才不抛异常（见 application.yml）
            localMessageMapper.markSent(msg.getMessageId(), LocalDateTime.now());
            log.debug("消息投递成功 messageId={} routingKey={}", msg.getMessageId(), msg.getRoutingKey());

        } catch (Exception e) {
            int retryCount = (msg.getRetryCount() == null ? 0 : msg.getRetryCount()) + 1;
            String error = truncate(e.toString(), 480);

            if (retryCount >= MAX_RETRY) {
                // 重试耗尽：转 FAILED，不再自动重试
                localMessageMapper.markDead(msg.getMessageId(), error);
                log.error("消息投递重试耗尽，转 FAILED 待人工处理 messageId={} retry={}",
                        msg.getMessageId(), retryCount, e);
                return;
            }

            LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(backoffSeconds(retryCount));
            localMessageMapper.markFailed(msg.getMessageId(), retryCount, error, nextRetry);
            log.warn("消息投递失败，{}s 后重试 messageId={} retry={}",
                    backoffSeconds(retryCount), msg.getMessageId(), retryCount);
        }
    }

    /**
     * 指数退避：1s、2s、4s、8s、16s…… 封顶 60s
     * <p>为什么不用固定间隔：broker 挂掉时固定间隔会让每轮都立刻重试同一批消息，
     * 重试风暴反而拖慢 broker 恢复。指数退避给系统留出恢复窗口。</p>
     */
    private long backoffSeconds(int retryCount) {
        long seconds = BASE_BACKOFF_SECONDS * (1L << Math.min(retryCount - 1, 6));
        return Math.min(seconds, MAX_BACKOFF_SECONDS);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
