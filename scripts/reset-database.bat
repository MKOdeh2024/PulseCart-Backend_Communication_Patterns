@echo off
REM Script to reset the database and fix Flyway migration issues

echo ========================================
echo Resetting Database for Flash Sale App
echo ========================================
echo.

echo Step 1: Checking if Docker is running...
docker ps >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker is not running!
    echo Please start Docker Desktop and run this script again.
    pause
    exit /b 1
)
echo Docker is running ✓
echo.

echo Step 2: Stopping application if running...
taskkill /F /IM java.exe >nul 2>&1
echo Application stopped (if it was running)
echo.

echo Step 3: Dropping existing tables...
docker exec -it pulsecart-postgres psql -U ecommerce -d ecommerce -c "DROP TABLE IF EXISTS flyway_schema_history CASCADE;"
docker exec -it pulsecart-postgres psql -U ecommerce -d ecommerce -c "DROP TABLE IF EXISTS product CASCADE;"
echo Tables dropped ✓
echo.

echo Step 4: Database reset complete!
echo.
echo You can now run the application:
echo   cd PulseCart-Backend_Communication_Patterns
echo   mvn spring-boot:run
echo.
echo Flyway will recreate all tables with the latest migrations.
echo.
pause
