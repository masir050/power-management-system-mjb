#!/usr/bin/env bash
# 本地联调启动脚本：先起中间件容器，再按依赖顺序拉起 5 个服务，最后起前端。
# 用法：./run-local.sh [start|stop|status|logs <服务名>]
#
# Windows 用户建议直接双击 start.bat / stop.bat（见文件末尾说明）。
#
# ⚠ 重要：如果本脚本是在 IDE 的自动化终端 / 智能体工具里跑的，
#   命令一结束，它启动的 java 子进程会被连带回收，浏览器马上就连不上 8080。
#   本脚本自己会打印 "[OK] power-auth:8081 已就绪"，看起来一切正常，
#   但那只是「那一刻」的状态。确认服务是否真的常驻，请另开一个终端执行：
#       ./run-local.sh status
#   要长期跑，请在你自己的终端窗口里执行本脚本。
#
# 环境变量（按需覆盖）：
#   MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD
#   REDIS_HOST / REDIS_PORT / REDIS_PASSWORD
#   RABBITMQ_HOST / RABBITMQ_PORT
#   NACOS_ADDR
set -u
# 需要 Windows 侧命令 netstat / taskkill / mysql / java。
# 注意不能只留 /usr/bin:/bin —— 那样 grep/awk 找不到，且 Git Bash 里带 .exe 的
# Windows 命令必须靠 System32 在 PATH 上。
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
cd "$(dirname "$0")" || exit 1

ROOT="$PWD"
LOGS="$ROOT/logs"
mkdir -p "$LOGS"

# ---------- 可覆盖的中间件地址 ----------
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
RABBITMQ_HOST="${RABBITMQ_HOST:-localhost}"
RABBITMQ_PORT="${RABBITMQ_PORT:-5672}"
NACOS_ADDR="${NACOS_ADDR:-localhost:8848}"

# 服务列表：名字|jar 路径|端口。顺序即启动顺序（业务服务先于网关）
SERVICES=(
  "power-auth|power-auth/target/power-auth-2.0.0.jar|8081"
  "power-device|power-device/target/power-device-2.0.0.jar|8082"
  "power-billing|power-billing/target/power-billing-2.0.0.jar|8083"
  "power-alert|power-alert/target/power-alert-2.0.0.jar|8084"
  "power-gateway|power-gateway/target/power-gateway-2.0.0.jar|8080"
)

common_env() {
  export MYSQL_HOST MYSQL_PORT MYSQL_USER MYSQL_PASSWORD
  export REDIS_HOST REDIS_PORT REDIS_PASSWORD
  export RABBITMQ_HOST RABBITMQ_PORT
  export NACOS_ADDR
  # 关键：某些宿主进程会注入 SERVER__PORT（宽松绑定后等价 server.port，优先级高于
  # application.yml），导致 5 个服务全部抢同一个端口、后启动的报 "Port already in use"。
  # 这里清掉，各服务按自己 yml 里的 server.port 监听。
  unset SERVER__PORT SERVER_PORT
}

port_open() {
  (echo > "/dev/tcp/127.0.0.1/$1") 2>/dev/null
}

wait_port() {
  local name="$1" port="$2" timeout="${3:-90}" i=0
  while [ $i -lt "$timeout" ]; do
    if port_open "$port"; then
      echo "  [OK] $name:$port 已就绪（${i}s）"
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  echo "  [FAIL] $name:$port 在 ${timeout}s 内未就绪，详见 $LOGS/$name.log"
  return 1
}

cmd_status() {
  echo "===== 中间件 ====="
  printf "  MySQL   %s:%s  " "$MYSQL_HOST" "$MYSQL_PORT"
  (echo > "/dev/tcp/$MYSQL_HOST/$MYSQL_PORT") 2>/dev/null && echo "UP" || echo "DOWN"
  printf "  Redis   %s:%s  " "$REDIS_HOST" "$REDIS_PORT"
  (echo > "/dev/tcp/$REDIS_HOST/$REDIS_PORT") 2>/dev/null && echo "UP" || echo "DOWN"
  printf "  RabbitMQ %s:%s  " "$RABBITMQ_HOST" "$RABBITMQ_PORT"
  (echo > "/dev/tcp/$RABBITMQ_HOST/$RABBITMQ_PORT") 2>/dev/null && echo "UP" || echo "DOWN"
  printf "  Nacos   %s  " "$NACOS_ADDR"
  nacos_host="${NACOS_ADDR%%:*}"
  nacos_port="${NACOS_ADDR##*:}"
  (echo > "/dev/tcp/$nacos_host/$nacos_port") 2>/dev/null && echo "UP" || echo "DOWN"

  echo "===== 服务 ====="
  for item in "${SERVICES[@]}"; do
    IFS='|' read -r name _ port <<< "$item"
    printf "  %-14s :%s  " "$name" "$port"
    port_open "$port" && echo "UP" || echo "DOWN"
  done
}

cmd_start() {
  common_env
  cmd_status
  echo

  # 预检：端口通不代表账号密码对，起 5 个 JVM 才发现连不上库太浪费时间
  echo "===== 连接自检 ====="
  local check
  check=$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" \
            ${MYSQL_PASSWORD:+-p"$MYSQL_PASSWORD"} -N -e "SELECT 1" 2>&1 | tail -1)
  if [ "$check" = "1" ]; then
    echo "  [OK] MySQL 账号可用"
    local missing=""
    for db in power_auth power_device power_billing power_alert; do
      local n
      n=$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" ${MYSQL_PASSWORD:+-p"$MYSQL_PASSWORD"} \
            -N -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$db'" 2>/dev/null)
      [ "$n" != "1" ] && missing="$missing $db"
    done
    if [ -n "$missing" ]; then
      echo "  [WARN] 缺少数据库:$missing —— 先执行 sql/microservice/00_init_all.sql"
    else
      echo "  [OK] 四个业务库已存在"
    fi
  else
    echo "  [FAIL] MySQL 连接失败：$check"
    echo "         用 MYSQL_PASSWORD=xxx ./run-local.sh start 指定密码"
    return 1
  fi
  port_open "$REDIS_PORT" && echo "  [OK] Redis 端口可达" || echo "  [FAIL] Redis 不可达，检查 REDIS_HOST/REDIS_PORT"
  port_open "$RABBITMQ_PORT" && echo "  [OK] RabbitMQ 端口可达" || echo "  [FAIL] RabbitMQ 不可达"
  echo

  echo "===== 启动服务 ====="
  for item in "${SERVICES[@]}"; do
    IFS='|' read -r name jar port <<< "$item"
    if [ ! -f "$jar" ]; then
      echo "  [SKIP] $name: 缺少 $jar，先跑 ./build.sh -DskipTests install"
      continue
    fi
    if port_open "$port"; then
      echo "  [SKIP] $name 已在 $port 运行"
      continue
    fi
    # --server.port 用命令行参数传：优先级高于环境变量（含宿主注入的 SERVER__PORT），
    # 保证每个服务落在自己 yml 约定的端口上
    nohup java -jar "$jar" --server.port="$port" > "$LOGS/$name.log" 2>&1 &
    echo "  [START] $name pid=$! port=$port 日志 $LOGS/$name.log"
    # 逐个等待端口，避免网关早于业务服务注册到 Nacos
    wait_port "$name" "$port" 120 || true
  done

  echo
  echo "===== 前端 ====="
  # 3000 是很常见的占用端口（其它项目、IDE 预览、node 工具都爱用），
  # vite 发现被占会自动顺延到 3001/3002…，导致这里以为起在 3000、实际访问不到。
  # 因此先探测一个空端口，再用 --port 显式钉住，最后以日志里 vite 打印的地址为准。
  local fe_port=""
  local p
  for p in 3000 3001 3002 3003 3010 3020; do
    if ! port_open "$p"; then fe_port="$p"; break; fi
  done
  if [ -z "$fe_port" ]; then
    echo "  [FAIL] 3000~3020 均被占用，请手动指定前端端口"
  else
    ( cd frontend && nohup node ./node_modules/vite/bin/vite.js --host 127.0.0.1 \
        --port "$fe_port" --strictPort > "$LOGS/frontend.log" 2>&1 & echo "  [START] frontend pid=$! port=$fe_port" )
    wait_port frontend "$fe_port" 60 || true
  fi

  echo
  echo "访问 http://127.0.0.1:${fe_port:-3000}"
  echo "账号 admin / admin123（其余两个测试号见 sql/microservice/01_power_auth.sql 注释）"
}

cmd_stop() {
  echo "===== 停止服务 ====="
  # 按端口反查 PID，比按进程名/jar 名匹配可靠：
  # Git Bash 的 ps -W 看不到全部 java 进程，按名字 grep 经常漏杀，导致 jar 被占用、重新打包失败
  for item in "${SERVICES[@]}"; do
    IFS='|' read -r name _ port <<< "$item"
    pids=$(netstat -ano 2>/dev/null | grep -E ":$port[[:space:]].*LISTENING" | awk '{print $NF}' | sort -u)
    if [ -n "${pids:-}" ]; then
      for pid in $pids; do
        taskkill /F /PID "$pid" >/dev/null 2>&1 && echo "  [STOP] $name port=$port pid=$pid"
      done
    else
      echo "  [--] $name 未在运行"
    fi
  done
  # 前端端口是动态选的，这里按 frontend.log 里 vite 打印的地址反查
  pids=$(netstat -ano 2>/dev/null | grep -E ":(300[0-9]|301[0-9]|302[0-9])[[:space:]].*LISTENING" | awk '{print $NF}' | sort -u)
  for pid in ${pids:-}; do
    taskkill /F /PID "$pid" >/dev/null 2>&1 && echo "  [STOP] frontend pid=$pid"
  done
}

case "${1:-start}" in
  start)  cmd_start ;;
  stop)   cmd_stop ;;
  status) cmd_status ;;
  logs)   tail -n 200 "$LOGS/${2:?用法: ./run-local.sh logs <服务名>}.log" ;;
  *)      echo "用法: ./run-local.sh [start|stop|status|logs <服务名>]"; exit 1 ;;
esac
