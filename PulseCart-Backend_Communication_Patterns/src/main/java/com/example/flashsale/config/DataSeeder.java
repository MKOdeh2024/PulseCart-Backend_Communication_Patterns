package com.example.flashsale.config;

import com.example.flashsale.entity.Product;
import com.example.flashsale.repository.ProductRepository;
import com.example.flashsale.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Data seeder to initialize flash sale products for testing
 * Creates test products with 1000 stock as per flash sale scenario
 */
@Configuration
@Slf4j
public class DataSeeder {

    @Bean
    CommandLineRunner initDatabase(ProductRepository productRepository, InventoryService inventoryService) {
        return args -> {
            log.info("=== Starting Data Seeding ===");

            // Check if products already exist
            if (productRepository.count() > 0) {
                log.info("Database already contains {} products. Skipping seed data.", productRepository.count());

                // Initialize stock in Redis for existing products
                log.info("Initializing stock in Redis for existing products...");
                productRepository.findAll().forEach(product -> {
                    try {
                        inventoryService.initializeStock(product.getId());
                        log.info("Initialized Redis stock for product: {} ({})", product.getId(), product.getName());
                    } catch (Exception e) {
                        log.error("Failed to initialize Redis stock for product {}: {}", product.getId(), e.getMessage());
                    }
                });

                return;
            }

            // Create flash sale products
            log.info("Creating flash sale products...");

            // Product 1: iPhone 15 Pro - Main flash sale item
            Product iphone = new Product();
            iphone.setName("iPhone 15 Pro - Flash Sale");
            iphone.setStockQuantity(1000);
            iphone.setPrice(new BigDecimal("999.99"));
            iphone = productRepository.save(iphone);
            inventoryService.initializeStock(iphone.getId());
            log.info("Created product: {} with stock: {}", iphone.getName(), iphone.getStockQuantity());

            // Product 2: Samsung Galaxy S24 - Secondary flash sale item
            Product samsung = new Product();
            samsung.setName("Samsung Galaxy S24 - Flash Sale");
            samsung.setStockQuantity(500);
            samsung.setPrice(new BigDecimal("899.99"));
            samsung = productRepository.save(samsung);
            inventoryService.initializeStock(samsung.getId());
            log.info("Created product: {} with stock: {}", samsung.getName(), samsung.getStockQuantity());

            // Product 3: MacBook Pro - Limited flash sale
            Product macbook = new Product();
            macbook.setName("MacBook Pro M3 - Flash Sale");
            macbook.setStockQuantity(100);
            macbook.setPrice(new BigDecimal("1999.99"));
            macbook = productRepository.save(macbook);
            inventoryService.initializeStock(macbook.getId());
            log.info("Created product: {} with stock: {}", macbook.getName(), macbook.getStockQuantity());

            // Product 4: PlayStation 5 - High demand item
            Product ps5 = new Product();
            ps5.setName("PlayStation 5 - Flash Sale");
            ps5.setStockQuantity(200);
            ps5.setPrice(new BigDecimal("499.99"));
            ps5 = productRepository.save(ps5);
            inventoryService.initializeStock(ps5.getId());
            log.info("Created product: {} with stock: {}", ps5.getName(), ps5.getStockQuantity());

            // Product 5: AirPods Pro - Accessories
            Product airpods = new Product();
            airpods.setName("AirPods Pro - Flash Sale");
            airpods.setStockQuantity(2000);
            airpods.setPrice(new BigDecimal("249.99"));
            airpods = productRepository.save(airpods);
            inventoryService.initializeStock(airpods.getId());
            log.info("Created product: {} with stock: {}", airpods.getName(), airpods.getStockQuantity());

            log.info("=== Data Seeding Complete ===");
            log.info("Total products created: {}", productRepository.count());
            log.info("Redis stock initialized for all products");
            log.info("");
            log.info("Test the application:");
            log.info("  - Health Check: GET http://localhost:8080/api/v1/sync/health");
            log.info("  - Get Stock: GET http://localhost:8080/api/v1/sync/stock/1");
            log.info("  - Purchase: POST http://localhost:8080/api/v1/sync/purchase");
            log.info("    Body: {\"productId\": 1, \"quantity\": 1}");
        };
    }
}
