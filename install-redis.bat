@echo off
title Install Redis 5.0.14.1 as Windows Service
cd /d F:\Redis

echo [1/4] Installing Redis service...
redis-server.exe --service-install redis.windows-service.conf --service-name Redis
if errorlevel 1 (
    echo [ERROR] Service install failed. Run this bat as Administrator.
    pause
    exit /b 1
)

echo [2/4] Starting Redis service...
redis-server.exe --service-start --service-name Redis
if errorlevel 1 (
    echo [ERROR] Service start failed.
    pause
    exit /b 1
)

echo [3/4] Verifying connection...
timeout /t 3 /nobreak >nul
redis-cli.exe ping

echo [4/4] Version:
redis-cli.exe info server | findstr redis_version

echo.
echo ============================================
echo   Done. Redis 5.0.14.1 running on port 6379
echo   (manage it in: Computer Management - Services)
echo ============================================
pause
