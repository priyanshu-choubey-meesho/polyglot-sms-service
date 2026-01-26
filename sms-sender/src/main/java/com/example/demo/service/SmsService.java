package com.example.demo.service;

import org.springframework.kafka.KafkaException;
import org.springframework.stereotype.Service;
import com.example.demo.service.BlacklistCache;
import com.example.demo.model.SmsRequest;
import com.example.demo.model.SmsEvent;

@Service
public class SmsService {
    private final BlacklistCache cache;
    private final SmsEventProducer eventProducer;
    private final TwillioService twillioService;

    public SmsService(BlacklistCache cache, SmsEventProducer eventProducer, TwillioService twillioService) {
        this.cache = cache;
        this.eventProducer = eventProducer;
        this.twillioService = twillioService;
    }

    public String sendSms(SmsRequest request) {
        String phoneNumber = request.getPhoneNumber();
        String message = request.getMessage();

        // Check if phone number is blacklisted
        if (cache.isBlacklisted(phoneNumber)) {
            SmsEvent event = new SmsEvent(phoneNumber, message, "blocked");
            try {
                eventProducer.sendSmsEvent(event);
            } catch (KafkaException e) {
                // Log Kafka failure but don't affect the response
                System.err.println("Failed to publish event to Kafka: " + e.getMessage());
            }
            return "Failed: Phone number is blacklisted";
        }

        try {
            twillioService.sendSms(phoneNumber, message);
            // SMS sent successfully - publish event (Kafka failures shouldn't affect success)
            SmsEvent event = new SmsEvent(phoneNumber, message, "successful");
            try {
                eventProducer.sendSmsEvent(event);
            } catch (KafkaException e) {
                // Log Kafka failure but don't affect the response
                System.err.println("Failed to publish event to Kafka: " + e.getMessage());
            }
            return "SMS sent to " + request.getPhoneNumber();
        } catch (Exception e) {
            // SMS failed - publish event (Kafka failures shouldn't affect failure response)
            SmsEvent event = new SmsEvent(phoneNumber, message, "unsuccessful");
            try {
                eventProducer.sendSmsEvent(event);
            } catch (KafkaException kafkaException) {
                // Log Kafka failure but don't affect the response
                System.err.println("Failed to publish event to Kafka: " + kafkaException.getMessage());
            }
            return "Failed to send SMS: " + e.getMessage();
        }
    }
}
