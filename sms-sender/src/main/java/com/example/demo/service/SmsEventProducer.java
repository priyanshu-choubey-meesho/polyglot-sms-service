package com.example.demo.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.KafkaException;
import org.springframework.stereotype.Service;
import com.example.demo.model.SmsEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class SmsEventProducer {
    private final KafkaTemplate<String, SmsEvent> kafkaTemplate;
    private static final String TOPIC = "sms_events";
    private static final long SEND_TIMEOUT_SECONDS = 5;
    
    public SmsEventProducer(KafkaTemplate<String, SmsEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }   

    public void sendSmsEvent(SmsEvent smsEvent) throws KafkaException {
        try {
            // Make the send synchronous with timeout to catch errors
            kafkaTemplate.send(TOPIC, smsEvent).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            System.out.println("Produced SMS Event: " + smsEvent);
        } catch (java.util.concurrent.ExecutionException e) {
            // Unwrap the actual Kafka exception
            Throwable cause = e.getCause();
            if (cause instanceof KafkaException) {
                throw (KafkaException) cause;
            }
            throw new KafkaException("Failed to send SMS event to Kafka", cause);
        } catch (TimeoutException e) {
            throw new KafkaException("Timeout while sending SMS event to Kafka", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaException("Interrupted while sending SMS event to Kafka", e);
        }
    }
}
