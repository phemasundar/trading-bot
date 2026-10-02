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
echo   Uninstalling %SERVICE_NAME% Windows Service
echo ===================================================

echo Stopping service %SERVICE_NAME%...
"!NSSM_EXE!" stop %SERVICE_NAME% >nul 2>&1

:: Free port 8080 if lingering
timeout /t 2 /nobreak >nul
for /f "tokens=5" %%a in ('netstat -aon 2^>nul ^| findstr :8080 ^| findstr LISTENING') do (
    taskkill /f /pid %%a >nul 2>&1
)

echo Removing service %SERVICE_NAME%...
"!NSSM_EXE!" remove %SERVICE_NAME% confirm

echo.
echo %SERVICE_NAME% service has been uninstalled.
pause
