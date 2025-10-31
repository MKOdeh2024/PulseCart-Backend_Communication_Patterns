package com.example.flashsale.service;

import com.example.flashsale.config.RedisConfig;
import com.example.flashsale.dto.PurchaseRequest;
import com.example.flashsale.dto.PurchaseResponse;
import com.example.flashsale.entity.Product;
import com.example.flashsale.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Synchronous Inventory Service using Redis atomic operations
 * This implementation uses Redis DECR for atomic stock management to prevent overselling
 */
@Service
@Slf4j
public class InventoryService {

    private final StringRedisTemplate redisTemplate;
    private final ProductRepository productRepository;

    @Autowired
    public InventoryService(StringRedisTemplate redisTemplate, ProductRepository productRepository) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
    }

    /**
     * Initialize stock in Redis from database
     * This should be called when the application starts or when a new product is added
     */
    public void initializeStock(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        String stockKey = RedisConfig.RedisKeys.productStockKey(productId);
        redisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStockQuantity()));

        log.info("Initialized stock for product {} with quantity: {}", productId, product.getStockQuantity());
    }

    /**
     * Process purchase request synchronously with Redis atomic operations
     * Uses Redis DECR for atomic stock decrement to prevent overselling
     */
    @Transactional
    public PurchaseResponse processPurchase(PurchaseRequest request) {
        Long productId = request.getProductId();
        Integer quantity = request.getQuantity();

        log.info("Processing purchase: productId={}, quantity={}", productId, quantity);

        try {
            // Step 1: Check if product exists
            if (!productRepository.existsById(productId)) {
                log.warn("Product not found: {}", productId);
                return PurchaseResponse.failure("Product not found", productId, quantity);
            }

            String stockKey = RedisConfig.RedisKeys.productStockKey(productId);

            // Step 2: Check current stock in Redis
            String currentStockStr = redisTemplate.opsForValue().get(stockKey);
            if (currentStockStr == null) {
                log.warn("Stock not initialized in Redis for product: {}, initializing now", productId);
                initializeStock(productId);
                currentStockStr = redisTemplate.opsForValue().get(stockKey);
            }

            Integer currentStock = Integer.parseInt(currentStockStr);
            log.debug("Current stock for product {}: {}", productId, currentStock);

            // Step 3: Check if sufficient stock available
            if (currentStock < quantity) {
                log.warn("Insufficient stock for product {}: requested={}, available={}",
                         productId, quantity, currentStock);
                return PurchaseResponse.outOfStock(productId, quantity, currentStock);
            }

            // Step 4: Atomic decrement of stock using Redis DECRBY
            // This is the critical section that prevents overselling
            Long remainingStock = redisTemplate.opsForValue().decrement(stockKey, quantity);

            if (remainingStock == null) {
                log.error("Redis DECR operation returned null for product: {}", productId);
                return PurchaseResponse.failure("System error during stock update", productId, quantity);
            }

            // Step 5: Check if decrement resulted in negative stock (race condition check)
            if (remainingStock < 0) {
                // Rollback: increment the stock back
                redisTemplate.opsForValue().increment(stockKey, quantity);
                log.warn("Race condition detected: stock went negative for product {}. Rolled back.", productId);
                return PurchaseResponse.outOfStock(productId, quantity, 0);
            }

            // Step 6: Generate order ID
            String orderId = UUID.randomUUID().toString();

            // Step 7: Asynchronously sync stock to database (in real scenario)
            // For now, we'll do it synchronously for reliability
            syncStockToDatabase(productId, quantity);

            log.info("Purchase successful: orderId={}, productId={}, quantity={}, remainingStock={}",
                     orderId, productId, quantity, remainingStock);

            return PurchaseResponse.success(orderId, productId, quantity, remainingStock.intValue());

        } catch (NumberFormatException e) {
            log.error("Invalid stock value in Redis for product: {}", productId, e);
            return PurchaseResponse.failure("System error: invalid stock data", productId, quantity);
        } catch (Exception e) {
            log.error("Error processing purchase for product: {}", productId, e);
            return PurchaseResponse.failure("System error: " + e.getMessage(), productId, quantity);
        }
    }

    /**
     * Sync stock from Redis to database
     * This ensures database remains consistent with Redis
     */
    @Transactional
    public void syncStockToDatabase(Long productId, Integer quantitySold) {
        try {
            int updated = productRepository.decrementStock(productId, quantitySold);
            if (updated > 0) {
                log.debug("Successfully synced stock to database for product: {}", productId);
            } else {
                log.warn("Failed to update stock in database for product: {} - may need manual sync", productId);
            }
        } catch (Exception e) {
            log.error("Error syncing stock to database for product: {}", productId, e);
            // In production, this would trigger an alert for manual intervention
        }
    }

    /**
     * Get current stock from Redis
     */
    public Integer getCurrentStock(Long productId) {
        String stockKey = RedisConfig.RedisKeys.productStockKey(productId);
        String stockStr = redisTemplate.opsForValue().get(stockKey);

        if (stockStr == null) {
            // If not in Redis, get from database and initialize Redis
            Product product = productRepository.findById(productId).orElse(null);
            if (product != null) {
                initializeStock(productId);
                return product.getStockQuantity();
            }
            return null;
        }

        return Integer.parseInt(stockStr);
    }

    /**
     * Reset stock (for testing purposes)
     */
    public void resetStock(Long productId, Integer stock) {
        String stockKey = RedisConfig.RedisKeys.productStockKey(productId);
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        log.info("Reset stock for product {} to {}", productId, stock);
    }
}

