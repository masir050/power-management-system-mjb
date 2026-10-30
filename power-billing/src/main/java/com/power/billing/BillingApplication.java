package com.power.billing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 电费核算服务启动类
 *
 * <p>负责用电客户、电费账单、电价标准。账单依赖电表读数，而电表归属 device 服务，
 * 因此需要时通过 Feign 调用 device 获取，不在本服务库内冗余电表主数据。</p>
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.power.billing.feign")
@SpringBootApplication
@ComponentScan(basePackages = {"com.power.billing", "com.power.common"})
@MapperScan("com.power.billing.mapper")
public class BillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }
}
