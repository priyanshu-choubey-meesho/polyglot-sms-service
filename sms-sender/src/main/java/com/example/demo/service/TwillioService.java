package com.example.demo.service;
import org.springframework.stereotype.Service;

@Service
public class TwillioService{
    public TwillioService(){}
    public void sendSms(String phoneNumber, String message){
        // Logic to send SMS via Twilio API would go here
        System.out.println("Sending SMS to " + phoneNumber + ": " + message);
    }
}