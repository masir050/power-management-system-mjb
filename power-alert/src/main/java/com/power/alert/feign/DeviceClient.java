package com.power.alert.feign;

import com.power.common.pojo.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

/**
 * 设备服务远程调用
 *
 * <p>仪表盘需要设备的统计数，但数据在 device 服务的库（power_device）里。
 * 微服务拆分后不能再像单体那样直接注入 device 的 Mapper（那样等于把两个库重新耦死），
 * 只能走 HTTP 调用。</p>
 *
 * <p>降级：所有方法都有 {@code fallbackFactory}，device 挂掉时返回空统计而不是让整个仪表盘 500。</p>
 */
@FeignClient(name = "power-device", path = "/api",
        fallbackFactory = DeviceClientFallbackFactory.class)
public interface DeviceClient {

    /** 设备统计（总数、按危害等级分布） */
    @GetMapping("/equipment/statistics")
    Result<Map<String, Object>> equipmentStatistics();

    /**
     * 运维汇总（设备 / 变电站 / 电表 三类统计一次拿全）
     *
     * <p>替代原来的 equipmentStatistics + substationStatistics + meterStatistics 三次串行调用。
     * 三组数字同库，提供方用一条 SQL 算完，这里一次请求就够。</p>
     */
    @GetMapping("/operations/summary")
    Result<Map<String, Object>> operationsSummary();

    /** 变电站统计（总数、运行中数量） */
    @GetMapping("/substation/statistics")
    Result<Map<String, Object>> substationStatistics();

    /** 电表统计（总数、正常/异常数量） */
    @GetMapping("/meter/statistics")
    Result<Map<String, Object>> meterStatistics();

    /**
     * 按 ID 查设备
     * <p>告警创建时用来补全设备名——告警记录只存 equipment_id，
     * 名称属于设备服务的数据，不应该在告警库里冗余一份。</p>
     */
    @GetMapping("/equipment/{id}")
    Result<Map<String, Object>> getEquipment(@PathVariable("id") Long id);

    /** 按危害等级分组 */
    @GetMapping("/equipment/group/hazard")
    Result<List<Map<String, Object>>> equipmentGroupByHazard();
}
