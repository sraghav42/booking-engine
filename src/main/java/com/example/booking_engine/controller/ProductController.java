package com.example.booking_engine.controller;

import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.booking_engine.config.RabbitMQConfig;
import com.example.booking_engine.model.Inventory;
import com.example.booking_engine.repository.ProductRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;



@RestController
@RequestMapping("/products")
public class ProductController {
    
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @GetMapping
    public List<Inventory> getAllProducts() {
        return productRepository.findAll();
    }

    @GetMapping("/{id}")
    @Cacheable(value="productQuantity", key="#id")
    public int getProductQuantity(@PathVariable Long id) {
        Inventory product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product not found with id: " + id));
        return product.getQuantity();
    }

    @PostMapping("/place-order/{id}/{userId}/{orderQuantity}")
    public String placeOrder(@PathVariable Long id, @PathVariable Long userId, @PathVariable int orderQuantity) {
        String message = "{\"productid\":\"" + id + "\",\"userid\":\"" + userId + "\",\"quantity\":\"" + orderQuantity + "\"}";
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY, message);
        return "Order placed successfully";
    }
    
    
}
