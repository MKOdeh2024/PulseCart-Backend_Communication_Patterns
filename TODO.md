# TODO List for E-Commerce Flash Sale System Implementation

## 1. Project Structure Setup
- [ ] Modify root pom.xml to parent with modules (approach-sync, approach-async)
- [ ] Create src/approach-sync/ directory and pom.xml
- [ ] Create src/approach-async/ directory and pom.xml
- [ ] Create load-balancer/ directory with nginx.conf and haproxy.cfg
- [ ] Create load-tests/ directory with k6 scripts
- [ ] Create results/ directory with placeholder JSON files
- [ ] Create benchmarks/ directory with placeholder PNG files

## 2. Database and Migrations
- [ ] Add Flyway migration for products table in src/main/resources/db/migration/
- [ ] Implement Product model and ProductRepository

## 3. Approach-Sync Implementation
- [ ] FlashSyncApplication.java
- [ ] ThreadConfig.java for purchaseExecutor
- [ ] PurchaseController.java (sync)
- [ ] InventoryService.java (sync with Redis atomic DECR)
- [ ] PurchaseRequest model

## 4. Approach-Async Implementation
- [ ] FlashAsyncApplication.java
- [ ] RabbitConfig.java
- [ ] PurchaseController.java (async)
- [ ] OrderConsumer.java
- [ ] StockSseController.java and SseEmitterRegistry
- [ ] InventoryService.java (async with reserve/release)
- [ ] PurchaseRequest model

## 5. Docker and Load Balancing
- [ ] docker-compose.yml with all services
- [ ] nginx.conf (round-robin)
- [ ] haproxy.cfg (leastconn)

## 6. Load Testing
- [ ] load-test-sync.js (k6 script)
- [ ] load-test-async.js (k6 script)

## 7. Documentation
- [ ] README.md
- [ ] ARCHITECTURE.md
- [ ] ANALYSIS_REPORT.md
- [ ] demo-video-notes.md

## 8. Testing and Validation
- [ ] Build and run docker-compose up --build
- [ ] Pre-populate data (productId=1, stock=1000)
- [ ] Run load tests and collect results
- [ ] Generate graphs and analysis report
