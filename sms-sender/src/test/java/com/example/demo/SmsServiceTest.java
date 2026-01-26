package com.example.demo;

import com.example.demo.model.SmsEvent;
import com.example.demo.model.SmsRequest;
import com.example.demo.service.BlacklistCache;
import com.example.demo.service.SmsEventProducer;
import com.example.demo.service.SmsService;
import com.example.demo.service.TwillioService;
import org.springframework.kafka.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SmsServiceTest {

    @Mock
    private BlacklistCache blacklistCache;

    @Mock
    private SmsEventProducer eventProducer;

    @Mock
    private TwillioService twillioService;

    @InjectMocks
    private SmsService smsService;

    @Captor
    private ArgumentCaptor<SmsEvent> smsEventCaptor;

    private SmsRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new SmsRequest("+1234567890", "Test message");
    }

    @Test
    void testSendSms_Success() {
        // Arrange
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(false);

        // Act
        String result = smsService.sendSms(validRequest);

        // Assert
        assertEquals("SMS sent to +1234567890", result);
        verify(blacklistCache, times(1)).isBlacklisted("+1234567890");
        verify(twillioService, times(1)).sendSms("+1234567890", "Test message");
        verify(eventProducer, times(1)).sendSmsEvent(smsEventCaptor.capture());

        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals("+1234567890", capturedEvent.getPhoneNumber());
        assertEquals("Test message", capturedEvent.getMessage());
        assertEquals("successful", capturedEvent.getStatus());
    }

    @Test
    void testSendSms_BlacklistedNumber() {
        // Arrange
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(true);

        // Act
        String result = smsService.sendSms(validRequest);

        // Assert
        assertEquals("Failed: Phone number is blacklisted", result);
        verify(blacklistCache, times(1)).isBlacklisted("+1234567890");
        verify(twillioService, never()).sendSms(any(), any());
        verify(eventProducer, times(1)).sendSmsEvent(smsEventCaptor.capture());

        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals("+1234567890", capturedEvent.getPhoneNumber());
        assertEquals("Test message", capturedEvent.getMessage());
        assertEquals("blocked", capturedEvent.getStatus());
    }

    @Test
    void testSendSms_DifferentPhoneNumber() {
        // Arrange
        SmsRequest request = new SmsRequest("+9876543210", "Hello World");
        when(blacklistCache.isBlacklisted("+9876543210")).thenReturn(false);

        // Act
        String result = smsService.sendSms(request);

        // Assert
        assertEquals("SMS sent to +9876543210", result);
        verify(blacklistCache, times(1)).isBlacklisted("+9876543210");
        verify(twillioService, times(1)).sendSms("+9876543210", "Hello World");
        verify(eventProducer, times(1)).sendSmsEvent(any(SmsEvent.class));
    }

    @Test
    void testSendSms_LongMessage() {
        // Arrange
        String longMessage = "This is a very long message that exceeds the typical SMS length limit to test how the service handles long messages";
        SmsRequest request = new SmsRequest("+1234567890", longMessage);
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(false);

        // Act
        String result = smsService.sendSms(request);

        // Assert
        assertEquals("SMS sent to +1234567890", result);
        verify(twillioService, times(1)).sendSms("+1234567890", longMessage);
        verify(eventProducer, times(1)).sendSmsEvent(smsEventCaptor.capture());

        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals(longMessage, capturedEvent.getMessage());
    }

    @Test
    void testSendSms_EmptyMessage() {
        // Arrange
        SmsRequest request = new SmsRequest("+1234567890", "");
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(false);

        // Act
        String result = smsService.sendSms(request);

        // Assert
        assertEquals("SMS sent to +1234567890", result);
        verify(twillioService, times(1)).sendSms("+1234567890", "");
    }

    @Test
    void testSendSms_MultipleCalls() {
        // Arrange
        SmsRequest request1 = new SmsRequest("+1111111111", "Message 1");
        SmsRequest request2 = new SmsRequest("+2222222222", "Message 2");
        SmsRequest request3 = new SmsRequest("+3333333333", "Message 3");

        when(blacklistCache.isBlacklisted(any())).thenReturn(false);

        // Act
        smsService.sendSms(request1);
        smsService.sendSms(request2);
        smsService.sendSms(request3);

        // Assert
        verify(blacklistCache, times(3)).isBlacklisted(any());
        verify(twillioService, times(3)).sendSms(any(), any());
        verify(eventProducer, times(3)).sendSmsEvent(any(SmsEvent.class));
    }

    @Test
    void testSendSms_ChecksBlacklistBeforeSending() {
        // Arrange
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(true);

        // Act
        smsService.sendSms(validRequest);

        // Assert - TwillioService should NOT be called if blacklisted
        verify(twillioService, never()).sendSms(any(), any());
    }

    @Test
    void testSendSms_EventProducerAlwaysCalled() {
        // Arrange
        when(blacklistCache.isBlacklisted(any())).thenReturn(false, true);

        // Act
        smsService.sendSms(validRequest);
        smsService.sendSms(new SmsRequest("+9999999999", "Test"));

        // Assert - Event producer should be called for both success and failure
        verify(eventProducer, times(2)).sendSmsEvent(any(SmsEvent.class));
    }

    @Test
    void testSendSms_TwillioServiceThrowsException() {
        // Arrange
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(false);
        doThrow(new RuntimeException("Failed to send SMS"))
                .when(twillioService).sendSms("+1234567890", "Test message");

        // Act
        String result = smsService.sendSms(validRequest);

        // Assert
        assertTrue(result.startsWith("Failed to send SMS:"));
        assertTrue(result.contains("Failed to send SMS"));
        verify(blacklistCache, times(1)).isBlacklisted("+1234567890");
        verify(twillioService, times(1)).sendSms("+1234567890", "Test message");
        verify(eventProducer, times(1)).sendSmsEvent(smsEventCaptor.capture());

        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals("+1234567890", capturedEvent.getPhoneNumber());
        assertEquals("Test message", capturedEvent.getMessage());
        assertEquals("unsuccessful", capturedEvent.getStatus());
    }

    @Test
    void testSendSms_KafkaFailureDoesNotAffectSuccessResponse() {
        // Arrange
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(false);
        doThrow(new org.springframework.kafka.KafkaException("Kafka connection failed"))
                .when(eventProducer).sendSmsEvent(any(SmsEvent.class));

        // Act
        String result = smsService.sendSms(validRequest);

        // Assert - SMS should still be sent successfully despite Kafka failure
        assertEquals("SMS sent to +1234567890", result);
        verify(blacklistCache, times(1)).isBlacklisted("+1234567890");
        verify(twillioService, times(1)).sendSms("+1234567890", "Test message");
        verify(eventProducer, times(1)).sendSmsEvent(any(SmsEvent.class));
    }

    @Test
    void testSendSms_KafkaFailureDoesNotAffectFailureResponse() {
        // Arrange
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(false);
        doThrow(new RuntimeException("Failed to send SMS"))
                .when(twillioService).sendSms("+1234567890", "Test message");
        doThrow(new org.springframework.kafka.KafkaException("Kafka connection failed"))
                .when(eventProducer).sendSmsEvent(any(SmsEvent.class));

        // Act
        String result = smsService.sendSms(validRequest);

        // Assert - Should return failure message despite Kafka failure
        assertTrue(result.startsWith("Failed to send SMS:"));
        verify(blacklistCache, times(1)).isBlacklisted("+1234567890");
        verify(twillioService, times(1)).sendSms("+1234567890", "Test message");
        // Event producer should be called twice - once for unsuccessful, but it fails
        // Then it tries again in the catch block
        verify(eventProducer, atLeast(1)).sendSmsEvent(any(SmsEvent.class));
    }

    @Test
    void testSendSms_KafkaFailureForBlacklistedNumber() {
        // Arrange
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(true);
        doThrow(new org.springframework.kafka.KafkaException("Kafka connection failed"))
                .when(eventProducer).sendSmsEvent(any(SmsEvent.class));

        // Act
        String result = smsService.sendSms(validRequest);

        // Assert - Should return blacklisted message despite Kafka failure
        assertEquals("Failed: Phone number is blacklisted", result);
        verify(blacklistCache, times(1)).isBlacklisted("+1234567890");
        verify(twillioService, never()).sendSms(any(), any());
        verify(eventProducer, times(1)).sendSmsEvent(any(SmsEvent.class));
    }
}
