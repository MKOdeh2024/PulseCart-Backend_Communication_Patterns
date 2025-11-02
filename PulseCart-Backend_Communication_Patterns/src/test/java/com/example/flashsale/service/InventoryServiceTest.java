package com.example.flashsale.service;

import com.example.flashsale.dto.PurchaseRequest;
import com.example.flashsale.dto.PurchaseResponse;
import com.example.flashsale.entity.Product;
import com.example.flashsale.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private InventoryService inventoryService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Test Product");
        testProduct.setStockQuantity(1000);
    }

    @Test
    void processPurchase_SufficientStock_ReturnsSuccess() {
        // Given
        PurchaseRequest request = new PurchaseRequest(1L, 1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.decrement(anyString(), anyLong())).thenReturn(999L);
        when(productRepository.existsById(1L)).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn("1000");

        // When
        PurchaseResponse response = inventoryService.processPurchase(request);

        // Then
        assertTrue(response.isSuccess());
        assertEquals("Purchase successful", response.getMessage());
        assertEquals(1L, response.getProductId());
        assertEquals(1, response.getQuantity());
        assertEquals(999, response.getRemainingStock());
        assertNotNull(response.getOrderId());
        assertNotNull(response.getTimestamp());

        verify(valueOperations).decrement(anyString(), eq(1L));
        verify(productRepository).existsById(1L);
    }

    @Test
    void processPurchase_InsufficientStock_ReturnsError() {
        // Given
        PurchaseRequest request = new PurchaseRequest(1L, 1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(productRepository.existsById(1L)).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn("0");

        // When
        PurchaseResponse response = inventoryService.processPurchase(request);

        // Then
        assertFalse(response.isSuccess());
        assertEquals("Insufficient stock", response.getMessage());
        assertEquals(1L, response.getProductId());
        assertEquals(1, response.getQuantity());
        assertEquals(0, response.getRemainingStock());
    }

    @Test
    void processPurchase_ProductNotFound_ReturnsError() {
        // Given
        PurchaseRequest request = new PurchaseRequest(1L, 1);
        when(productRepository.existsById(1L)).thenReturn(false);

        // When
        PurchaseResponse response = inventoryService.processPurchase(request);

        // Then
        assertFalse(response.isSuccess());
        assertEquals("Product not found", response.getMessage());
    }

    @Test
    void processPurchase_ZeroQuantity_ReturnsError() {
        // Given
        PurchaseRequest request = new PurchaseRequest(1L, 0);

        // When
        PurchaseResponse response = inventoryService.processPurchase(request);

        // Then
        assertFalse(response.isSuccess());
        assertEquals("Invalid quantity", response.getMessage());
    }

    @Test
    void processPurchase_NegativeQuantity_ReturnsError() {
        // Given
        PurchaseRequest request = new PurchaseRequest(1L, -1);

        // When
        PurchaseResponse response = inventoryService.processPurchase(request);

        // Then
        assertFalse(response.isSuccess());
        assertEquals("Invalid quantity", response.getMessage());
    }

    @Test
    void getCurrentStock_ValidProduct_ReturnsStock() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("950");

        // When
        Integer stock = inventoryService.getCurrentStock(1L);

        // Then
        assertEquals(950, stock);
        verify(valueOperations).get(anyString());
    }

    @Test
    void getCurrentStock_ProductNotInRedis_ReturnsFromDatabase() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        // When
        Integer stock = inventoryService.getCurrentStock(1L);

        // Then
        assertEquals(1000, stock);
        verify(valueOperations).set(anyString(), eq("1000"));
    }

    @Test
    void initializeStock_SetsInitialStock() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        // When
        inventoryService.initializeStock(1L);

        // Then
        verify(valueOperations).set(anyString(), eq("1000"));
        verify(productRepository).findById(1L);
    }

    @Test
    void initializeStock_ProductNotFound_ThrowsException() {
        // Given
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> inventoryService.initializeStock(1L));
    }

    @Test
    void resetStock_SetsStockValue() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        inventoryService.resetStock(1L, 500);

        // Then
        verify(valueOperations).set(anyString(), eq("500"));
    }
}
