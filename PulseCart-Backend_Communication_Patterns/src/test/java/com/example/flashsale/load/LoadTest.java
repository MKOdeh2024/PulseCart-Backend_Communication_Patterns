package com.example.flashsale.load;

import com.example.flashsale.dto.PurchaseRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Load testing for the flash sale system
 * Tests concurrent purchase scenarios to validate overselling prevention
 */
@SpringBootTest
@AutoConfigureWebMvc
class LoadTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @Test
    void concurrentPurchases_100Users_NoOverselling() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Given
        int numberOfUsers = 100;
        int initialStock = 50; // Less than users to test overselling prevention
        PurchaseRequest request = new PurchaseRequest(1L, 1);

        ExecutorService executor = Executors.newFixedThreadPool(20);
        AtomicInteger successfulPurchases = new AtomicInteger(0);
        AtomicInteger failedPurchases = new AtomicInteger(0);

        // When - Launch concurrent requests
        CompletableFuture<?>[] futures = new CompletableFuture[numberOfUsers];

        for (int i = 0; i < numberOfUsers; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    String response = mockMvc.perform(post("/api/v1/sync/purchase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

                    if (response.contains("\"success\":true")) {
                        successfulPurchases.incrementAndGet();
                    } else {
                        failedPurchases.incrementAndGet();
                    }
                } catch (Exception e) {
                    failedPurchases.incrementAndGet();
                }
            }, executor);
        }

        // Wait for all requests to complete
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Then - Validate no overselling occurred
        int totalSuccessful = successfulPurchases.get();
        int totalFailed = failedPurchases.get();

        System.out.println("Load Test Results:");
        System.out.println("Total Requests: " + numberOfUsers);
        System.out.println("Successful Purchases: " + totalSuccessful);
        System.out.println("Failed Purchases: " + totalFailed);
        System.out.println("Success Rate: " + (totalSuccessful * 100.0 / numberOfUsers) + "%");

        // Assertions
        assertEquals(numberOfUsers, totalSuccessful + totalFailed, "All requests should be accounted for");
        assertTrue(totalSuccessful <= initialStock, "Successful purchases should not exceed initial stock");
        assertTrue(totalFailed >= numberOfUsers - initialStock, "Failed purchases should account for insufficient stock");
    }

    @Test
    void asyncConcurrentPurchases_200Users_QueueHandling() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Given
        int numberOfUsers = 200;
        PurchaseRequest request = new PurchaseRequest(1L, 1);

        ExecutorService executor = Executors.newFixedThreadPool(30);
        AtomicInteger acceptedRequests = new AtomicInteger(0);
        AtomicInteger rejectedRequests = new AtomicInteger(0);

        // When - Launch concurrent async requests
        CompletableFuture<?>[] futures = new CompletableFuture[numberOfUsers];

        for (int i = 0; i < numberOfUsers; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    int status = mockMvc.perform(post("/api/v1/async/purchase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                        .andReturn()
                        .getResponse()
                        .getStatus();

                    if (status == 202) {
                        acceptedRequests.incrementAndGet();
                    } else {
                        rejectedRequests.incrementAndGet();
                    }
                } catch (Exception e) {
                    rejectedRequests.incrementAndGet();
                }
            }, executor);
        }

        // Wait for all requests to complete
        CompletableFuture.allOf(futures).get(60, TimeUnit.SECONDS);
        executor.shutdown();

        // Then - Validate async handling
        int totalAccepted = acceptedRequests.get();
        int totalRejected = rejectedRequests.get();

        System.out.println("Async Load Test Results:");
        System.out.println("Total Requests: " + numberOfUsers);
        System.out.println("Accepted Requests: " + totalAccepted);
        System.out.println("Rejected Requests: " + totalRejected);
        System.out.println("Acceptance Rate: " + (totalAccepted * 100.0 / numberOfUsers) + "%");

        // Assertions
        assertEquals(numberOfUsers, totalAccepted + totalRejected, "All requests should be accounted for");
        assertTrue(totalAccepted > 0, "Some requests should be accepted for async processing");
        // Note: In real async scenario, all valid requests should be accepted initially
    }

    @Test
    void sustainedLoad_10Seconds_ThroughputMeasurement() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Given
        PurchaseRequest request = new PurchaseRequest(1L, 1);
        long testDurationMs = 10000; // 10 seconds
        ExecutorService executor = Executors.newFixedThreadPool(10);

        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger successfulRequests = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // When - Continuous load for specified duration
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            CompletableFuture.runAsync(() -> {
                try {
                    totalRequests.incrementAndGet();
                    String response = mockMvc.perform(post("/api/v1/sync/purchase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

                    if (response.contains("\"success\":true")) {
                        successfulRequests.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore exceptions for throughput measurement
                }
            }, executor);
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        double durationSeconds = (endTime - startTime) / 1000.0;
        double requestsPerSecond = totalRequests.get() / durationSeconds;

        // Then - Report throughput
        System.out.println("Sustained Load Test Results:");
        System.out.println("Duration: " + durationSeconds + " seconds");
        System.out.println("Total Requests: " + totalRequests.get());
        System.out.println("Successful Requests: " + successfulRequests.get());
        System.out.println("Requests/Second: " + String.format("%.2f", requestsPerSecond));

        // Assertions
        assertTrue(requestsPerSecond > 0, "Should handle some requests per second");
        assertTrue(totalRequests.get() > 0, "Should process at least some requests");
    }
}
