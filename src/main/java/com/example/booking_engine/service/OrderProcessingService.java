package com.example.booking_engine.service;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.booking_engine.exception.InsufficientInventoryException;
import com.example.booking_engine.exception.ProductNotFoundException;
import com.example.booking_engine.model.Inventory;
import com.example.booking_engine.repository.ProductRepository;
import org.hibernate.StaleObjectStateException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@Service
public class OrderProcessingService {

    @Autowired
    private ProductRepository productRepository;

    @Transactional
    public void processOrder(Long productId, int orderQuantity) {
        Inventory product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (product.getQuantity() < orderQuantity) {
            throw new InsufficientInventoryException(productId, orderQuantity, product.getQuantity());
        }

        if (ThreadLocalRandom.current().nextBoolean()) {
            throw new RuntimeException("Simulated transient failure in processOrder");
        }

        product.setQuantity(product.getQuantity() - orderQuantity);
        productRepository.save(product);
    }
}
