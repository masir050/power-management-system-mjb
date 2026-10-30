package com.power.common.security;

import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 身份透传配置
 *
 * <p><b>为什么必须有这个</b>：服务间调用走 Feign 时，默认只带网络层信息，
 * 网关注入的 {@code X-User-Role / X-Customer-Id} 这些头不会自动跟过去。
 * 结果就是——用户在网关上被认成"客户 1"，到了下游服务变成"查无此人"，
 * 数据范围过滤器看到 scope 为空，直接回退成"不限"，于是一个客户
 * 拿到了全站六块电表的用电量。</p>
 *
 * <p>这类问题在单库单体里根本不存在，是拆服务之后才冒出来的，
 * 而且它不会报错、只会静默放大数据范围，比 500 更危险。</p>
 *
 * <p>用 {@link CurrentUserContext} 而不是从 RequestContextHolder 取：
 * 前者是我们在拦截器里统一装填的单一口径，业务线程里一定有值；
 * 异步/定时任务里没有登录上下文时自然什么都不加，下游按"无身份"处理。</p>
 */
@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignIdentityConfig {

    @Bean
    public RequestInterceptor identityForwardingInterceptor() {
        return template -> {
            CurrentUserContext.CurrentUser user = CurrentUserContext.get();
            if (user == null) {
                return;
            }
            put(template, CurrentUserInterceptor.HEADER_USER_ID, user.userId());
            put(template, CurrentUserInterceptor.HEADER_USERNAME, user.username());
            put(template, CurrentUserInterceptor.HEADER_ROLE, user.role());
            put(template, CurrentUserInterceptor.HEADER_CUSTOMER_ID, user.customerId());
            put(template, CurrentUserInterceptor.HEADER_STAFF_ID, user.staffId());
        };
    }

    private void put(feign.RequestTemplate template, String name, Object value) {
        if (value == null) {
            // 空值也要显式清掉：否则上一次调用残留的值或调用方手填的值会被带过去
            template.removeHeader(name);
            return;
        }
        template.header(name, String.valueOf(value));
    }
}
