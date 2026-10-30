package com.power.alert.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 端点注册
 *
 * <p>{@link ServerEndpointExporter} 的作用是扫描并注册所有 {@code @ServerEndpoint}
 * 标注的类。没有它，端点类只是个普通 Bean，WebSocket 容器根本不知道它的存在，
 * 前端连接会直接 404。</p>
 *
 * <p>注意：只有<b>内嵌容器</b>（Spring Boot 默认的 Tomcat/Jetty）才需要这个 Bean；
 * 若打成 war 部署到外部容器，容器会自己扫描，此时要移除本配置，否则会重复注册报错。</p>
 */
@Configuration
public class WebSocketConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
