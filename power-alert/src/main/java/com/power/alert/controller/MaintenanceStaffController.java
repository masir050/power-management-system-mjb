package com.power.alert.controller;

import com.power.alert.pojo.MaintenanceStaff;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.pojo.Result;
import com.power.alert.service.MaintenanceStaffService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 维修人员控制器，提供维修人员的分页查询、详情、统计及增删改查接口 */
@RestController
@RequestMapping("/api/staff")
public class MaintenanceStaffController {

    private final MaintenanceStaffService maintenanceStaffService;

    public MaintenanceStaffController(MaintenanceStaffService maintenanceStaffService) {
        this.maintenanceStaffService = maintenanceStaffService;
    }

    /** 分页查询维修人员列表 */
    @GetMapping("/list")
    public Result<PageResult<MaintenanceStaff>> list(PageDTO pageDTO, @RequestParam Map<String, String> params) {
        return Result.success(maintenanceStaffService.pageList(pageDTO, params));
    }

    /** 根据ID查询维修人员详情 */
    @GetMapping("/{id}")
    public Result<MaintenanceStaff> getById(@PathVariable Long id) {
        MaintenanceStaff entity = maintenanceStaffService.getById(id);
        if (entity == null) return Result.notFound();
        return Result.success(entity);
    }

    /** 多条件查询维修人员（含自定义SQL） */
    @GetMapping("/vo/page")
    public Result<List<Map<String, Object>>> pageWithSql(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String workStatus) {
        return Result.success(maintenanceStaffService.queryStaffPage(name, department, position, workStatus));
    }

    /** 查询维修人员及其维修记录数量 */
    @GetMapping("/vo/record-count")
    public Result<List<Map<String, Object>>> withRecordCount(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String workStatus) {
        return Result.success(maintenanceStaffService.queryStaffWithRecordCount(department, workStatus));
    }

    /** 查询维修人员统计数据 */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(maintenanceStaffService.queryStaffStatistics());
    }

    /** 按部门分组统计维修人员 */
    @GetMapping("/group/department")
    public Result<List<Map<String, Object>>> groupByDepartment() {
        return Result.success(maintenanceStaffService.queryStaffGroupByDepartment());
    }

    /** 新增维修人员 */
    @PostMapping
    public Result<Void> create(@RequestBody MaintenanceStaff entity) {
        maintenanceStaffService.create(entity);
        return Result.success();
    }

    /** 更新维修人员 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MaintenanceStaff entity) {
        entity.setId(id);
        maintenanceStaffService.update(entity);
        return Result.success();
    }

    /** 删除维修人员 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        maintenanceStaffService.delete(id);
        return Result.success();
    }
}
