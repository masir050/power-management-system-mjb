package com.power.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 认证服务启动类
 *
 * <p>组件扫描范围包含 {@code com.power.common}，这样公共模块里的
 * JwtUtil、全局异常处理器、密码编码器等 Bean 才能被装配。</p>
 */
@EnableDiscoveryClient
@SpringBootApplication
@ComponentScan(basePackages = {"com.power.auth", "com.power.common"})
@MapperScan("com.power.auth.mapper")
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
