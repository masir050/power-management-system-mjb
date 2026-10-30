package com.power.alert.feign;

import com.power.common.pojo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 设备服务降级工厂
 *
 * <p>为什么用 {@code FallbackFactory} 而不是 {@code fallback}？
 * 因为在工厂里能拿到真实异常并打日志。用 {@code fallback} 的话，下游报什么错都被吞成一句
 * "服务不可用"，排查时不知道是超时、是 404 还是 Nacos 里没有这个实例。</p>
 */
@Slf4j
@Component
public class DeviceClientFallbackFactory implements FallbackFactory<DeviceClient> {

    @Override
    public DeviceClient create(Throwable cause) {
        return new DeviceClient() {
            @Override
            public Result<Map<String, Object>> equipmentStatistics() {
                log.warn("调用设备服务失败，返回空统计: {}", cause.toString());
                return Result.success(Map.of());
            }

            @Override
            public Result<Map<String, Object>> substationStatistics() {
                log.warn("调用设备服务失败，返回空统计: {}", cause.toString());
                return Result.success(Map.of());
            }

            @Override
            public Result<Map<String, Object>> meterStatistics() {
                log.warn("调用设备服务失败，返回空统计: {}", cause.toString());
                return Result.success(Map.of());
            }

            @Override
            public Result<Map<String, Object>> operationsSummary() {
                log.warn("调用设备服务失败，返回空汇总: {}", cause.toString());
                return Result.success(Map.of());
            }

            @Override
            public Result<Map<String, Object>> getEquipment(Long id) {
                log.warn("调用设备服务失败，设备 {} 信息缺失: {}", id, cause.toString());
                return Result.success(null);
            }

            @Override
            public Result<List<Map<String, Object>>> equipmentGroupByHazard() {
                log.warn("调用设备服务失败，返回空分组: {}", cause.toString());
                return Result.success(List.of());
            }
        };
    }
}
