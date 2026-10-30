package com.power.device;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 设备管理服务启动类
 *
 * <p>负责供电区域、变电站、电力设备、智能电表四类资产的主数据维护。</p>
 *
 * <p>拆分依据：这四类实体同属"电网资产"域，变更频率一致、被同一个前端页面消费，
 * 且它们之间有外键关联（区域 → 变电站 → 设备），放在同一个库内可以用 JOIN 一次查全，
 * 拆得更细反而要跨服务组装数据。</p>
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.power.device.feign")
@SpringBootApplication
@ComponentScan(basePackages = {"com.power.device", "com.power.common"})
@MapperScan("com.power.device.mapper")
public class DeviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceApplication.class, args);
    }
}
