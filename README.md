# 区域电力设备管理系统

面向区域供电公司的设备台账、电费核算与缴纳、告警处置一体化系统，采用 Spring Cloud Alibaba 微服务架构：按业务拆库拆服务，网关统一鉴权，热点数据走 Redis 缓存，告警事件经 RabbitMQ 异步投递并通过 WebSocket 实时推送。

技术栈：Java 21 · Spring Boot 3.3.5 · Spring Cloud 2023.0.3 · Spring Cloud Alibaba 2023.0.3.2 · MyBatis · MySQL 8 · Redis（Redisson）· RabbitMQ · Nacos · Sentinel · Vue 3 + Element Plus + Vite 5。

## 1 模块结构

| 模块 | 端口 | 职责 | 数据库 |
| --- | --- | --- | --- |
| power-gateway | 8080 | 路由转发、JWT 统一鉴权、Sentinel 限流、CORS、WebSocket 转发 | — |
| power-auth | 8081 | 登录认证、JWT 签发、用户管理 | power_auth |
| power-device | 8082 | 供电区域 / 变电站 / 电力设备 / 智能电表 | power_device |
| power-billing | 8083 | 用电客户 / 电费账单 / 缴费流水 / 电价标准 / 日用电量 | power_billing |
| power-alert | 8084 | 告警中心 / 检修工单 / 运维人员 / 仪表盘聚合 / WebSocket 推送 | power_alert |
| power-common | — | 公共组件：统一返回体、缓存模板、分布式锁、身份上下文与数据权限 | — |
| frontend | 3000 | Vue 3 管理控制台 | — |

服务间调用统一走 OpenFeign，不跨库 JOIN；下游服务均配置 FallbackFactory 降级。

## 2 功能说明

### 2.1 业务功能

- **设备管理**：供电区域 → 变电站 → 电力设备 → 智能电表四级台账，维护运行状态、电压等级、隐患等级。
- **电费管理**：按「电量 × 电价」出账，支持部分缴纳、多次缴纳、柜台代收，每次缴费写入 `payment_record` 流水。
- **告警管理**：告警产生 → 转检修工单 → 处置完结，配套运维人员档案维护。
- **数据看板**：KPI、告警列表、月度用电趋势由服务端从数据库实时聚合。

### 2.2 角色与数据权限

| 账号 | 角色 | 数据范围 |
| --- | --- | --- |
| admin | ADMIN 总负责人 | 全站数据；设备台账与运维人员档案的增删改；账单代收 |
| weiyixiao | OPERATOR 运维人员 | 设备、变电站、电表、告警与检修工单；无客户账务数据 |
| customer1 | CUSTOMER 用电客户 | 仅本人电表、日用电量与账单；设备与告警接口返回 403 |

三个账号口令均为 `admin123`。角色由 `sys_user.role` 定义，客户账号必须绑定 `customer_id`。

权限控制在服务端完成：网关解析 JWT 后将 `role / customerId / staffId` 写入请求头（并先清除客户端伪造的同名请求头）；下游服务将身份装入 `CurrentUserContext`，由 `DataScope` 在 SQL 层追加 `customer_id` 过滤条件，`CurrentUserInterceptor` 在资源层拦截越权访问。客户端篡改请求参数不影响判定结果。

### 2.3 缴费金额口径

| 字段 | 含义 | 维护方 |
| --- | --- | --- |
| bill_amount | 本期应缴金额 = 电量 × 电价，出账时确定 | 出账流程 |
| paid_amount | 累计已缴金额，每次缴费累加 | 缴费流程 |
| arrears_amount | 尚欠金额 = 应缴 − 已缴，由服务端重算 | 缴费流程 |

缴费边界：超过欠费额、金额为 0、已结清账单、非法支付方式均被拒绝；并发缴纳通过扣款 SQL 中的余额条件保证一致性，更新 0 行时提示刷新重试。演示环境不接入真实支付网关，确认缴费即写库。

## 3 快速启动

### 3.1 环境要求

| 组件 | 要求 |
| --- | --- |
| JDK | 21 |
| MySQL | 8.0+（本机服务） |
| Redis / RabbitMQ / Nacos | Docker 容器 `power-redis` / `power-rabbit` / `power-nacos`，随 Docker Desktop 启动自动恢复 |
| Node.js | 18+（前端构建） |
| IDE | IntelliJ IDEA（运行配置已内置） |

启动前确认 3306、6379、5672、8848 端口可用，否则后端服务会因注册中心或数据库不可用而退出。

### 3.2 初始化数据库（首次执行）

`00_init_all.sql` 通过 `SOURCE` 依次执行 01~04、06、07 共六个脚本，`SOURCE` 是 mysql 客户端命令，须在**项目根目录**通过 mysql 命令行执行。

cmd：

```bat
mysql -uroot -p --default-character-set=utf8mb4 < sql\microservice\00_init_all.sql
```

PowerShell（不支持 `<` 重定向，改用管道）：

```powershell
Get-Content .\sql\microservice\00_init_all.sql -Encoding UTF8 | mysql -uroot -p --default-character-set=utf8mb4
```

也可在 IDEA 的 Database 面板连接 `localhost:3306` 后，依次执行 `sql/microservice/` 下的 01、02、03、04、06、07（跳过 00）。

### 3.3 启动后端

1. IDEA 打开项目根目录（`pom.xml` 所在处），JDK 选择 21，等待 Maven 依赖导入完成。
2. 运行配置已随工程提供（`.idea/runConfigurations/`），在右上角运行下拉框选择 **`全部后端（5 个服务）`**，点击运行按钮即可同时启动五个服务；也可单独运行某个服务。

| 运行配置 | 端口 | 启动类 |
| --- | --- | --- |
| power-auth | 8081 | com.power.auth.AuthApplication |
| power-device | 8082 | com.power.device.DeviceApplication |
| power-billing | 8083 | com.power.billing.BillingApplication |
| power-alert | 8084 | com.power.alert.AlertApplication |
| power-gateway | 8080 | com.power.gateway.GatewayApplication |

3. 启动成功的标志：各 Run 窗口输出 `Started XxxApplication`，且 Nacos 控制台 `http://localhost:8848/nacos` 服务列表中存在 5 个服务。停止服务点击对应 Run 窗口的停止按钮。

说明：

- 五个服务均须启动，前端仅连接网关 8080，缺少任一服务会导致对应接口不可用。
- 各运行配置已内置 `MYSQL_PASSWORD`，修改数据库口令后需同步更新这 5 个配置文件。
- `.idea/` 已被 `.gitignore` 排除，迁移到其他机器时需一并拷贝 `.idea/runConfigurations/` 目录。
- 自行新建运行配置时，需在 Environment variables 中添加 `MYSQL_PASSWORD`，并移除 `SERVER__PORT`（该变量会被 Spring 松散绑定解析为 `server.port`，导致所有服务占用同一端口）。

### 3.4 启动前端

在 IDEA 中打开 `frontend/package.json`，运行 `dev` 脚本；或执行：

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:3000`。`/api` 与 `/ws` 请求由 Vite 代理至网关 8080。

端口已配置 `strictPort`：3000 被占用时 Vite 会**直接报错退出**（`Port 3000 is in use`），不会悄悄换端口——此时说明上一次的 vite 没有关干净，先结束残留进程再启动：

```powershell
netstat -ano | findstr :3000        # 查看 LISTENING 行末尾的 PID
taskkill /F /PID <PID>
```

### 3.5 演示数据

`07_payment.sql` 内置演示账单：客户 1 名下 2026-08（已缴）、2026-09（部分缴纳、逾期）共 4 条，当前待缴 2 笔合计 1854.46 元。重复执行该脚本可将演示数据重置为初始状态。

## 4 配置项

以下环境变量均有默认值，本地开发通常只需设置 `MYSQL_PASSWORD`：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD | localhost / 3306 / root / root | 四个业务库共用同一 MySQL 实例 |
| REDIS_HOST / REDIS_PORT | localhost / 6379 | 缓存、限流、分布式锁 |
| RABBITMQ_HOST / RABBITMQ_PORT | localhost / 5672 | 告警事件投递 |
| NACOS_ADDR | localhost:8848 | 注册与配置中心 |
| JWT_SECRET | 内置开发密钥 | 网关与各业务服务必须一致 |
| WS_AUTH_REQUIRED | true | WebSocket 握手是否校验 token |

## 5 目录结构

```
power-management-system/
├── pom.xml                     # 父工程，6 个 module
├── .idea/runConfigurations/    # 后端运行配置（5 个服务 + 1 个 Compound）
├── build.sh                    # 命令行打包脚本
├── start.bat / stop.bat        # 命令行方式的启停批处理
├── sql/microservice/           # 建库脚本
│   ├── 00_init_all.sql         # 汇总入口（SOURCE 串联以下脚本）
│   ├── 01_power_auth.sql       # 用户与角色
│   ├── 02_power_device.sql     # 区域 / 变电站 / 设备 / 电表
│   ├── 03_power_billing.sql    # 客户 / 账单 / 电价
│   ├── 04_power_alert.sql      # 告警 / 检修 / 人员 / 本地消息表
│   ├── 05_explain_check.sql    # 索引执行计划验证
│   ├── 06_seed_daily_usage.sql # 近 90 天日用电量数据
│   └── 07_payment.sql          # 缴费表结构与演示账单
├── power-common/               # 公共组件
├── power-gateway/              # 网关
├── power-auth/                 # 认证服务
├── power-device/               # 设备服务
├── power-billing/              # 电费服务
├── power-alert/                # 告警服务
├── frontend/                   # Vue 3 控制台
├── verify-roles.mjs            # 角色数据边界验证脚本（node verify-roles.mjs，需服务已启动）
└── verify-payment.mjs          # 缴费流程验证脚本（node verify-payment.mjs，同上）
```
