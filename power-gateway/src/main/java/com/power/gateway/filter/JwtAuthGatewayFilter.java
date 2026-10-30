package com.power.gateway.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关统一 JWT 鉴权过滤器
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li><b>鉴权前置到网关</b>：所有业务服务不再各自实现一遍 Token 校验，
 *       下游服务只需信任网关透传的 {@code X-User-Id} / {@code X-Username} 请求头。</li>
 *   <li><b>白名单</b>：登录接口、Swagger 文档、健康检查放行；其余全部要求 Token。</li>
 *   <li><b>防伪造</b>：<b>先剥离</b>客户端可能自带的 {@code X-User-Id} 等请求头再重新写入，
 *       否则客户端自己填一个 X-User-Id 就能冒充任意用户，绕过鉴权。</li>
 *   <li><b>WebFlux 非阻塞</b>：不能用阻塞式写法，401 响应直接写 DataBuffer。</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtAuthGatewayFilter implements GlobalFilter, Ordered {

    /** 从 Token 解析出的用户信息，透传给下游服务的请求头名 */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    /** 客户 ID：后端据此把查询限制到"这一户"，前端无法伪造（客户端同名头会被剥离） */
    public static final String HEADER_CUSTOMER_ID = "X-Customer-Id";
    /** 运维人员 ID：后端据此识别"这个运维是谁"，用于"我的工单" */
    public static final String HEADER_STAFF_ID = "X-Staff-Id";

    /**
     * 免鉴权路径（Ant 风格前缀匹配）
     *
     * <p>{@code /ws/} 例外说明：浏览器的 WebSocket API 不能自定义请求头，
     * 带不上 {@code Authorization}，因此握手请求在这里必然过不去。
     * 该路径的鉴权改由 {@code AlertWebSocketEndpoint#verifyToken} 用
     * {@code ?token=} 查询参数完成（校验失败直接拒绝握手），
     * 所以这里只放行握手本身，不把通道变成公开。</p>
     */
    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/login",
            "/api/auth/captcha",
            "/ws/",
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs",
            "/swagger-ui",
            "/webjars"
    );

    private final String secret;

    public JwtAuthGatewayFilter(@Value("${power.jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("缺少配置 power.jwt.secret");
        }
        this.secret = secret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 白名单直接放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 2. 提取 Bearer Token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "未携带 Token，请先登录");
        }
        String token = authHeader.substring(7).trim();

        // 3. 校验签名与有效期，解析出用户身份
        DecodedJWT jwt;
        try {
            jwt = JWT.require(Algorithm.HMAC256(secret)).build().verify(token);
        } catch (JWTVerificationException e) {
            log.warn("网关鉴权失败 path={} reason={}", path, e.getMessage());
            return unauthorized(exchange, "Token 无效或已过期，请重新登录");
        }

        Long userId = jwt.getClaim("userId").asLong();
        String username = jwt.getClaim("username").asString();
        String role = jwt.getClaim("role").asString();
        Long customerId = jwt.getClaim("customerId").asLong();
        Long staffId = jwt.getClaim("staffId").asLong();

        // 4. 剥离客户端可能伪造的身份头，再由网关写入可信值
        //    注意 customerId / staffId 虽然是"数据范围"而不是"权限"，同样必须剥离——
        //    否则客户端自己填一个 X-Customer-Id 就能看别人的账单。
        ServerHttpRequest mutated = request.mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USERNAME);
                    headers.remove(HEADER_USER_ROLE);
                    headers.remove(HEADER_CUSTOMER_ID);
                    headers.remove(HEADER_STAFF_ID);
                    if (userId != null) {
                        headers.set(HEADER_USER_ID, String.valueOf(userId));
                    }
                    if (username != null) {
                        headers.set(HEADER_USERNAME, username);
                    }
                    if (role != null) {
                        headers.set(HEADER_USER_ROLE, role);
                    }
                    if (customerId != null) {
                        headers.set(HEADER_CUSTOMER_ID, String.valueOf(customerId));
                    }
                    if (staffId != null) {
                        headers.set(HEADER_STAFF_ID, String.valueOf(staffId));
                    }
                })
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /** 白名单前缀匹配 */
    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    /** 直接写出 401 JSON 响应（保持与业务服务统一的 Result 结构，前端只需处理一种格式） */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 过滤器顺序：必须早于路由过滤器执行。
     * -100 高于 Spring Cloud Gateway 内置的 RoutingFilter 等组件。
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
