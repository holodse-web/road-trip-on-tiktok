@echo off
chcp 65001 >nul
cd /d "%~dp0"
set "JAVA=%APPDATA%\.minecraft\runtime\java-runtime-epsilon\windows\java-runtime-epsilon\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java"
echo Java: %JAVA%
"%JAVA%" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 Build.java %*
echo.
pause
