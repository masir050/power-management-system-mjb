package com.power.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * 答案缓存：key = sha16(system + \u0001 + user)，TTL 30 分钟。
 * key 里带 system 是因为换提示词答案该变；用 \u0001 分隔是防 "ab"+"c" 与 "a"+"bc" 撞车。
 * 只用于单轮 /ask，/chat/mem 带历史绝不能走缓存。
 */
@Service
public class AnswerCache {

    private static final Logger log = LoggerFactory.getLogger(AnswerCache.class);
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String PREFIX = "ans:";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Stats stats;

    public AnswerCache(StringRedisTemplate redis, ObjectMapper json, Stats stats) {
        this.redis = redis;
        this.json = json;
        this.stats = stats;
    }

    /** 命中直接返回，未命中执行 loader 并写回；取答案的动作交给调用方，缓存层对业务无感 */
    public AskResult getOrLoad(String system, String user, Supplier<AskResult> loader) {
        stats.request();
        String key = PREFIX + sha16(system + "\u0001" + user);

        AskResult cached = read(key);
        if (cached != null) {
            stats.hit();
            log.info("[cache] HIT {}", key);
            return cached;
        }

        stats.miss();
        log.info("[cache] MISS {}", key);
        AskResult fresh = loader.get();
        write(key, fresh);
        return fresh;
    }

    private AskResult read(String key) {
        String raw;
        try {
            raw = redis.opsForValue().get(key);
        } catch (Exception e) {
            // Redis 挂了不能拖垮问答：降级成"永远 miss"
            log.warn("[cache] 读 Redis 失败，按 miss 处理：{}", e.getMessage());
            return null;
        }
        if (raw == null) return null;
        try {
            return json.readValue(raw, AskResult.class);
        } catch (Exception e) {
            log.warn("[cache] 反序列化失败，按 miss 处理：{}", e.getMessage());
            return null;
        }
    }

    private void write(String key, AskResult result) {
        try {
            redis.opsForValue().set(key, json.writeValueAsString(result), TTL);
        } catch (Exception e) {
            log.warn("[cache] 写入失败（不影响本次回答）：{}", e.getMessage());
        }
    }

    /** SHA-256 取前 8 字节（16 个 hex 字符）——够短、撞车概率可忽略 */
    static String sha16(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(Character.forDigit((d[i] >> 4) & 0xF, 16));
                sb.append(Character.forDigit(d[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
