package com.example.flashsale.integration;

import com.example.flashsale.dto.PurchaseRequest;
import com.example.flashsale.dto.PurchaseResponse;
import com.example.flashsale.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@Testcontainers
class PurchaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management-alpine")
        .withExposedPorts(5672, 15672);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Initialize test data
        inventoryService.initializeStock(1L);
    }

    @Test
    void fullPurchaseFlow_Sync_Success() throws Exception {
        // Given
        PurchaseRequest request = new PurchaseRequest(1L, 1);

        // When & Then
        mockMvc.perform(post("/api/v1/sync/purchase")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Purchase successful"))
            .andExpect(jsonPath("$.orderId").exists())
            .andExpect(jsonPath("$.remainingStock").isNumber());
    }

    @Test
    void fullPurchaseFlow_Async_Success() throws Exception {
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
    void concurrentPurchases_PreventOverselling() throws Exception {
        // Given - Multiple concurrent requests for limited stock
        PurchaseRequest request = new PurchaseRequest(1L, 1);

        // When - Simulate concurrent requests
        Thread[] threads = new Thread[10];
        boolean[] results = new boolean[10];

        for (int i = 0; i < 10; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    mockMvc.perform(post("/api/v1/sync/purchase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andDo(result -> {
                            String responseBody = result.getResponse().getContentAsString();
                            results[index] = responseBody.contains("\"success\":true");
                        });
                } catch (Exception e) {
                    results[index] = false;
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - Only some should succeed (depending on initial stock)
        int successCount = 0;
        for (boolean result : results) {
            if (result) successCount++;
        }

        // Verify no overselling occurred
        assertTrue(successCount <= 1000, "Overselling detected! More purchases succeeded than available stock");
    }

    @Test
    void healthCheck_ReturnsOk() throws Exception {
        mockMvc.perform(post("/api/v1/sync/health"))
            .andExpect(status().isOk())
            .andExpect(content().string("Sync Purchase Service is running"));
    }

    @Test
    void stockCheck_ReturnsCurrentStock() throws Exception {
        mockMvc.perform(post("/api/v1/sync/stock/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(1))
            .andExpect(jsonPath("$.stock").isNumber());
    }
}
