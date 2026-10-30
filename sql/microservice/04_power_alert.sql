-- ============================================================
-- power_alert 库 —— 告警中心服务专用
-- 服务：power-alert (8084)
-- 执行：mysql -uroot -p < 04_power_alert.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS power_alert DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE power_alert;

DROP TABLE IF EXISTS local_message;
DROP TABLE IF EXISTS alert;
DROP TABLE IF EXISTS maintenance_record;
DROP TABLE IF EXISTS maintenance_staff;

-- ------------------------------------------------------------
-- 运维人员
-- ------------------------------------------------------------
CREATE TABLE maintenance_staff (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    department VARCHAR(100),
    position VARCHAR(50),
    phone VARCHAR(20),
    work_status VARCHAR(20) NOT NULL COMMENT '在岗/休假/离职',
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    KEY idx_deleted_status (deleted, work_status),
    KEY idx_department (department)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维人员表';

-- ------------------------------------------------------------
-- 检修记录
-- ------------------------------------------------------------
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
    -- 指向 power_device 库的设备 ID，跨库不建外键
    equipment_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    KEY idx_equipment_id (equipment_id),
    KEY idx_staff_id (staff_id),
    -- 仪表盘"今日告警数"：按 occurrence_time 区间计数，走该索引可只扫目标区间
    KEY idx_deleted_occurrence (deleted, occurrence_time),
    -- 检修列表页：按优先级过滤 + 按发生时间排序
    KEY idx_deleted_priority_occurrence (deleted, repair_priority, occurrence_time),
    KEY idx_deleted_fault_type (deleted, fault_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检修记录表';

-- ------------------------------------------------------------
-- 告警表
-- ------------------------------------------------------------
CREATE TABLE alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_id BIGINT NOT NULL COMMENT '设备ID（power_device 库）',
    equipment_name VARCHAR(100) COMMENT '设备名称快照：告警发生时取一次，设备改名不影响历史',
    fault_type VARCHAR(50) NOT NULL COMMENT '设备过热/开关跳闸/绝缘降低等',
    alert_description VARCHAR(500) COMMENT '告警描述',
    alert_level VARCHAR(20) NOT NULL COMMENT '紧急/高/中/低',
    alert_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACKNOWLEDGED/RESOLVED',
    occurrence_time DATETIME NOT NULL COMMENT '触发时间',
    acknowledge_time DATETIME COMMENT '确认时间',
    resolve_time DATETIME COMMENT '处理完成时间',
    handler_id BIGINT COMMENT '处理人（maintenance_staff.id）',
    handle_result VARCHAR(500) COMMENT '处理结果',
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    -- 告警列表页主查询：未处理告警 + 按等级 + 按时间倒序。
    -- 把 alert_status 放 create_time 前面，是因为"只看 PENDING"是最常见的过滤，且该列基数低、
    -- 配合时间排序能直接用索引有序性，省掉 filesort。
    KEY idx_deleted_status_level_time (deleted, alert_status, alert_level, occurrence_time),
    -- 按设备查历史告警（设备详情页）
    KEY idx_deleted_equipment_time (deleted, equipment_id, occurrence_time),
    -- 状态流转时按 id + 前置状态校验，走主键即可；这里补时间范围计数用的索引
    KEY idx_deleted_occurrence (deleted, occurrence_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警表';

-- ------------------------------------------------------------
-- 本地消息表（事务性发件箱）
-- ------------------------------------------------------------
CREATE TABLE local_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL COMMENT '消息唯一ID，同时是消费端幂等键',
    exchange_name VARCHAR(100) NOT NULL COMMENT '目标交换机',
    routing_key VARCHAR(100) NOT NULL COMMENT '路由键',
    payload TEXT NOT NULL COMMENT '消息体 JSON',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    last_error VARCHAR(500) COMMENT '最近一次失败原因',
    next_retry_time DATETIME COMMENT '下次可投递时间（指数退避）',
    sent_time DATETIME COMMENT '投递成功时间',
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    -- message_id 必须唯一：既是幂等键，也能在并发插入时由数据库兜底拦截重复
    UNIQUE KEY uk_message_id (message_id),
    -- 补偿任务的核心查询：status = 'PENDING' AND next_retry_time <= now() ORDER BY id LIMIT n
    -- 索引顺序与查询完全一致，且带上 id 便于按插入顺序优先投递（FIFO 语义）
    KEY idx_status_retry_time (status, next_retry_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表（保证业务与消息的最终一致性）';

-- ------------------------------------------------------------
-- 初始数据
-- ------------------------------------------------------------
INSERT INTO maintenance_staff (id, name, department, position, phone, work_status, create_time) VALUES
(1, '韦一笑', '运维部', '高级工程师', '13800138001', '在岗', NOW()),
(2, '杨逍',   '运维部', '工程师',     '13800138002', '在岗', NOW()),
(3, '殷天正', '运维部', '助理工程师', '13800138003', '休假', NOW()),
(4, '小昭',   '运维部', '工程师',     '13800138004', '在岗', NOW());

INSERT INTO maintenance_record (id, occurrence_time, fault_type, fault_description, repair_result, completion_time, repair_hours, repair_priority, responsibility_division, equipment_id, staff_id, create_time) VALUES
(1, '2025-01-15 08:30:00', '设备过热', '圣火令主变压器油温异常升高至85°C',     '更换冷却风扇，清洗散热片',           '2025-01-15 16:30:00', 8.0, '高',   '韦一笑负责主变检修，杨逍负责安全监护',       1, 1, NOW()),
(2, '2025-02-20 14:00:00', '开关跳闸', '倚天剑断路器保护跳闸，原因待查',       '发现CT二次回路松动，重新紧固',       '2025-02-20 18:30:00', 4.5, '紧急', '杨逍负责二次回路检查，殷天正负责现场操作',   3, 2, NOW()),
(3, '2025-03-10 09:00:00', '表计故障', '少林寺电表计量异常，误差偏大',         '更换电表并重新校验',                 '2025-03-10 12:00:00', 3.0, '中',   '小昭负责电表更换和校验',                     4, 4, NOW()),
(4, '2025-04-05 10:30:00', '绝缘降低', '屠龙刀隔离开关绝缘电阻低于标准值',     '清洁绝缘子表面并进行防污闪处理',     '2025-04-05 15:00:00', 4.5, '高',   '韦一笑负责绝缘处理，杨逍负责质量验收',       4, 1, NOW()),
(5, '2025-05-12 07:00:00', '设备故障', '灭绝变压器轻瓦斯保护动作报警',         '气体取样分析，排除内部故障后复位',   '2025-05-12 14:00:00', 7.0, '高',   '小昭负责瓦斯继电器检查，殷天正负责气体分析', 5, 4, NOW());

-- 与检修记录对应的告警（设备名称为快照值）
INSERT INTO alert (id, equipment_id, equipment_name, fault_type, alert_description, alert_level, alert_status, occurrence_time, acknowledge_time, resolve_time, handler_id, handle_result, create_time) VALUES
(1, 1, '圣火令主变压器', '设备过热', '主变压器油温升高至85°C，超过报警阈值', '高',   'RESOLVED',     '2025-01-15 08:30:00', '2025-01-15 09:00:00', '2025-01-15 16:30:00', 1, '更换冷却风扇，清洗散热片',   NOW()),
(2, 3, '倚天剑断路器',   '开关跳闸', '断路器保护动作跳闸',                   '紧急', 'RESOLVED',     '2025-02-20 14:00:00', '2025-02-20 14:10:00', '2025-02-20 18:30:00', 2, 'CT二次回路松动，已重新紧固', NOW()),
(3, 4, '屠龙刀隔离开关', '绝缘降低', '绝缘电阻低于标准值',                   '高',   'RESOLVED',     '2025-04-05 10:30:00', '2025-04-05 10:45:00', '2025-04-05 15:00:00', 1, '清洁绝缘子并做防污闪处理',   NOW()),
(4, 5, '灭绝变压器',     '设备故障', '轻瓦斯保护动作',                       '高',   'ACKNOWLEDGED', '2025-05-12 07:00:00', '2025-05-12 07:20:00', NULL,                  4, NULL,                         NOW()),
(5, 6, '天鹰刀断路器',   '设备过热', '隔离开关触头温度异常',                 '中',   'PENDING',      '2025-05-20 11:00:00', NULL,                  NULL,                  NULL, NULL,                      NOW()),
(6, 2, '真武剑主变压器', '绝缘降低', '油色谱乙炔含量超注意值',               '紧急', 'PENDING',      '2025-05-21 03:15:00', NULL,                  NULL,                  NULL, NULL,                      NOW());
