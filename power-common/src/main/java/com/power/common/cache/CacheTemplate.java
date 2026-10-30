package com.power.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 缓存模板 —— Cache Aside 模式的统一封装
 *
 * <p>解决的问题（这三类正是缓存设计里最常被问到的）：</p>
 * <ol>
 *   <li><b>缓存穿透</b>：查询一个必然不存在的 key，每次都打到数据库。
 *       这里对空结果写入一个短 TTL 的空值占位（{@link #NULL_PLACEHOLDER}），
 *       后续请求直接命中占位符返回 null，不再穿透。</li>
 *   <li><b>缓存雪崩</b>：大批 key 在同一时刻集中过期，导致请求全部落到数据库。
 *       这里对每个 key 的 TTL 加一个随机的抖动区间（±{@link #TTL_JITTER_RATIO}）。</li>
 *   <li><b>缓存击穿</b>：单个热点 key 过期瞬间，大量并发请求同时回源。
 *       这里由调用方配合 {@link DistributedLock} 使用（见 device 服务的热点查询实现）。</li>
 * </ol>
 *
 * <p><b>写操作的一致性策略</b>：先更新数据库，再删除缓存（不是更新缓存）。
 * 之所以删除而非更新，是为了避免两个并发写请求以不同顺序落库和写缓存，导致缓存里留下旧值。
 * 进一步地，删除后延迟一小段时间再删一次（延迟双删），用于兜底"读请求在删缓存前读到了旧值、
 * 又在删缓存后把旧值写回缓存"的极端竞态。具体实现见 {@link #deleteAfterCommit}。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "power.cache.enabled", havingValue = "true")
public class CacheTemplate {

    /** 空值占位符：缓存穿透防护用，读到它等同于读到一个确定的 null */
    private static final String NULL_PLACEHOLDER = "__NULL__";

    /** TTL 抖动比例：±10%，避免同一批 key 同时过期造成雪崩 */
    private static final double TTL_JITTER_RATIO = 0.1;

    /** 空值占位的 TTL：短一些，业务数据补录后能尽快恢复正常读取 */
    private static final Duration NULL_TTL = Duration.ofSeconds(60);

    /** 延迟双删的第二删延迟 */
    private static final long DOUBLE_DELETE_DELAY_MS = 500L;

    private final StringRedisTemplate redis;

    public CacheTemplate(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 读缓存，未命中则回源并写回（标准 Cache Aside 读路径）
     *
     * @param key      缓存 key
     * @param ttl      基础过期时间
     * @param loader   回源函数，返回 null 表示数据库里确实没有
     * @param <T>      返回值类型（由调用方决定序列化方式，这里统一走 String）
     * @return 回源函数的结果（可能是 null）
     */
    public String getOrLoad(String key, Duration ttl, Supplier<String> loader) {
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            // 命中空值占位：确定不存在，直接返回 null，不再回源（防穿透）
            if (NULL_PLACEHOLDER.equals(cached)) {
                log.debug("命中空值占位，跳过回源 key={}", key);
                return null;
            }
            return cached;
        }
        String loaded = loader.get();
        if (loaded == null) {
            // 回源为空：写入短 TTL 占位，挡住后续穿透请求
            redis.opsForValue().set(key, NULL_PLACEHOLDER, NULL_TTL);
            return null;
        }
        redis.opsForValue().set(key, loaded, jitter(ttl));
        return loaded;
    }

    /**
     * 写操作后的缓存失效：先删除，延迟一段时间再删一次（延迟双删）
     *
     * <p>调用时机必须是<b>数据库事务提交之后</b>，不能在事务方法内部调用——
     * 否则事务还没提交，缓存已删，另一个读请求会把旧数据重新写回缓存。</p>
     *
     * @param key 缓存 key
     */
    public void deleteAfterCommit(String key) {
        redis.delete(key);
        // 用 Redis 的轻量延迟：避免在业务线程里 sleep 阻塞
        try {
            Thread.sleep(DOUBLE_DELETE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("延迟双删被中断 key={}", key);
            return;
        }
        redis.delete(key);
        log.debug("延迟双删完成 key={}", key);
    }

    /** 直接删除缓存（无需延迟双删的弱一致场景） */
    public void evict(String key) {
        redis.delete(key);
    }

    /**
     * 纯读缓存（命中返回值，未命中返回 null，<b>不</b>回源也不写占位）
     *
     * <p>存在意义：击穿保护场景下，调用方需要"先探一次缓存，再决定要不要抢锁回源"。
     * 若只有 {@link #getOrLoad}，未命中时它自己就会回源，等于绕过了锁，
     * 击穿保护形同虚设。</p>
     *
     * <p>与 {@code getOrLoad} 的差别：命中空值占位时同样返回 null，
     * 但<b>不会</b>因为未命中而写占位——写占位是回源者的责任。</p>
     */
    public String get(String key) {
        String cached = redis.opsForValue().get(key);
        if (cached == null || NULL_PLACEHOLDER.equals(cached)) {
            return null;
        }
        return cached;
    }

    /**
     * 强制写缓存（回源后由调用方主动写入）
     *
     * <p>与 {@code getOrLoad} 的区别：不做"已有则跳过"的判断，直接覆盖。
     * 用于回源成功后写回，避免占位符还在时 getOrLoad 误判。</p>
     */
    public void put(String key, String value, Duration ttl) {
        if (value == null) {
            redis.opsForValue().set(key, NULL_PLACEHOLDER, NULL_TTL);
        } else {
            redis.opsForValue().set(key, value, jitter(ttl));
        }
    }

    /** 查询缓存 key 的剩余存活时间（秒），调试与演示一致性用 */
    public Long ttl(String key) {
        return redis.getExpire(key);
    }

    /**
     * 给 TTL 加 ±10% 随机抖动
     * <p>同一个 key 每次的 TTL 略有差异，避免批量预热的 key 在同一秒集体过期。</p>
     */
    private Duration jitter(Duration base) {
        long millis = base.toMillis();
        long delta = (long) (millis * TTL_JITTER_RATIO);
        long offset = ThreadLocalRandom.current().nextLong(-delta, delta + 1);
        return Duration.ofMillis(Math.max(1000L, millis + offset));
    }
}
