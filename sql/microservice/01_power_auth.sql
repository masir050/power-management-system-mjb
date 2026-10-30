-- ============================================================
-- power_auth 库 —— 认证服务专用
-- 服务：power-auth (8081)
-- 执行：mysql -uroot -p < 01_power_auth.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS power_auth DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE power_auth;

DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt 密文，禁止存明文', 
    nickname VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    status INT DEFAULT 1 COMMENT '1=启用 0=禁用',
    -- 角色决定登录后能看到哪些视图、能操作哪些数据。
    -- 取值：ADMIN 总负责人 / OPERATOR 运维人员 / CUSTOMER 用电客户
    role VARCHAR(20) NOT NULL DEFAULT 'OPERATOR' COMMENT 'ADMIN/OPERATOR/CUSTOMER',
    -- 角色到业务实体的绑定：客户账号必须指向 electricity_customer.id，
    -- 运维账号必须指向 maintenance_staff.id。两处都是跨库逻辑关联，不建外键。
    customer_id BIGINT COMMENT '用电客户ID（role=CUSTOMER 时必填，power_billing 库）',
    staff_id BIGINT COMMENT '运维人员ID（role=OPERATOR 时必填，power_alert 库）',
    create_time DATETIME NOT NULL,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    -- 登录时按 username 精确查（selectUserByUsername），username 已有唯一索引可复用；
    -- 用户列表页按状态过滤时走该联合索引，避免全表扫
    KEY idx_status_username (status, username),
    KEY idx_role_customer (role, customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 三个角色各一个演示账号，密码统一 admin123 (BCrypt)
-- admin     → 总负责人，能看全部
-- weiyixiao → 运维人员，绑定 maintenance_staff.id = 1，只能看设备与告警
-- customer1 → 用电客户，绑定 electricity_customer.id = 1，只能看自己的账单与用电
INSERT INTO sys_user (id, username, password, nickname, status, role, customer_id, staff_id, create_time) VALUES
(1, 'admin',     '$2a$10$XE..g4OeVmOEjv280cdsleNOaDPsOcfHMpal25ohR0CIIdX2J4S/G', '张无忌', 1, 'ADMIN',    NULL, NULL, NOW()),
(2, 'weiyixiao', '$2a$10$XE..g4OeVmOEjv280cdsleNOaDPsOcfHMpal25ohR0CIIdX2J4S/G', '韦一笑', 1, 'OPERATOR', NULL, 1,    NOW()),
(3, 'customer1', '$2a$10$XE..g4OeVmOEjv280cdsleNOaDPsOcfHMpal25ohR0CIIdX2J4S/G', '明教光明顶总坛', 1, 'CUSTOMER', 1, NULL, NOW());
