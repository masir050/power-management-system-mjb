package com.power.alert.controller;

import com.power.alert.pojo.MaintenanceRecord;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.pojo.Result;
import com.power.alert.service.MaintenanceRecordService;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 维修记录控制器，提供维修记录的分页查询、详情、统计及增删改查接口 */
@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceRecordController {

    private final MaintenanceRecordService maintenanceRecordService;

    public MaintenanceRecordController(MaintenanceRecordService maintenanceRecordService) {
        this.maintenanceRecordService = maintenanceRecordService;
    }

    /** 分页查询维修记录列表 */
    @GetMapping("/list")
    public Result<PageResult<MaintenanceRecord>> list(PageDTO pageDTO, @RequestParam Map<String, String> params) {
        return Result.success(maintenanceRecordService.pageList(pageDTO, params));
    }

    /** 根据ID查询维修记录详情 */
    @GetMapping("/{id}")
    public Result<MaintenanceRecord> getById(@PathVariable Long id) {
        MaintenanceRecord entity = maintenanceRecordService.getById(id);
        if (entity == null) return Result.notFound();
        return Result.success(entity);
    }

    /** 查询维修记录及其关联信息 */
    @GetMapping("/vo/{id}")
    public Result<Map<String, Object>> getWithDetails(@PathVariable Long id) {
        Map<String, Object> data = maintenanceRecordService.queryRecordWithDetails(id);
        if (data == null) return Result.notFound();
        return Result.success(data);
    }

    /** 多条件查询维修记录（含自定义SQL） */
    @GetMapping("/vo/page")
    public Result<List<Map<String, Object>>> pageWithSql(
            @RequestParam(required = false) String faultType,
            @RequestParam(required = false) String repairPriority,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return Result.success(maintenanceRecordService.queryRecordPage(faultType, repairPriority, equipmentId, staffId, startTime, endTime));
    }

    /** 查询维修记录统计数据 */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(maintenanceRecordService.queryRecordStatistics());
    }

    /** 按故障类型分组统计维修记录 */
    @GetMapping("/group/fault-type")
    public Result<List<Map<String, Object>>> groupByFaultType() {
        return Result.success(maintenanceRecordService.queryRecordGroupByFaultType());
    }

    /** 查询近期告警记录 */
    @GetMapping("/recent-alerts")
    public Result<List<Map<String, Object>>> recentAlerts(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(maintenanceRecordService.queryRecentAlerts(limit));
    }

    /** 新增维修记录 */
    @PostMapping
    public Result<Void> create(@RequestBody MaintenanceRecord entity) {
        maintenanceRecordService.create(entity);
        return Result.success();
    }

    /** 更新维修记录 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MaintenanceRecord entity) {
        entity.setId(id);
        maintenanceRecordService.update(entity);
        return Result.success();
    }

    /** 删除维修记录 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        maintenanceRecordService.delete(id);
        return Result.success();
    }
}
