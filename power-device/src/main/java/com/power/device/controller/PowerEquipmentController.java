package com.power.device.controller;

import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.device.pojo.PowerEquipment;
import com.power.common.pojo.Result;
import com.power.device.service.PowerEquipmentService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 电力设备控制器，提供电力设备的分页查询、详情、统计及增删改查接口 */
@RestController
@RequestMapping("/api/equipment")
public class PowerEquipmentController {

    private final PowerEquipmentService powerEquipmentService;

    public PowerEquipmentController(PowerEquipmentService powerEquipmentService) {
        this.powerEquipmentService = powerEquipmentService;
    }

    /** 分页查询电力设备列表 */
    @GetMapping("/list")
    public Result<PageResult<PowerEquipment>> list(PageDTO pageDTO, @RequestParam Map<String, String> params) {
        return Result.success(powerEquipmentService.pageList(pageDTO, params));
    }

    /** 根据ID查询电力设备详情 */
    @GetMapping("/{id}")
    public Result<PowerEquipment> getById(@PathVariable Long id) {
        PowerEquipment entity = powerEquipmentService.getById(id);
        if (entity == null) return Result.notFound();
        return Result.success(entity);
    }

    /** 查询电力设备及其所属变电站 */
    @GetMapping("/vo/{id}")
    public Result<Map<String, Object>> getWithSubstation(@PathVariable Long id) {
        Map<String, Object> data = powerEquipmentService.queryEquipmentWithSubstation(id);
        if (data == null) return Result.notFound();
        return Result.success(data);
    }

    /** 多条件查询电力设备（含自定义SQL） */
    @GetMapping("/vo/page")
    public Result<List<Map<String, Object>>> pageWithSql(
            @RequestParam(required = false) String equipmentName,
            @RequestParam(required = false) String equipmentType,
            @RequestParam(required = false) String hazardLevel,
            @RequestParam(required = false) Long substationId,
            @RequestParam(required = false) String installBatch) {
        return Result.success(powerEquipmentService.queryEquipmentPage(equipmentName, equipmentType, hazardLevel, substationId, installBatch));
    }

    /** 查询电力设备统计数据 */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(powerEquipmentService.queryEquipmentStatistics());
    }

    /** 按危害等级分组统计电力设备 */
    @GetMapping("/group/hazard")
    public Result<List<Map<String, Object>>> groupByHazard() {
        return Result.success(powerEquipmentService.queryEquipmentGroupByHazard());
    }

    /** 新增电力设备 */
    @PostMapping
    public Result<Void> create(@RequestBody PowerEquipment entity) {
        powerEquipmentService.create(entity);
        return Result.success();
    }

    /** 更新电力设备 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PowerEquipment entity) {
        entity.setId(id);
        powerEquipmentService.update(entity);
        return Result.success();
    }

    /** 删除电力设备 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        powerEquipmentService.delete(id);
        return Result.success();
    }
}
