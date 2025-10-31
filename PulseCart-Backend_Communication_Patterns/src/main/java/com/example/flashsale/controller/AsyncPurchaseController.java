package com.example.flashsale.controller;

import com.example.flashsale.dto.PurchaseRequest;
import com.example.flashsale.messaging.PurchaseMessagePublisher;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Asynchronous Purchase Controller
 * Handles HTTP requests but queues purchase processing via RabbitMQ
 * Returns immediately without waiting for processing to complete
 */
@RestController
@RequestMapping("/api/v1/async")
@Validated
@Slf4j
public class AsyncPurchaseController {

    private final PurchaseMessagePublisher messagePublisher;

    @Autowired
    public AsyncPurchaseController(PurchaseMessagePublisher messagePublisher) {
        this.messagePublisher = messagePublisher;
    }

    /**
     * Asynchronous purchase endpoint
     * Accepts request, queues it to RabbitMQ, returns immediately
     *
     * @param request Purchase request with productId and quantity
     * @return Acceptance response with tracking ID
     */
    @PostMapping("/purchase")
    public ResponseEntity<AsyncPurchaseResponse> purchaseAsync(@Valid @RequestBody PurchaseRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("Received async purchase request: productId={}, quantity={}",
            request.getProductId(), request.getQuantity());

        try {
            // Publish to message queue
            boolean published = messagePublisher.publishPurchaseRequest(request);

            long duration = System.currentTimeMillis() - startTime;

            if (published) {
                // Generate tracking ID for user to track their request
                String trackingId = UUID.randomUUID().toString();

                log.info("Purchase request queued successfully in {}ms: trackingId={}", duration, trackingId);

                AsyncPurchaseResponse response = new AsyncPurchaseResponse(
                    true,
                    "Purchase request accepted and queued for processing",
                    trackingId,
                    request.getProductId(),
                    request.getQuantity(),
                    LocalDateTime.now()
                );

                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            } else {
                log.error("Failed to queue purchase request after {}ms", duration);

                AsyncPurchaseResponse response = new AsyncPurchaseResponse(
                    false,
                    "Failed to queue purchase request. Please try again.",
                    null,
                    request.getProductId(),
                    request.getQuantity(),
                    LocalDateTime.now()
                );

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error handling async purchase request after {}ms: {}", duration, e.getMessage(), e);

            AsyncPurchaseResponse response = new AsyncPurchaseResponse(
                false,
                "Internal server error: " + e.getMessage(),
                null,
                request.getProductId(),
                request.getQuantity(),
                LocalDateTime.now()
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Health check endpoint for async service
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Async Purchase Service is running");
    }

    /**
     * Get queue status (for monitoring)
     */
    @GetMapping("/queue/status")
    public ResponseEntity<String> queueStatus() {
        // In production, this would query RabbitMQ management API
        return ResponseEntity.ok("Queue monitoring endpoint - implement RabbitMQ management integration");
    }

    /**
     * Async purchase response DTO
     * Note: This returns 202 Accepted, not 200 OK like sync approach
     */
    public record AsyncPurchaseResponse(
        boolean accepted,
        String message,
        String trackingId,
        Long productId,
        Integer quantity,
        LocalDateTime timestamp
    ) {}
}
