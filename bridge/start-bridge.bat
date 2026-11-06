@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist node_modules (
  echo Устанавливаю зависимости...
  call npm install
)
set /p "NICK=Ваш ник в TikTok (без @): "
node bridge.js %NICK%
pause
