package com.power.alert.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.power.common.util.JsonSupport;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 告警 WebSocket 推送端点
 *
 * <p>前端连接 {@code ws://localhost:8084/ws/alert} 即可实时接收告警，
 * 无需轮询。这是简历里"WebSocket 推送提升告警触达效率"的实际落点。</p>
 *
 * <h3>线程安全设计（三个容易踩的点）</h3>
 * <ol>
 *   <li><b>@ServerEndpoint 的实例是"每个连接一个"</b>，不是单例。
 *       因此 {@code @Autowired} 在端点类里拿不到 Bean（Spring 默认只注入单例），
 *       静态集合才是在所有连接实例间共享状态的地方。</li>
 *   <li><b>sessions 用 CopyOnWriteArraySet</b>：推送线程（MQ 消费线程）遍历时，
 *       连接/断开发生在 WebSocket 容器线程，普通 HashSet 会抛 ConcurrentModificationException。</li>
 *   <li><b>发送要做并发保护</b>：JSR-356 的 {@code Session.getBasicRemote().sendText()}
 *       不是线程安全的，两个线程同时给同一 session 发消息会抛 IllegalStateException。
 *       这里用 {@code synchronized (session)} 串行化，而不是上全局锁——全局锁会让慢客户端
 *       拖住所有推送。</li>
 * </ol>
 *
 * <h3>关于集群</h3>
 * <p>本实现是<b>单实例内存广播</b>：只在当前 alert 实例的连接上推送。多实例部署时，
 * 前端连的是哪个实例就只能收到那个实例的推送。生产环境的做法是引入
 * Redis Pub/Sub 或 STOMP relay，把消息广播给所有实例——见类尾注释。</p>
 */
@Slf4j
@Component
@ServerEndpoint("/ws/alert")
public class AlertWebSocketEndpoint {

    /** 所有活跃连接。键为 sessionId，值为 session（便于日志定位与定向推送） */
    private static final Set<Session> SESSIONS = new CopyOnWriteArraySet<>();

    /** 单实例内的连接数上限，防止异常客户端把内存撑爆 */
    private static final int MAX_SESSIONS = 500;

    /** JWT 密钥：由 Spring 在启动时注入。端点实例不是 Spring Bean，@Value 不会生效 */
    private static volatile String jwtSecret;

    /** 是否强制校验握手 token（单机调试可关） */
    private static volatile boolean authRequired = true;

    @Value("${power.jwt.secret:}")
    public void setJwtSecret(String secret) {
        jwtSecret = secret;
    }

    @Value("${power.ws.auth-required:true}")
    public void setAuthRequired(boolean required) {
        authRequired = required;
    }

    // 注意：这里用 JsonSupport 而不是 Spring 的 ObjectMapper，
    // 因为端点实例由 WebSocket 容器创建，拿不到 Spring 上下文
    private static final ObjectMapper MAPPER = JsonSupport.mapper();

    /**
     * 握手前从查询参数取 token 并按与网关相同的规则校验。
     *
     * <p>为什么浏览器连 WebSocket 一定要走这里：{@code new WebSocket(url)} 不能自定义请求头，
     * 带不上 {@code Authorization: Bearer xxx}，过滤链看到没 Token 就会拒绝握手。
     * 常见两种绕法：把 {@code /ws/**} 加入网关白名单（通道变成完全公开，
     * 任何人都能订阅告警流），或者把 token 放 URL 查询串（会进 access log / Referer）。
     * 这里选后者但只做"够用"的处理：校验失败直接拒绝握手，成功也不回显 token。</p>
     *
     * <p>单机调试时 {@code power.ws.auth-required=false} 可关闭校验，
     * 免去先登录再连通道的麻烦；生产必须保持 true。</p>
     */
    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        if (!verifyToken(session)) {
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "token 无效");
            return;
        }
        if (SESSIONS.size() >= MAX_SESSIONS) {
            try {
                session.close(new CloseReason(
                        CloseReason.CloseCodes.TRY_AGAIN_LATER, "连接数已满"));
            } catch (IOException e) {
                log.warn("拒绝连接时关闭失败 sessionId={}", session.getId(), e);
            }
            return;
        }
        SESSIONS.add(session);
        log.info("WebSocket 连接建立 sessionId={} 当前连接数={}", session.getId(), SESSIONS.size());

        // 建立连接后立刻推一条欢迎消息，前端可据此确认通道已通
        sendTo(session, Map.of("type", "CONNECTED", "message", "告警推送通道已建立"));
    }

    /**
     * 校验握手参数里的 token
     *
     * <p>参数优先取 {@code ?token=xxx}，兼容 {@code ?access_token=xxx}（SockJS 风格）。</p>
     */
    private static boolean verifyToken(Session session) {
        if (!authRequired) {
            return true;
        }
        String secret = jwtSecret;
        if (secret == null || secret.isBlank()) {
            log.error("未配置 power.jwt.secret，拒绝所有 WebSocket 连接");
            return false;
        }
        String token = null;
        Map<String, List<String>> params = session.getRequestParameterMap();
        if (params != null) {
            for (String name : new String[]{"token", "access_token"}) {
                List<String> values = params.get(name);
                if (values != null && !values.isEmpty()) {
                    token = values.get(0);
                    break;
                }
            }
        }
        if (token == null || token.isBlank()) {
            log.warn("WebSocket 握手缺少 token sessionId={}", session.getId());
            return false;
        }
        try {
            JWT.require(Algorithm.HMAC256(secret)).build().verify(token.trim());
            return true;
        } catch (JWTVerificationException e) {
            log.warn("WebSocket 握手 token 校验失败 sessionId={} reason={}", session.getId(), e.getMessage());
            return false;
        }
    }

    private static void closeQuietly(Session session, CloseReason.CloseCode code, String reason) {
        try {
            session.close(new CloseReason(code, reason));
        } catch (IOException e) {
            log.debug("关闭 WebSocket 连接失败 sessionId={}", session.getId(), e);
        }
    }

    @OnClose
    public void onClose(Session session) {

        SESSIONS.remove(session);
        log.info("WebSocket 连接关闭 sessionId={} 剩余连接数={}", session.getId(), SESSIONS.size());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        SESSIONS.remove(session);
        log.warn("WebSocket 异常 sessionId={}", session.getId(), error);
    }

    /**
     * 接收前端消息
     *
     * <p>当前只支持心跳：前端定时发 {@code ping}，服务端回 {@code pong}。
     * 心跳的意义是让中间的反向代理/负载均衡知道连接还活着，避免被空闲超时切断。</p>
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        if ("ping".equalsIgnoreCase(message)) {
            sendTo(session, Map.of("type", "PONG"));
        }
    }

    /**
     * 广播给所有在线连接（供 MQ 消费者调用）
     *
     * @param payload 已序列化的 JSON 字符串
     */
    public static void broadcast(String payload) {
        if (SESSIONS.isEmpty()) {
            log.debug("当前无 WebSocket 连接，跳过广播");
            return;
        }
        int failed = 0;
        for (Session session : SESSIONS) {
            if (!sendRaw(session, payload)) {
                failed++;
                // 发送失败的连接直接清理，避免死连接一直占着集合
                SESSIONS.remove(session);
            }
        }
        log.info("WebSocket 广播完成 目标={} 失败={}", SESSIONS.size() + failed, failed);
    }

    /** 广播一个对象（自动序列化） */
    public static void broadcast(Object data) {
        try {
            broadcast(MAPPER.writeValueAsString(data));
        } catch (Exception e) {
            log.error("WebSocket 消息序列化失败", e);
        }
    }

    /** 查询当前在线连接数（健康检查/监控用） */
    public static int onlineCount() {
        return SESSIONS.size();
    }

    private void sendTo(Session session, Object data) {
        try {
            sendRaw(session, MAPPER.writeValueAsString(data));
        } catch (Exception e) {
            log.warn("WebSocket 发送失败 sessionId={}", session.getId(), e);
        }
    }

    /**
     * 实际发送：按 session 加锁
     *
     * <p>为什么锁 session 而不是用 getAsyncRemote：异步发送需要自己做发送队列与
     * 背压控制，实现复杂且容易丢消息；告警消息体积小（几百字节），
     * 同步发送 + 单连接串行足够，且顺序有保证。</p>
     */
    private static boolean sendRaw(Session session, String payload) {
        if (session == null || !session.isOpen()) {
            return false;
        }
        synchronized (session) {
            try {
                session.getBasicRemote().sendText(payload);
                return true;
            } catch (IOException e) {
                log.warn("WebSocket 发送 IO 异常 sessionId={}", session.getId(), e);
                return false;
            }
        }
    }

    /*
     * ===== 集群部署时的改造点（当前单实例实现，留作说明） =====
     *
     * 多实例时，MQ 消费者在 A 实例、前端连在 B 实例，A 的 broadcast 推到不了 B。
     * 两种做法：
     *   1. Redis Pub/Sub：消费者 publish 到 channel "alert:push"，
     *      每个实例订阅并在本地 broadcast —— 改动最小，适合中小规模；
     *   2. Spring 的 STOMP relay 接 RabbitMQ 的 /topic 交换机 ——
     *      功能更全（点对点、订阅关系持久化），但要引入 SockJS/STOMP 协议栈。
     */
}
