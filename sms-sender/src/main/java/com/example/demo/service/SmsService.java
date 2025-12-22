package com.example.demo.service;
import org.springframework.stereotype.Service;

import com.example.demo.model.SmsRequest;

@Service
public class SmsService {
    
    public String sendSms(SmsRequest request) {
        // Logic to send SMS
        return "SMS sent to " + request.getPhoneNumber();
    }
}
