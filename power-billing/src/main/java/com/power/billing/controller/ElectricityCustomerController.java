package com.power.billing.controller;

import com.power.billing.pojo.ElectricityCustomer;
import com.power.billing.pojo.ElectricityCustomerVO;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.pojo.Result;
import com.power.billing.service.ElectricityCustomerService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 用电客户控制器，提供用电客户的分页查询、详情、统计及增删改查接口 */
@RestController
@RequestMapping("/api/customer")
public class ElectricityCustomerController {

    private final ElectricityCustomerService electricityCustomerService;

    public ElectricityCustomerController(ElectricityCustomerService electricityCustomerService) {
        this.electricityCustomerService = electricityCustomerService;
    }

    /** 分页查询用电客户列表 */
    @GetMapping("/list")
    public Result<PageResult<ElectricityCustomer>> list(PageDTO pageDTO, @RequestParam Map<String, String> params) {
        return Result.success(electricityCustomerService.pageList(pageDTO, params));
    }

    /** 根据ID查询用电客户详情 */
    @GetMapping("/{id}")
    public Result<ElectricityCustomer> getById(@PathVariable Long id) {
        ElectricityCustomer entity = electricityCustomerService.getById(id);
        if (entity == null) return Result.notFound();
        return Result.success(entity);
    }

    /** 查询用电客户及其关联电表 */
    @GetMapping("/vo/{id}")
    public Result<List<Map<String, Object>>> getWithMeters(@PathVariable Long id) {
        return Result.success(electricityCustomerService.queryCustomerWithMeters(id));
    }

    /** 多条件查询用电客户（含自定义SQL） */
    @GetMapping("/vo/page")
    public Result<List<ElectricityCustomerVO>> pageWithSql(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String electricityCategory,
            @RequestParam(required = false) String electricityStatus,
            @RequestParam(required = false) String contact) {
        return Result.success(electricityCustomerService.queryCustomerPage(customerName, electricityCategory, electricityStatus, contact));
    }

    /** 查询用电客户统计数据 */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(electricityCustomerService.queryCustomerStatistics());
    }

    /** 按用电类别分组统计用电客户 */
    @GetMapping("/group/category")
    public Result<List<Map<String, Object>>> groupByCategory() {
        return Result.success(electricityCustomerService.queryCustomerGroupByCategory());
    }

    /** 新增用电客户 */
    @PostMapping
    public Result<Void> create(@RequestBody ElectricityCustomer entity) {
        electricityCustomerService.create(entity);
        return Result.success();
    }

    /** 更新用电客户 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ElectricityCustomer entity) {
        entity.setId(id);
        electricityCustomerService.update(entity);
        return Result.success();
    }

    /** 删除用电客户 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        electricityCustomerService.delete(id);
        return Result.success();
    }
}
