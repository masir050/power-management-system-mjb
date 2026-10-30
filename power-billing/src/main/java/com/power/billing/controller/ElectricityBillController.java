package com.power.billing.controller;

import com.power.billing.pojo.ElectricityBill;
import com.power.billing.pojo.PayRequest;
import com.power.billing.pojo.PaymentRecord;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.pojo.Result;
import com.power.billing.service.ElectricityBillService;
import com.power.billing.service.PaymentService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 电费账单控制器，提供电费账单的分页查询、详情、统计及增删改查接口 */
@RestController
@RequestMapping("/api/bill")
public class ElectricityBillController {

    private final ElectricityBillService electricityBillService;
    private final PaymentService paymentService;

    public ElectricityBillController(ElectricityBillService electricityBillService,
                                     PaymentService paymentService) {
        this.electricityBillService = electricityBillService;
        this.paymentService = paymentService;
    }

    /** 分页查询电费账单列表 */
    @GetMapping("/list")
    public Result<PageResult<ElectricityBill>> list(PageDTO pageDTO, @RequestParam Map<String, String> params) {
        return Result.success(electricityBillService.pageList(pageDTO, params));
    }

    /** 根据ID查询电费账单详情 */
    @GetMapping("/{id}")
    public Result<ElectricityBill> getById(@PathVariable Long id) {
        ElectricityBill entity = electricityBillService.getById(id);
        if (entity == null) return Result.notFound();
        return Result.success(entity);
    }

    /** 查询电费账单及其详细明细 */
    @GetMapping("/vo/{id}")
    public Result<Map<String, Object>> getWithDetails(@PathVariable Long id) {
        Map<String, Object> data = electricityBillService.queryBillWithDetails(id);
        if (data == null) return Result.notFound();
        return Result.success(data);
    }

    /** 多条件查询电费账单（含自定义SQL） */
    @GetMapping("/vo/page")
    public Result<List<Map<String, Object>>> pageWithSql(
            @RequestParam(required = false) Integer billingYear,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) Long meterId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Double minConsumption,
            @RequestParam(required = false) Double maxConsumption) {
        return Result.success(electricityBillService.queryBillPage(billingYear, paymentStatus, meterId, customerName, minConsumption, maxConsumption));
    }

    /** 查询未缴费账单 */
    @GetMapping("/unpaid")
    public Result<List<Map<String, Object>>> unpaidBills(
            @RequestParam(required = false) Integer billingYear,
            @RequestParam(required = false) String customerName) {
        return Result.success(electricityBillService.queryUnpaidBills(billingYear, customerName));
    }

    /** 查询电费账单统计数据 */
    @GetMapping("/statistics")
    public Result<List<Map<String, Object>>> statistics() {
        return Result.success(electricityBillService.queryBillStatistics());
    }

    /** 查询欠费分布情况 */
    @GetMapping("/arrears-distribution")
    public Result<List<Map<String, Object>>> arrearsDistribution() {
        return Result.success(electricityBillService.queryArrearsDistribution());
    }

    /** 新增电费账单 */
    @PostMapping
    public Result<Void> create(@RequestBody ElectricityBill entity) {
        electricityBillService.create(entity);
        return Result.success();
    }

    /** 更新电费账单 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ElectricityBill entity) {
        entity.setId(id);
        electricityBillService.update(entity);
        return Result.success();
    }

    /** 删除电费账单 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        electricityBillService.delete(id);
        return Result.success();
    }

    /**
     * 缴费
     *
     * <p>客户自助缴费与管理员柜台代收走同一个接口，身份差异在服务层处理：
     * 客户只能缴自己名下的账单，运维被拒绝。金额不传表示一次结清。</p>
     */
    @PostMapping("/{id}/pay")
    public Result<Map<String, Object>> pay(@PathVariable Long id,
                                           @RequestBody(required = false) PayRequest req) {
        return Result.success(paymentService.pay(id, req));
    }

    /** 某张账单的缴费记录 */
    @GetMapping("/{id}/payments")
    public Result<List<PaymentRecord>> payments(@PathVariable Long id) {
        return Result.success(paymentService.listByBill(id));
    }

    /** 当前客户最近的缴费记录（首页/账单页的"最近缴费"用） */
    @GetMapping("/payments/recent")
    public Result<List<PaymentRecord>> recentPayments(
            @RequestParam(defaultValue = "5") int limit) {
        return Result.success(paymentService.recentForCurrentCustomer(limit));
    }
}
