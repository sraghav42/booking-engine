package com.example.booking_engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class BookingEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookingEngineApplication.class, args);
	}

}
