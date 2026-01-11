@echo off
echo ========================================
echo Stopping All Collaborative Editing Services
echo ========================================
echo.

echo Finding Java processes on service ports...
echo.

REM Find and kill processes on each port
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8761" ^| findstr "LISTENING"') do (
    echo Stopping process on port 8761 (PID: %%a)
    taskkill /F /PID %%a >nul 2>&1
)

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
    echo Stopping process on port 8080 (PID: %%a)
    taskkill /F /PID %%a >nul 2>&1
)

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8081" ^| findstr "LISTENING"') do (
    echo Stopping process on port 8081 (PID: %%a)
    taskkill /F /PID %%a >nul 2>&1
)

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8084" ^| findstr "LISTENING"') do (
    echo Stopping process on port 8084 (PID: %%a)
    taskkill /F /PID %%a >nul 2>&1
)

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8083" ^| findstr "LISTENING"') do (
    echo Stopping process on port 8083 (PID: %%a)
    taskkill /F /PID %%a >nul 2>&1
)

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3000" ^| findstr "LISTENING"') do (
    echo Stopping process on port 3000 (PID: %%a)
    taskkill /F /PID %%a >nul 2>&1
)

REM Also kill Node.js processes that might be running the frontend
echo.
echo Stopping Node.js frontend processes...
taskkill /FI "IMAGENAME eq node.exe" /T /F >nul 2>&1
if %errorlevel% equ 0 (
    echo Node.js processes stopped.
) else (
    echo No Node.js processes found or already stopped.
)

echo.
echo Waiting 3 seconds for processes to terminate...
timeout /t 3 /nobreak >nul

echo.
echo Checking for remaining Java processes...
tasklist | findstr /i "java.exe" >nul
if %errorlevel% equ 0 (
    echo WARNING: Some Java processes may still be running.
    echo You may need to close service windows manually.
) else (
    echo All Java processes stopped.
)

echo.
echo Checking for remaining Node.js processes on port 3000...
netstat -ano | findstr ":3000" | findstr "LISTENING" >nul
if %errorlevel% equ 0 (
    echo WARNING: Port 3000 is still in use. Attempting to kill again...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3000" ^| findstr "LISTENING"') do (
        echo Force killing process on port 3000 (PID: %%a)
        taskkill /F /PID %%a >nul 2>&1
    )
    timeout /t 1 /nobreak >nul
) else (
    echo Port 3000 is free.
)

echo.
echo Closing all service command windows...
echo.

REM Close cmd windows by their titles
taskkill /FI "WINDOWTITLE eq Service Discovery*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq API Gateway*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq User Management*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Document Editing*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Version Control*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Frontend*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq *Frontend*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq *React*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq *npm*" /T /F >nul 2>&1

REM Also kill any remaining cmd.exe processes that might be service windows
REM Use PowerShell to get current PID and kill all other cmd.exe processes
for /f "tokens=2" %%a in ('powershell -Command "[System.Diagnostics.Process]::GetCurrentProcess().Id"') do set CURRENT_PID=%%a
setlocal enabledelayedexpansion
for /f "tokens=2" %%a in ('tasklist /FI "IMAGENAME eq cmd.exe" /FO LIST ^| findstr "PID:"') do (
    if not "%%a"=="!CURRENT_PID!" (
        taskkill /F /PID %%a >nul 2>&1
    )
)
endlocal

echo All service windows closed.
echo.

REM Stop MySQL containers if Docker is available
where docker >nul 2>&1
if %errorlevel% equ 0 (
    echo Stopping MySQL containers...
    docker-compose stop mysql-user mysql-document mysql-version 2>nul
    if %errorlevel% neq 0 (
        docker compose stop mysql-user mysql-document mysql-version 2>nul
        if %errorlevel% equ 0 (
            echo MySQL containers stopped.
        ) else (
            echo MySQL containers may not be running or already stopped.
        )
    ) else (
        echo MySQL containers stopped.
    )
    echo.
) else (
    echo Docker not found. Skipping MySQL container shutdown.
    echo Please stop MySQL containers manually if needed.
    echo.
)

echo ========================================
echo Done! Ports should now be free.
echo ========================================
echo.
pause

