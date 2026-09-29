@echo off
setlocal EnableDelayedExpansion

set "SERVICE_NAME=TradingBot"
set "NSSM_EXE=C:\Installations\nssm\nssm.exe"

echo ===================================================
echo   Trading Bot Service Status
echo ===================================================

if exist "!NSSM_EXE!" (
    echo Service State:
    "!NSSM_EXE!" status %SERVICE_NAME% 2>nul
    if %errorLevel% neq 0 (
        echo [INFO] Service "%SERVICE_NAME%" is not currently installed.
    )
) else (
    sc query %SERVICE_NAME% 2>nul
)

echo.
echo Port 8080 Status:
netstat -aon | findstr :8080 | findstr LISTENING
if %errorLevel% equ 0 (
    echo [OK] Application is actively listening on port 8080.
) else (
    echo [WARNING] No process is currently listening on port 8080.
)

echo.
if exist "C:\Projects\trading-bot\logs\trading-bot.log" (
    echo Last 10 lines of application log:
    echo ---------------------------------------------------
    powershell -Command "Get-Content -Path 'C:\Projects\trading-bot\logs\trading-bot.log' -Tail 10 -ErrorAction SilentlyContinue"
    echo ---------------------------------------------------
)
echo ===================================================
pause
