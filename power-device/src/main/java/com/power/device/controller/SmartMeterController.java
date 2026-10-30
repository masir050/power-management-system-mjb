package com.power.device.controller;

import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.pojo.Result;
import com.power.device.pojo.SmartMeter;
import com.power.device.service.SmartMeterService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 智能电表控制器，提供智能电表的分页查询、详情、统计及增删改查接口 */
@RestController
@RequestMapping("/api/meter")
public class SmartMeterController {

    private final SmartMeterService smartMeterService;

    public SmartMeterController(SmartMeterService smartMeterService) {
        this.smartMeterService = smartMeterService;
    }

    /** 分页查询智能电表列表 */
    @GetMapping("/list")
    public Result<PageResult<SmartMeter>> list(PageDTO pageDTO, @RequestParam Map<String, String> params) {
        return Result.success(smartMeterService.pageList(pageDTO, params));
    }

    /** 根据ID查询智能电表详情 */
    @GetMapping("/{id}")
    public Result<SmartMeter> getById(@PathVariable Long id) {
        SmartMeter entity = smartMeterService.getById(id);
        if (entity == null) return Result.notFound();
        return Result.success(entity);
    }

    /** 查询智能电表及其所属用户 */
    @GetMapping("/vo/{id}")
    public Result<Map<String, Object>> getWithCustomer(@PathVariable Long id) {
        Map<String, Object> data = smartMeterService.queryMeterWithCustomer(id);
        if (data == null) return Result.notFound();
        return Result.success(data);
    }

    /** 多条件查询智能电表（含自定义SQL） */
    @GetMapping("/vo/page")
    public Result<List<Map<String, Object>>> pageWithSql(
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String meterStatus,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String installLocation) {
        return Result.success(smartMeterService.queryMeterPage(model, meterStatus, customerId, installLocation));
    }

    /** 查询智能电表统计数据 */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(smartMeterService.queryMeterStatistics());
    }

    /** 按型号分组统计智能电表 */
    @GetMapping("/group/model")
    public Result<List<Map<String, Object>>> groupByModel() {
        return Result.success(smartMeterService.queryMetersByStatusGroupByModel());
    }

    /** 新增智能电表 */
    @PostMapping
    public Result<Void> create(@RequestBody SmartMeter entity) {
        smartMeterService.create(entity);
        return Result.success();
    }

    /** 更新智能电表 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SmartMeter entity) {
        entity.setId(id);
        smartMeterService.update(entity);
        return Result.success();
    }

    /** 删除智能电表 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        smartMeterService.delete(id);
        return Result.success();
    }
}
