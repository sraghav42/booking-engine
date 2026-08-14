package com.example.booking_engine.queueListener;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;


import com.example.booking_engine.exception.InsufficientInventoryException;
import com.example.booking_engine.exception.ProductNotFoundException;
import com.example.booking_engine.service.OrderProcessingService;
import com.example.booking_engine.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;

@Component
public class RabbitMQListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQListener.class);
    private static final Logger ORDER_AUDIT_LOGGER = LoggerFactory.getLogger("ORDER_AUDIT_LOGGER");

    @Autowired
    private OrderProcessingService orderProcessingService;

    @Autowired
    private PaymentService paymentService;
    
    @RabbitListener(queues="orderQueue", concurrency = "3")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> data = mapper.readValue(message, Map.class);
            
            Long productId = Long.parseLong(data.get("productid").toString());
            String userId = data.get("userid") != null ? data.get("userid").toString() : "unknown";
            int orderQuantity = Integer.parseInt(data.get("quantity").toString());

            boolean paymentProcessed = paymentService.processPayment();

            if (paymentProcessed) {
                LOGGER.info("Payment service processed successfully");

                try {
                    orderProcessingService.processOrder(productId, orderQuantity);
                    LOGGER.info("Order processed successfully for product id: {}, user id: {}, quantity: {}", productId, userId, orderQuantity);
                    ORDER_AUDIT_LOGGER.info("Order success | productId={} | userId={} | quantity={} | status=SUCCESS", productId, userId, orderQuantity);
                    channel.basicAck(tag, false);
                } catch (ProductNotFoundException | InsufficientInventoryException e) {
                    LOGGER.warn("Business error while processing order: {}", e.getMessage());
                    ORDER_AUDIT_LOGGER.info("Order failure | productId={} | userId={} | quantity={} | status=FAILED | reason={}", productId, userId, orderQuantity, e.getMessage());
                    channel.basicAck(tag, false);
                } catch (ObjectOptimisticLockingFailureException e) {
                    LOGGER.warn("Optimistic lock failure: {}", e.getMessage());
                    ORDER_AUDIT_LOGGER.info("Order failure | productId={} | userId={} | quantity={} | status=FAILED | reason={}", productId, userId, orderQuantity, e.getMessage());
                    channel.basicNack(tag, false, true);
                } catch (Exception e) {
                    LOGGER.error("Error processing order", e);
                    ORDER_AUDIT_LOGGER.info("Order failure | productId={} | userId={} | quantity={} | status=FAILED | reason={}", productId, userId, orderQuantity, e.getMessage());
                    channel.basicNack(tag, false, true);
                }
            } else {
                LOGGER.warn("Failed to process payment. Place the order again.");
                ORDER_AUDIT_LOGGER.info("Order failure | productId={} | userId={} | quantity={} | status=FAILED | reason=payment_failed", productId, userId, orderQuantity);
                channel.basicAck(tag, false);
            }

        } catch (Exception e) {
            LOGGER.error("Error processing message", e);
        }
    }
}
