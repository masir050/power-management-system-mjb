# 区域电力设备管理系统

面向区域供电公司的设备台账、电费核算与缴纳、告警处置一体化系统，采用 Spring Cloud Alibaba 微服务架构：按业务拆库拆服务，网关统一鉴权，热点数据走 Redis 缓存，告警事件经 RabbitMQ 异步投递并通过 WebSocket 实时推送。

技术栈：Java 21 · Spring Boot 3.3.5 · Spring Cloud 2023.0.3 · Spring Cloud Alibaba 2023.0.3.2 · MyBatis · MySQL 8 · Redis（Redisson）· RabbitMQ · Nacos · Sentinel · Spring AI 1.0.0 · pgvector · Neo4j 5 · Vue 3 + Element Plus + Vite 5。

## 1 模块结构

| 模块 | 端口 | 职责 | 数据库 |
| --- | --- | --- | --- |
| power-gateway | 8080 | 路由转发、JWT 统一鉴权、Sentinel 限流、CORS、WebSocket 转发 | — |
| power-auth | 8081 | 登录认证、JWT 签发、用户管理 | power_auth |
| power-device | 8082 | 供电区域 / 变电站 / 电力设备 / 智能电表 | power_device |
| power-billing | 8083 | 用电客户 / 电费账单 / 缴费流水 / 电价标准 / 日用电量 | power_billing |
| power-alert | 8084 | 告警中心 / 检修工单 / 运维人员 / 仪表盘聚合 / WebSocket 推送 | power_alert |
| power-rag | 8088 | 规程问答与告警研判：pgvector + BM25 双路召回、RRF 融合 + 重排、Neo4j 拓扑、多工具 Agent；前端入口为右下角「电力规程助手」角标 | 只读 power_device / power_alert，另用 PostgreSQL(pgvector) + Neo4j |
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

### 2.4 规程问答与告警研判（RAG）

登录后任意页面右下角有一个「电力规程助手」角标，点开即可用，两种模式：

- **规程问答**：问处置条款、试验周期、定值等，回答带「资料 N」出处；知识库里没有的直接说未规定，不编。
- **告警研判**：粘贴一条告警文本，依次查设备台账、历史告警、Neo4j 供电路径与规程，输出结论 / 影响范围 / 处置建议 / 规程依据四段，面板里可展开每一步工具调用。

链路：向量 top10 + BM25 top10 → RRF(k=60) → bge-reranker 精排 → 重排分 < 0.5 拒答。接口由网关 `/api/rag/**` 转发到 power-rag（8088），鉴权仍走网关 JWT。指标与限制见 `power-rag/README.md`、`power-rag/docs/eval.md`。

## 3 启动（按顺序 4 步：容器 → 建库 → 后端 → 前端）

### 3.1 前置条件

| 组件 | 要求 | 必需 |
| --- | --- | --- |
| JDK | 21 | 是 |
| MySQL | 8.0+，本机服务，3306 | 是 |
| Docker 容器 `power-redis` / `power-rabbit` / `power-nacos` | 6379 / 5672 / 8848 | 是 |
| Docker 容器 `pgv` / `neo4j` | 5432 / 7687·7474 | 仅规程问答需要 |
| Node.js | 18+ | 前端 |

```bash
docker ps                                                      # 五个容器都应是 Up
docker start power-redis power-rabbit power-nacos pgv neo4j     # 有 Exited 的就起一下
```

Nacos 必须先于后端就绪，否则服务注册失败、进程启动即退出（`failFast` 默认开启，不会降级启动）。

五个容器的重启策略已设为 `unless-stopped`，Docker Desktop 随开机启动后它们会自动回来；但如果曾经手动停过（或策略被改回 `no`），仍需上面的 `docker start`。判断依据只看 `docker ps` 的 STATUS，不看是否有过启动记录。

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

### 3.3 启动后端（一键）

IDEA 打开项目根目录（`pom.xml` 所在处），JDK 选 21，等 Maven 依赖导入完；右上角运行下拉框选 **`全部后端（6 个服务）`**，点运行，六个进程一次起完。

| 运行配置 | 端口 | 启动类 |
| --- | --- | --- |
| power-auth | 8081 | com.power.auth.AuthApplication |
| power-device | 8082 | com.power.device.DeviceApplication |
| power-billing | 8083 | com.power.billing.BillingApplication |
| power-alert | 8084 | com.power.alert.AlertApplication |
| power-gateway | 8080 | com.power.gateway.GatewayApplication |
| power-rag | 8088 | com.power.rag.RagApplication |

启动成功的判定：

1. 六个 Run 窗口都打印 `Started XxxApplication`；
2. Nacos 控制台 `http://localhost:8848/nacos` 服务列表有 5 个（power-rag 不注册 Nacos，由网关直连 8088）；
3. `curl http://localhost:8088/stats` 返回 `blocks: 75`。

启动失败按现象对号入座：

| 现象 | 原因 |
| --- | --- |
| 几个服务抢同一个端口 / 端口无故被占 | 环境里有 `SERVER__PORT`，Run 配置的 Environment variables 里删掉 |
| 启动即退出，日志有 `Client not connected, current status:STARTING` 与 `register failed` | `power-nacos` 容器没起（`docker ps` 里是 Exited），8848 无监听 |
| `Access denied for user 'root'` | `MYSQL_PASSWORD` 与数据库实际口令不一致 |
| 8088 起不来，角标提示服务未启动 | `pgv`、`neo4j` 没起，或 power-rag 没启动 |
| 日志刷 `PRECONDITION_FAILED ... inequivalent arg 'x-dead-letter-exchange'` | 队列被两处以不同参数声明；本项目已改为由 `AlertMqConfig` 统一声明，若再现说明又加了 `@QueueBinding` |

### 3.4 不用 IDEA 时：命令行启停

```bash
./build.sh -DskipTests package     # 打包，产物在各模块 target/（本机 mvn 有 classpath 问题，用这个脚本）
start.bat                          # 双击：起 6 个 jar + 前端；power-rag 没打包时跳过并提示
stop.bat                           # 按端口停掉全部服务与前端
```

也可以只把 `全部后端（6 个服务）` 拆开逐个 Run，端口与启动类见上表。

### 3.5 启动前端

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

### 3.6 首次使用规程问答（只需一次）

```bash
curl -X POST http://localhost:8088/ingest    # 18 份文档 md → 216 个向量块
```

Neo4j 的设备节点与 FEEDS 供电关系按 `power-rag/docs/cypher.md` 里的语句建。建完 `curl http://localhost:8088/stats` 应看到 `blocks: 75`，然后登录系统、点右下角「电力规程助手」角标即可对话。

### 3.7 演示数据

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
├── pom.xml                     # 父工程，7 个 module
├── .idea/runConfigurations/    # 后端运行配置（6 个服务 + 1 个 Compound）
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
├── power-rag/                  # 规程问答（RAG + Agent，8088，见 power-rag/README.md）
├── frontend/                   # Vue 3 控制台
├── verify-roles.mjs            # 角色数据边界验证脚本（node verify-roles.mjs，需服务已启动）
└── verify-payment.mjs          # 缴费流程验证脚本（node verify-payment.mjs，同上）
```
