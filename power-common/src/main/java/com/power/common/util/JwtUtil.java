package com.power.common.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * JWT 工具类
 * <p>提供 Token 的生成、验证和解析功能。使用 HMAC256 算法签名。</p>
 *
 * <p>签名密钥与有效期从配置读取（{@code power.jwt.secret} / {@code power.jwt.expire-minutes}），
 * 不再硬编码在源码中；网关与各业务服务共用同一份密钥配置，以便网关统一校验后
 * 业务服务可复用同一 Token 解析逻辑。</p>
 */
@Component
public class JwtUtil {

    /** 签名密钥，由配置注入；未配置时启动失败，避免静默使用弱默认值 */
    private final String secret;
    /** Token 有效期（毫秒） */
    private final long expiration;

    public JwtUtil(@Value("${power.jwt.secret:}") String secret,
                   @Value("${power.jwt.expire-minutes:1440}") long expireMinutes) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("缺少配置 power.jwt.secret，请在 application.yml 或 Nacos 中配置 JWT 签名密钥");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("power.jwt.secret 长度不足 32 位，HMAC256 密钥强度不够");
        }
        this.secret = secret;
        this.expiration = expireMinutes * 60L * 1000L;
    }

    /**
     * 生成 JWT Token
     *
     * @param userId   用户 ID（存入 claim）
     * @param username 用户名（存入 claim）
     * @param role     角色标识（存入 claim，供网关做粗粒度鉴权）
     * @return 签名的 JWT 字符串
     */
    public String generateToken(Long userId, String username, String role) {
        return generateToken(userId, username, role, null, null);
    }

    /**
     * 生成 JWT Token（带数据范围）
     *
     * <p>把 {@code customerId} / {@code staffId} 一并写进 Token，是为了让下游服务<b>不必再回查认证库</b>
     * 就能知道"这次请求属于哪一户 / 哪个运维人员"。这比让每个服务都调一次认证服务便宜得多，
     * 也避免了分布式场景下"鉴权服务挂了整个系统就用不了"的强耦合。</p>
     *
     * <p>代价是角色与绑定关系变更后，旧 Token 在过期前仍带老值。本项目有效期 24 小时，
     * 且绑定关系几乎不变，可接受；若将来要强制即时生效，引入 Token 版本号或黑名单即可。</p>
     *
     * @param customerId 用电客户 ID，非客户角色传 null
     * @param staffId    运维人员 ID，非运维角色传 null
     */
    public String generateToken(Long userId, String username, String role,
                                Long customerId, Long staffId) {
        Date now = new Date();
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withClaim("role", role)
                .withClaim("customerId", customerId)
                .withClaim("staffId", staffId)
                .withIssuedAt(now)
                .withExpiresAt(new Date(now.getTime() + expiration))
                .sign(Algorithm.HMAC256(secret));
    }

    /**
     * 验证 Token 签名并解码
     *
     * @param token JWT 字符串
     * @return 解码后的 JWT 对象
     * @throws com.auth0.jwt.exceptions.JWTVerificationException 签名无效或过期时抛出
     */
    public DecodedJWT verifyToken(String token) {
        return JWT.require(Algorithm.HMAC256(secret)).build().verify(token);
    }

    /** 从 Token 中提取用户 ID */
    public Long getUserId(String token) {
        return verifyToken(token).getClaim("userId").asLong();
    }

    /** 从 Token 中提取用户名 */
    public String getUsername(String token) {
        return verifyToken(token).getClaim("username").asString();
    }

    /** 从 Token 中提取角色 */
    public String getRole(String token) {
        return verifyToken(token).getClaim("role").asString();
    }

    /** 当前配置下 Token 的有效期（毫秒），供登录响应返回过期时间 */
    public long getExpiration() {
        return expiration;
    }
}
