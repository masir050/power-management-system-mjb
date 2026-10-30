package com.power.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关启动类
 *
 * <p>网关基于 WebFlux，自身不做 Spring Security 的会话鉴权——鉴权完全由
 * {@code JwtAuthGatewayFilter} 在路由过滤器阶段完成。这里排除
 * {@link ReactiveSecurityAutoConfiguration} 是必须的：否则 Spring Boot 会启用
 * 默认的 reactive 安全链，把所有请求拦成 401，与自定义过滤器冲突且难以排查。</p>
 */
@EnableDiscoveryClient
@SpringBootApplication(exclude = ReactiveSecurityAutoConfiguration.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
