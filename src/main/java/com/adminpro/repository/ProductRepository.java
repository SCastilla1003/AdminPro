package com.adminpro.repository;

import com.adminpro.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(p) FROM Product p WHERE p.stock <= p.minStock")
    long countLowStock();
}
