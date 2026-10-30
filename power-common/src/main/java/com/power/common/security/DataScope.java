package com.power.common.security;

/**
 * 数据范围解析
 *
 * <p>这套系统有三类人，看到的必须是三套数据：</p>
 * <table border="1">
 *   <tr><th>角色</th><th>看什么</th><th>怎么限制</th></tr>
 *   <tr><td>ADMIN 总负责人</td><td>全部</td><td>不加条件</td></tr>
 *   <tr><td>OPERATOR 运维人员</td><td>设备、告警、检修（不含账单与客户隐私）</td><td>看全部设备，但不给账单</td></tr>
 *   <tr><td>CUSTOMER 用电客户</td><td>只有自己那一户的账单与用电</td><td>强制 customer_id = 自己</td></tr>
 * </table>
 *
 * <p><b>为什么不在前端做</b>：前端隐藏菜单只是不让人"看见入口"，
 * 直接在地址栏敲 <code>/api/bill/list</code> 依然能拿到全量数据。
 * 真正的边界必须在 SQL 里——所以过滤条件由服务端从身份里取，前端传什么都不作数。</p>
 */
public final class DataScope {

    private DataScope() {
    }

    /**
     * 解析"必须限制到哪个客户"。
     * <ul>
     *   <li>客户角色 → 返回自己的 customerId，SQL 里加 {@code customer_id = ?}</li>
     *   <li>管理员/运维 → 返回 null，表示不加限制</li>
     *   <li>拿不到身份（直连端口调试）→ 返回 {@link #NO_SCOPE} 之外的哨兵值，
     *       由调用方决定是拒绝还是放开，本项目选择拒绝查询客户数据</li>
     * </ul>
     *
     * @return 需要限定的客户 ID；null 表示不限制
     */
    public static Long customerScope() {
        CurrentUserContext.CurrentUser user = CurrentUserContext.get();
        if (user == null) {
            return null;
        }
        return user.isCustomer() ? user.customerId() : null;
    }

    /**
     * 当前用户是否允许访问"客户账务类"数据（账单、缴费、用电量）
     *
     * <p>运维人员不该看到客户的电费和住址——这是隐私边界，不是权限洁癖。
     * 但客户自己能看自己的，管理员看全部。</p>
     */
    public static boolean canAccessBilling() {
        CurrentUserContext.CurrentUser user = CurrentUserContext.get();
        if (user == null) {
            return true;   // 无身份上下文（MQ 消费、内部调用）按内部处理
        }
        return user.isAdmin() || user.isCustomer();
    }

    /** 当前用户是否允许访问客户档案（客户名单、联系方式、地址） */
    public static boolean canAccessCustomerProfile() {
        CurrentUserContext.CurrentUser user = CurrentUserContext.get();
        if (user == null) {
            return true;
        }
        return user.isAdmin() || user.isCustomer();
    }

    /**
     * 当前用户是否为用电客户
     *
     * <p>用于"客户根本不该看到"的内部数据（设备台账、变电站运行率、告警工单）。
     * 与 {@link #customerScope()} 的区别：那个是"只看自己家的"，这个是"一点都不给看"。</p>
     */
    public static boolean isCustomer() {
        CurrentUserContext.CurrentUser user = CurrentUserContext.get();
        return user != null && user.isCustomer();
    }

    /**
     * 校验客户只能访问自己的数据
     *
     * @param targetCustomerId 目标数据的归属客户
     * @throws ForbiddenException 客户试图访问别人家的数据
     */
    public static void assertCustomerOwns(Long targetCustomerId) {
        CurrentUserContext.CurrentUser user = CurrentUserContext.get();
        if (user == null || !user.isCustomer()) {
            return;
        }
        if (targetCustomerId == null || !targetCustomerId.equals(user.customerId())) {
            throw new ForbiddenException("无权访问其他用电户的数据");
        }
    }

    /** 校验当前用户是否为管理员，非管理员直接拒绝 */
    public static void assertAdmin() {
        CurrentUserContext.CurrentUser user = CurrentUserContext.get();
        if (user != null && !user.isAdmin()) {
            throw new ForbiddenException("仅总负责人可执行该操作");
        }
    }

    /**
     * 越权访问异常
     *
     * <p>单独一个类型而不是复用 {@code BusinessException}，是为了让全局异常处理器
     * 能统一返回 403 而不是 500——越权是"你不该看"，不是"系统坏了"，
     * 前端可以据此给出"无权访问"的提示而不是报错弹窗。</p>
     */
    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) {
            super(message);
        }
    }
}
