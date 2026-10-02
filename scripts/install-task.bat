@echo off
setlocal

set "TASK_NAME=TradingBotTask"
set "PROJECT_DIR=C:\Projects\trading-bot"

echo ===================================================
echo   Installing %TASK_NAME% in Windows Task Scheduler
echo   (Starts automatically whenever you log into Windows)
echo ===================================================

:: Remove existing task if present
schtasks /delete /tn "%TASK_NAME%" /f >nul 2>&1

:: Create scheduled task on logon
schtasks /create /tn "%TASK_NAME%" /tr "\"%PROJECT_DIR%\scripts\run-bot.bat\"" /sc onlogon /rl HIGHEST /f

if %errorLevel% equ 0 (
    echo.
    echo [SUCCESS] Task "%TASK_NAME%" created!
    echo Starting task now...
    schtasks /run /tn "%TASK_NAME%"
) else (
    echo.
    echo [ERROR] Failed to create scheduled task.
)

pause
