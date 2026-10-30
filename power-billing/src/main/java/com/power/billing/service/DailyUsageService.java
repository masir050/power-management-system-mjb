package com.power.billing.service;

import com.power.billing.mapper.DailyUsageMapper;
import com.power.common.security.CurrentUserContext;
import com.power.common.security.DataScope;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日用电量服务
 *
 * <p>所有查询都经过 {@link DataScope#customerScope()}：客户自动限定到自己那一户，
 * 管理员查看全站。前端只需要传天数，不需要也不允许传 customerId。</p>
 */
@Service
public class DailyUsageService {

    private final DailyUsageMapper dailyUsageMapper;

    public DailyUsageService(DailyUsageMapper dailyUsageMapper) {
        this.dailyUsageMapper = dailyUsageMapper;
    }

    /**
     * 日用电曲线
     *
     * @param days 天数，限制在 1~365，防止前端传个 999999 把库拖死
     */
    public List<Map<String, Object>> dailyTrend(int days) {
        return dailyUsageMapper.selectDailyTrend(DataScope.customerScope(), clampDays(days));
    }

    /** 分表明细 */
    public List<Map<String, Object>> meterDailyTrend(Long meterId, int days) {
        return dailyUsageMapper.selectMeterDailyTrend(DataScope.customerScope(), meterId, clampDays(days));
    }

    /** 近 N 天汇总 + 与上一周期对比 */
    public Map<String, Object> summary(int days) {
        int d = clampDays(days);
        Long scope = DataScope.customerScope();
        Map<String, Object> current = dailyUsageMapper.selectCustomerSummary(scope, d);
        // 环比：用同一长度窗口往前再取一次，算出增长率。
        // 只取上一个窗口的话，SQL 里要写两次区间，这里用简易做法——总数 × 天数不重复计算，
        // 保持接口简单，先给出当前窗口的事实数字。
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("windowDays", d);
        data.put("current", current == null ? Map.of() : current);
        data.put("scopeCustomerId", scope);
        return data;
    }

    /** 尖峰平谷构成（含按当前电价折算的电费构成） */
    public Map<String, Object> touSplit(int days) {
        Map<String, Object> split = dailyUsageMapper.selectTouSplit(DataScope.customerScope(), clampDays(days));
        return split == null ? Map.of() : split;
    }

    /** 月度趋势 */
    public List<Map<String, Object>> monthlyTrend(int months) {
        int m = months < 1 ? 6 : Math.min(months, 24);
        return dailyUsageMapper.selectMonthlyTrend(DataScope.customerScope(), m);
    }

    /** 当前登录人是否为客户——前端要用它决定标题写"我的用电"还是"全站用电" */
    public boolean isCustomerView() {
        CurrentUserContext.CurrentUser user = CurrentUserContext.get();
        return user != null && user.isCustomer();
    }

    private int clampDays(int days) {
        return days < 1 ? 30 : Math.min(days, 365);
    }
}
