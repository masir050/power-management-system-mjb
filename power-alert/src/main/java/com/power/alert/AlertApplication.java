package com.power.alert;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 告警中心服务启动类
 *
 * <p>负责检修记录、运维人员、告警事件的全链路处理：</p>
 * <pre>
 *  告警写入（事务内落库 + 登记本地消息）
 *    → 定时任务补偿投递 RabbitMQ
 *      → 消费者幂等去重
 *        → WebSocket 推送到前端
 * </pre>
 *
 * <p>拆分依据：告警是"事件驱动"的域，写入频率高、读放大明显（大屏轮询），
 * 且天然需要异步化——把它和主数据（设备/账单）放在一起，一次告警风暴就会把
 * 整个系统的数据库连接占满。单独拆出后可以独立扩容、独立限流。</p>
 *
 * <p><b>{@code @EnableScheduling} 必不可少</b>：本地消息表的补偿任务
 * （{@code LocalMessageDispatcher}）依赖定时调度，漏了这个注解补偿任务不会执行，
 * 表现就是"告警落了库但永远推不出去"——而且不报任何错，非常难查。</p>
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.power.alert.feign")
@SpringBootApplication
@ComponentScan(basePackages = {"com.power.alert", "com.power.common"})
@MapperScan("com.power.alert.mapper")
@EnableScheduling
public class AlertApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertApplication.class, args);
    }
}
