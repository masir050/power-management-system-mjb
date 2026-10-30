package com.power.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * 网关限流 Key 解析器
 *
 * <p>Spring Cloud Gateway 的 {@code RequestRateLimiter} 基于 Redis + Lua 实现令牌桶，
 * 需要一个 {@link KeyResolver} 决定"按什么维度限流"：</p>
 * <ul>
 *   <li>{@link #userKeyResolver()}：按登录用户限流（优先），适合已登录的业务接口；</li>
 *   <li>{@link #ipKeyResolver()}：按客户端 IP 限流，用于登录这类尚未有用户身份的接口。</li>
 * </ul>
 *
 * <p>注意：<b>不能</b>用 {@code request.getRemoteAddress()} 直接取 IP——经过 Nginx 等反向代理后
 * 拿到的是代理地址，所有请求会被算作同一个 key，一限流就全局限死。
 * 这里优先读 {@code X-Forwarded-For} 的第一段。</p>
 */
@Configuration
public class RateLimiterConfig {

    /**
     * 按用户维度限流：网关鉴权过滤器已把 userId 写入请求头，这里直接复用。
     * 未登录（无该头）时退化为按 IP，避免全部落到同一个 "anonymous" 桶里。
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            return Mono.just("ip:" + clientIp(exchange));
        };
    }

    /** 按客户端 IP 限流（登录等未鉴权接口使用） */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + clientIp(exchange));
    }

    /**
     * 提取真实客户端 IP
     * <p>X-Forwarded-For 可能包含多级代理，格式为 "客户端IP, 代理1, 代理2"，取第一段。</p>
     */
    private String clientIp(org.springframework.web.server.ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        var remote = exchange.getRequest().getRemoteAddress();
        return remote == null ? "unknown" : remote.getAddress().getHostAddress();
    }
}
