package com.power.rag.service;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程级指标：请求数、缓存命中、累计 token。
 * 用 AtomicLong 而不是 synchronized —— 这些操作是纯自增，无锁更快，也不需要一致快照。
 * 注意：重启就清零，这是"进程指标"不是"持久化监控"。要长期看命中率得上 Micrometer + Prometheus。
 */
@Service
public class Stats {

    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();

    /** 每进一次带缓存的问答记一次，命中/未命中再各记一次 */
    public void request() {
        requests.incrementAndGet();
    }

    public void hit() {
        hits.incrementAndGet();
    }

    public void miss() {
        misses.incrementAndGet();
    }

    /** 模型返回的 usage 可能是 null（某些网关不透传），这里一律容忍 */
    public void addUsage(Integer prompt, Integer completion) {
        if (prompt != null) promptTokens.addAndGet(prompt);
        if (completion != null) completionTokens.addAndGet(completion);
    }

    public void addUsage(ChatResponse resp) {
        if (resp == null || resp.getMetadata() == null) return;
        Usage u = resp.getMetadata().getUsage();
        if (u == null) return;
        addUsage(u.getPromptTokens(), u.getCompletionTokens());
    }

    public Map<String, Object> snapshot() {
        long h = hits.get();
        long m = misses.get();
        long cached = h + m;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requests", requests.get());
        out.put("hit", h);
        out.put("miss", m);
        // 命中率的分母是"进过缓存的请求数"，不是全部请求（/stats 自己不算，/chat/mem 也不进缓存）
        out.put("hitRate", cached == 0 ? "0.0%" : String.format("%.1f%%", h * 100.0 / cached));
        out.put("promptTokens", promptTokens.get());
        out.put("completionTokens", completionTokens.get());
        out.put("totalTokens", promptTokens.get() + completionTokens.get());
        return out;
    }

    /** 反复压测时手动清零，正常业务不调 */
    public void reset() {
        requests.set(0);
        hits.set(0);
        misses.set(0);
        promptTokens.set(0);
        completionTokens.set(0);
    }
}
