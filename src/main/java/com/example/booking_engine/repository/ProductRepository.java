package com.example.booking_engine.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.booking_engine.model.Inventory;

public interface ProductRepository extends JpaRepository<Inventory, Long> {
    
}
