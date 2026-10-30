-- ============================================================
-- power_billing 库 —— 电费核算服务专用
-- 服务：power-billing (8083)
-- 执行：mysql -uroot -p < 03_power_billing.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS power_billing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE power_billing;

DROP TABLE IF EXISTS electricity_bill;
DROP TABLE IF EXISTS electricity_customer;
DROP TABLE IF EXISTS price_standard;

-- ------------------------------------------------------------
-- 用电客户
-- ------------------------------------------------------------
CREATE TABLE electricity_customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    electricity_category VARCHAR(50) NOT NULL COMMENT '居民/商业/工业/农业',
    address VARCHAR(200),
    contact VARCHAR(50),
    electricity_status VARCHAR(20) NOT NULL COMMENT '正常用电/暂停/销户',
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    KEY idx_deleted_category (deleted, electricity_category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用电客户表';

-- ------------------------------------------------------------
-- 电费账单
-- ------------------------------------------------------------
CREATE TABLE electricity_bill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    billing_year INT NOT NULL COMMENT '计费年份',
    total_consumption DECIMAL(15,2) NOT NULL COMMENT '总用电量(kWh)',
    -- 金额三件套的关系必须写清，否则「缴了多少」永远算不出来：
    --   bill_amount    本期应缴 = 用电量 × 电价，出账时定死，不随后续缴费变动
    --   paid_amount    累计已缴 = 历次缴费之和
    --   arrears_amount 尚欠 = bill_amount - paid_amount，由服务端维护
    -- 只存 arrears_amount 是不够的：缴清后欠费归零，账单原始金额就丢了。
    bill_amount DECIMAL(15,2) NOT NULL DEFAULT 0 COMMENT '本期应缴电费(元)',
    paid_amount DECIMAL(15,2) NOT NULL DEFAULT 0 COMMENT '累计已缴(元)',
    paid_time DATETIME COMMENT '最近一次缴费时间',
    payment_method VARCHAR(20) COMMENT '最近一次支付方式(微信/支付宝/银行卡/现金)',
    arrears_amount DECIMAL(15,2) COMMENT '尚欠金额(元) = bill_amount - paid_amount',
    payment_status VARCHAR(20) NOT NULL COMMENT '已缴/未缴/部分缴纳/逾期',
    generate_time DATETIME NOT NULL,
    reminder_count INT DEFAULT 0 COMMENT '催缴次数',
    payment_deadline DATE COMMENT '缴费期限',
    current_reading DECIMAL(15,2) NOT NULL COMMENT '本期止码',
    previous_reading DECIMAL(15,2) NOT NULL COMMENT '上期止码',
    meter_reading_time DATETIME NOT NULL COMMENT '抄表时间',
    -- 指向 power_device 库的 smart_meter，跨库只存 ID，不建外键
    meter_id BIGINT NOT NULL,
    -- 冗余一份客户 ID：账单列表里「按客户查账单」是最高频的查询（客户登录看自己的账单），
    -- 若只存 meter_id，必须先去 device 库查该客户名下所有电表，再回 billing 库做 IN 查询，
    -- 两个库之间做分页无法保证正确。生成账单时从 meter 反查一次写入，属于有意的冗余。
    customer_id BIGINT NOT NULL COMMENT '用电客户ID（power_billing 库内关联）',
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    KEY idx_meter_id (meter_id),
    -- 客户自助视图主查询：我的账单，按年倒序
    KEY idx_deleted_customer_year (deleted, customer_id, billing_year),
    -- 账单列表页最常见的组合：按年份 + 缴费状态过滤，再按创建时间排序。
    -- 索引列顺序与 where 条件一致，排序也能直接用索引，避免 filesort。
    KEY idx_deleted_year_status_create (deleted, billing_year, payment_status, create_time),
    -- 欠费催缴页：只查未缴类状态并按欠费金额排序，用覆盖索引直接拿金额
    KEY idx_deleted_status_arrears (deleted, payment_status, arrears_amount),
    -- 按用电量区间筛选（minConsumption / maxConsumption）
    KEY idx_deleted_consumption (deleted, total_consumption)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电费账单表（meter_id 跨库逻辑关联）';

-- ------------------------------------------------------------
-- 电价标准
-- ------------------------------------------------------------
CREATE TABLE price_standard (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    electricity_category VARCHAR(50) NOT NULL COMMENT '居民/商业/工业/农业',
    base_price DECIMAL(10,4) NOT NULL COMMENT '基础电价(元/kWh)',
    peak_price DECIMAL(10,4) COMMENT '高峰电价',
    valley_price DECIMAL(10,4) COMMENT '低谷电价',
    effective_date DATE NOT NULL COMMENT '执行日期',
    implementation_plan VARCHAR(100) COMMENT '执行方案',
    preferential_policy TEXT COMMENT '优惠政策',
    pricing_method VARCHAR(50) COMMENT '单一制/两部制',
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    -- 查"某类用户当前生效的电价"：按类别 + 执行日期倒序取第一条
    KEY idx_deleted_category_date (deleted, electricity_category, effective_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电价标准表';

-- ------------------------------------------------------------
-- 日用电量（客户视图与首页负荷曲线的数据源）
-- ------------------------------------------------------------
-- 设计说明：
--   * 一块电表一天一行，含尖/峰/平/谷四段电量，总计 = 四段之和。
--   * (meter_id, usage_date) 唯一，保证重复导入不会翻倍，也天然成为最左索引，
--     查「某表最近 N 天」是一次范围扫描。
--   * customer_id 冗余：客户看自己的日用电时不必先查电表清单。
--   * 单位 kWh，DECIMAL 而不是 FLOAT——电量要参与计费，不能丢精度。
CREATE TABLE electricity_daily_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meter_id BIGINT NOT NULL COMMENT '电表ID（power_device 库）',
    customer_id BIGINT NOT NULL COMMENT '用电客户ID',
    usage_date DATE NOT NULL COMMENT '用电日期',
    peak_usage DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '尖峰段电量(kWh)',
    high_usage DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '高峰段电量(kWh)',
    flat_usage DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '平段电量(kWh)',
    valley_usage DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '低谷段电量(kWh)',
    total_usage DECIMAL(12,2) GENERATED ALWAYS AS
        (peak_usage + high_usage + flat_usage + valley_usage) STORED COMMENT '合计电量(kWh)',
    create_time DATETIME NOT NULL,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_meter_date (meter_id, usage_date),
    -- 客户自助视图：我的日用电，按日期倒序
    KEY idx_deleted_customer_date (deleted, customer_id, usage_date),
    -- 全局日曲线：全站在某段时间的总用电，按日期聚合
    KEY idx_deleted_date (deleted, usage_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日用电量表';

-- ------------------------------------------------------------
-- 初始数据
-- ------------------------------------------------------------
INSERT INTO electricity_customer (id, customer_name, electricity_category, address, contact, electricity_status, create_time) VALUES
(1, '明教光明顶总坛', '居民', '光明顶圣火路1号',       '13800001111', '正常用电', NOW()),
(2, '武当派铁匠铺',   '工业', '武当山工业区真武街',     '13800002222', '正常用电', NOW()),
(3, '大都悦来客栈',   '商业', '大都繁华大道88号',       '13800003333', '正常用电', NOW()),
(4, '蝴蝶谷药田基地', '农业', '蝴蝶谷农业园18号',       '13800004444', '正常用电', NOW()),
(5, '少林寺藏经阁',   '居民', '少室山学府路99号',       '13800005555', '正常用电', NOW());

INSERT INTO electricity_bill (id, billing_year, total_consumption, arrears_amount, payment_status, generate_time, reminder_count, payment_deadline, current_reading, previous_reading, meter_reading_time, meter_id, customer_id, create_time) VALUES
(1, 2025, 3280.50,  0.00,     '已缴',     NOW(), 0, '2025-03-15', 15820.30,  12539.80,  '2025-02-28 10:00:00', 1, 1, NOW()),
(2, 2025, 58600.00, 58600.00, '未缴',     NOW(), 2, '2025-03-20', 1256300.50,1197700.50,'2025-02-28 14:30:00', 2, 2, NOW()),
(3, 2025, 12560.80, 5000.00,  '部分缴纳', NOW(), 1, '2025-03-15', 89560.40,  76999.60,  '2025-02-28 11:00:00', 3, 3, NOW()),
(4, 2025, 890.20,   0.00,     '已缴',     NOW(), 0, '2025-03-10', 4560.80,   3670.60,   '2025-02-28 09:00:00', 4, 4, NOW()),
(5, 2024, 15200.00, 15200.00, '逾期',     NOW(), 5, '2024-12-20', 89650.30,  74450.30,  '2024-11-30 16:00:00', 2, 2, NOW()),
-- 客户 1（明教光明顶总坛）名下有 1 号、6 号两块表，补一条 6 号表的账单，让「我的账单」不止一条
(6, 2025, 1420.60,  0.00,     '已缴',     NOW(), 0, '2025-03-15', 9310.20,   7889.60,   '2025-02-28 10:30:00', 6, 1, NOW());

INSERT INTO price_standard (id, electricity_category, base_price, peak_price, valley_price, effective_date, implementation_plan, preferential_policy, pricing_method, create_time) VALUES
(1, '居民', 0.5283, 0.7283, 0.3283, '2024-01-01', '阶梯电价方案一',     '低保户每月免费用电15kWh', '单一制', NOW()),
(2, '商业', 0.6852, 1.0252, 0.3852, '2024-01-01', '一般工商业电价',     '夜间低谷用电优惠10%',     '单一制', NOW()),
(3, '工业', 0.6258, 0.9858, 0.3158, '2024-01-01', '大工业电价方案',     '两部制电价可选',          '两部制', NOW()),
(4, '农业', 0.3158, 0.4858, 0.2158, '2024-06-01', '农业生产用电电价',   '农业排灌用电优惠20%',     '单一制', NOW());
