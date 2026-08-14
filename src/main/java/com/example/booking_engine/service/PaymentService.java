package com.example.booking_engine.service;

import org.springframework.stereotype.Service;
import java.util.Random;

@Service
public class PaymentService {
    
    public boolean processPayment(){
        //Block for simulating random payment failures
        // Random rand=new Random();

        // int random=rand.nextInt(2);
        // if(random==0){
        //     return false;
        // }
        // else{
        //     return true;
        // }
        return true;
    }
}
