package com.power.alert.controller;

import com.power.alert.feign.BillingClient;
import com.power.alert.feign.DeviceClient;
import com.power.alert.mapper.MaintenanceRecordMapper;
import com.power.common.pojo.Result;
import com.power.common.security.CurrentUserContext;
import com.power.common.security.DataScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * 总览页控制器
 *
 * <p><b>这一版与上一版的根本区别：页面上不再有任何"造出来"的数字。</b>
 * 上一版有两条曲线是假的——负荷曲线用"月总电量 ÷ 30 ÷ 24 × 典型系数"折算，
 * 功率因数曲线干脆是 {@code new Random(123)} 生成的固定序列，库里根本没这张表。
 * 那种"看起来在动、其实是常数"的图比不画更糟：演示时一旦被问"这数哪来的"就答不上来。</p>
 *
 * <p>现在的做法：</p>
 * <ul>
 *   <li><b>日负荷曲线</b> → {@code electricity_daily_usage} 真表，90 天逐日电量；</li>
 *   <li><b>功率因数图</b> → 删除。数据模型里没有三相功率因数采集，宁可空着也不画假的；</li>
 *   <li><b>尖峰平谷构成</b> → 同一张表的四段电量聚合，是真实存在的字段；</li>
 *   <li><b>KPI</b> → 一次 Feign 批量调用拿设备/变电站/电表三类统计，不再串行发三次请求。</li>
 * </ul>
 *
 * <p><b>角色差异</b>：客户进来看到的是"我的用电 + 我的账单"，
 * 运维看到的是"设备健康 + 待处理工单"，总负责人看到全站汇总。
 * 差异在服务端判定，不是前端藏菜单。</p>
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DeviceClient deviceClient;
    private final BillingClient billingClient;
    private final MaintenanceRecordMapper maintenanceMapper;

    public DashboardController(DeviceClient deviceClient,
                               BillingClient billingClient,
                               MaintenanceRecordMapper maintenanceMapper) {
        this.deviceClient = deviceClient;
        this.billingClient = billingClient;
        this.maintenanceMapper = maintenanceMapper;
    }

    /**
     * 当前登录人的视图配置
     *
     * <p>前端拿这一份决定：标题叫什么、显示哪几张卡、要不要渲染"全站"类图表。
     * 这样做的好处是前端不需要自己判断角色——角色变了，前端一行不用改。</p>
     */
    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        CurrentUserContext.CurrentUser user = CurrentUserContext.get();
        Map<String, Object> data = new LinkedHashMap<>();
        if (user == null) {
            data.put("role", "ANONYMOUS");
            data.put("title", "运行概览");
            data.put("scopeName", "未知身份");
            return Result.success(data);
        }
        data.put("role", user.role());
        data.put("username", user.username());
        switch (user.role() == null ? "" : user.role().toUpperCase()) {
            case "CUSTOMER" -> {
                data.put("title", "我的用电");
                data.put("scopeName", "当前查看：本户");
                data.put("showEquipment", false);
                data.put("showBilling", true);
            }
            case "OPERATOR" -> {
                data.put("title", "运维工作台");
                data.put("scopeName", "当前查看：全站设备与工单");
                data.put("showEquipment", true);
                data.put("showBilling", false);
            }
            default -> {
                data.put("title", "运行概览");
                data.put("scopeName", "当前查看：全站");
                data.put("showEquipment", true);
                data.put("showBilling", true);
            }
        }
        return Result.success(data);
    }

    /**
     * KPI 指标
     *
     * <p>按角色分流，不再所有人看同一组数字：</p>
     * <ul>
     *   <li>客户：近 30 天用电量、电费、欠费、最近一次抄表日</li>
     *   <li>运维/管理员：设备总数、变电站运行率、今日告警、待处理告警</li>
     * </ul>
     */
    @GetMapping("/kpi")
    public Result<Map<String, Object>> kpi() {
        CurrentUserContext.CurrentUser user = CurrentUserContext.get();
        if (user != null && user.isCustomer()) {
            return Result.success(customerKpi());
        }
        return Result.success(operationsKpi());
    }

    /** 运维/管理层 KPI：设备、变电站、告警三类 */
    private Map<String, Object> operationsKpi() {
        Map<String, Object> data = new LinkedHashMap<>();

        // 一次调用拿到设备、变电站、电表三组统计，替代原来的三次串行 Feign
        Map<String, Object> summary = unwrap(deviceClient.operationsSummary());

        long totalEquipment = num(summary, "totalEquipment");
        long totalSubstations = num(summary, "totalSubstations");
        long onlineSubstations = num(summary, "runningSubstations");
        long totalMeters = num(summary, "totalMeters");
        long normalMeters = num(summary, "normalMeters");
        long abnormalMeters = totalMeters - normalMeters;

        LocalDate today = LocalDate.now();
        long todayAlerts = maintenanceMapper.countTodayAlerts(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        long pendingAlerts = maintenanceMapper.countPendingAlerts();

        double onlineRate = totalSubstations > 0
                ? BigDecimal.valueOf(onlineSubstations).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalSubstations), 1, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        data.put("totalEquipment", String.valueOf(totalEquipment));
        data.put("onlineRate", onlineRate + "%");
        data.put("todayAlerts", String.valueOf(todayAlerts));
        data.put("pendingAlerts", String.valueOf(pendingAlerts));
        data.put("abnormalMeters", String.valueOf(abnormalMeters));

        // 原始的数值也一并给前端，进度条/趋势线要用数字而不是带 % 的字符串
        data.put("raw", Map.of(
                "totalEquipment", totalEquipment,
                "totalSubstations", totalSubstations,
                "onlineSubstations", onlineSubstations,
                "totalMeters", totalMeters,
                "abnormalMeters", abnormalMeters,
                "onlineRate", onlineRate
        ));
        return data;
    }

    /**
     * 客户 KPI：只算自己那一户
     *
     * <p>数据经 billing 服务的 {@code /usage/summary} 和 {@code /bill/statistics} 取，
     * 这两个接口都按网关透传的 {@code X-Customer-Id} 做范围过滤，
     * <b>这里不传任何"我要看谁"的参数</b>——传了也没用，服务端只认身份头。</p>
     */
    private Map<String, Object> customerKpi() {
        Map<String, Object> data = new LinkedHashMap<>();

        Map<String, Object> usageSummary = unwrap(billingClient.usageSummary(30));
        Map<String, Object> current = asMap(usageSummary.get("current"));
        double totalUsage = toDouble(current.get("totalUsage"));
        double avgDaily = toDouble(current.get("avgDaily"));

        // 账单概况（同样是按身份过滤后的结果）
        double arrears = 0.0;
        int unpaidCount = 0;
        Result<List<Map<String, Object>>> statResult = billingClient.billStatistics();
        if (statResult != null && statResult.getData() != null) {
            for (Map<String, Object> row : statResult.getData()) {
                arrears += toDouble(row.get("totalArrears"));
                unpaidCount += (int) (toDouble(row.get("unpaid")) + toDouble(row.get("overdue")));
            }
        }

        data.put("monthUsage", fmt(totalUsage));
        data.put("avgDaily", fmt(avgDaily));
        data.put("arrears", fmt(arrears));
        data.put("unpaidBills", String.valueOf(unpaidCount));
        data.put("raw", Map.of(
                "monthUsage", totalUsage,
                "avgDaily", avgDaily,
                "arrears", arrears,
                "unpaidBills", unpaidCount
        ));
        return data;
    }

    /** 最近告警：客户不显示（与他无关），运维与管理员显示 */
    @GetMapping("/alerts")
    public Result<List<Map<String, Object>>> alerts() {
        CurrentUserContext.CurrentUser user = CurrentUserContext.get();
        if (user != null && user.isCustomer()) {
            return Result.success(List.of());
        }
        return Result.success(maintenanceMapper.selectRecentAlerts(20));
    }

    /**
     * 日用电曲线 —— <b>真实数据</b>
     *
     * <p>数据源：{@code electricity_daily_usage}，按日把当天所有电表的电量加总。
     * 客户进来时只统计自己那一户（范围由 billing 服务按身份头判定）。</p>
     *
     * <p>参数 {@code days} 决定窗口长度（7/30/90），前端切换按钮直接改它。</p>
     */
    @GetMapping("/load-chart")
    public Result<Map<String, Object>> loadChart(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "30") int days) {
        Result<List<Map<String, Object>>> usageResult = billingClient.dailyUsage(days);

        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        List<Double> peak = new ArrayList<>();
        List<Double> valley = new ArrayList<>();

        if (usageResult != null && usageResult.getData() != null) {
            for (Map<String, Object> row : usageResult.getData()) {
                Object date = row.get("usageDate");
                labels.add(date == null ? "" : shortDate(date.toString()));
                values.add(toDouble(row.get("totalUsage")));
                peak.add(toDouble(row.get("peakUsage")));
                valley.add(toDouble(row.get("valleyUsage")));
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("labels", labels);
        data.put("values", values);
        data.put("peak", peak);
        data.put("valley", valley);
        data.put("days", days);
        data.put("source", "electricity_daily_usage");
        return Result.success(data);
    }

    /**
     * 尖峰平谷电量构成 —— <b>真实数据</b>
     *
     * <p>替代原来的"三相功率因数曲线"。功率因数在数据模型里没有采集表，
     * 画出来只能是随机数；而尖峰平谷是库里实实在在的四个字段，
     * 对客户还有直接意义——电费就是按这四段电价算出来的。</p>
     */
    @GetMapping("/tou-chart")
    public Result<Map<String, Object>> touChart(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "30") int days) {
        Map<String, Object> split = unwrapRaw(billingClient.usageTouSplit(days));

        double peak = toDouble(split.get("peakUsage"));
        double high = toDouble(split.get("highUsage"));
        double flat = toDouble(split.get("flatUsage"));
        double valley = toDouble(split.get("valleyUsage"));

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("尖峰", peak));
        items.add(item("高峰", high));
        items.add(item("平段", flat));
        items.add(item("低谷", valley));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", fmt(peak + high + flat + valley));
        data.put("days", days);
        data.put("source", "electricity_daily_usage");
        return Result.success(data);
    }

    /**
     * 设备健康分布 —— <b>真实数据</b>
     *
     * <p>按危害等级统计设备台数，用于运维视角的"哪类设备风险最集中"。
     * 客户不返回（看不到别人的设备台账）。</p>
     */
    @GetMapping("/equipment-health")
    public Result<List<Map<String, Object>>> equipmentHealth() {
        CurrentUserContext.CurrentUser user = CurrentUserContext.get();
        if (user != null && user.isCustomer()) {
            return Result.success(List.of());
        }
        Result<List<Map<String, Object>>> group = deviceClient.equipmentGroupByHazard();
        if (group == null || group.getData() == null) {
            return Result.success(List.of());
        }
        return Result.success(group.getData());
    }

    /** 取出 Feign 响应里的 data；服务降级时返回空 Map 而不是 null */
    private Map<String, Object> unwrap(Result<Map<String, Object>> result) {
        if (result == null || result.getData() == null) {
            return Map.of();
        }
        return result.getData();
    }

    private Map<String, Object> unwrapRaw(Result<Map<String, Object>> result) {
        return unwrap(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object v) {
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private long num(Map<String, Object> stat, String key) {
        Object v = stat.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** 千分位保留一位小数 */
    private String fmt(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private Map<String, Object> item(String name, double value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    /** 2026-10-29 -> 10-29，横轴标签用，太长会挤成一团 */
    private String shortDate(String iso) {
        return iso.length() >= 10 ? iso.substring(5, 10) : iso;
    }
}
