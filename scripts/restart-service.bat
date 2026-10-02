@echo off
setlocal EnableDelayedExpansion

:: Check for Administrator privileges; if not elevated, request UAC elevation
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo Administrator privileges required. Requesting elevation...
    powershell -Command "Start-Process cmd.exe -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
    exit /b
)

set "SERVICE_NAME=TradingBot"
set "NSSM_EXE=C:\Installations\nssm\nssm.exe"

echo ===================================================
echo   Restarting %SERVICE_NAME% Windows Service
echo ===================================================

echo [1/3] Stopping service %SERVICE_NAME%...
"!NSSM_EXE!" stop %SERVICE_NAME% >nul 2>&1

:: Wait 3 seconds for graceful shutdown
timeout /t 3 /nobreak >nul

echo [2/3] Checking for any lingering process on port 8080...
for /f "tokens=5" %%a in ('netstat -aon 2^>nul ^| findstr :8080 ^| findstr LISTENING') do (
    echo Terminating lingering process on port 8080 (PID: %%a)...
    taskkill /f /pid %%a >nul 2>&1
)

echo [3/3] Starting service %SERVICE_NAME%...
"!NSSM_EXE!" start %SERVICE_NAME%

echo.
echo ===================================================
echo [SUCCESS] %SERVICE_NAME% restarted successfully!
echo Status:
"!NSSM_EXE!" status %SERVICE_NAME%
echo ===================================================
pause
