# E-Commerce Flash Sale System - PulseCart Backend

## Overview

This project implements a high-performance backend system for handling flash sales (like Black Friday) where 100,000+ users attempt to purchase limited inventory (1,000 items) within 5 minutes. The system demonstrates two communication patterns: synchronous HTTP request-response and asynchronous message queuing.

## Scenario

**Real-Life Context:** Your company runs flash sales where thousands of concurrent users try to purchase limited inventory within short time windows.

**Mission:** Build a backend system that handles:
- Product inventory management
- Purchase requests from thousands of concurrent users
- Payment processing queue
- Real-time stock updates to users

## Features Implemented

### 1. Request Handling Patterns
- **Synchronous HTTP** (`/api/v1/sync/purchase`) - Immediate response with order details
- **Asynchronous Message Queue** (`/api/v1/async/purchase`) - Queued processing with tracking ID

### 2. Execution Architecture
- **Multi-threaded Spring Boot** - Thread pool configuration (10-50 threads)
- **Multi-process** - RabbitMQ consumers running in separate threads

### 3. Load Distribution (Infrastructure Ready)
- Docker Compose setup with PostgreSQL, Redis, RabbitMQ
- Ready for nginx/HAProxy load balancers

### 4. Analysis Capabilities
- Prevents overselling through Redis atomic operations
- Performance monitoring with Spring Boot Actuator
- Resource usage tracking

## Tech Stack

- **Framework:** Spring Boot 3.5.7
- **Language:** Java 17
- **Database:** PostgreSQL with Flyway migrations
- **Cache:** Redis for atomic stock operations
- **Message Queue:** RabbitMQ with dead letter queues
- **Containerization:** Docker & Docker Compose
- **Build Tool:** Maven

## Quick Start

### Prerequisites
- Docker Desktop installed and running
- Java 17+ (for local development)
- Maven 3.6+ (for local development)

### 1. Clone and Setup
```bash
git clone <repository-url>
cd PulseCart-Backend_Communication_Patterns
```

### 2. Start Infrastructure
```bash
# Start all services (PostgreSQL, Redis, RabbitMQ)
docker-compose up -d

# Verify services are running
docker-compose ps
```

### 3. Run Application
```bash
# Option 1: Using Maven wrapper (recommended)
cd PulseCart-Backend_Communication_Patterns
./mvnw spring-boot:run

# Option 2: Using Maven (if installed)
mvn spring-boot:run
```

### 4. Verify Setup
```bash
# Health checks
curl http://localhost:8080/api/v1/sync/health
curl http://localhost:8080/api/v1/async/health

# Check initial stock
curl http://localhost:8080/api/v1/sync/stock/1
```

## API Endpoints

### Synchronous API
```bash
# Purchase (immediate response)
POST /api/v1/sync/purchase
Content-Type: application/json
{
  "productId": 1,
  "quantity": 1,
  "userId": 12345
}

# Get stock
GET /api/v1/sync/stock/{productId}

# Initialize stock
POST /api/v1/sync/stock/init/{productId}

# Reset stock (testing)
PUT /api/v1/sync/stock/{productId}/reset?stock=1000

# Health check
GET /api/v1/sync/health
```

### Asynchronous API
```bash
# Purchase (queued processing)
POST /api/v1/async/purchase
Content-Type: application/json
{
  "productId": 1,
  "quantity": 1,
  "userId": 12345
}

# Health check
GET /api/v1/async/health

# Queue status
GET /api/v1/async/queue/status
```

## Testing Examples

### Basic Purchase Flow
```bash
# 1. Check initial stock
curl http://localhost:8080/api/v1/sync/stock/1
# Response: {"productId":1,"stock":1000}

# 2. Make sync purchase
curl -X POST http://localhost:8080/api/v1/sync/purchase \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 1, "userId": 12345}'

# 3. Verify stock decreased
curl http://localhost:8080/api/v1/sync/stock/1
# Response: {"productId":1,"stock":999}
```

### Async Purchase Flow
```bash
# Make async purchase (immediate acceptance)
curl -X POST http://localhost:8080/api/v1/async/purchase \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 1, "userId": 12346}'

# Response: 202 Accepted with trackingId
# Stock updates in background
```

## Architecture

### Synchronous Flow
```
User Request → PurchaseController
             → InventoryService
             → Redis DECR (atomic)
             → Database sync
             → Response to User (200 OK/409 Conflict)
```

### Asynchronous Flow
```
User Request → AsyncPurchaseController
             → RabbitMQ Queue
             → Response to User (202 Accepted)

Background:
RabbitMQ Queue → PurchaseMessageConsumer
               → InventoryService
               → Redis DECR (atomic)
               → Database sync
               → Stock Update Broadcast
```

## Key Technical Features

### Overselling Prevention
- Redis atomic DECR operations ensure no stock goes negative
- Race condition handling with rollback mechanisms
- Database synchronization for consistency

### Scalability
- Thread pool configuration for concurrent processing
- RabbitMQ message queuing for decoupling
- Redis caching for high-performance stock operations

### Reliability
- Dead letter queues for failed messages
- Comprehensive error handling and logging
- Health check endpoints for monitoring

## Load Testing

The system is designed to handle 10,000+ concurrent requests. Load testing scripts are available in the `tests/` directory.

### Prerequisites for Load Testing
```bash
# Install k6 (load testing tool)
# https://k6.io/docs/get-started/installation/

# For Windows (using Chocolatey)
choco install k6

# For macOS (using Homebrew)
brew install k6

# For Linux
sudo apt update
sudo apt install k6
```

### Running Load Tests

#### Option 1: Test with Load Balancers (Recommended)
```bash
# Start full infrastructure with load balancers
docker-compose up --build -d

# Wait for all services to be healthy
docker-compose ps

# Test HAProxy load balancer (port 80)
k6 run -e BASE_URL=http://localhost tests/load-test-sync.js
k6 run -e BASE_URL=http://localhost tests/load-test-async.js

# Test nginx load balancer (port 81)
k6 run -e BASE_URL=http://localhost:81 tests/load-test-sync.js
k6 run -e BASE_URL=http://localhost:81 tests/load-test-async.js
```

#### Option 2: Test Single Instance (Development)
```bash
# Start only infrastructure services
docker-compose up -d postgres redis rabbitmq

# Run application locally
./mvnw spring-boot:run

# Test local instance
k6 run -e BASE_URL=http://localhost:8080 tests/load-test-sync.js
k6 run -e BASE_URL=http://localhost:8080 tests/load-test-async.js
```

### Load Test Scenarios

#### Synchronous Tests (`load-test-sync.js`)
- **Ramp-up test:** 0 → 100 → 500 → 1000 users over 5 minutes
- **Constant load:** 50 users for 5 minutes
- **Metrics:** Response time, success rate, throughput
- **Thresholds:** p95 < 2s, success rate > 95%

#### Asynchronous Tests (`load-test-async.js`)
- **Ramp-up test:** 0 → 200 → 1000 → 2000 users over 5 minutes
- **Burst test:** 100 users for 2 minutes
- **Metrics:** Acceptance rate, response time, queue processing
- **Thresholds:** p95 < 500ms, acceptance rate > 99%

### Analyzing Results

```bash
# Results are displayed in console and can be exported
k6 run --out json=results/sync-results.json tests/load-test-sync.js
k6 run --out json=results/async-results.json tests/load-test-async.js

# View results in browser
# Open results/sample-sync-results.json
# Open results/sample-async-results.json
```

### Key Metrics to Monitor

#### Performance Metrics
- **Response Time:** p50, p95, p99 percentiles
- **Throughput:** Requests per second
- **Error Rate:** HTTP 4xx/5xx responses

#### System Resources
```bash
# Monitor Docker containers
docker stats

# Check application logs
docker-compose logs -f app1

# Monitor Redis operations
docker exec -it pulsecart-redis redis-cli MONITOR

# RabbitMQ management
open http://localhost:15672
```

#### Business Metrics
- **Stock Levels:** Verify no overselling occurs
- **Order Success Rate:** Percentage of successful purchases
- **Queue Backlog:** Async message processing status

## Monitoring

### Application Metrics
- Spring Boot Actuator endpoints: `/actuator/health`, `/actuator/metrics`
- Prometheus metrics available at `/actuator/prometheus`

### Infrastructure Monitoring
- RabbitMQ Management UI: http://localhost:15672 (guest/guest)
- Redis CLI: `docker exec -it pulsecart-redis redis-cli`

## Development

### Project Structure
```
├── PulseCart-Backend_Communication_Patterns/  # Main application
│   ├── src/main/java/com/example/flashsale/
│   │   ├── config/          # Redis, RabbitMQ, ThreadPool configs
│   │   ├── controller/      # REST endpoints
│   │   ├── service/         # Business logic
│   │   ├── entity/          # JPA entities
│   │   ├── dto/            # Data transfer objects
│   │   ├── messaging/      # RabbitMQ producers/consumers
│   │   └── repository/     # Data access layer
│   └── src/main/resources/
│       ├── application.yaml # Configuration
│       └── db/migration/    # Flyway migrations
├── docker-compose.yml       # Infrastructure setup
├── tests/                   # Load testing scripts
├── results/                 # Test results
└── benchmarks/             # Performance graphs
```

### Database Reset (Development)
```bash
# Reset database and migrations
./scripts/reset-database.bat
```

## Troubleshooting

### Common Issues

1. **Port already in use**
   ```bash
   # Kill process on port 8080
   netstat -ano | findstr :8080
   taskkill /PID <PID> /F
   ```

2. **Docker containers not starting**
   ```bash
   # Check Docker Desktop is running
   docker --version

   # Clean restart
   docker-compose down
   docker-compose up -d
   ```

3. **Database connection issues**
   ```bash
   # Reset database
   ./scripts/reset-database.bat
   ```

### Logs
```bash
# Application logs
tail -f logs/spring.log

# Docker logs
docker-compose logs -f pulsecart-postgres
docker-compose logs -f pulsecart-redis
docker-compose logs -f pulsecart-rabbitmq
```

## Performance Analysis

See `ANALYSIS_REPORT.pdf` for detailed comparative analysis of:
- Latency comparison (p50, p95, p99)
- Throughput under 10K concurrent requests
- Resource usage (CPU, memory)
- Pattern recommendations

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes with tests
4. Submit pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.
