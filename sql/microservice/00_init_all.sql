-- ============================================================
-- 分库初始化一键脚本
--
-- 用法（在项目根目录执行）：
--   mysql -uroot -p --default-character-set=utf8mb4 < sql/microservice/00_init_all.sql
--
-- 说明：MySQL 的 SOURCE 指令是客户端命令，必须在 mysql 命令行交互模式下才生效。
-- 用重定向方式执行本文件时，SOURCE 同样有效（客户端会逐行解析）。
-- 若使用 Navicat / DataGrip，请逐个打开 01~04 文件执行，或直接用命令行。
-- ============================================================

-- 注意：SOURCE 的路径相对于「执行 mysql 客户端时的当前工作目录」，不是 sql 文件所在目录。
SOURCE sql/microservice/01_power_auth.sql;
SOURCE sql/microservice/02_power_device.sql;
SOURCE sql/microservice/03_power_billing.sql;
SOURCE sql/microservice/04_power_alert.sql;
-- 05 是索引命中检查（EXPLAIN 验证），不建表，按需单独跑，不放在初始化里
-- 06 造近 90 天日用电量：首页曲线、"我的用电"、尖峰平谷构成全靠这张表，
--    不跑这个脚本首页图表是空的，看起来像坏了其实是没数据
SOURCE sql/microservice/06_seed_daily_usage.sql;
-- 07 缴费功能：账单补「应缴/已缴/缴费时间/支付方式」四列 + 建缴费流水表 +
--    给演示客户造 2026 年账单。不跑这个脚本客户登录后没有可缴的账单
SOURCE sql/microservice/07_payment.sql;

SELECT '分库初始化完成，共创建 4 个库：power_auth / power_device / power_billing / power_alert' AS result;
