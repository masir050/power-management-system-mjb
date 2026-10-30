package com.power.common.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 身份拦截器注册
 *
 * <p>放在 common 模块里，各业务服务只要依赖了 power-common 就自动生效，
 * 不需要每个服务各写一份 WebMvcConfigurer。</p>
 */
@Configuration
public class SecurityWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CurrentUserInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/**", "/error");
    }
}
