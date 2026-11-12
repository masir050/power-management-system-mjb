@echo off
REM 一键启动：依赖容器 -> 6 个后端服务 -> 前端。必须用 bat 起进程，工具启动的子进程会被回收。
chcp 65001 >nul
setlocal enabledelayedexpansion
cd /d "%~dp0"

REM ---- 依赖容器：没起就拉起来，Nacos 不亮后端会 failFast 退出 ----
echo.
echo ===== 检查依赖容器 =====
docker info >nul 2>&1
if errorlevel 1 (
  echo [错误] Docker 未运行，请先启动 Docker Desktop
  pause
  exit /b 1
)
for %%C in (power-nacos power-redis power-rabbit pgv neo4j) do (
  set RUNNING=
  for /f "delims=" %%S in ('docker inspect -f "{{.State.Running}}" %%C 2^>nul') do set RUNNING=%%S
  if /i "!RUNNING!"=="true" (
    echo   [OK]   %%C
  ) else (
    echo   [启动] %%C
    docker start %%C >nul 2>&1
  )
)

echo 等待 Nacos 就绪（8848 / 9848）…
set /a NT=0
:nacosloop
set /a NUP=0
for %%P in (8848 9848) do (
  netstat -ano | findstr ":%%P " | findstr LISTENING >nul && set /a NUP+=1
)
if !NUP! GEQ 2 goto nacosok
set /a NT+=1
if !NT! GEQ 30 (
  echo [警告] Nacos 60 秒内未监听，后端大概率注册失败并退出
  goto nacosok
)
timeout /t 2 /nobreak >nul
goto nacosloop
:nacosok
echo Nacos 就绪。

REM ---- 数据库口令 ----
if "%MYSQL_PASSWORD%"=="" set /p MYSQL_PASSWORD=请输入 MySQL root 口令（直接回车表示空口令）:
REM 宿主若注入 SERVER__PORT，会把所有服务挤到同一个端口
set SERVER__PORT=
set SERVER_PORT=

echo.
echo ===== 启动后端 =====
start "power-auth"    /min java -jar power-auth\target\power-auth-2.0.0.jar       --server.port=8081
start "power-device"  /min java -jar power-device\target\power-device-2.0.0.jar   --server.port=8082
start "power-billing" /min java -jar power-billing\target\power-billing-2.0.0.jar --server.port=8083
start "power-alert"   /min java -jar power-alert\target\power-alert-2.0.0.jar     --server.port=8084
start "power-gateway" /min java -jar power-gateway\target\power-gateway-2.0.0.jar --server.port=8080

if exist power-rag\target\power-rag-2.0.0.jar (
  start "power-rag" /min java -jar power-rag\target\power-rag-2.0.0.jar --server.port=8088
) else (
  echo [跳过] power-rag 未打包，右下角规程问答不可用；需要时执行 build.sh -pl power-rag -am -DskipTests package
)

echo 等待服务就绪（power-rag 约需 20 秒）…
set /a TRIES=0
:waitloop
set /a READY=0
for %%P in (8080 8081 8082 8083 8084) do (
  netstat -ano | findstr ":%%P " | findstr LISTENING >nul && set /a READY+=1
)
if !READY! GEQ 5 goto ready
set /a TRIES+=1
if !TRIES! GEQ 40 (
  echo [警告] 100 秒内只有 !READY!/5 个端口就绪，请查看对应服务的控制台窗口
  goto frontend
)
timeout /t 3 /nobreak >nul
goto waitloop

:ready
echo 5 个主服务端口就绪。

:frontend
echo.
echo ===== 启动前端 =====
pushd "%~dp0frontend"
start "power-frontend" /min node .\node_modules\vite\bin\vite.js --host 127.0.0.1 --port 3000 --strictPort
popd
timeout /t 6 /nobreak >nul

echo.
echo ==================================================
echo   访问 http://127.0.0.1:3000
echo.
echo   admin      / admin123   总负责人（全站数据）
echo   weiyixiao  / admin123   运维人员（设备与工单）
echo   customer1  / admin123   用电客户（只看自己）
echo.
echo   关闭本窗口不会停服务；要停请运行 stop.bat
echo ==================================================
start "" http://127.0.0.1:3000
echo.
pause
