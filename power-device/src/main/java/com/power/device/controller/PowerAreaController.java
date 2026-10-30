package com.power.device.controller;

import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.device.pojo.PowerArea;
import com.power.common.pojo.Result;
import com.power.device.service.PowerAreaService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 供电区域控制器，提供供电区域的分页查询、详情、统计及增删改查接口 */
@RestController
@RequestMapping("/api/power-area")
public class PowerAreaController {

    private final PowerAreaService powerAreaService;

    public PowerAreaController(PowerAreaService powerAreaService) {
        this.powerAreaService = powerAreaService;
    }

    /** 分页查询供电区域列表 */
    @GetMapping("/list")
    public Result<PageResult<PowerArea>> list(PageDTO pageDTO, @RequestParam Map<String, String> params) {
        return Result.success(powerAreaService.pageList(pageDTO, params));
    }

    /** 根据ID查询供电区域详情 */
    @GetMapping("/{id}")
    public Result<PowerArea> getById(@PathVariable Long id) {
        PowerArea entity = powerAreaService.getById(id);
        if (entity == null) return Result.notFound();
        return Result.success(entity);
    }

    /** 多条件查询供电区域（含自定义SQL） */
    @GetMapping("/vo/page")
    public Result<List<com.power.device.pojo.PowerAreaVO>> pageWithSql(
            @RequestParam(required = false) String areaName,
            @RequestParam(required = false) String adminLevel,
            @RequestParam(required = false) String leader) {
        return Result.success(powerAreaService.queryAreaPage(areaName, adminLevel, leader));
    }

    /** 查询供电区域统计数据 */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(powerAreaService.queryAreaStatistics());
    }

    /** 查询供电区域及其下属变电站数量 */
    @GetMapping("/with-substations")
    public Result<List<Map<String, Object>>> withSubstations(
            @RequestParam(required = false) String areaName) {
        return Result.success(powerAreaService.queryAreaWithSubstationCount(areaName));
    }

    /** 新增供电区域 */
    @PostMapping
    public Result<Void> create(@RequestBody PowerArea entity) {
        powerAreaService.create(entity);
        return Result.success();
    }

    /** 更新供电区域 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PowerArea entity) {
        entity.setId(id);
        powerAreaService.update(entity);
        return Result.success();
    }

    /** 删除供电区域 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        powerAreaService.delete(id);
        return Result.success();
    }

    /** 批量删除供电区域 */
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        powerAreaService.deleteAreaByIds(ids);
        return Result.success();
    }
}
