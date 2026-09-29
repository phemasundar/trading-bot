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

echo Stopping %SERVICE_NAME% Windows Service...
"!NSSM_EXE!" stop %SERVICE_NAME%

:: Check and release port 8080 if lingering
timeout /t 2 /nobreak >nul
for /f "tokens=5" %%a in ('netstat -aon 2^>nul ^| findstr :8080 ^| findstr LISTENING') do (
    echo Freeing port 8080 (PID: %%a)...
    taskkill /f /pid %%a >nul 2>&1
)

echo %SERVICE_NAME% stopped.
pause
