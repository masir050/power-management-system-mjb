package com.power.alert.mq;

import com.power.common.mq.AlertMqConstants;
import com.power.common.util.JsonSupport;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明
 *
 * <h3>为什么由消费者声明拓扑</h3>
 * <p>交换机与队列是幂等的"声明式"资源：谁先启动谁创建，后续启动的只是校验。
 * 由消费方（本服务）声明更稳妥——如果由生产方声明，生产方还没启动时消息会因为没有队列
 * 而被 broker 丢弃（topic 交换机在没有匹配绑定时会静默丢消息）。</p>
 *
 * <h3>死信队列的作用</h3>
 * <p>队列上绑定 {@code x-dead-letter-exchange} 后，以下三种消息会进入死信队列而不是消失：</p>
 * <ol>
 *   <li>被消费者 nack 且 {@code requeue=false}（我们用它来拒收"毒消息"）；</li>
 *   <li>消息 TTL 过期；</li>
 *   <li>队列达到最大长度被挤出的。</li>
 * </ol>
 * <p>没有死信队列时，消费端拒收的消息会直接消失，线上排查时"消息去哪了"根本无法回答。</p>
 */
@Configuration
public class AlertMqConfig {

    /** 主交换机：topic 类型，便于后续按严重级别（alert.urgent.*）扩展路由 */
    @Bean
    public TopicExchange alertExchange() {
        return new TopicExchange(AlertMqConstants.ALERT_EXCHANGE, true, false);
    }

    /** 死信交换机：用 direct，死信不需要模糊路由 */
    @Bean
    public DirectExchange alertDlxExchange() {
        return new DirectExchange(AlertMqConstants.ALERT_DLX_EXCHANGE, true, false);
    }

    /** 告警队列：持久化 + 绑定死信交换机 */
    @Bean
    public Queue alertQueue() {
        return QueueBuilder.durable(AlertMqConstants.ALERT_QUEUE)
                .withArgument("x-dead-letter-exchange", AlertMqConstants.ALERT_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", AlertMqConstants.RK_DEAD)
                // 队列上限：避免消费者长时间不可用把 broker 磁盘写满
                .withArgument("x-max-length", 100000)
                .build();
    }

    /** 死信队列 */
    @Bean
    public Queue alertDlq() {
        return QueueBuilder.durable(AlertMqConstants.ALERT_DLQ).build();
    }

    /** 新建告警事件绑定 */
    @Bean
    public Binding alertCreatedBinding(Queue alertQueue, TopicExchange alertExchange) {
        return BindingBuilder.bind(alertQueue).to(alertExchange).with(AlertMqConstants.RK_ALERT_CREATED);
    }

    /** 告警状态变更事件绑定 */
    @Bean
    public Binding alertResolvedBinding(Queue alertQueue, TopicExchange alertExchange) {
        return BindingBuilder.bind(alertQueue).to(alertExchange).with(AlertMqConstants.RK_ALERT_RESOLVED);
    }

    /** 死信队列绑定 */
    @Bean
    public Binding alertDlqBinding(Queue alertDlq, DirectExchange alertDlxExchange) {
        return BindingBuilder.bind(alertDlq).to(alertDlxExchange).with(AlertMqConstants.RK_DEAD);
    }

    /**
     * JSON 消息转换器
     *
     * <p>默认的 SimpleMessageConverter 用 Java 原生序列化，有两个问题：
     * 一是消息体不可读（排查时看不到内容），二是跨语言/跨版本不兼容。
     * 换成 JSON 后，{@link com.power.common.mq.AlertEvent} 收发都以可读 JSON 传输。</p>
     *
     * <p><b>必须复用 JsonSupport 的 ObjectMapper</b>：{@code AlertEvent.occurredAt} 是
     * {@code LocalDateTime}，Jackson 默认不支持 Java 8 时间类型，不加 JavaTimeModule 会在
     * 反序列化时直接抛 {@code InvalidDefinitionException}。复用同一个 Mapper 也保证了
     * MQ 与缓存两条路径的日期格式完全一致。</p>
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter(JsonSupport.mapper());
    }

    /**
     * RabbitTemplate：开启 mandatory + 发布确认
     *
     * <p>{@code setMandatory(true)} 让"路由不到队列"的消息触发 returnCallback 而不是静默丢弃。
     * 发布确认（publisher-confirm）在 application.yml 里开启，
     * {@link LocalMessageDispatcher} 依赖它——只有 broker 确认接收后投递才算成功。</p>
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setMandatory(true);
        return template;
    }
}
