package com.example.flashsale.repository;

import com.example.flashsale.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Find product by ID with pessimistic write lock (for synchronous approach backup)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    /**
     * Find product by name
     */
    Optional<Product> findByName(String name);

    /**
     * Check if product has sufficient stock
     */
    @Query("SELECT CASE WHEN p.stockQuantity >= :quantity THEN true ELSE false END " +
           "FROM Product p WHERE p.id = :productId")
    boolean hasStockAvailable(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * Decrement stock quantity (used as fallback if Redis fails)
     */
    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity " +
           "WHERE p.id = :productId AND p.stockQuantity >= :quantity")
    int decrementStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * Get current stock quantity
     */
    @Query("SELECT p.stockQuantity FROM Product p WHERE p.id = :productId")
    Optional<Integer> getStockQuantity(@Param("productId") Long productId);
}
