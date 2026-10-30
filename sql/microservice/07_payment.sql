-- =====================================================================
-- 07 缴费功能
--   1) electricity_bill 补「应缴 / 已缴 / 缴费时间 / 支付方式」四列
--   2) 新建 payment_record 缴费流水表（一账多次缴纳必须留痕）
--   3) 修正历史账单的金额口径（原种子把用电量抄进了欠费额）
--   4) 给演示客户造 2026 年 8/9 月账单，让「缴费」有真实对象可操作
--
-- 前置：01 / 03 / 06 已执行
-- 幂等：可重复执行
-- =====================================================================
USE power_billing;

-- ------------------------------------------------------------
-- 1. 补列（老库升级用；新库跑 03 时已经带上了这几列，此处自动跳过）
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS power_add_column;
DELIMITER $$
CREATE PROCEDURE power_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = p_table
                     AND COLUMN_NAME = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL power_add_column('electricity_bill', 'bill_amount',
    'bill_amount DECIMAL(15,2) NOT NULL DEFAULT 0 COMMENT ''本期应缴电费(元)'' AFTER total_consumption');
CALL power_add_column('electricity_bill', 'paid_amount',
    'paid_amount DECIMAL(15,2) NOT NULL DEFAULT 0 COMMENT ''累计已缴(元)'' AFTER bill_amount');
CALL power_add_column('electricity_bill', 'paid_time',
    'paid_time DATETIME NULL COMMENT ''最近一次缴费时间'' AFTER paid_amount');
CALL power_add_column('electricity_bill', 'payment_method',
    'payment_method VARCHAR(20) NULL COMMENT ''最近一次支付方式'' AFTER paid_time');

DROP PROCEDURE IF EXISTS power_add_column;

-- ------------------------------------------------------------
-- 2. 缴费流水
-- ------------------------------------------------------------
-- 为什么不把缴费信息只塞在账单行里：一张账单允许分多次缴纳（部分缴纳是业务常态），
-- 「最近一次」覆盖不了历史。流水表让客户能查缴费记录、管理员能对账。
CREATE TABLE IF NOT EXISTS payment_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_id BIGINT NOT NULL COMMENT '账单ID（power_billing 库内）',
    customer_id BIGINT NOT NULL COMMENT '缴费客户ID',
    amount DECIMAL(15,2) NOT NULL COMMENT '本次缴费金额(元)',
    payment_method VARCHAR(20) NOT NULL COMMENT '微信/支付宝/银行卡/现金',
    payment_no VARCHAR(64) NOT NULL COMMENT '交易流水号，前端展示用',
    payer_type VARCHAR(20) NOT NULL DEFAULT 'SELF' COMMENT 'SELF 客户自助 / AGENT 柜台代收',
    operator_id BIGINT COMMENT '代收操作人ID（自助缴费为空）',
    pay_time DATETIME NOT NULL,
    create_time DATETIME NOT NULL,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_bill (bill_id),
    KEY idx_deleted_customer_time (deleted, customer_id, pay_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缴费流水表';

-- ------------------------------------------------------------
-- 3. 电价数据补齐
-- ------------------------------------------------------------
-- 03 里这段 INSERT 依赖的列与建表语句对不上，导致表一直为空，
-- 账单金额没有可挂的电价。这里用固定 id 重插，可重复执行。
INSERT INTO price_standard (id, electricity_category, base_price, peak_price, valley_price,
                            effective_date, implementation_plan, preferential_policy, pricing_method, create_time)
VALUES
(1, '居民', 0.5283, 0.7283, 0.3283, '2024-01-01', '阶梯电价方案一', '低保户每月免费用电15kWh', '单一制', NOW()),
(2, '商业', 0.6852, 1.0252, 0.3852, '2024-01-01', '一般工商业电价', '夜间低谷用电优惠10%', '单一制', NOW()),
(3, '工业', 0.6258, 0.9858, 0.3158, '2024-01-01', '大工业电价方案', '两部制电价可选', '两部制', NOW()),
(4, '农业', 0.3158, 0.4858, 0.2158, '2024-06-01', '农业生产用电电价', '农业排灌用电优惠20%', '单一制', NOW())
ON DUPLICATE KEY UPDATE base_price = VALUES(base_price), update_time = NOW();

-- ------------------------------------------------------------
-- 4. 历史账单金额归位
-- ------------------------------------------------------------
-- 原始种子里 total_consumption 和 arrears_amount 是同一个数（例如 58600 kWh / 58600 元），
-- 等于把「用电量」当「金额」用。按居民电价重算应缴额，再按状态倒推已缴与欠费。
UPDATE electricity_bill
SET bill_amount = ROUND(total_consumption * 0.5283, 2)
WHERE deleted = 0 AND billing_year < 2026;

UPDATE electricity_bill
SET arrears_amount = CASE
        WHEN payment_status = '已缴' THEN 0
        WHEN payment_status = '部分缴纳' THEN ROUND(bill_amount * 0.6, 2)
        ELSE bill_amount
    END,
    paid_amount = CASE
        WHEN payment_status = '已缴' THEN bill_amount
        WHEN payment_status = '部分缴纳' THEN bill_amount - ROUND(bill_amount * 0.6, 2)
        ELSE 0
    END,
    paid_time = CASE WHEN payment_status = '已缴' THEN generate_time ELSE NULL END,
    payment_method = CASE WHEN payment_status = '已缴' THEN '银行卡' ELSE NULL END
WHERE deleted = 0 AND billing_year < 2026;

-- ------------------------------------------------------------
-- 5. 演示客户 2026 年账单
-- ------------------------------------------------------------
-- 金额不是编的：直接按 electricity_daily_usage 里该表该月的真实电量 × 居民电价。
DELETE FROM electricity_bill WHERE customer_id = 1 AND billing_year = 2026;

-- 上面删掉的账单会留下孤儿流水（bill_id 已不存在），一并清掉，避免重复执行后越积越多
DELETE FROM payment_record
WHERE bill_id NOT IN (SELECT id FROM electricity_bill WHERE deleted = 0);

-- 8 月：两块表都已结清
INSERT INTO electricity_bill
    (billing_year, total_consumption, bill_amount, paid_amount, paid_time, payment_method,
     arrears_amount, payment_status, generate_time, reminder_count, payment_deadline,
     current_reading, previous_reading, meter_reading_time, meter_id, customer_id,
     create_time, update_time, deleted)
SELECT 2026,
       u.usage_total,
       u.amount,
       u.amount,
       '2026-09-06 19:42:00',
       '微信',
       0,
       '已缴',
       '2026-09-01 08:00:00',
       0,
       '2026-09-15',
       u.base + u.usage_total,
       u.base,
       '2026-08-31 23:30:00',
       u.meter_id,
       1,
       '2026-09-01 08:00:00',
       '2026-09-06 19:42:00',
       0
FROM (SELECT meter_id,
             CASE meter_id WHEN 1 THEN 20000 ELSE 5000 END AS base,
             ROUND(SUM(total_usage), 2) AS usage_total,
             ROUND(SUM(total_usage) * 0.5283, 2) AS amount
      FROM electricity_daily_usage
      WHERE deleted = 0 AND customer_id = 1
        AND usage_date >= '2026-08-01' AND usage_date < '2026-09-01'
      GROUP BY meter_id) u;

-- 9 月：1 号表只交了一部分（欠 60%），6 号表整月未交且已过期限 → 逾期
INSERT INTO electricity_bill
    (billing_year, total_consumption, bill_amount, paid_amount, paid_time, payment_method,
     arrears_amount, payment_status, generate_time, reminder_count, payment_deadline,
     current_reading, previous_reading, meter_reading_time, meter_id, customer_id,
     create_time, update_time, deleted)
SELECT 2026,
       u.usage_total,
       u.amount,
       CASE WHEN u.meter_id = 1 THEN u.amount - ROUND(u.amount * 0.6, 2) ELSE 0 END,
       CASE WHEN u.meter_id = 1 THEN '2026-10-18 09:15:00' ELSE NULL END,
       CASE WHEN u.meter_id = 1 THEN '支付宝' ELSE NULL END,
       CASE WHEN u.meter_id = 1 THEN ROUND(u.amount * 0.6, 2) ELSE u.amount END,
       CASE WHEN u.meter_id = 1 THEN '部分缴纳' ELSE '逾期' END,
       '2026-10-01 08:00:00',
       CASE WHEN u.meter_id = 1 THEN 1 ELSE 2 END,
       '2026-10-25',
       u.base + u.usage_total,
       u.base,
       '2026-09-30 23:30:00',
       u.meter_id,
       1,
       '2026-10-01 08:00:00',
       '2026-10-18 09:15:00',
       0
FROM (SELECT meter_id,
             CASE meter_id WHEN 1 THEN 20000 + 3340.84 ELSE 5000 + 1444.31 END AS base,
             ROUND(SUM(total_usage), 2) AS usage_total,
             ROUND(SUM(total_usage) * 0.5283, 2) AS amount
      FROM electricity_daily_usage
      WHERE deleted = 0 AND customer_id = 1
        AND usage_date >= '2026-09-01' AND usage_date < '2026-10-01'
      GROUP BY meter_id) u;

-- 已缴 / 部分缴纳的账单补一条流水，保证「缴费记录」从历史起就是完整的。
-- paid_time 可能为空（历史数据没记录缴费时刻），用 update_time、generate_time 依次兜底，
-- 否则 DATE_FORMAT(NULL) 会让 payment_no 变成 NULL 而插入失败。
INSERT INTO payment_record (bill_id, customer_id, amount, payment_method, payment_no,
                            payer_type, operator_id, pay_time, create_time, deleted)
SELECT b.id, b.customer_id, b.paid_amount,
       COALESCE(b.payment_method, '银行卡'),
       CONCAT('PAY',
              DATE_FORMAT(COALESCE(b.paid_time, b.update_time, b.generate_time), '%Y%m%d%H%i%s'),
              LPAD(b.id, 4, '0')),
       'SELF', NULL,
       COALESCE(b.paid_time, b.update_time, b.generate_time),
       COALESCE(b.paid_time, b.update_time, b.generate_time),
       0
FROM electricity_bill b
WHERE b.deleted = 0 AND b.paid_amount > 0
  AND NOT EXISTS (SELECT 1 FROM payment_record p WHERE p.bill_id = b.id AND p.deleted = 0);

-- ------------------------------------------------------------
-- 6. 自检
-- ------------------------------------------------------------
SELECT '=== 客户 1 的账单 ===' AS section;
SELECT id, billing_year, total_consumption AS kwh, bill_amount, paid_amount,
       arrears_amount, payment_status, payment_deadline
FROM electricity_bill
WHERE customer_id = 1 AND deleted = 0
ORDER BY billing_year DESC, id;

SELECT '=== 待缴合计 ===' AS section;
SELECT COUNT(*) AS unpaid_bills, ROUND(SUM(arrears_amount), 2) AS arrears_total
FROM electricity_bill
WHERE customer_id = 1 AND deleted = 0 AND payment_status IN ('未缴', '逾期', '部分缴纳');

SELECT '=== 流水条数 ===' AS section;
SELECT COUNT(*) AS payment_records FROM payment_record WHERE deleted = 0;
