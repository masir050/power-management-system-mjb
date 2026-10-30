package com.power.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置
 *
 * <p>为什么手写而不依赖 starter 自动装配：</p>
 * <ul>
 *   <li>自动装配读取的是 {@code spring.data.redis.*}，与项目里其它地方用的
 *       {@code REDIS_HOST/REDIS_PORT} 环境变量容易脱节；</li>
 *   <li><b>看门狗超时</b>（{@code lockWatchdogTimeout}）必须显式设置并让团队知道含义：
 *       它决定"业务卡住时锁最多被续期多久"。默认 30s，这里也取 30s，
 *       即业务执行超过 30s 且续期失败，锁才会自动释放。</li>
 * </ul>
 *
 * <p><b>条件装配很重要</b>：无状态服务（auth、billing）不需要分布式锁，
 * 也不该因为 Redis 没起就启动失败。因此只有显式配置了
 * {@code power.redis.enabled=true} 的服务才会创建该 Bean。</p>
 */
@Configuration
@ConditionalOnProperty(name = "power.redis.enabled", havingValue = "true")
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${power.redis.address:redis://localhost:6379}") String address,
            @Value("${power.redis.database:0}") int database,
            @Value("${power.redis.password:}") String password,
            @Value("${power.redis.watchdog-timeout-ms:30000}") long watchdogTimeoutMs) {

        Config config = new Config();
        config.setLockWatchdogTimeout(watchdogTimeoutMs);
        config.useSingleServer()
                .setAddress(address)
                .setDatabase(database)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(16)
                .setTimeout(3000);
        if (password != null && !password.isBlank()) {
            config.useSingleServer().setPassword(password);
        }
        return Redisson.create(config);
    }
}
