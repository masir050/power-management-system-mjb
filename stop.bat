@echo off
REM 停止前端与 6 个后端服务：按端口反查 PID
chcp 65001 >nul
setlocal

for %%P in (8080 8081 8082 8083 8084 8088 3000) do (
  for /f "tokens=5" %%I in ('netstat -ano ^| findstr ":%%P " ^| findstr LISTENING') do (
    taskkill /F /PID %%I >nul 2>&1 && echo 已停止端口 %%P 上的进程 %%I
  )
)
echo.
echo 已清理完毕。
timeout /t 3 /nobreak >nul
