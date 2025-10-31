package com.example.flashsale.messaging;

import com.example.flashsale.config.RabbitMQConfig;
import com.example.flashsale.dto.PurchaseRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Publisher service for sending purchase requests to RabbitMQ
 * Used in async approach to decouple request handling from processing
 */
@Service
@Slf4j
public class PurchaseMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public PurchaseMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publish purchase request to queue for async processing
     *
     * @param request Purchase request to be processed
     * @return true if published successfully, false otherwise
     */
    public boolean publishPurchaseRequest(PurchaseRequest request) {
        try {
            log.info("Publishing purchase request to queue: productId={}, quantity={}",
                request.getProductId(), request.getQuantity());

            rabbitTemplate.convertAndSend(
                RabbitMQConfig.PURCHASE_EXCHANGE,
                RabbitMQConfig.PURCHASE_ROUTING_KEY,
                request
            );

            log.debug("Purchase request published successfully");
            return true;

        } catch (Exception e) {
            log.error("Failed to publish purchase request: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Publish stock update notification
     *
     * @param productId Product ID
     * @param newStock New stock quantity
     */
    public void publishStockUpdate(Long productId, Integer newStock) {
        try {
            StockUpdateMessage message = new StockUpdateMessage(productId, newStock, System.currentTimeMillis());

            log.debug("Publishing stock update: productId={}, newStock={}", productId, newStock);

            rabbitTemplate.convertAndSend(
                RabbitMQConfig.STOCK_UPDATE_EXCHANGE,
                RabbitMQConfig.STOCK_UPDATE_ROUTING_KEY,
                message
            );

        } catch (Exception e) {
            log.error("Failed to publish stock update: {}", e.getMessage(), e);
        }
    }

    /**
     * Stock update message DTO
     */
    public record StockUpdateMessage(Long productId, Integer stock, Long timestamp) {}
}
