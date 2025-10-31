package com.example.flashsale.controller;

import com.example.flashsale.dto.PurchaseRequest;
import com.example.flashsale.dto.PurchaseResponse;
import com.example.flashsale.service.InventoryService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Synchronous Purchase Controller
 * Handles HTTP requests for flash sale purchases using synchronous processing
 */
@RestController
@RequestMapping("/api/v1/sync")
@Validated
@Slf4j
public class PurchaseController {

    private final InventoryService inventoryService;

    @Autowired
    public PurchaseController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Synchronous purchase endpoint
     * Processes purchase requests immediately and returns result
     *
     * @param request Purchase request with productId and quantity
     * @return PurchaseResponse with order details or error message
     */
    @PostMapping("/purchase")
    public ResponseEntity<PurchaseResponse> purchase(@Valid @RequestBody PurchaseRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("Received purchase request: {}", request);

        try {
            PurchaseResponse response = inventoryService.processPurchase(request);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Purchase request processed in {}ms: success={}, orderId={}",
                     duration, response.isSuccess(), response.getOrderId());

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                // Return 409 Conflict for out of stock or business logic failures
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error processing purchase request after {}ms: {}", duration, e.getMessage(), e);

            PurchaseResponse errorResponse = PurchaseResponse.failure(
                "Internal server error: " + e.getMessage(),
                request.getProductId(),
                request.getQuantity()
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get current stock for a product
     *
     * @param productId Product ID
     * @return Current stock quantity
     */
    @GetMapping("/stock/{productId}")
    public ResponseEntity<?> getStock(@PathVariable Long productId) {
        try {
            Integer stock = inventoryService.getCurrentStock(productId);

            if (stock == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Product not found: " + productId);
            }

            return ResponseEntity.ok(new StockResponse(productId, stock));

        } catch (Exception e) {
            log.error("Error getting stock for product {}: {}", productId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error retrieving stock: " + e.getMessage());
        }
    }

    /**
     * Initialize stock for a product (for testing/setup)
     *
     * @param productId Product ID
     * @return Success message
     */
    @PostMapping("/stock/init/{productId}")
    public ResponseEntity<String> initializeStock(@PathVariable Long productId) {
        try {
            inventoryService.initializeStock(productId);
            return ResponseEntity.ok("Stock initialized for product: " + productId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error initializing stock for product {}: {}", productId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error initializing stock: " + e.getMessage());
        }
    }

    /**
     * Reset stock for a product (for testing purposes only)
     *
     * @param productId Product ID
     * @param stock New stock quantity
     * @return Success message
     */
    @PutMapping("/stock/{productId}/reset")
    public ResponseEntity<String> resetStock(@PathVariable Long productId, @RequestParam Integer stock) {
        try {
            inventoryService.resetStock(productId, stock);
            return ResponseEntity.ok("Stock reset to " + stock + " for product: " + productId);
        } catch (Exception e) {
            log.error("Error resetting stock for product {}: {}", productId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error resetting stock: " + e.getMessage());
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Sync Purchase Service is running");
    }

    /**
     * Stock response DTO
     */
    private record StockResponse(Long productId, Integer stock) {}
}
