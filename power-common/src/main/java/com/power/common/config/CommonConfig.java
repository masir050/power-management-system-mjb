package com.power.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 通用 Bean 配置
 * <p>注册各服务共用的基础设施 Bean。</p>
 */
@Configuration
public class CommonConfig {

    /**
     * 密码加密器
     *
     * <p>使用 BCrypt：自带随机盐，同一明文每次加密结果不同，因此
     * <b>不能</b>用"加密后字符串相等"来比较密码，必须用 {@code matches()}。
     * 相比 MD5/SHA 这类快速哈希，BCrypt 的 cost=10 使单次校验约 50~100ms，
     * 暴力破解成本大幅提高——这也正是登录接口需要限流的原因。</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
