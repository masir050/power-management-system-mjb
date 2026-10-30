package com.power.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 把网关透传的身份头装进 {@link CurrentUserContext}，并顺带做一层资源级访问控制
 *
 * <p>注册一次（见 {@link SecurityWebConfig}），所有 Controller 都能拿到当前用户。</p>
 *
 * <p>没有身份头的情况要区分开：</p>
 * <ul>
 *   <li>请求根本没过网关（例如直连 8082 端口调试）——此时没有 {@code X-User-Id}，
 *       按匿名处理，下游的权限判断会自然拒绝受限数据；</li>
 *   <li>过了网关但 Token 里没有角色——视为普通运维，不给管理权限。</li>
 * </ul>
 *
 * <p><b>为什么访问控制也写在这里</b>：身份装配和消费必须在同一个拦截器里完成。
 * 拆成两个注册在同一个 {@code HandlerInterceptorRegistry} 上时，第二个拦截器
 * 可能拿到 {@code preHandle} 但此时身份已由第一个装好——更关键的是，
 * 一旦任何一个拦截器被 Spring 复用到别的 DispatcherServlet 配置里，
 * 就会出现"身份有了、闸门没关上"的静默漏洞（实测踩过一次：
 * 独立注册的 DeviceAccessInterceptor 从未触发，客户仍能读设备清单）。
 * 装身份的人自己把关，就没有这个缝。</p>
 */
public class CurrentUserInterceptor implements HandlerInterceptor {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_ROLE = "X-User-Role";
    public static final String HEADER_CUSTOMER_ID = "X-Customer-Id";
    public static final String HEADER_STAFF_ID = "X-Staff-Id";

    /** 客户账号一律不可读的资源前缀（内部资产与运营数据） */
    private static final List<String> INTERNAL_PREFIXES = List.of(
            "/api/power-area/",
            "/api/substation/",
            "/api/equipment/",
            "/api/meter/",
            "/api/operations/",
            "/api/alert/",
            "/api/maintenance/",
            "/api/staff/"
    );

    /** 只有管理员能写的前缀（运维可读可处置，但不能改台账与人员档案） */
    private static final List<String> ADMIN_WRITE_PREFIXES = List.of(
            "/api/power-area/",
            "/api/substation/",
            "/api/equipment/",
            "/api/meter/",
            "/api/staff/"
    );

    /** 客户唯一被允许的账单写操作：给自己的账单缴费 */
    private static final java.util.regex.Pattern BILL_PAY_PATTERN =
            java.util.regex.Pattern.compile("^/api/bill/\\d+/pay/?$");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId != null && !userId.isBlank()) {
            String role = request.getHeader(HEADER_ROLE);
            CurrentUserContext.set(new CurrentUserContext.CurrentUser(
                    parseLong(userId),
                    request.getHeader(HEADER_USERNAME),
                    role == null || role.isBlank() ? "OPERATOR" : role,
                    parseLong(request.getHeader(HEADER_CUSTOMER_ID)),
                    parseLong(request.getHeader(HEADER_STAFF_ID))
            ));
        }

        checkAccess(request);
        return true;
    }

    /**
     * 资源级访问控制
     *
     * <p>三层规则，从上到下依次收紧：</p>
     * <ol>
     *   <li>客户：内部资源一个都不给读（设备、变电站、电表、告警、工单、人员）</li>
     *   <li>非管理员：不能改设备台账与人员档案（运维要能现场关工单，所以不拦写操作）</li>
     *   <li>匿名：什么都不拦，交给业务层按"无身份"处理</li>
     * </ol>
     */
    private void checkAccess(HttpServletRequest request) {
        CurrentUserContext.CurrentUser user = CurrentUserContext.get();
        if (user == null) {
            return;
        }
        String path = request.getRequestURI();

        if (user.isCustomer()) {
            for (String prefix : INTERNAL_PREFIXES) {
                if (path.startsWith(prefix)) {
                    throw new DataScope.ForbiddenException("该数据属于内部运维资产，不对用电客户开放");
                }
            }
        }

        boolean isWrite = !("GET".equals(request.getMethod())
                || "HEAD".equals(request.getMethod())
                || "OPTIONS".equals(request.getMethod()));
        if (isWrite && !user.isAdmin()) {
            for (String prefix : ADMIN_WRITE_PREFIXES) {
                if (path.startsWith(prefix)) {
                    throw new DataScope.ForbiddenException("台账与档案的维护仅限总负责人");
                }
            }
        }

        // 账单：客户与运维都不能生成/修改/删除账单，客户唯一的写操作是「给自己的账单缴费」。
        // 这条不能只靠前端藏按钮——直接 POST /api/bill 或 DELETE /api/bill/6 一样能改库。
        if (isWrite && isBillPath(path) && !user.isAdmin()
                && !("POST".equals(request.getMethod()) && BILL_PAY_PATTERN.matcher(path).matches())) {
            throw new DataScope.ForbiddenException("账单的生成与修改仅限总负责人，客户只能缴费");
        }
    }

    /** /api/bill 本身与它的所有子路径 */
    private static boolean isBillPath(String path) {
        return "/api/bill".equals(path) || path.startsWith("/api/bill/");
    }

    /** 必须清理 ThreadLocal：Tomcat 线程是复用的，不清理会把身份泄漏给下一个请求 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CurrentUserContext.clear();
    }

    private Long parseLong(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
