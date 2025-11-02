package com.example.flashsale.controller;

import com.example.flashsale.dto.PurchaseRequest;
import com.example.flashsale.dto.PurchaseResponse;
import com.example.flashsale.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({PurchaseController.class, AsyncPurchaseController.class})
class PurchaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InventoryService inventoryService;

    @Test
    void syncPurchase_ValidRequest_ReturnsSuccess() throws Exception {
        // Given
        PurchaseRequest request = new PurchaseRequest(1L, 1);
        PurchaseResponse expectedResponse = PurchaseResponse.success(
            UUID.randomUUID().toString(),
            1L,
            1,
            999
        );

        when(inventoryService.processPurchase(any(PurchaseRequest.class))).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/sync/purchase")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Purchase successful"))
            .andExpect(jsonPath("$.productId").value(1))
            .andExpect(jsonPath("$.quantity").value(1));
    }

    @Test
    void syncPurchase_InvalidRequest_ReturnsBadRequest() throws Exception {
        // Given
        PurchaseRequest request = new PurchaseRequest(1L, -1); // Invalid quantity

        // When & Then
        mockMvc.perform(post("/api/v1/sync/purchase")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void syncPurchase_OutOfStock_ReturnsError() throws Exception {
        // Given
        PurchaseRequest request = new PurchaseRequest(1L, 1);
        PurchaseResponse expectedResponse = PurchaseResponse.outOfStock(
            1L,
            1,
            0
        );

        when(inventoryService.processPurchase(any(PurchaseRequest.class))).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/sync/purchase")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Out of stock. Requested: 1, Available: 0"));
    }

    @Test
    void asyncPurchase_ValidRequest_ReturnsAccepted() throws Exception {
        // Given
        PurchaseRequest request = new PurchaseRequest(1L, 1);

        // When & Then
        mockMvc.perform(post("/api/v1/async/purchase")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.accepted").value(true))
            .andExpect(jsonPath("$.message").value("Purchase request accepted and queued"))
            .andExpect(jsonPath("$.trackingId").exists());
    }

    @Test
    void asyncPurchase_InvalidRequest_ReturnsBadRequest() throws Exception {
        // Given
        PurchaseRequest request = new PurchaseRequest(1L, 0); // Invalid quantity

        // When & Then
        mockMvc.perform(post("/api/v1/async/purchase")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getStock_ValidProduct_ReturnsStock() throws Exception {
        // Given
        when(inventoryService.getCurrentStock(1L)).thenReturn(100);

        // When & Then
        mockMvc.perform(get("/api/v1/sync/stock/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(1))
            .andExpect(jsonPath("$.stock").value(100));
    }

    @Test
    void healthCheck_ReturnsOk() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/sync/health"))
            .andExpect(status().isOk())
            .andExpect(content().string("Sync Purchase Service is running"));
    }
}
