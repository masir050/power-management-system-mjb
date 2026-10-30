package com.power.device.controller;

import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.pojo.Result;
import com.power.device.pojo.Substation;
import com.power.device.service.SubstationService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 变电站控制器，提供变电站的分页查询、详情、统计及增删改查接口 */
@RestController
@RequestMapping("/api/substation")
public class SubstationController {

    private final SubstationService substationService;

    public SubstationController(SubstationService substationService) {
        this.substationService = substationService;
    }

    /** 分页查询变电站列表 */
    @GetMapping("/list")
    public Result<PageResult<Substation>> list(PageDTO pageDTO, @RequestParam Map<String, String> params) {
        return Result.success(substationService.pageList(pageDTO, params));
    }

    /** 根据ID查询变电站详情 */
    @GetMapping("/{id}")
    public Result<Substation> getById(@PathVariable Long id) {
        Substation entity = substationService.getById(id);
        if (entity == null) return Result.notFound();
        return Result.success(entity);
    }

    /** 查询变电站及其所属供电区域 */
    @GetMapping("/vo/{id}")
    public Result<Map<String, Object>> getWithArea(@PathVariable Long id) {
        Map<String, Object> data = substationService.querySubstationWithArea(id);
        if (data == null) return Result.notFound();
        return Result.success(data);
    }

    /** 多条件查询变电站（含自定义SQL） */
    @GetMapping("/vo/page")
    public Result<List<Map<String, Object>>> pageWithSql(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String voltageLevel,
            @RequestParam(required = false) String runStatus,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) String managementLevel) {
        return Result.success(substationService.querySubstationPage(name, voltageLevel, runStatus, areaId, managementLevel));
    }

    /** 查询变电站统计数据 */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(substationService.querySubstationStatistics());
    }

    /** 按电压等级分组统计变电站 */
    @GetMapping("/group/voltage")
    public Result<List<Map<String, Object>>> groupByVoltage() {
        return Result.success(substationService.querySubstationGroupByVoltage());
    }

    /** 新增变电站 */
    @PostMapping
    public Result<Void> create(@RequestBody Substation entity) {
        substationService.create(entity);
        return Result.success();
    }

    /** 更新变电站 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Substation entity) {
        entity.setId(id);
        substationService.update(entity);
        return Result.success();
    }

    /** 删除变电站 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        substationService.delete(id);
        return Result.success();
    }
}
