-- ============================================================
-- 电力管理系统 - 完整数据库脚本（建表 + 初始数据）
-- 使用方法: 在 MySQL 中执行 source power_management.sql;
-- ============================================================

CREATE DATABASE IF NOT EXISTS power_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE power_management;

-- ============================================================
-- 删除已有表（按外键依赖逆序）
-- ============================================================
DROP TABLE IF EXISTS maintenance_record;
DROP TABLE IF EXISTS electricity_bill;
DROP TABLE IF EXISTS smart_meter;
DROP TABLE IF EXISTS electricity_customer;
DROP TABLE IF EXISTS power_equipment;
DROP TABLE IF EXISTS substation;
DROP TABLE IF EXISTS power_area;
DROP TABLE IF EXISTS price_standard;
DROP TABLE IF EXISTS maintenance_staff;
DROP TABLE IF EXISTS sys_user;

-- ============================================================
-- 1. 系统用户表
-- ============================================================
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    status INT DEFAULT 1 COMMENT '1=启用 0=禁用',
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ============================================================
-- 2. 供电区域表
-- ============================================================
CREATE TABLE power_area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    area_name VARCHAR(100) NOT NULL,
    admin_level VARCHAR(50) NOT NULL COMMENT '省级/市级/县级/乡镇级',
    jurisdiction TEXT COMMENT '管辖范围',
    leader VARCHAR(50) COMMENT '负责人',
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供电区域表';

-- ============================================================
-- 3. 变电站表
-- ============================================================
CREATE TABLE substation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    voltage_level VARCHAR(50) NOT NULL COMMENT '如:110kV/220kV/500kV',
    commission_date DATE NOT NULL COMMENT '投运时间',
    run_status VARCHAR(20) NOT NULL COMMENT '运行中/停运/检修中',
    jurisdiction_years INT COMMENT '管辖年限',
    management_level VARCHAR(50) COMMENT '管理等级',
    area_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    INDEX idx_area_id (area_id),
    FOREIGN KEY (area_id) REFERENCES power_area(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='变电站表';

-- ============================================================
-- 4. 电力设备表
-- ============================================================
CREATE TABLE power_equipment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_name VARCHAR(100) NOT NULL,
    equipment_type VARCHAR(50) NOT NULL COMMENT '变压器/断路器/隔离开关',
    rated_params VARCHAR(200) COMMENT '额定参数',
    install_location VARCHAR(200) COMMENT '安装位置',
    manufacture_date DATE COMMENT '出厂日期',
    impact_scope VARCHAR(200) COMMENT '影响范围',
    hazard_level VARCHAR(20) COMMENT '一级/二级/三级',
    install_batch VARCHAR(50) COMMENT '安装批次',
    quantity INT COMMENT '设备数量',
    commission_batch VARCHAR(50) COMMENT '投运批次',
    substation_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    INDEX idx_substation_id (substation_id),
    FOREIGN KEY (substation_id) REFERENCES substation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电力设备表';

-- ============================================================
-- 5. 用电客户表
-- ============================================================
CREATE TABLE electricity_customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    electricity_category VARCHAR(50) NOT NULL COMMENT '居民/商业/工业/农业',
    address VARCHAR(200),
    contact VARCHAR(50),
    electricity_status VARCHAR(20) NOT NULL COMMENT '正常用电/暂停/销户',
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用电客户表';

-- ============================================================
-- 6. 智能电表表
-- ============================================================
CREATE TABLE smart_meter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model VARCHAR(50) NOT NULL COMMENT '电表型号',
    install_date DATE COMMENT '安装日期',
    verification_cycle INT COMMENT '检定周期(月)',
    meter_status VARCHAR(20) NOT NULL COMMENT '正常/故障/待检定',
    install_location VARCHAR(200) COMMENT '安装位置',
    bind_date DATE COMMENT '绑定日期',
    ratio DECIMAL(10,2) COMMENT '表计倍率',
    customer_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    INDEX idx_customer_id (customer_id),
    FOREIGN KEY (customer_id) REFERENCES electricity_customer(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能电表表';

-- ============================================================
-- 7. 电费账单表
-- ============================================================
CREATE TABLE electricity_bill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    billing_year INT NOT NULL COMMENT '计费年份',
    total_consumption DECIMAL(15,2) NOT NULL COMMENT '总用电量(kWh)',
    arrears_amount DECIMAL(15,2) COMMENT '欠费金额(元)',
    payment_status VARCHAR(20) NOT NULL COMMENT '已缴/未缴/部分缴纳/逾期',
    generate_time DATETIME NOT NULL,
    reminder_count INT DEFAULT 0 COMMENT '催缴次数',
    payment_deadline DATE COMMENT '缴费期限',
    current_reading DECIMAL(15,2) NOT NULL COMMENT '本期止码',
    previous_reading DECIMAL(15,2) NOT NULL COMMENT '上期止码',
    meter_reading_time DATETIME NOT NULL COMMENT '抄表时间',
    meter_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    INDEX idx_meter_id (meter_id),
    FOREIGN KEY (meter_id) REFERENCES smart_meter(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电费账单表';

-- ============================================================
-- 8. 电价标准表
-- ============================================================
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
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电价标准表';

-- ============================================================
-- 9. 运维人员表
-- ============================================================
CREATE TABLE maintenance_staff (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    department VARCHAR(100),
    position VARCHAR(50),
    phone VARCHAR(20),
    work_status VARCHAR(20) NOT NULL COMMENT '在岗/休假/离职',
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维人员表';

-- ============================================================
-- 10. 检修记录表
-- ============================================================
CREATE TABLE maintenance_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    occurrence_time DATETIME NOT NULL COMMENT '发生时间',
    fault_type VARCHAR(50) NOT NULL,
    fault_description TEXT,
    repair_result VARCHAR(200),
    completion_time DATETIME,
    repair_hours DECIMAL(8,2) COMMENT '检修工时',
    repair_priority VARCHAR(20) COMMENT '紧急/高/中/低',
    responsibility_division TEXT COMMENT '责任分工',
    equipment_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    INDEX idx_equipment_id (equipment_id),
    INDEX idx_staff_id (staff_id),
    FOREIGN KEY (equipment_id) REFERENCES power_equipment(id),
    FOREIGN KEY (staff_id) REFERENCES maintenance_staff(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检修记录表';

-- ============================================================
-- 初始数据
-- ============================================================

-- 默认管理员密码: admin123 (BCrypt加密)
INSERT INTO sys_user (id, username, password, nickname, status, create_time) VALUES
(1, 'admin', '$2a$10$XE..g4OeVmOEjv280cdsleNOaDPsOcfHMpal25ohR0CIIdX2J4S/G', '张无忌', 1, NOW()),
(2, 'zhangsf', '$2a$10$XE..g4OeVmOEjv280cdsleNOaDPsOcfHMpal25ohR0CIIdX2J4S/G', '张三丰', 1, NOW()),
(3, 'zhaomin', '$2a$10$XE..g4OeVmOEjv280cdsleNOaDPsOcfHMpal25ohR0CIIdX2J4S/G', '赵敏', 1, NOW());

INSERT INTO power_area (id, area_name, admin_level, jurisdiction, leader, create_time) VALUES
(1, '光明顶供电区', '市级', '市中心区域', '张无忌', NOW()),
(2, '武当供电区', '市级', '东部工业区', '张三丰', NOW()),
(3, '峨眉供电区', '县级', '西部郊区', '周芷若', NOW()),
(4, '天鹰供电区', '县级', '南部开发区', '殷天正', NOW());

INSERT INTO substation (id, name, voltage_level, commission_date, run_status, jurisdiction_years, management_level, area_id, create_time) VALUES
(1, '明教总坛变电站', '110kV', '2020-01-01', '运行中', 5, 'A级', 1, NOW()),
(2, '武当真武变电站', '220kV', '2019-06-15', '运行中', 6, 'A级', 2, NOW()),
(3, '峨眉金顶变电站', '35kV', '2021-03-20', '运行中', 3, 'B级', 3, NOW()),
(4, '天鹰坛变电站', '110kV', '2022-07-01', '检修中', 2, 'B级', 4, NOW());

INSERT INTO power_equipment (id, equipment_name, equipment_type, rated_params, install_location, manufacture_date, impact_scope, hazard_level, install_batch, quantity, commission_batch, substation_id, create_time) VALUES
(1, '圣火令主变压器', '变压器', '容量120MVA/电压110kV', '明教总坛主控室', '2019-08-15', '覆盖光明顶50%负荷', '一级', '2019-B01', 1, '2019-C01', 1, NOW()),
(2, '真武剑主变压器', '变压器', '容量180MVA/电压220kV', '武当真武主控室', '2018-11-20', '覆盖武当70%负荷', '一级', '2018-B01', 1, '2018-C01', 2, NOW()),
(3, '倚天剑断路器', '断路器', '额定电流3150A/电压220kV', '武当真武101间隔', '2020-03-10', '武当110kV母线', '二级', '2020-B02', 1, '2020-C02', 2, NOW()),
(4, '屠龙刀隔离开关', '隔离开关', '额定电流2000A/电压110kV', '明教总坛102间隔', '2021-06-01', '明教总坛主变进线', '二级', '2021-B03', 2, '2021-C03', 1, NOW()),
(5, '灭绝变压器', '变压器', '容量50MVA/电压35kV', '峨眉金顶主控室', '2021-01-15', '覆盖峨眉30%负荷', '二级', '2021-B01', 1, '2021-C01', 3, NOW()),
(6, '天鹰刀断路器', '断路器', '额定电流2000A/电压110kV', '天鹰坛101间隔', '2022-05-10', '天鹰坛110kV母线', '三级', '2022-B01', 1, '2022-C01', 4, NOW());

INSERT INTO electricity_customer (id, customer_name, electricity_category, address, contact, electricity_status, create_time) VALUES
(1, '明教光明顶总坛', '居民', '光明顶圣火路1号', '13800001111', '正常用电', NOW()),
(2, '武当派铁匠铺', '工业', '武当山工业区真武街', '13800002222', '正常用电', NOW()),
(3, '大都悦来客栈', '商业', '大都繁华大道88号', '13800003333', '正常用电', NOW()),
(4, '蝴蝶谷药田基地', '农业', '蝴蝶谷农业园18号', '13800004444', '正常用电', NOW()),
(5, '少林寺藏经阁', '居民', '少室山学府路99号', '13800005555', '正常用电', NOW());

INSERT INTO smart_meter (id, model, install_date, verification_cycle, meter_status, install_location, bind_date, ratio, customer_id, create_time) VALUES
(1, 'DDZY666-Z', '2022-01-15', 24, '正常', '光明顶圣火殿东侧', '2022-01-15', 1.00, 1, NOW()),
(2, 'DTZ888-H', '2021-06-01', 12, '正常', '武当铁匠铺配电房', '2021-06-01', 100.00, 2, NOW()),
(3, 'DDZY666-Z', '2022-03-01', 24, '正常', '悦来客栈B1层电表间', '2022-03-01', 1.00, 3, NOW()),
(4, 'DSZ777-G', '2023-02-10', 12, '正常', '蝴蝶谷药田控制室', '2023-02-10', 5.00, 4, NOW()),
(5, 'DDZY666-Z', '2023-09-01', 24, '待检定', '少林藏经阁总务处', '2023-09-01', 1.00, 5, NOW()),
(6, 'DTZ888-H', '2024-01-10', 12, '故障', '光明顶圣火殿西侧', '2024-01-10', 1.00, 1, NOW());

INSERT INTO electricity_bill (id, billing_year, total_consumption, arrears_amount, payment_status, generate_time, reminder_count, payment_deadline, current_reading, previous_reading, meter_reading_time, meter_id, create_time) VALUES
(1, 2025, 3280.50, 0.00, '已缴', NOW(), 0, '2025-03-15', 15820.30, 12539.80, '2025-02-28 10:00:00', 1, NOW()),
(2, 2025, 58600.00, 58600.00, '未缴', NOW(), 2, '2025-03-20', 1256300.50, 1197700.50, '2025-02-28 14:30:00', 2, NOW()),
(3, 2025, 12560.80, 5000.00, '部分缴纳', NOW(), 1, '2025-03-15', 89560.40, 76999.60, '2025-02-28 11:00:00', 3, NOW()),
(4, 2025, 890.20, 0.00, '已缴', NOW(), 0, '2025-03-10', 4560.80, 3670.60, '2025-02-28 09:00:00', 4, NOW()),
(5, 2024, 15200.00, 15200.00, '逾期', NOW(), 5, '2024-12-20', 89650.30, 74450.30, '2024-11-30 16:00:00', 2, NOW());

INSERT INTO price_standard (id, electricity_category, base_price, peak_price, valley_price, effective_date, implementation_plan, preferential_policy, pricing_method, create_time) VALUES
(1, '居民', 0.5283, 0.7283, 0.3283, '2024-01-01', '阶梯电价方案一', '低保户每月免费用电15kWh', '单一制', NOW()),
(2, '商业', 0.6852, 1.0252, 0.3852, '2024-01-01', '一般工商业电价', '夜间低谷用电优惠10%', '单一制', NOW()),
(3, '工业', 0.6258, 0.9858, 0.3158, '2024-01-01', '大工业电价方案', '两部制电价可选', '两部制', NOW()),
(4, '农业', 0.3158, 0.4858, 0.2158, '2024-06-01', '农业生产用电电价', '农业排灌用电优惠20%', '单一制', NOW());

INSERT INTO maintenance_staff (id, name, department, position, phone, work_status, create_time) VALUES
(1, '韦一笑', '运维部', '高级工程师', '13800138001', '在岗', NOW()),
(2, '杨逍', '运维部', '工程师', '13800138002', '在岗', NOW()),
(3, '殷天正', '运维部', '助理工程师', '13800138003', '休假', NOW()),
(4, '小昭', '运维部', '工程师', '13800138004', '在岗', NOW());

INSERT INTO maintenance_record (id, occurrence_time, fault_type, fault_description, repair_result, completion_time, repair_hours, repair_priority, responsibility_division, equipment_id, staff_id, create_time) VALUES
(1, '2025-01-15 08:30:00', '设备过热', '圣火令主变压器油温异常升高至85°C', '更换冷却风扇，清洗散热片', '2025-01-15 16:30:00', 8.0, '高', '韦一笑负责主变检修，杨逍负责安全监护', 1, 1, NOW()),
(2, '2025-02-20 14:00:00', '开关跳闸', '倚天剑断路器保护跳闸，原因待查', '发现CT二次回路松动，重新紧固', '2025-02-20 18:30:00', 4.5, '紧急', '杨逍负责二次回路检查，殷天正负责现场操作', 3, 2, NOW()),
(3, '2025-03-10 09:00:00', '表计故障', '少林寺电表计量异常，误差偏大', '更换电表并重新校验', '2025-03-10 12:00:00', 3.0, '中', '小昭负责电表更换和校验', 4, 4, NOW()),
(4, '2025-04-05 10:30:00', '绝缘降低', '屠龙刀隔离开关绝缘电阻低于标准值', '清洁绝缘子表面并进行防污闪处理', '2025-04-05 15:00:00', 4.5, '高', '韦一笑负责绝缘处理，杨逍负责质量验收', 4, 1, NOW()),
(5, '2025-05-12 07:00:00', '设备故障', '灭绝变压器轻瓦斯保护动作报警', '气体取样分析，排除内部故障后复位', '2025-05-12 14:00:00', 7.0, '高', '小昭负责瓦斯继电器检查，殷天正负责气体分析', 5, 4, NOW());
