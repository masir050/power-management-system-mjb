package com.power.billing.controller;

import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.billing.pojo.PriceStandard;
import com.power.common.pojo.Result;
import com.power.billing.service.PriceStandardService;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 电价标准控制器，提供电价标准的分页查询、详情、统计及增删改查接口 */
@RestController
@RequestMapping("/api/price")
public class PriceStandardController {

    private final PriceStandardService priceStandardService;

    public PriceStandardController(PriceStandardService priceStandardService) {
        this.priceStandardService = priceStandardService;
    }

    /** 分页查询电价标准列表 */
    @GetMapping("/list")
    public Result<PageResult<PriceStandard>> list(PageDTO pageDTO, @RequestParam Map<String, String> params) {
        return Result.success(priceStandardService.pageList(pageDTO, params));
    }

    /** 根据ID查询电价标准详情 */
    @GetMapping("/{id}")
    public Result<PriceStandard> getById(@PathVariable Long id) {
        PriceStandard entity = priceStandardService.getById(id);
        if (entity == null) return Result.notFound();
        return Result.success(entity);
    }

    /** 多条件查询电价标准（含自定义SQL） */
    @GetMapping("/vo/page")
    public Result<List<Map<String, Object>>> pageWithSql(
            @RequestParam(required = false) String electricityCategory,
            @RequestParam(required = false) String pricingMethod,
            @RequestParam(required = false) LocalDate effectiveDate) {
        return Result.success(priceStandardService.queryPricePage(electricityCategory, pricingMethod, effectiveDate));
    }

    /** 查询指定日期生效的电价标准 */
    @GetMapping("/effective")
    public Result<List<Map<String, Object>>> effectivePrices(
            @RequestParam(required = false) LocalDate queryDate) {
        if (queryDate == null) queryDate = LocalDate.now();
        return Result.success(priceStandardService.queryEffectivePrices(queryDate));
    }

    /** 查询电价标准统计数据 */
    @GetMapping("/statistics")
    public Result<List<Map<String, Object>>> statistics() {
        return Result.success(priceStandardService.queryPriceStatistics());
    }

    /** 新增电价标准 */
    @PostMapping
    public Result<Void> create(@RequestBody PriceStandard entity) {
        priceStandardService.create(entity);
        return Result.success();
    }

    /** 更新电价标准 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PriceStandard entity) {
        entity.setId(id);
        priceStandardService.update(entity);
        return Result.success();
    }

    /** 删除电价标准 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        priceStandardService.delete(id);
        return Result.success();
    }
}
