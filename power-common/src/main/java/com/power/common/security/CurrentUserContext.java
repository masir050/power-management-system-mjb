package com.power.common.security;

/**
 * 当前请求的身份载体
 *
 * <p>由网关从 JWT 解出身份后，通过 {@code X-User-Id} / {@code X-User-Role} /
 * {@code X-Customer-Id} / {@code X-Staff-Id} 请求头透传给下游。
 * 业务服务用 {@link CurrentUserInterceptor} 把它塞进 {@link CurrentUserContext}，
 * 业务代码只调 {@code CurrentUserContext.get()}，不碰 HttpServletRequest。</p>
 *
 * <p><b>为什么不信 Token 而信请求头</b>：网关已经做过一次签名校验，
 * 且会先剥离客户端自带的所有身份头再写入（见 {@code JwtAuthGatewayFilter} 第 4 步），
 * 下游再验一遍签名纯属重复劳动。前提是业务服务<b>不对外暴露端口</b>，只能经网关访问。</p>
 */
public final class CurrentUserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 请求结束时必须清理，否则线程池复用线程会把上一个人的身份带给下一个人 */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 当前用户
     *
     * @param userId     系统用户 ID
     * @param username   用户名
     * @param role       ADMIN / OPERATOR / CUSTOMER
     * @param customerId 客户 ID（仅 CUSTOMER 有值）
     * @param staffId    运维人员 ID（仅 OPERATOR 有值）
     */
    public record CurrentUser(Long userId, String username, String role,
                              Long customerId, Long staffId) {

        public boolean isAdmin() {
            return "ADMIN".equalsIgnoreCase(role);
        }

        public boolean isCustomer() {
            return "CUSTOMER".equalsIgnoreCase(role);
        }

        public boolean isOperator() {
            return "OPERATOR".equalsIgnoreCase(role);
        }

        /** 内置系统身份：定时任务、MQ 消费者等无 HTTP 上下文的场景使用 */
        public static CurrentUser system() {
            return new CurrentUser(0L, "system", "ADMIN", null, null);
        }
    }
}
