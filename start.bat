@echo off
REM ============================================================
REM 一键启动：5 个后端服务 + 前端
REM
REM 用法：双击本文件，或在命令行执行 start.bat
REM
REM 为什么用 bat 而不是让自动化工具去 nohup：
REM   工具会在每次命令结束时回收它启动的子进程，java 被连带杀掉，
REM   浏览器一刷新就连不上 8080。双击运行 = 你的桌面会话托管，
REM   关掉这个窗口服务照跑，要停请用 stop.bat。
REM ============================================================
chcp 65001 >nul
setlocal enabledelayedexpansion
cd /d "%~dp0"

REM ---- 数据库口令：环境变量没有就提示输入 ----
if "%MYSQL_PASSWORD%"=="" set /p MYSQL_PASSWORD=请输入 MySQL root 口令（直接回车表示空口令）:
set MYSQL_PASSWORD
REM 宿主进程可能注入 SERVER__PORT，会把 5 个服务挤到同一个端口
set SERVER__PORT=
set SERVER_PORT=

echo.
echo ===== 启动后端 =====
start "power-auth"    /min java -jar power-auth\target\power-auth-2.0.0.jar       --server.port=8081
start "power-device"  /min java -jar power-device\target\power-device-2.0.0.jar   --server.port=8082
start "power-billing" /min java -jar power-billing\target\power-billing-2.0.0.jar --server.port=8083
start "power-alert"   /min java -jar power-alert\target\power-alert-2.0.0.jar     --server.port=8084
start "power-gateway" /min java -jar power-gateway\target\power-gateway-2.0.0.jar --server.port=8080

echo 等待 5 个服务就绪…
set /a TRIES=0
:waitloop
set /a READY=0
for %%P in (8080 8081 8082 8083 8084) do (
  netstat -ano | findstr ":%%P " | findstr LISTENING >nul && set /a READY+=1
)
if !READY! GEQ 5 goto ready
set /a TRIES+=1
if !TRIES! GEQ 40 (
  echo [警告] 100 秒内只有 !READY!/5 个端口就绪，请查看 logs\ 下对应服务的日志
  goto frontend
)
timeout /t 3 /nobreak >nul
goto waitloop

:ready
echo 5 个服务端口全部就绪。

:frontend
echo.
echo ===== 启动前端 =====
REM 用 pushd 而不是 cmd /c "cd /d ... && ..."，少一层引号嵌套，出错面更小
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
