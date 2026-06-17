@echo off
REM bookKeeping v2.0.0 一键编译脚本
REM 需要 JDK 17+ 和 Android SDK，双击运行

echo === bookKeeping v2.0.0 Build Script ===
echo.

cd /d "%~dp0"

where java >/dev/null 2>/dev/null
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] 未找到 JDK，请先安装 JDK 17+
    echo 下载: https://adoptium.net/temurin/releases/?version=17
    pause
    exit /b 1
)

echo [OK] JDK found
java -version 2>&1 | findstr "version"

call gradlew assembleRelease
if %ERRORLEVEL% NEQ 0 goto error

mkdir releases 2>/dev/null
copy /Y app\build\outputs\flutter-apk\app-release.apk releases\bookKeeping-v2.0.0.apk 2>/dev/null
copy /Y app\build\outputs\apk\release\app-release.apk releases\bookKeeping-v2.0.0.apk 2>/dev/null

echo.
echo ============================================
echo [OK] 编译完成！
echo APK: releases\bookKeeping-v2.0.0.apk
echo ============================================
pause
exit /b 0

:error
echo [ERROR] 编译失败
pause
exit /b 1
