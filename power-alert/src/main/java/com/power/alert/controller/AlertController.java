package com.power.alert.controller;

import com.power.alert.pojo.AlertRecord;
import com.power.alert.service.AlertService;
import com.power.alert.service.AlertTxService;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.pojo.Result;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警控制器
 *
 * <p>创建与状态流转走 {@link AlertService}（事务外做远程补全）；
 * 查询类接口走 {@link AlertTxService}（只读，无事务语义需求）。</p>
 */
@RestController
@RequestMapping("/api/alert")
public class AlertController {

    private final AlertService alertService;
    private final AlertTxService alertTxService;

    public AlertController(AlertService alertService, AlertTxService alertTxService) {
        this.alertService = alertService;
        this.alertTxService = alertTxService;
    }

    /** 分页查询告警列表 */
    @GetMapping("/list")
    public Result<PageResult<AlertRecord>> list(
            PageDTO pageDTO,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String alertStatus,
            @RequestParam(required = false) String faultType,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(alertTxService.queryAlertPage(
                pageDTO, alertLevel, alertStatus, faultType, equipmentId, startTime, endTime));
    }

    /** 告警详情 */
    @GetMapping("/{id}")
    public Result<AlertRecord> getById(@PathVariable Long id) {
        AlertRecord entity = alertTxService.getById(id);
        if (entity == null) {
            return Result.notFound();
        }
        return Result.success(entity);
    }

    /**
     * 新增告警
     * <p>落库与消息登记在同一事务内完成，随后由补偿任务异步投递到 MQ。</p>
     */
    @PostMapping
    public Result<Long> create(@RequestBody AlertRecord entity) {
        return Result.success(alertService.createAlert(entity));
    }

    /** 确认告警（PENDING → ACKNOWLEDGED） */
    @PutMapping("/{id}/acknowledge")
    public Result<Void> acknowledge(@PathVariable Long id,
                                    @RequestParam Long handlerId) {
        alertTxService.changeStatus(id, AlertRecord.STATUS_ACKNOWLEDGED, handlerId, null);
        return Result.success();
    }

    /** 处理告警（→ RESOLVED） */
    @PutMapping("/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id,
                                @RequestParam Long handlerId,
                                @RequestParam String handleResult) {
        alertTxService.changeStatus(id, AlertRecord.STATUS_RESOLVED, handlerId, handleResult);
        return Result.success();
    }

    /** 告警统计 */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(alertTxService.queryStatistics());
    }

    /** 按故障类型分组统计 */
    @GetMapping("/group/fault-type")
    public Result<List<Map<String, Object>>> groupByFaultType() {
        return Result.success(alertTxService.queryGroupByFaultType());
    }

    /** 按小时统计告警趋势（默认最近 24 小时） */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "24") int hours) {
        return Result.success(alertTxService.queryHourlyTrend(hours));
    }

    /** 近期告警 */
    @GetMapping("/recent")
    public Result<List<Map<String, Object>>> recent(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(alertTxService.queryRecentAlerts(limit));
    }
}
