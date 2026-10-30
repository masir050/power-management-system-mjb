package com.power.alert.feign;

import com.power.common.pojo.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 电费核算服务远程调用
 *
 * <p>总览页要用到 billing 的三类数据：账单统计、日用电曲线、尖峰平谷构成。
 * 三者都落在 {@code power_billing} 库，billing 服务的对应接口已按网关透传的
 * {@code X-Customer-Id} 做过范围过滤——<b>这里不传"看谁的数据"，身份由请求头带过去</b>。
 * Feign 默认会转发原始请求头，所以网关注入的 X-User-* / X-Customer-Id 能一路传到 billing。</p>
 *
 * <p><b>返回类型必须与提供方完全一致</b>：{@code /api/bill/statistics} 返回数组（按年分组），
 * 声明成 Map 编译期不报错，但运行期反序列化会失败。</p>
 */
@FeignClient(name = "power-billing", path = "/api",
        fallbackFactory = BillingClientFallbackFactory.class)
public interface BillingClient {

    /** 账单统计：按年分组的数组（总用电量、应收、实收、欠费） */
    @GetMapping("/bill/statistics")
    Result<List<Map<String, Object>>> billStatistics();

    /**
     * 日用电曲线（真实表 electricity_daily_usage）
     *
     * @param days 窗口天数
     */
    @GetMapping("/usage/daily")
    Result<List<Map<String, Object>>> dailyUsage(@RequestParam("days") int days);

    /** 近 N 天汇总（总量、日均、峰值日） */
    @GetMapping("/usage/summary")
    Result<Map<String, Object>> usageSummary(@RequestParam("days") int days);

    /** 尖峰平谷电量构成 */
    @GetMapping("/usage/tou-split")
    Result<Map<String, Object>> usageTouSplit(@RequestParam("days") int days);
}
