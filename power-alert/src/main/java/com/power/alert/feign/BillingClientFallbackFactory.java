package com.power.alert.feign;

import com.power.common.pojo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 电费核算服务降级工厂，理由同 {@link DeviceClientFallbackFactory} */
@Slf4j
@Component
public class BillingClientFallbackFactory implements FallbackFactory<BillingClient> {

    @Override
    public BillingClient create(Throwable cause) {
        return new BillingClient() {
            @Override
            public Result<List<Map<String, Object>>> billStatistics() {
                log.warn("调用电费服务失败，账单统计置空: {}", cause.toString());
                return Result.success(List.of());
            }

            @Override
            public Result<List<Map<String, Object>>> dailyUsage(int days) {
                // 刻意返回空列表而不是编一条曲线：
                // 降级时给出"暂无数据"比给一条看起来正常的假曲线诚实得多
                log.warn("调用电费服务失败，日用电曲线置空（不做假数据兜底）: {}", cause.toString());
                return Result.success(List.of());
            }

            @Override
            public Result<Map<String, Object>> usageSummary(int days) {
                log.warn("调用电费服务失败，用电汇总置空: {}", cause.toString());
                return Result.success(Map.of());
            }

            @Override
            public Result<Map<String, Object>> usageTouSplit(int days) {
                log.warn("调用电费服务失败，尖峰平谷构成置空: {}", cause.toString());
                return Result.success(Map.of());
            }
        };
    }
}
