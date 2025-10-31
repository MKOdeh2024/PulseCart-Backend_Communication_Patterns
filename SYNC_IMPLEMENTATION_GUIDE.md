# Synchronous Implementation Guide
## E-Commerce Flash Sale System - Sync Approach

### Overview
This implementation uses **synchronous HTTP request-response** with **Redis atomic operations** to handle flash sale purchases and prevent overselling.

---

## Architecture

### Key Components

1. **PurchaseController** (`/api/v1/sync/*`)
   - Synchronous REST endpoints
   - Request validation with `@Valid`
   - Proper error handling

2. **InventoryService**
   - Redis DECR for atomic stock management
   - Database synchronization
   - Race condition prevention

3. **Redis**
   - Atomic operations (DECR/INCR)
   - Key pattern: `product:stock:{productId}`
   - Prevents overselling through atomic decrements

4. **PostgreSQL**
   - Product master data
   - Eventual consistency with Redis
   - Transaction support

---

## How It Prevents Overselling

### Redis Atomic Operations

```java
// Step 1: Check current stock
Integer currentStock = getCurrentStockFromRedis(productId);
if (currentStock < quantity) {
    return OUT_OF_STOCK;
}

// Step 2: Atomic decrement (THIS IS THE KEY!)
Long remaining = redisTemplate.opsForValue().decrement(stockKey, quantity);

// Step 3: Race condition check
if (remaining < 0) {
    // Rollback if we went negative
    redisTemplate.opsForValue().increment(stockKey, quantity);
    return OUT_OF_STOCK;
}

// Step 4: Success - stock reserved atomically
return SUCCESS;
```

### Why This Works

1. **Atomic Operation**: Redis DECR is single-threaded and atomic
2. **No Race Conditions**: Even with 100,000 concurrent requests, Redis processes them sequentially
3. **Negative Check**: Extra safety to rollback if somehow we went negative
4. **Database Sync**: Eventually syncs to PostgreSQL for persistence

---

## API Endpoints

### 1. Purchase Product
```bash
POST /api/v1/sync/purchase
Content-Type: application/json

{
  "productId": 1,
  "quantity": 1,
  "userId": 12345
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Purchase successful",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "productId": 1,
  "quantity": 1,
  "remainingStock": 999,
  "timestamp": "2025-10-31T20:45:00"
}
```

**Out of Stock (409 Conflict):**
```json
{
  "success": false,
  "message": "Out of stock. Requested: 1, Available: 0",
  "orderId": null,
  "productId": 1,
  "quantity": 1,
  "remainingStock": 0,
  "timestamp": "2025-10-31T20:45:00"
}
```

### 2. Get Stock
```bash
GET /api/v1/sync/stock/1
```

**Response:**
```json
{
  "productId": 1,
  "stock": 999
}
```

### 3. Initialize Stock
```bash
POST /api/v1/sync/stock/init/1
```

### 4. Reset Stock (Testing Only)
```bash
PUT /api/v1/sync/stock/1/reset?stock=1000
```

### 5. Health Check
```bash
GET /api/v1/sync/health
```

---

## Setup Instructions

### Prerequisites
- Docker and Docker Compose
- Java 17+
- Maven 3.8+

### Step 1: Start Infrastructure
```bash
cd C:\Users\Admin\Downloads\PulseCart-Backend_Communication_Patterns

# Start PostgreSQL and Redis
docker-compose up -d postgres redis

# Verify services are running
docker-compose ps
```

### Step 2: Build and Run Application
```bash
cd PulseCart-Backend_Communication_Patterns

# Build the application
mvn clean install -DskipTests

# Run the application
mvn spring-boot:run
```

### Step 3: Verify Setup

The application will automatically:
1. Create database tables via Flyway migration
2. Seed 5 products with stock (1000, 500, 100, 200, 2000)
3. Initialize Redis with stock quantities

Check logs for:
```
=== Starting Data Seeding ===
Created product: iPhone 15 Pro - Flash Sale with stock: 1000
...
=== Data Seeding Complete ===
```

---

## Testing the Implementation

### Manual Testing with cURL

#### 1. Health Check
```bash
curl http://localhost:8080/api/v1/sync/health
```

#### 2. Check Initial Stock
```bash
curl http://localhost:8080/api/v1/sync/stock/1
```

#### 3. Make a Purchase
```bash
curl -X POST http://localhost:8080/api/v1/sync/purchase \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 1}'
```

#### 4. Verify Stock Decreased
```bash
curl http://localhost:8080/api/v1/sync/stock/1
```

#### 5. Test Out of Stock
```bash
# Reset stock to 0
curl -X PUT "http://localhost:8080/api/v1/sync/stock/1/reset?stock=0"

# Try to purchase
curl -X POST http://localhost:8080/api/v1/sync/purchase \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 1}'

# Should return 409 Conflict with "Out of stock" message
```

#### 6. Test Validation
```bash
# Invalid quantity (less than 1)
curl -X POST http://localhost:8080/api/v1/sync/purchase \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 0}'

# Missing productId
curl -X POST http://localhost:8080/api/v1/sync/purchase \
  -H "Content-Type: application/json" \
  -d '{"quantity": 1}'
```

---

## Concurrent Testing

### Simple Concurrent Test (PowerShell)
```powershell
# Reset stock to 100
curl -X PUT "http://localhost:8080/api/v1/sync/stock/1/reset?stock=100"

# Run 150 concurrent purchase requests (should sell only 100)
1..150 | ForEach-Object -Parallel {
    $result = Invoke-RestMethod -Method POST `
        -Uri "http://localhost:8080/api/v1/sync/purchase" `
        -ContentType "application/json" `
        -Body '{"productId": 1, "quantity": 1}' `
        -ErrorAction SilentlyContinue

    Write-Host "$($result.success) - $($result.message)"
} -ThrottleLimit 50

# Check final stock (should be 0)
curl http://localhost:8080/api/v1/sync/stock/1
```

### Expected Result
- Exactly 100 purchases should succeed
- Exactly 50 purchases should fail with "Out of stock"
- Final stock should be 0
- **NO negative stock** (this proves no overselling!)

---

## Performance Characteristics

### Synchronous Approach Traits

**Advantages:**
- Simple and straightforward
- Immediate feedback to user
- Easier to debug and monitor
- Strong consistency guaranteed

**Disadvantages:**
- Thread blocking (each request holds a thread)
- Limited by thread pool size (10-50 threads)
- Higher memory usage under load
- Longer response times under high load

### Expected Metrics (under 10K concurrent requests)

| Metric | Expected Value |
|--------|----------------|
| Avg Latency | 50-100ms |
| P95 Latency | 200-300ms |
| P99 Latency | 500-800ms |
| Throughput | 5,000-8,000 req/s |
| CPU Usage | 60-80% |
| Memory Usage | 400-600 MB |
| Error Rate | <0.1% |

---

## Redis Operations Log

Watch Redis operations in real-time:
```bash
# Connect to Redis container
docker exec -it pulsecart-redis redis-cli

# Monitor all commands
MONITOR

# In another terminal, make purchases and watch Redis operations
```

You'll see:
```
"DECR" "product:stock:1"
"DECR" "product:stock:1"
"DECR" "product:stock:1"
```

---

## Configuration

### Thread Pool (ThreadPoolConfig.java)
```java
Core threads: 10
Max threads: 50
Queue capacity: 100
```

### Redis Connection Pool (application.yaml)
```yaml
max-active: 8
max-idle: 8
min-idle: 2
```

---

## Troubleshooting

### Issue: "Connection refused" to Redis
**Solution:**
```bash
docker-compose up -d redis
docker-compose ps  # Verify redis is running
```

### Issue: Stock not initialized
**Solution:**
```bash
# Manually initialize stock
curl -X POST http://localhost:8080/api/v1/sync/stock/init/1
```

### Issue: Stock mismatch between Redis and Database
**Solution:**
```bash
# Check Redis
docker exec -it pulsecart-redis redis-cli
GET product:stock:1

# Check Database
docker exec -it pulsecart-postgres psql -U ecommerce -d ecommerce
SELECT * FROM product WHERE id = 1;

# Reset if needed
curl -X PUT "http://localhost:8080/api/v1/sync/stock/1/reset?stock=1000"
```

---

## Next Steps

1. ✅ **Synchronous implementation complete**
2. ⏭️ **Implement asynchronous approach** (RabbitMQ + SSE)
3. ⏭️ **Add load balancer** (nginx/HAProxy)
4. ⏭️ **Create load testing scripts** (k6)
5. ⏭️ **Run performance tests** and collect metrics
6. ⏭️ **Comparative analysis** (sync vs async)
7. ⏭️ **Documentation** (README, ARCHITECTURE, ANALYSIS_REPORT)

---

## Code Quality Notes

✅ **Implemented:**
- Proper logging with SLF4J
- Request validation with Bean Validation
- Global exception handler
- Environment variables for configuration
- Transactional database operations
- Comprehensive error handling

✅ **Best Practices:**
- Lombok for reducing boilerplate
- DTOs for request/response
- Repository pattern for data access
- Service layer for business logic
- Atomic operations for concurrency
- Clean code with comments

---

**Status:** ✅ **Synchronous Implementation Complete and Ready for Testing**
