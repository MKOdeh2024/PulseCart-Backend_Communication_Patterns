package com.example.flashsale.messaging;

import com.example.flashsale.config.RabbitMQConfig;
import com.example.flashsale.dto.PurchaseRequest;
import com.example.flashsale.dto.PurchaseResponse;
import com.example.flashsale.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Consumer for processing purchase requests from RabbitMQ
 * Part of async approach - processes messages in background
 */
@Component
@Slf4j
public class PurchaseMessageConsumer {

    private final InventoryService inventoryService;
    private final PurchaseMessagePublisher messagePublisher;

    @Autowired
    public PurchaseMessageConsumer(InventoryService inventoryService,
                                    PurchaseMessagePublisher messagePublisher) {
        this.inventoryService = inventoryService;
        this.messagePublisher = messagePublisher;
    }

    /**
     * Listen to purchase queue and process messages
     * Runs in separate thread pool managed by RabbitMQ listener
     */
    @RabbitListener(queues = RabbitMQConfig.PURCHASE_QUEUE)
    public void processPurchaseRequest(PurchaseRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("Received purchase request from queue: productId={}, quantity={}, userId={}",
            request.getProductId(), request.getQuantity(), request.getUserId());

        try {
            // Process the purchase using InventoryService (with Redis atomic ops)
            PurchaseResponse response = inventoryService.processPurchase(request);

            long duration = System.currentTimeMillis() - startTime;

            if (response.isSuccess()) {
                log.info("Purchase processed successfully in {}ms: orderId={}, productId={}, remainingStock={}",
                    duration, response.getOrderId(), response.getProductId(), response.getRemainingStock());

                // Publish stock update for real-time notifications
                messagePublisher.publishStockUpdate(
                    response.getProductId(),
                    response.getRemainingStock()
                );
            } else {
                log.warn("Purchase failed after {}ms: productId={}, reason={}",
                    duration, response.getProductId(), response.getMessage());
            }

            // In a real system, you might:
            // 1. Store the order in database
            // 2. Send notification to user (email/SMS/push)
            // 3. Trigger payment processing
            // 4. Update analytics/metrics

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error processing purchase request after {}ms: productId={}, error={}",
                duration, request.getProductId(), e.getMessage(), e);

            // Exception will cause message to be requeued or sent to DLQ based on retry config
            throw new RuntimeException("Failed to process purchase: " + e.getMessage(), e);
        }
    }

    /**
     * Listen to dead letter queue for failed messages
     * Useful for monitoring and manual intervention
     */
    @RabbitListener(queues = RabbitMQConfig.PURCHASE_DLQ)
    public void handleFailedPurchase(PurchaseRequest request) {
        log.error("Purchase request moved to DLQ: productId={}, quantity={}, userId={}",
            request.getProductId(), request.getQuantity(), request.getUserId());

        // In production:
        // 1. Send alert to monitoring system
        // 2. Log to error tracking service (Sentry, etc.)
        // 3. Store for manual review
        // 4. Potentially notify customer service team
    }
}
