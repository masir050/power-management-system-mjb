-- ============================================================
-- power_device 库 —— 设备服务专用
-- 服务：power-device (8082)
-- 执行：mysql -uroot -p < 02_power_device.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS power_device DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE power_device;

-- 按外键依赖逆序删
DROP TABLE IF EXISTS smart_meter;
DROP TABLE IF EXISTS power_equipment;
DROP TABLE IF EXISTS substation;
DROP TABLE IF EXISTS power_area;

-- ------------------------------------------------------------
-- 供电区域
-- ------------------------------------------------------------
CREATE TABLE power_area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    area_name VARCHAR(100) NOT NULL,
    admin_level VARCHAR(50) NOT NULL COMMENT '省级/市级/县级/乡镇级',
    jurisdiction TEXT COMMENT '管辖范围',
    leader VARCHAR(50) COMMENT '负责人',
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    KEY idx_admin_level (admin_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供电区域表';

-- ------------------------------------------------------------
-- 变电站
-- ------------------------------------------------------------
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
    KEY idx_area_id (area_id),
    -- 列表页最常用的组合：先按运行状态筛（"只看运行中"），再按电压等级分组展示。
    -- 把 deleted 放最左，因为所有查询都带 deleted = 0，可让索引直接跳过已删数据。
    KEY idx_deleted_status_voltage (deleted, run_status, voltage_level),
    -- 按名称模糊查询用前缀匹配可走索引（LIKE 'xx%'），这里主要服务精确名与排序
    KEY idx_deleted_create (deleted, create_time),
    CONSTRAINT fk_substation_area FOREIGN KEY (area_id) REFERENCES power_area(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='变电站表';

-- ------------------------------------------------------------
-- 电力设备
-- ------------------------------------------------------------
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
    KEY idx_substation_id (substation_id),
    -- 设备列表页固定过滤条件：deleted + 类型 + 危险等级，三者组合覆盖了绝大多数查询
    KEY idx_deleted_type_hazard (deleted, equipment_type, hazard_level),
    -- 按变电站查设备（详情页"该站下所有设备"）
    KEY idx_deleted_substation (deleted, substation_id),
    CONSTRAINT fk_equipment_substation FOREIGN KEY (substation_id) REFERENCES substation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电力设备表';

-- ------------------------------------------------------------
-- 智能电表
-- ------------------------------------------------------------
CREATE TABLE smart_meter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model VARCHAR(50) NOT NULL COMMENT '电表型号',
    install_date DATE COMMENT '安装日期',
    verification_cycle INT COMMENT '检定周期(月)',
    meter_status VARCHAR(20) NOT NULL COMMENT '正常/故障/待检定',
    install_location VARCHAR(200) COMMENT '安装位置',
    bind_date DATE COMMENT '绑定日期',
    ratio DECIMAL(10,2) COMMENT '表计倍率',
    -- 注意：customer_id 指向 power_billing 库的 electricity_customer，
    -- 跨库无法建外键，这里只建普通索引。数据一致性由 billing 服务保证。
    customer_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    KEY idx_customer_id (customer_id),
    KEY idx_deleted_status (deleted, meter_status),
    KEY idx_deleted_model_status (deleted, model, meter_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能电表表（customer_id 跨库逻辑关联）';

-- ------------------------------------------------------------
-- 初始数据
-- ------------------------------------------------------------
INSERT INTO power_area (id, area_name, admin_level, jurisdiction, leader, create_time) VALUES
(1, '光明顶供电区', '市级', '市中心区域', '张无忌', NOW()),
(2, '武当供电区',   '市级', '东部工业区', '张三丰', NOW()),
(3, '峨眉供电区',   '县级', '西部郊区',   '周芷若', NOW()),
(4, '天鹰供电区',   '县级', '南部开发区', '殷天正', NOW());

INSERT INTO substation (id, name, voltage_level, commission_date, run_status, jurisdiction_years, management_level, area_id, create_time) VALUES
(1, '明教总坛变电站', '110kV', '2020-01-01', '运行中', 5, 'A级', 1, NOW()),
(2, '武当真武变电站', '220kV', '2019-06-15', '运行中', 6, 'A级', 2, NOW()),
(3, '峨眉金顶变电站', '35kV',  '2021-03-20', '运行中', 3, 'B级', 3, NOW()),
(4, '天鹰坛变电站',   '110kV', '2022-07-01', '检修中', 2, 'B级', 4, NOW());

INSERT INTO power_equipment (id, equipment_name, equipment_type, rated_params, install_location, manufacture_date, impact_scope, hazard_level, install_batch, quantity, commission_batch, substation_id, create_time) VALUES
(1, '圣火令主变压器', '变压器',   '容量120MVA/电压110kV', '明教总坛主控室', '2019-08-15', '覆盖光明顶50%负荷', '一级', '2019-B01', 1, '2019-C01', 1, NOW()),
(2, '真武剑主变压器', '变压器',   '容量180MVA/电压220kV', '武当真武主控室', '2018-11-20', '覆盖武当70%负荷',   '一级', '2018-B01', 1, '2018-C01', 2, NOW()),
(3, '倚天剑断路器',   '断路器',   '额定电流3150A/电压220kV','武当真武101间隔', '2020-03-10', '武当110kV母线',     '二级', '2020-B02', 1, '2020-C02', 2, NOW()),
(4, '屠龙刀隔离开关', '隔离开关', '额定电流2000A/电压110kV','明教总坛102间隔', '2021-06-01', '明教总坛主变进线', '二级', '2021-B03', 2, '2021-C03', 1, NOW()),
(5, '灭绝变压器',     '变压器',   '容量50MVA/电压35kV',   '峨眉金顶主控室', '2021-01-15', '覆盖峨眉30%负荷',   '二级', '2021-B01', 1, '2021-C01', 3, NOW()),
(6, '天鹰刀断路器',   '断路器',   '额定电流2000A/电压110kV','天鹰坛101间隔',  '2022-05-10', '天鹰坛110kV母线',   '三级', '2022-B01', 1, '2022-C01', 4, NOW());

INSERT INTO smart_meter (id, model, install_date, verification_cycle, meter_status, install_location, bind_date, ratio, customer_id, create_time) VALUES
(1, 'DDZY666-Z', '2022-01-15', 24, '正常',   '光明顶圣火殿东侧', '2022-01-15', 1.00,   1, NOW()),
(2, 'DTZ888-H',  '2021-06-01', 12, '正常',   '武当铁匠铺配电房', '2021-06-01', 100.00, 2, NOW()),
(3, 'DDZY666-Z', '2022-03-01', 24, '正常',   '悦来客栈B1层电表间','2022-03-01', 1.00,   3, NOW()),
(4, 'DSZ777-G',  '2023-02-10', 12, '正常',   '蝴蝶谷药田控制室', '2023-02-10', 5.00,   4, NOW()),
(5, 'DDZY666-Z', '2023-09-01', 24, '待检定', '少林藏经阁总务处', '2023-09-01', 1.00,   5, NOW()),
(6, 'DTZ888-H',  '2024-01-10', 12, '故障',   '光明顶圣火殿西侧', '2024-01-10', 1.00,   1, NOW());
