package com.power.auth.pojo;

import lombok.Data;

/**
 * 登录成功响应 VO
 * <p>登录成功后返回给前端的用户信息和 Token。</p>
 */
@Data
public class LoginVO {
    /** JWT Token，后续请求需在 Header 中携带 */
    private String token;
    /** 用户名 */
    private String username;
    /** 用户昵称（显示名称） */
    private String nickname;
    /** 角色标识：ADMIN / OPERATOR / CUSTOMER，前端据此渲染菜单、路由与按钮 */
    private String role;
    /** 用电客户ID（role=CUSTOMER 时非空），前端用于"我的账单"跳转 */
    private Long customerId;
    /** 运维人员ID（role=OPERATOR 时非空），前端用于展示"我的工单" */
    private Long staffId;
    /** Token 有效期（秒），前端可据此提前续期或提示重新登录 */
    private Long expiresIn;
}
