package com.example.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseResponse {

    private boolean success;
    private String message;
    private String orderId;
    private Long productId;
    private Integer quantity;
    private Integer remainingStock;
    private LocalDateTime timestamp;

    public static PurchaseResponse success(String orderId, Long productId, Integer quantity, Integer remainingStock) {
        return new PurchaseResponse(
            true,
            "Purchase successful",
            orderId,
            productId,
            quantity,
            remainingStock,
            LocalDateTime.now()
        );
    }

    public static PurchaseResponse failure(String message, Long productId, Integer quantity) {
        return new PurchaseResponse(
            false,
            message,
            null,
            productId,
            quantity,
            null,
            LocalDateTime.now()
        );
    }

    public static PurchaseResponse outOfStock(Long productId, Integer quantity, Integer remainingStock) {
        return new PurchaseResponse(
            false,
            "Out of stock. Requested: " + quantity + ", Available: " + remainingStock,
            null,
            productId,
            quantity,
            remainingStock,
            LocalDateTime.now()
        );
    }
}
