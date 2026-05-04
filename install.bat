@echo off
chcp 65001 >nul
echo ========================================
echo CarListener 安装脚本
echo ========================================
echo.

REM 检查 ADB 是否可用
where adb >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 ADB 命令
    echo 请确保已安装 Android SDK Platform-Tools 并添加到 PATH
    echo.
    pause
    exit /b 1
)

REM 检查 APK 是否存在
if not exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo [错误] 未找到 APK 文件
    echo 请先运行构建命令：gradlew.bat assembleDebug
    echo.
    pause
    exit /b 1
)

echo [信息] 正在检查设备连接...
adb devices | findstr /r "[0-9a-zA-Z].*device$" >nul
if %errorlevel% neq 0 (
    echo [错误] 未检测到连接的 Android 设备
    echo 请确保：
    echo 1. 已通过 USB 连接 Android 设备
    echo 2. 已在设备上启用 USB 调试
    echo.
    pause
    exit /b 1
)

echo [信息] 检测到设备，开始安装...
adb install -r "app\build\outputs\apk\debug\app-debug.apk"

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo [成功] 应用安装完成！
    echo ========================================
    echo.
    echo 请在设备上打开 CarListener 应用并授予必要权限
    echo.
) else (
    echo.
    echo ========================================
    echo [失败] 应用安装失败
    echo ========================================
    echo.
)

pause
