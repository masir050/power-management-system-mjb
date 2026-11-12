package com.power.rag.controller;

import com.power.rag.service.Stats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 指标清零开关。GET /stats 本体在 DebugController，两处共用同一个 Stats。 */
@RestController
public class StatsController {

    private final Stats stats;

    public StatsController(Stats stats) {
        this.stats = stats;
    }

    /** 反复压测时清零后重跑，正常业务不调 */
    @GetMapping("/stats/reset")
    public Map<String, Object> reset() {
        stats.reset();
        return Map.of("reset", true);
    }
}
