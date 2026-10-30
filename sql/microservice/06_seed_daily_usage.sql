-- ============================================================
-- 日用电量初始数据：6 块电表 × 最近 90 天
-- 执行：mysql -uroot -p < 06_seed_daily_usage.sql
--
-- 造数思路（不是随机数，是按业务规则推出来的）：
--   * 每块表的日均电量基准 = 该客户月度账单总电量 / 30。
--     居民 ~109kWh/天、工业 ~4883kWh/天、商业 ~419kWh/天、农业 ~30kWh/天，
--     量级差异来自客户类别本身，不是拍脑袋。
--   * 日内分段按各类型的典型负荷特性分配：工业三班倒全天均衡，
--     商业集中在营业时段（尖峰段占比高），居民集中在早晚，农业低谷段占比高。
--   * 叠加三层真实存在的波动：周末效应（工业降、商业升）、
--     季节性趋势（近 90 天逐日缓慢变化）、日间随机扰动（确定性伪随机，可重放）。
--   * 全部用确定性公式，重跑脚本得到同一份数据，便于对账。
-- ============================================================

USE power_billing;

DELETE FROM electricity_daily_usage;

-- 生成器：递归 CTE 造出 0..89 的天偏移，与 6 块表交叉连接
INSERT INTO electricity_daily_usage
    (meter_id, customer_id, usage_date, peak_usage, high_usage, flat_usage, valley_usage, create_time)
SELECT
    x.meter_id,
    x.customer_id,
    x.usage_date,
    ROUND(x.total * x.p_peak, 2),
    ROUND(x.total * x.p_high, 2),
    ROUND(x.total * x.p_flat, 2),
    -- 谷段用减法收尾，保证四段相加严格等于 total（避免分段舍入后合计对不上）
    ROUND(x.total - ROUND(x.total * x.p_peak, 2)
                 - ROUND(x.total * x.p_high, 2)
                 - ROUND(x.total * x.p_flat, 2), 2),
    NOW()
FROM (
    SELECT
        m.meter_id,
        m.customer_id,
        DATE_SUB(CURDATE(), INTERVAL d.d DAY) AS usage_date,
        CASE m.category WHEN '工业' THEN 0.18 WHEN '商业' THEN 0.24
                        WHEN '居民' THEN 0.15 ELSE 0.10 END AS p_peak,
        CASE m.category WHEN '工业' THEN 0.26 WHEN '商业' THEN 0.32
                        WHEN '居民' THEN 0.22 ELSE 0.18 END AS p_high,
        CASE m.category WHEN '工业' THEN 0.32 WHEN '商业' THEN 0.28
                        WHEN '居民' THEN 0.33 ELSE 0.30 END AS p_flat,
        ROUND(
            m.base
            -- 周末效应：工业周末减产 35%，商业周末上升 25%，居民周末上升 12%
            * CASE
                WHEN DAYOFWEEK(DATE_SUB(CURDATE(), INTERVAL d.d DAY)) IN (1, 7) THEN
                     CASE m.category WHEN '工业' THEN 0.65 WHEN '商业' THEN 1.25
                                     WHEN '居民' THEN 1.12 ELSE 1.05 END
                ELSE 1.0
              END
            -- 季节性趋势：90 天内从 0.92 缓慢抬到 1.08
            * (0.92 + 0.16 * (89 - d.d) / 89.0)
            -- 日间扰动：确定性伪随机，幅度 ±6%，同一天同表永远同一个值
            * (1 + 0.12 * (SIN(d.d * 12.9898 + m.meter_id * 78.233) * 43758.5453
                           - FLOOR(SIN(d.d * 12.9898 + m.meter_id * 78.233) * 43758.5453) - 0.5))
        , 2) AS total
    FROM (
        SELECT 1 AS meter_id, 1 AS customer_id, 109.4 AS base, '居民' AS category
        UNION ALL SELECT 2, 2, 4883.3, '工业'
        UNION ALL SELECT 3, 3,  418.7, '商业'
        UNION ALL SELECT 4, 4,   29.7, '农业'
        UNION ALL SELECT 5, 5,  120.0, '居民'
        UNION ALL SELECT 6, 1,   47.4, '居民'
    ) m
    CROSS JOIN (
        WITH RECURSIVE days AS (
            SELECT 0 AS d
            UNION ALL SELECT d + 1 FROM days WHERE d < 89
        )
        SELECT d FROM days
    ) d
) x;

-- 校验：行数应为 6 × 90 = 540，且合计与账单月度量级对得上
SELECT COUNT(*)            AS rows_total,
       COUNT(DISTINCT meter_id)   AS meters,
       COUNT(DISTINCT usage_date) AS days,
       ROUND(SUM(total_usage), 2) AS total_kwh,
       MIN(usage_date)            AS from_date,
       MAX(usage_date)            AS to_date
FROM electricity_daily_usage;
