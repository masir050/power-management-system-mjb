package com.power.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 分布式锁封装（基于 Redisson）
 *
 * <p>两个关键点：</p>
 * <ul>
 *   <li><b>看门狗续期</b>：使用无参的 {@code lock()}（不传 leaseTime）时，
 *       Redisson 会启动一个后台任务，每 {@code lockWatchdogTimeout/3}（默认 10s）给锁续期一次，
 *       保证业务没执行完锁不会因为到达租期被提前释放。如果传了 leaseTime，
 *       就等于关闭了看门狗，业务超过该时长锁会自动释放——那正是并发超卖/重复更新的来源。</li>
 *   <li><b>finally 释放</b>：锁必须在 finally 里释放，且只由持有者释放。
 *       Redisson 的 RLock 会在释放时校验当前线程/客户端是否是持有者，避免误删别人的锁。</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "power.cache.enabled", havingValue = "true")
public class DistributedLock {

    private final RedissonClient redisson;

    public DistributedLock(RedissonClient redisson) {
        this.redisson = redisson;
    }

    /**
     * 在分布式锁保护下执行一段逻辑（阻塞获取锁，靠看门狗续期）
     *
     * @param lockKey 锁的 key，建议带上业务前缀，如 "lock:equipment:update:12"
     * @param supplier 临界区逻辑
     * @param <T> 返回值类型
     * @return 临界区逻辑的返回值
     * @throws IllegalStateException 获取锁失败时抛出
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
        RLock lock = redisson.getLock(lockKey);
        try {
            // 不传 leaseTime => 启用看门狗，每 10s 自动续期
            lock.lock();
            log.debug("已获取分布式锁 {}", lockKey);
            return supplier.get();
        } finally {
            // 只有当前线程持有锁时才释放，避免释放他人的锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("已释放分布式锁 {}", lockKey);
            }
        }
    }

    /**
     * 在分布式锁保护下执行一段逻辑（带等待超时，抢不到锁直接失败）
     *
     * @param lockKey   锁的 key
     * @param waitSeconds 最长等待秒数
     * @param supplier  临界区逻辑
     * @param <T> 返回值类型
     * @return 临界区逻辑的返回值
     * @throws IllegalStateException 等待超时未拿到锁时抛出
     */
    public <T> T tryExecuteWithLock(String lockKey, long waitSeconds, Supplier<T> supplier) {
        RLock lock = redisson.getLock(lockKey);
        boolean acquired = false;
        try {
            // 不传 leaseTime => 仍然启用看门狗续期
            acquired = lock.tryLock(waitSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("操作过于频繁，请稍后重试");
            }
            log.debug("已获取分布式锁 {}", lockKey);
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待锁被中断", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
