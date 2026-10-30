-- ============================================================
-- 索引有效性验证（EXPLAIN）
--
-- 用法：mysql -uroot -p < sql/microservice/05_explain_check.sql
-- 目的：证明建出来的联合索引确实被优化器采用，而不是只"写了好看"。
-- 判读标准（每一段都写在注释里）：
--   type 为 ref / range 或更好；key 列出现预期索引名；
--   rows 明显小于表总行数；Extra 中不出现 "Using filesort"（对带 ORDER BY 的查询）。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 告警列表：未处理告警按等级 + 时间倒序
--    预期：key = idx_deleted_status_level_time，type = ref，无 filesort
-- ------------------------------------------------------------
USE power_alert;
EXPLAIN FORMAT=TRADITIONAL
SELECT id, equipment_name, alert_level, occurrence_time
FROM alert
WHERE deleted = 0 AND alert_status = 'PENDING'
ORDER BY occurrence_time DESC
LIMIT 20;

-- ------------------------------------------------------------
-- 2. 仪表盘"今日告警数"：occurrence_time 区间计数
--    预期：key = idx_deleted_occurrence，type = range
-- ------------------------------------------------------------
EXPLAIN FORMAT=TRADITIONAL
SELECT COUNT(*) FROM maintenance_record
WHERE deleted = 0
  AND occurrence_time >= '2025-05-01 00:00:00'
  AND occurrence_time <  '2025-06-01 00:00:00';

-- ------------------------------------------------------------
-- 3. 本地消息表补偿任务：抢占待投递消息
--    预期：key = idx_status_retry_time，type = range，rows 很小
-- ------------------------------------------------------------
EXPLAIN FORMAT=TRADITIONAL
SELECT id, message_id, exchange_name, routing_key, payload, retry_count
FROM local_message
WHERE status = 'PENDING' AND next_retry_time <= NOW()
ORDER BY id
LIMIT 100;

-- ------------------------------------------------------------
-- 4. 账单列表：年份 + 缴费状态，按创建时间排序
--    预期：key = idx_deleted_year_status_create，无 filesort
-- ------------------------------------------------------------
USE power_billing;
EXPLAIN FORMAT=TRADITIONAL
SELECT id, billing_year, total_consumption, arrears_amount
FROM electricity_bill
WHERE deleted = 0 AND billing_year = 2025 AND payment_status = '未缴'
ORDER BY create_time DESC
LIMIT 20;

-- ------------------------------------------------------------
-- 5. 欠费催缴：未缴类状态按欠费金额排序
--    预期：key = idx_deleted_status_arrears（可用到 arrears_amount 的有序性）
-- ------------------------------------------------------------
EXPLAIN FORMAT=TRADITIONAL
SELECT id, arrears_amount FROM electricity_bill
WHERE deleted = 0 AND payment_status IN ('未缴', '部分缴纳', '逾期')
ORDER BY arrears_amount ASC;

-- ------------------------------------------------------------
-- 6. 设备列表：类型 + 危险等级
--    预期：key = idx_deleted_type_hazard，type = ref
-- ------------------------------------------------------------
USE power_device;
EXPLAIN FORMAT=TRADITIONAL
SELECT id, equipment_name, hazard_level
FROM power_equipment
WHERE deleted = 0 AND equipment_type = '变压器' AND hazard_level = '一级';

-- ------------------------------------------------------------
-- 7. 变电站按状态筛选 + 电压等级分组
--    预期：key = idx_deleted_status_voltage，type = ref
-- ------------------------------------------------------------
EXPLAIN FORMAT=TRADITIONAL
SELECT voltage_level, COUNT(*) FROM substation
WHERE deleted = 0 AND run_status = '运行中'
GROUP BY voltage_level;

-- ------------------------------------------------------------
-- 8. 确认各表实际索引清单
-- ------------------------------------------------------------
SELECT TABLE_SCHEMA, TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA IN ('power_auth', 'power_device', 'power_billing', 'power_alert')
  AND INDEX_NAME <> 'PRIMARY'
GROUP BY TABLE_SCHEMA, TABLE_NAME, INDEX_NAME
ORDER BY TABLE_SCHEMA, TABLE_NAME, INDEX_NAME;
