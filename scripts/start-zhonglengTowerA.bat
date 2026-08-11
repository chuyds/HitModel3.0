@echo off
setlocal
chcp 65001 >nul
cd /d %~dp0

start "PY | MPC" cmd /k python zhonglengTowerA.py

exit /b
