package com.example.booking_engine.service;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.booking_engine.exception.InsufficientInventoryException;
import com.example.booking_engine.exception.ProductNotFoundException;
import com.example.booking_engine.model.Inventory;
import com.example.booking_engine.repository.ProductRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;


@Service
public class OrderProcessingService {

    private final ProductRepository productRepository;
    private final MeterRegistry meterRegistry;

    public OrderProcessingService(ProductRepository productRepository, MeterRegistry meterRegistry) {
        this.productRepository = productRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void processOrder(Long productId, int orderQuantity) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Inventory product = productRepository.findById(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));

            if (product.getQuantity() < orderQuantity) {
                meterRegistry.counter("booking.inventory.insufficient.total",
                        "productId", String.valueOf(productId)).increment();
                throw new InsufficientInventoryException(productId, orderQuantity, product.getQuantity());
            }

            if (ThreadLocalRandom.current().nextBoolean()) {
                meterRegistry.counter("booking.orders.transient_failure.total",
                        "productId", String.valueOf(productId)).increment();
                throw new RuntimeException("Simulated transient failure in processOrder");
            }

            product.setQuantity(product.getQuantity() - orderQuantity);
            productRepository.save(product);

            meterRegistry.counter("booking.orders.processed.success.total",
                    "productId", String.valueOf(productId)).increment();

        } catch (Exception e) {
            meterRegistry.counter("booking.orders.processed.failure.total",
                    "productId", String.valueOf(productId),
                    "reason", e.getClass().getSimpleName()).increment();
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("booking.order.processing.duration",
                    "productId", String.valueOf(productId)));
        }
    }
}
