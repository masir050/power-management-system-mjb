package com.power.billing.controller;

import com.power.billing.service.DailyUsageService;
import com.power.common.pojo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 日用电量控制器
 *
 * <p>客户登录后进"我的用电"看到的是自己那一户；管理员/运维看到的是全站。
 * <b>同一个 URL，不同的数据</b>——判断在服务端，前端只负责展示。</p>
 */
@RestController
@RequestMapping("/api/usage")
public class DailyUsageController {

    private final DailyUsageService dailyUsageService;

    public DailyUsageController(DailyUsageService dailyUsageService) {
        this.dailyUsageService = dailyUsageService;
    }

    /** 日用电曲线（首页负荷曲线、客户"我的用电"共用同一份数据源） */
    @GetMapping("/daily")
    public Result<List<Map<String, Object>>> daily(@RequestParam(defaultValue = "30") int days) {
        return Result.success(dailyUsageService.dailyTrend(days));
    }

    /** 分表明细：客户名下多块表时逐表看 */
    @GetMapping("/meter-daily")
    public Result<List<Map<String, Object>>> meterDaily(
            @RequestParam(required = false) Long meterId,
            @RequestParam(defaultValue = "30") int days) {
        return Result.success(dailyUsageService.meterDailyTrend(meterId, days));
    }

    /** 近 N 天汇总：总量、日均、峰值日 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(defaultValue = "30") int days) {
        return Result.success(dailyUsageService.summary(days));
    }

    /** 尖峰平谷构成 */
    @GetMapping("/tou-split")
    public Result<Map<String, Object>> touSplit(@RequestParam(defaultValue = "30") int days) {
        return Result.success(dailyUsageService.touSplit(days));
    }

    /** 月度趋势 */
    @GetMapping("/monthly")
    public Result<List<Map<String, Object>>> monthly(@RequestParam(defaultValue = "6") int months) {
        return Result.success(dailyUsageService.monthlyTrend(months));
    }
}
