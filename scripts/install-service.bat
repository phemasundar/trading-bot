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
set "PROJECT_DIR=C:\Projects\trading-bot"
set "NSSM_EXE=C:\Installations\nssm\nssm.exe"

if not exist "!NSSM_EXE!" (
    echo [ERROR] NSSM executable not found at !NSSM_EXE!.
    echo Please ensure NSSM is placed at C:\Installations\nssm\nssm.exe.
    pause
    exit /b 1
)

echo ===================================================
echo   Installing %SERVICE_NAME% as a Windows Service
echo ===================================================

:: Ensure logs directory exists
if not exist "%PROJECT_DIR%\logs" mkdir "%PROJECT_DIR%\logs"

:: Check if service already exists; if so, stop and remove it first
sc query %SERVICE_NAME% >nul 2>&1
if %errorLevel% equ 0 (
    echo Existing %SERVICE_NAME% service found. Stopping and removing...
    "!NSSM_EXE!" stop %SERVICE_NAME% >nul 2>&1
    "!NSSM_EXE!" remove %SERVICE_NAME% confirm >nul 2>&1
)

:: Install service using cmd.exe running run-bot.bat
"!NSSM_EXE!" install %SERVICE_NAME% "C:\Windows\System32\cmd.exe" "/c \"\"%PROJECT_DIR%\scripts\run-bot.bat\"\""
"!NSSM_EXE!" set %SERVICE_NAME% AppDirectory "%PROJECT_DIR%"
"!NSSM_EXE!" set %SERVICE_NAME% DisplayName "Trading Bot Service"
"!NSSM_EXE!" set %SERVICE_NAME% Description "Automated Options Trading Bot Spring Boot Application"
"!NSSM_EXE!" set %SERVICE_NAME% Start SERVICE_AUTO_START
"!NSSM_EXE!" set %SERVICE_NAME% AppStdout "%PROJECT_DIR%\logs\service-stdout.log"
"!NSSM_EXE!" set %SERVICE_NAME% AppStderr "%PROJECT_DIR%\logs\service-stderr.log"
"!NSSM_EXE!" set %SERVICE_NAME% AppRotateFiles 1
"!NSSM_EXE!" set %SERVICE_NAME% AppRotateOnline 1
"!NSSM_EXE!" set %SERVICE_NAME% AppRotateBytes 10485760

echo.
echo Starting %SERVICE_NAME% service...
"!NSSM_EXE!" start %SERVICE_NAME%

echo.
echo ===================================================
echo [SUCCESS] %SERVICE_NAME% installed and started!
echo It will now automatically start every time Windows boots.
echo Logs are saved to: %PROJECT_DIR%\logs\
echo ===================================================
pause
