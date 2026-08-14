package com.example.booking_engine.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;



@Configuration
public class RabbitMQConfig {
    
    public static final String QUEUE_NAME="orderQueue";
    public static final String EXCHANGE_NAME="orderExchange";
    public static final String ROUTING_KEY="orderRoutingKey";

    @Bean
    public Queue orderQueue() {
        return new Queue(QUEUE_NAME,true);
    }

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding orderBinding(Queue orderQueue, DirectExchange orderExchange){
        return BindingBuilder.bind(orderQueue).to(orderExchange).with(ROUTING_KEY);
    }
}
