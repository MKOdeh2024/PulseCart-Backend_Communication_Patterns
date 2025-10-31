#!/bin/bash

# Script to reset the database and fix Flyway migration issues

echo "========================================"
echo "Resetting Database for Flash Sale App"
echo "========================================"
echo ""

echo "Step 1: Checking if Docker is running..."
if ! docker ps > /dev/null 2>&1; then
    echo "ERROR: Docker is not running!"
    echo "Please start Docker and run this script again."
    exit 1
fi
echo "Docker is running ✓"
echo ""

echo "Step 2: Dropping existing tables..."
docker exec -it pulsecart-postgres psql -U ecommerce -d ecommerce -c "DROP TABLE IF EXISTS flyway_schema_history CASCADE;"
docker exec -it pulsecart-postgres psql -U ecommerce -d ecommerce -c "DROP TABLE IF EXISTS product CASCADE;"
echo "Tables dropped ✓"
echo ""

echo "Step 3: Database reset complete!"
echo ""
echo "You can now run the application:"
echo "  cd PulseCart-Backend_Communication_Patterns"
echo "  mvn spring-boot:run"
echo ""
echo "Flyway will recreate all tables with the latest migrations."
echo ""
