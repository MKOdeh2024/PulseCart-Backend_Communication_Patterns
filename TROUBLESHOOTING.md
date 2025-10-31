# Troubleshooting Guide
## E-Commerce Flash Sale System

---

## ❌ Issue: Flyway Migration Checksum Mismatch

### Error Message:
```
Migration checksum mismatch for migration version 1
-> Applied to database : -40732926
-> Resolved locally    : -239577751
Either revert the changes to the migration, or run repair to update the schema history.
```

### Cause:
This happens when a Flyway migration file is modified **after** it has already been applied to the database. Flyway tracks checksums to ensure migrations haven't changed.

### ✅ Solutions (Choose ONE)

---

#### **Solution 1: Use Reset Script (RECOMMENDED)**

The easiest way to fix this is to reset the database:

**Windows:**
```bash
cd C:\Users\Admin\Downloads\PulseCart-Backend_Communication_Patterns
scripts\reset-database.bat
```

**Linux/Mac:**
```bash
cd /path/to/PulseCart-Backend_Communication_Patterns
chmod +x scripts/reset-database.sh
./scripts/reset-database.sh
```

Then run the application:
```bash
cd PulseCart-Backend_Communication_Patterns
mvn spring-boot:run
```

---

#### **Solution 2: Manual Database Reset**

1. **Start Docker services:**
```bash
cd C:\Users\Admin\Downloads\PulseCart-Backend_Communication_Patterns
docker-compose up -d postgres redis rabbitmq
```

2. **Drop the problematic tables:**
```bash
docker exec -it pulsecart-postgres psql -U ecommerce -d ecommerce -c "DROP TABLE IF EXISTS flyway_schema_history CASCADE;"
docker exec -it pulsecart-postgres psql -U ecommerce -d ecommerce -c "DROP TABLE IF EXISTS product CASCADE;"
```

3. **Run the application:**
```bash
cd PulseCart-Backend_Communication_Patterns
mvn spring-boot:run
```

Flyway will automatically recreate all tables with the correct checksums.

---

#### **Solution 3: Disable Flyway Validation (TEMPORARY FIX)**

If you need to run the app quickly without fixing the database:

1. Open `application.yaml`
2. Find the line: `# validate-on-migrate: false`
3. Uncomment it (remove the `#`)

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    clean-disabled: false
    validate-on-migrate: false  # ← Uncomment this line
```

4. Run the application

⚠️ **Warning:** This skips validation and may cause issues. Only use for quick testing!

---

#### **Solution 4: Flyway Repair Command**

Use Maven to run Flyway repair:

```bash
cd PulseCart-Backend_Communication_Patterns
mvn flyway:repair
```

Then run the application:
```bash
mvn spring-boot:run
```

---

## ❌ Issue: Docker is Not Running

### Error Message:
```
The system cannot find the file specified: dockerDesktopLinuxEngine
```

### Solution:

1. **Start Docker Desktop**
   - Windows: Search for "Docker Desktop" and launch it
   - Mac: Open Docker Desktop from Applications
   - Linux: `sudo systemctl start docker`

2. **Verify Docker is running:**
```bash
docker ps
```

You should see a list of containers (or empty list if none running).

3. **Start the required services:**
```bash
docker-compose up -d postgres redis rabbitmq
```

4. **Verify services are running:**
```bash
docker-compose ps
```

Expected output:
```
NAME                  STATUS      PORTS
pulsecart-postgres    running     0.0.0.0:5432->5432/tcp
pulsecart-redis       running     0.0.0.0:6379->6379/tcp
pulsecart-rabbitmq    running     0.0.0.0:5672->5672/tcp, 0.0.0.0:15672->15672/tcp
```

---

## ❌ Issue: Connection Refused to Redis

### Error Message:
```
RedisConnectionFailureException: Unable to connect to Redis
Connection refused: localhost:6379
```

### Solution:

1. **Check if Redis is running:**
```bash
docker-compose ps redis
```

2. **If not running, start it:**
```bash
docker-compose up -d redis
```

3. **Test Redis connection:**
```bash
docker exec -it pulsecart-redis redis-cli ping
```

Expected output: `PONG`

4. **If still failing, restart Redis:**
```bash
docker-compose restart redis
```

---

## ❌ Issue: Connection Refused to RabbitMQ

### Error Message:
```
AmqpConnectException: Connection refused
java.net.ConnectException: Connection refused: localhost:5672
```

### Solution:

1. **Check if RabbitMQ is running:**
```bash
docker-compose ps rabbitmq
```

2. **If not running, start it:**
```bash
docker-compose up -d rabbitmq
```

3. **Wait for RabbitMQ to fully start (takes ~30 seconds):**
```bash
docker logs -f pulsecart-rabbitmq
```

Look for: `Server startup complete`

4. **Test RabbitMQ connection:**
```bash
docker exec pulsecart-rabbitmq rabbitmqctl status
```

5. **Access RabbitMQ Management UI:**
Open browser: http://localhost:15672
Login: `guest` / `guest`

---

## ❌ Issue: Port Already in Use

### Error Message:
```
Bind for 0.0.0.0:5432 failed: port is already allocated
```

### Solution:

**Find what's using the port:**

Windows:
```bash
netstat -ano | findstr :5432
taskkill /PID <PID> /F
```

Linux/Mac:
```bash
lsof -i :5432
kill -9 <PID>
```

**Or change the port in docker-compose.yml:**
```yaml
services:
  postgres:
    ports:
      - "5433:5432"  # Use 5433 on host instead
```

Then update `application.yaml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/ecommerce
```

---

## ❌ Issue: Maven Build Failed

### Error Message:
```
[ERROR] Failed to execute goal on project: Could not resolve dependencies
```

### Solution:

1. **Clean and reinstall:**
```bash
mvn clean install -U
```

2. **If still failing, delete .m2 cache:**
```bash
# Windows
rmdir /s %USERPROFILE%\.m2\repository\com\example

# Linux/Mac
rm -rf ~/.m2/repository/com/example
```

3. **Try again:**
```bash
mvn clean install
```

---

## ❌ Issue: Lombok Not Working

### Error Message:
```
cannot find symbol: method getProductId()
```

### Solution:

1. **Verify Lombok is in pom.xml:**
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

2. **Enable annotation processing in IDE:**

**IntelliJ IDEA:**
- File → Settings → Build, Execution, Deployment → Compiler → Annotation Processors
- Check "Enable annotation processing"

**Eclipse:**
- Project → Properties → Java Compiler → Annotation Processing
- Enable annotation processing

3. **Install Lombok plugin:**
- IntelliJ: File → Settings → Plugins → Search "Lombok" → Install
- Eclipse: Help → Install New Software → Add Lombok

4. **Rebuild project:**
```bash
mvn clean compile
```

---

## ❌ Issue: Application Starts but No Endpoints Work

### Error Message:
```
404 Not Found
or
Whitelabel Error Page
```

### Solution:

1. **Check if application started successfully:**
Look for in logs:
```
Started PulseCartBackendCommunicationPatternsApplication in X seconds
```

2. **Verify correct port:**
Default is 8080. Try:
```bash
curl http://localhost:8080/api/v1/sync/health
```

3. **Check if port is correct in logs:**
```
Tomcat started on port(s): 8080 (http)
```

4. **Test with correct endpoints:**
- Sync: `http://localhost:8080/api/v1/sync/purchase`
- Async: `http://localhost:8080/api/v1/async/purchase`

---

## ❌ Issue: Out of Memory Error

### Error Message:
```
java.lang.OutOfMemoryError: Java heap space
```

### Solution:

1. **Increase JVM memory:**
```bash
# Windows
set MAVEN_OPTS=-Xmx2048m -Xms512m
mvn spring-boot:run

# Linux/Mac
export MAVEN_OPTS="-Xmx2048m -Xms512m"
mvn spring-boot:run
```

2. **Or add to application.yaml:**
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 10
```

---

## 🔧 Complete Reset (Nuclear Option)

If nothing works, reset everything:

```bash
# Stop all containers
docker-compose down -v

# Remove all containers and volumes
docker system prune -a --volumes

# Clean Maven
mvn clean

# Restart Docker Desktop

# Start fresh
docker-compose up -d
mvn clean install
mvn spring-boot:run
```

---

## 📞 Getting Help

If you're still stuck:

1. **Check logs:**
```bash
# Application logs
mvn spring-boot:run > app.log 2>&1

# Docker logs
docker-compose logs postgres
docker-compose logs redis
docker-compose logs rabbitmq
```

2. **Verify environment:**
```bash
java -version    # Should be 17+
mvn -version     # Should be 3.8+
docker --version # Should be running
```

3. **Check configuration:**
```bash
# Verify application.yaml syntax
cat src/main/resources/application.yaml

# Check for typos in property names
```

---

## ✅ Health Checks

**Quick verification that everything works:**

1. **Docker services:**
```bash
docker-compose ps
# All should show "running"
```

2. **Application:**
```bash
curl http://localhost:8080/api/v1/sync/health
# Should return: "Sync Purchase Service is running"
```

3. **Redis:**
```bash
docker exec -it pulsecart-redis redis-cli ping
# Should return: PONG
```

4. **RabbitMQ:**
```bash
curl -u guest:guest http://localhost:15672/api/overview
# Should return JSON with RabbitMQ stats
```

5. **PostgreSQL:**
```bash
docker exec -it pulsecart-postgres psql -U ecommerce -d ecommerce -c "SELECT COUNT(*) FROM product;"
# Should return: 5 (after data seeding)
```

---

## 🎯 Prevention Tips

1. **Never modify migration files** after they've been applied
2. **Always start Docker** before running the application
3. **Use the reset script** when switching branches or after schema changes
4. **Keep Docker Desktop running** during development
5. **Check logs first** before asking for help

---

**Last Updated:** October 31, 2025
