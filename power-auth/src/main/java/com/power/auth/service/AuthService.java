package com.power.auth.service;

import com.power.auth.pojo.LoginVO;

/**
 * 认证服务接口
 * <p>提供用户登录认证功能，校验用户名密码后返回 JWT Token。</p>
 */
public interface AuthService {
    /** 用户登录认证 */
    LoginVO login(String username, String password);
}
