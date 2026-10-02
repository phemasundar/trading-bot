@echo off
setlocal

:: Set working directory
cd /d "C:\Projects\trading-bot"

:: Configure Maven and Java environment variables
set "M2_HOME=C:\Installations\apache-maven-3.9.7"
set "MAVEN_HOME=C:\Installations\apache-maven-3.9.7"
set "PATH=%M2_HOME%\bin;C:\Program Files\Common Files\Oracle\Java\javapath;%PATH%"
set "USERPROFILE=C:\Users\HemaSundar"

echo [%DATE% %TIME%] Starting Trading Bot application via Maven...
call "%M2_HOME%\bin\mvn.cmd" -Dmaven.repo.local="C:\Users\HemaSundar\.m2\repository" -Duser.home="C:\Users\HemaSundar" spring-boot:run
