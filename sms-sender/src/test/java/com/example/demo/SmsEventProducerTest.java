package com.example.demo;

import com.example.demo.model.SmsEvent;
import com.example.demo.service.SmsEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsEventProducerTest {

    @Mock
    private KafkaTemplate<String, SmsEvent> kafkaTemplate;

    @InjectMocks
    private SmsEventProducer smsEventProducer;

    @Captor
    private ArgumentCaptor<SmsEvent> smsEventCaptor;

    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        // Capture System.out for testing console output
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
    }

    @Test
    void testSendSmsEvent_Success() throws Exception {
        // Arrange
        SmsEvent smsEvent = new SmsEvent("+1234567890", "Test message", "successful");
        SendResult<String, SmsEvent> sendResult = mock(SendResult.class);
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        future.set(sendResult);

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act
        smsEventProducer.sendSmsEvent(smsEvent);

        // Assert
        verify(kafkaTemplate, times(1)).send(eq("sms_events"), smsEventCaptor.capture());

        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals("+1234567890", capturedEvent.getPhoneNumber());
        assertEquals("Test message", capturedEvent.getMessage());
        assertEquals("successful", capturedEvent.getStatus());

        String output = outputStream.toString();
        assertTrue(output.contains("Produced SMS Event:"));
    }

    @Test
    void testSendSmsEvent_WithFailedStatus() throws Exception {
        // Arrange
        SmsEvent smsEvent = new SmsEvent("+9876543210", "Failed message", "unsuccessful");
        SendResult<String, SmsEvent> sendResult = mock(SendResult.class);
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        future.set(sendResult);

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act
        smsEventProducer.sendSmsEvent(smsEvent);

        // Assert
        verify(kafkaTemplate, times(1)).send(eq("sms_events"), smsEventCaptor.capture());

        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals("+9876543210", capturedEvent.getPhoneNumber());
        assertEquals("Failed message", capturedEvent.getMessage());
        assertEquals("unsuccessful", capturedEvent.getStatus());
    }

    @Test
    void testSendSmsEvent_WithBlacklistedStatus() throws Exception {
        // Arrange
        SmsEvent smsEvent = new SmsEvent("+1111111111", "Blocked", "blocked");
        SendResult<String, SmsEvent> sendResult = mock(SendResult.class);
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        future.set(sendResult);

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act
        smsEventProducer.sendSmsEvent(smsEvent);

        // Assert
        verify(kafkaTemplate, times(1)).send(eq("sms_events"), smsEventCaptor.capture());

        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals("+1111111111", capturedEvent.getPhoneNumber());
        assertEquals("Blocked", capturedEvent.getMessage());
        assertEquals("blocked", capturedEvent.getStatus());
    }

    @Test
    void testSendSmsEvent_WithEventId() throws Exception {
        // Arrange
        SmsEvent smsEvent = new SmsEvent("+1234567890", "Test", "successful");
        smsEvent.setEventId("evt-12345");
        SendResult<String, SmsEvent> sendResult = mock(SendResult.class);
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        future.set(sendResult);

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act
        smsEventProducer.sendSmsEvent(smsEvent);

        // Assert
        verify(kafkaTemplate, times(1)).send(eq("sms_events"), smsEventCaptor.capture());

        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals("evt-12345", capturedEvent.getEventId());
    }

    @Test
    void testSendSmsEvent_MultipleEvents() throws Exception {
        // Arrange
        SmsEvent event1 = new SmsEvent("+1111111111", "Message 1", "successful");
        SmsEvent event2 = new SmsEvent("+2222222222", "Message 2", "successful");
        SmsEvent event3 = new SmsEvent("+3333333333", "Message 3", "unsuccessful");
        
        SendResult<String, SmsEvent> sendResult = mock(SendResult.class);
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        future.set(sendResult);

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act
        smsEventProducer.sendSmsEvent(event1);
        smsEventProducer.sendSmsEvent(event2);
        smsEventProducer.sendSmsEvent(event3);

        // Assert
        verify(kafkaTemplate, times(3)).send(eq("sms_events"), any(SmsEvent.class));
    }

    @Test
    void testSendSmsEvent_VerifyTopicName() throws Exception {
        // Arrange
        SmsEvent smsEvent = new SmsEvent("+1234567890", "Test", "successful");
        SendResult<String, SmsEvent> sendResult = mock(SendResult.class);
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        future.set(sendResult);

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act
        smsEventProducer.sendSmsEvent(smsEvent);

        // Assert
        verify(kafkaTemplate).send(eq("sms_events"), any(SmsEvent.class));
    }

    @Test
    void testSendSmsEvent_ThrowsKafkaExceptionOnExecutionException() throws Exception {
        // Arrange
        SmsEvent smsEvent = new SmsEvent("+1234567890", "Test", "successful");
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        KafkaException kafkaException = new KafkaException("Kafka connection failed");
        future.setException(new ExecutionException(kafkaException));

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act & Assert
        KafkaException thrown = assertThrows(KafkaException.class, () -> {
            smsEventProducer.sendSmsEvent(smsEvent);
        });

        assertTrue(thrown.getMessage().contains("Failed to send SMS event to Kafka"));
        verify(kafkaTemplate, times(1)).send(eq("sms_events"), any(SmsEvent.class));
    }

    @Test
    void testSendSmsEvent_ThrowsKafkaExceptionOnTimeout() throws Exception {
        // Arrange
        SmsEvent smsEvent = new SmsEvent("+1234567890", "Test", "successful");
        // Create a future that will timeout (never completes)
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        // Don't set the result, so .get() with timeout will throw TimeoutException

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act & Assert
        // This will wait for 5 seconds (SEND_TIMEOUT_SECONDS) then throw TimeoutException
        KafkaException thrown = assertThrows(KafkaException.class, () -> {
            smsEventProducer.sendSmsEvent(smsEvent);
        });

        assertTrue(thrown.getMessage().contains("Timeout") || thrown.getMessage().contains("timeout"));
        verify(kafkaTemplate, times(1)).send(eq("sms_events"), any(SmsEvent.class));
    }

    @Test
    void testSendSmsEvent_ThrowsKafkaExceptionOnInterruption() throws Exception {
        // Arrange
        SmsEvent smsEvent = new SmsEvent("+1234567890", "Test", "successful");
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        future.setException(new InterruptedException("Interrupted"));

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act & Assert
        KafkaException thrown = assertThrows(KafkaException.class, () -> {
            smsEventProducer.sendSmsEvent(smsEvent);
        });

        assertTrue(thrown.getMessage().contains("Interrupted"));
        verify(kafkaTemplate, times(1)).send(eq("sms_events"), any(SmsEvent.class));
        // Note: The implementation sets interrupt flag, but we can't reliably test it here
        // as the test framework may clear it
    }

    @Test
    void testConstructor() {
        // Act
        SmsEventProducer producer = new SmsEventProducer(kafkaTemplate);

        // Assert
        assertNotNull(producer);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        // Restore original System.out
        System.setOut(originalOut);
    }
}
