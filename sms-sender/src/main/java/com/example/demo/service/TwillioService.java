package com.example.demo.service;
import org.springframework.stereotype.Service;

@Service
public class TwillioService{
    public TwillioService(){}
    public void sendSms(String phoneNumber, String message){
        // Logic to send SMS via Twilio API would go here
        //currently it deterministically sends a succesful message
        System.out.println("Sending SMS to " + phoneNumber + ": " + message);
    }
}