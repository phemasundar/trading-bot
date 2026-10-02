@echo off
setlocal

set "TASK_NAME=TradingBotTask"

echo ===================================================
echo   Restarting %TASK_NAME% (Task Scheduler)
echo ===================================================

echo [1/3] Ending task...
schtasks /end /tn "%TASK_NAME%" >nul 2>&1

timeout /t 2 /nobreak >nul

echo [2/3] Freeing port 8080 if lingering...
for /f "tokens=5" %%a in ('netstat -aon 2^>nul ^| findstr :8080 ^| findstr LISTENING') do (
    taskkill /f /pid %%a >nul 2>&1
)

echo [3/3] Starting task...
schtasks /run /tn "%TASK_NAME%"

echo.
echo [SUCCESS] Task restarted!
pause
