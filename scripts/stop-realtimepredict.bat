@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion



echo 未找到窗口，尝试通过Python进程终止...
wmic process where "name='python.exe' and commandline like '%%zhonglengTower.py%%'" call terminate >nul 2>&1
if !errorlevel! equ 0 (
    echo 已通过进程终止。
    exit /b 0
) else (
    echo 未找到相关进程。
    exit /b 1
)
