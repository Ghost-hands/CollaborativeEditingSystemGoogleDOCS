@echo off
setlocal enabledelayedexpansion
echo ========================================
echo Starting Collaborative Editing System
echo ========================================
echo.
echo Script location: %~dp0
echo.

REM Add Maven to PATH
set "PATH=%USERPROFILE%\Tools\apache-maven-3.9.6\bin;%PATH%"

REM Change to script directory
cd /d "%~dp0"

REM Check if Docker is available
where docker >nul 2>&1
if %errorlevel% neq 0 (
    echo WARNING: Docker not found in PATH. Skipping MySQL startup.
    echo Please ensure MySQL databases are running on ports 3307, 3308, and 3309.
    echo.
    goto :skip_docker
)

REM Start MySQL databases using Docker Compose
echo [Step 1/8] Starting MySQL Databases...
echo.
echo Starting MySQL containers (ports 3307, 3308, 3309)...

REM Try docker-compose (older version) first, then docker compose (newer version)
docker-compose up -d mysql-user mysql-document mysql-version
if not errorlevel 1 goto :mysql_success
echo Trying alternative docker compose command...
docker compose up -d mysql-user mysql-document mysql-version
if not errorlevel 1 goto :mysql_success
echo WARNING: Failed to start MySQL containers. They may already be running.
echo Continuing anyway...
goto :mysql_done

:mysql_success
echo Waiting for MySQL databases to be ready...
timeout /t 10 /nobreak >nul
echo MySQL databases started.

:mysql_done
echo.

:skip_docker

REM Build common module
echo [Step 2/8] Building Common Module...
cd common
call mvn clean install -DskipTests
if %errorlevel% neq 0 (
    echo ERROR: Failed to build common module
    pause
    exit /b 1
)
cd ..
echo.

REM Start Service Discovery
echo [Step 3/8] Starting Service Discovery (Port 8761)...
start "Service Discovery" cmd /k "cd /d %~dp0service-discovery && mvn spring-boot:run"
timeout /t 10 /nobreak >nul

REM Start API Gateway
echo [Step 4/8] Starting API Gateway (Port 8080)...
start "API Gateway" cmd /k "cd /d %~dp0api-gateway && mvn spring-boot:run"
timeout /t 10 /nobreak >nul

REM Start User Management
echo [Step 5/8] Starting User Management (Port 8081)...
start "User Management" cmd /k "cd /d %~dp0user-management-service && mvn spring-boot:run"
timeout /t 10 /nobreak >nul

REM Start Document Editing
echo [Step 6/8] Starting Document Editing (Port 8084)...
start "Document Editing" cmd /k "cd /d %~dp0document-editing-service && mvn spring-boot:run"
timeout /t 10 /nobreak >nul

REM Start Version Control
echo [Step 7/8] Starting Version Control (Port 8083)...
start "Version Control" cmd /k "cd /d %~dp0version-control-service && mvn spring-boot:run"
timeout /t 10 /nobreak >nul

echo.
echo Waiting 30 seconds for services to start...
timeout /t 30 /nobreak >nul

REM Start Frontend
echo [Step 8/8] Starting Frontend (Port 3000)...
if not exist "frontend\node_modules" (
    echo Installing frontend dependencies...
    cd frontend
    call npm install
    cd ..
)
cd frontend
start "Frontend" cmd /k "npm run dev"
cd ..

echo.
echo ========================================
echo All services started!
echo ========================================
echo.
echo Service Endpoints:
echo   Frontend:              http://localhost:3000
echo   API Gateway:           http://localhost:8080
echo   Service Discovery:     http://localhost:8761
echo.
echo Microservices:
echo   User Management:       http://localhost:8081
echo   Document Editing:      http://localhost:8084
echo   Version Control:       http://localhost:8083
echo.
echo Internal REST APIs (for inter-service communication):
echo   User Management:       http://localhost:8081/internal/users/**
echo   Document Editing:       http://localhost:8084/internal/documents/**
echo   Version Control:        http://localhost:8083/internal/versions/**
echo.
echo Note: Internal APIs are routed through the API Gateway at:
echo   http://localhost:8080/internal/**
echo.
echo Check the service windows for status.
echo.
pause

