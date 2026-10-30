package com.power.auth.service.impl;

import com.power.auth.mapper.SysUserMapper;
import com.power.auth.pojo.LoginVO;
import com.power.auth.pojo.SysUser;
import com.power.auth.service.AuthService;
import com.power.common.exception.BusinessException;
import com.power.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现类
 *
 * <p>使用自定义 SQL（{@code SysUserMapper.selectUserByUsername}）查询用户，
 * BCrypt 校验密码，成功后签发 JWT。</p>
 *
 * <p>安全上的两处刻意处理：</p>
 * <ol>
 *   <li>用户不存在与密码错误返回<b>同一条</b>提示，避免账号枚举攻击；</li>
 *   <li>用户不存在时也执行一次 BCrypt 校验，使两条分支耗时接近，避免通过响应时间差判断账号是否存在。</li>
 * </ol>
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /**
     * 一个固定的 BCrypt 哈希（明文 "dummy-password" 的哈希），
     * 仅用于用户不存在时也走一遍密码校验，消除时间侧信道。
     */
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(SysUserMapper sysUserMapper, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户登录认证
     * <p>校验流程：用户名是否存在 -> 密码是否匹配 -> 账户是否禁用。
     * 全部通过后生成 JWT Token 返回给前端。</p>
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 包含 Token 和用户信息的登录结果
     * @throws BusinessException 用户名或密码错误 / 账户被禁用
     */
    @Override
    public LoginVO login(String username, String password) {
        SysUser user = sysUserMapper.selectUserByUsername(username);
        if (user == null) {
            // 仍然做一次 bcrypt 比对，让耗时与"用户存在但密码错"一致
            passwordEncoder.matches(password, DUMMY_HASH);
            log.warn("登录失败：用户不存在 username={}", username);
            throw new BusinessException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("登录失败：密码错误 username={}", username);
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("登录失败：账户已禁用 username={}", username);
            throw new BusinessException("账户已被禁用");
        }

        String role = resolveRole(user);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), role,
                user.getCustomerId(), user.getStaffId());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(role);
        vo.setCustomerId(user.getCustomerId());
        vo.setStaffId(user.getStaffId());
        vo.setExpiresIn(jwtUtil.getExpiration() / 1000);
        log.info("登录成功 username={} role={} customerId={} staffId={}",
                username, role, user.getCustomerId(), user.getStaffId());
        return vo;
    }

    /**
     * 解析用户角色标识
     *
     * <p>角色取自 {@code sys_user.role} 列，不再用"用户名是不是 admin"来猜。
     * 角色同时决定了登录后的<b>数据范围</b>：客户只能看自己的账单与用电，
     * 运维只能看设备与告警，总负责人看全部——这个边界由客户/人员 ID 一起带进 Token，
     * 下游服务不做二次查询就能知道"这个人是哪一户 / 哪个运维"。</p>
     *
     * <p>数据完整性兜底：客户账号没绑 customer_id、运维账号没绑 staff_id，
     * 登录直接拒绝。宁可登不进去，也不能登进去看到一个空得莫名其妙的系统。</p>
     */
    private String resolveRole(SysUser user) {
        String role = user.getRole();
        if (role == null || role.isBlank()) {
            // 老数据没有 role 列的兜底：只认 admin，其余按运维处理
            role = "admin".equalsIgnoreCase(user.getUsername()) ? "ADMIN" : "OPERATOR";
        }
        role = role.toUpperCase();
        if ("CUSTOMER".equals(role) && user.getCustomerId() == null) {
            throw new BusinessException("该客户账号未绑定用电户，请联系管理员");
        }
        if ("OPERATOR".equals(role) && user.getStaffId() == null) {
            throw new BusinessException("该运维账号未绑定运维人员档案，请联系管理员");
        }
        return role;
    }
}
