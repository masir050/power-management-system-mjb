package com.power.auth.controller;

import com.power.auth.pojo.LoginDTO;
import com.power.auth.pojo.LoginVO;
import com.power.auth.pojo.SysUser;
import com.power.auth.service.AuthService;
import com.power.auth.service.SysUserService;
import com.power.common.pojo.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证控制器
 *
 * <p>提供登录、登出、当前用户信息接口。登录成功后返回 JWT Token，
 * 后续请求需在 Authorization 头中携带 {@code Bearer {token}}。
 * 网关校验 Token 后会把用户身份写入 {@code X-User-Id} / {@code X-Username} 请求头，
 * 本服务直接读取这些头即可，无需再次解析 Token。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SysUserService sysUserService;

    public AuthController(AuthService authService, SysUserService sysUserService) {
        this.authService = authService;
        this.sysUserService = sysUserService;
    }

    /**
     * 用户登录
     * <p>{@code @Valid} 触发 LoginDTO 上的注解校验，校验失败由全局异常处理器
     * 统一返回 400 + 具体字段原因。</p>
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto.getUsername(), dto.getPassword()));
    }

    /**
     * 获取当前登录用户信息
     * <p>前端刷新页面后可用它校验本地 Token 是否仍然有效，并拿到昵称/角色。</p>
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestHeader("X-User-Id") Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return Result.notFound();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        // 角色与绑定关系一并返回：前端刷新页面后靠它恢复菜单与视图，
        // 不必再解一遍 JWT，也不用为了拿 customerId 去查账单表
        data.put("role", user.getRole());
        data.put("customerId", user.getCustomerId());
        data.put("staffId", user.getStaffId());
        // 不返回 password 字段
        return Result.success(data);
    }

    /**
     * 用户登出
     * <p>JWT 是无状态的，服务端不保存会话，真正的"失效"由前端丢弃本地 Token 完成。
     * 这里返回成功仅用于前端统一调用与埋点；如需服务端强制下线，
     * 可在此把 Token 的 jti 写入 Redis 黑名单，由网关校验。</p>
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }
}
