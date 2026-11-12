package com.power.rag.memory;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class Memory {

    private final StringRedisTemplate redis;
    private static final int KEEP = 20;   // 10 轮 = 20 条消息

    public Memory(StringRedisTemplate redis) { this.redis = redis; }

    public void save(String sid, String role, String text) {
        String k = "chat:" + sid;
        redis.opsForList().rightPush(k, role + "|" + text);
        redis.opsForList().trim(k, -KEEP, -1);      // 只留最近 20 条
        redis.expire(k, Duration.ofHours(2));       // 别忘了过期，否则 key 无限涨
    }

    public List<String> load(String sid) {
        List<String> raw = redis.opsForList().range("chat:" + sid, 0, -1);
        return raw == null ? List.of() : raw;
    }
}

