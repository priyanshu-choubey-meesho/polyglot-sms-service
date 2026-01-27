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

/**
 * Unit tests for SmsEventProducer.
 * 
 * This test class verifies that SMS events are correctly sent to Kafka.
 * 
 * Testing Strategy:
 * - Successful event production: Various event statuses (successful, unsuccessful, blocked)
 * - Event content verification: Phone number, message, status, event ID
 * - Error handling: Kafka exceptions, timeouts, interruptions
 * - Multiple events: Sequential event production
 * - Topic verification: Events are sent to the correct Kafka topic
 * 
 * Mocked Dependencies:
 * - KafkaTemplate: Mocked to avoid actual Kafka connections during tests
 * 
 * Note: System.out is captured to verify console logging behavior.
 */
@ExtendWith(MockitoExtension.class)
class SmsEventProducerTest {

    // Mock the KafkaTemplate to avoid actual Kafka connections during tests
    // This allows us to test event production logic without a running Kafka broker
    @Mock
    private KafkaTemplate<String, SmsEvent> kafkaTemplate;

    // Inject the mocked KafkaTemplate into SmsEventProducer
    // Mockito will automatically inject the mock above
    @InjectMocks
    private SmsEventProducer smsEventProducer;

    // ArgumentCaptor allows us to capture and inspect SmsEvent objects sent to Kafka
    // This lets us verify the event content (phone number, message, status, etc.)
    @Captor
    private ArgumentCaptor<SmsEvent> smsEventCaptor;

    // Used to capture System.out.println() output for testing console logging
    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;

    /**
     * Set up test environment before each test.
     * 
     * This method captures System.out to verify console logging behavior.
     * The original System.out is restored in tearDown() after each test.
     */
    @BeforeEach
    void setUp() {
        // Capture System.out for testing console output
        // This allows us to verify that the producer logs events correctly
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
    }

    /**
     * Tests successful SMS event production to Kafka.
     * 
     * This test verifies:
     * 1. Events are sent to the correct Kafka topic ("sms_events")
     * 2. Event content is preserved (phone number, message, status)
     * 3. Console logging occurs when events are produced
     * 4. The producer handles successful Kafka sends correctly
     */
    @Test
    void testSendSmsEvent_Success() throws Exception {
        // Arrange: Create a successful SMS event
        // Status "successful" indicates the SMS was sent successfully
        SmsEvent smsEvent = new SmsEvent("+1234567890", "Test message", "successful");
        
        // Create a mock SendResult to simulate successful Kafka send
        SendResult<String, SmsEvent> sendResult = mock(SendResult.class);
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        future.set(sendResult);  // Set the result immediately (simulates successful send)

        // Configure KafkaTemplate mock to return the successful future
        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act: Send the event to Kafka
        smsEventProducer.sendSmsEvent(smsEvent);

        // Assert: Verify the event was sent to the correct topic
        verify(kafkaTemplate, times(1)).send(eq("sms_events"), smsEventCaptor.capture());

        // Extract and verify the captured event content
        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals("+1234567890", capturedEvent.getPhoneNumber());
        assertEquals("Test message", capturedEvent.getMessage());
        assertEquals("successful", capturedEvent.getStatus());

        // Verify console logging occurred
        String output = outputStream.toString();
        assertTrue(output.contains("Produced SMS Event:"));
    }

    /**
     * Tests that events with "unsuccessful" status are produced correctly.
     * 
     * This test verifies:
     * 1. Failed SMS attempts are still logged to Kafka
     * 2. The "unsuccessful" status is preserved in the event
     * 3. All event fields (phone, message, status) are correctly sent
     * 
     * This is important for audit trails - we need to track failed attempts too.
     */
    @Test
    void testSendSmsEvent_WithFailedStatus() throws Exception {
        // Arrange: Create an unsuccessful SMS event
        // Status "unsuccessful" indicates the SMS failed to send (e.g., Twillio error)
        SmsEvent smsEvent = new SmsEvent("+9876543210", "Failed message", "unsuccessful");
        SendResult<String, SmsEvent> sendResult = mock(SendResult.class);
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        future.set(sendResult);

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act: Send the unsuccessful event
        smsEventProducer.sendSmsEvent(smsEvent);

        // Assert: Verify the event was sent with correct content
        verify(kafkaTemplate, times(1)).send(eq("sms_events"), smsEventCaptor.capture());

        // Verify all event fields are preserved
        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals("+9876543210", capturedEvent.getPhoneNumber());
        assertEquals("Failed message", capturedEvent.getMessage());
        assertEquals("unsuccessful", capturedEvent.getStatus());
    }

    /**
     * Tests that events with "blocked" status (blacklisted numbers) are produced correctly.
     * 
     * This test verifies:
     * 1. Blacklisted SMS attempts are logged to Kafka
     * 2. The "blocked" status is preserved in the event
     * 3. All event fields are correctly sent for blocked attempts
     * 
     * This is important for security monitoring and compliance - we need to track
     * all blocked attempts for audit purposes.
     */
    @Test
    void testSendSmsEvent_WithBlacklistedStatus() throws Exception {
        // Arrange: Create a blocked SMS event
        // Status "blocked" indicates the phone number was blacklisted
        SmsEvent smsEvent = new SmsEvent("+1111111111", "Blocked", "blocked");
        SendResult<String, SmsEvent> sendResult = mock(SendResult.class);
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        future.set(sendResult);

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act: Send the blocked event
        smsEventProducer.sendSmsEvent(smsEvent);

        // Assert: Verify the event was sent with correct content
        verify(kafkaTemplate, times(1)).send(eq("sms_events"), smsEventCaptor.capture());

        // Verify all event fields are preserved
        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals("+1111111111", capturedEvent.getPhoneNumber());
        assertEquals("Blocked", capturedEvent.getMessage());
        assertEquals("blocked", capturedEvent.getStatus());
    }

    /**
     * Tests that events with custom event IDs are produced correctly.
     * 
     * This test verifies:
     * 1. Custom event IDs are preserved when sending to Kafka
     * 2. Event IDs can be set before sending
     * 3. The event ID is included in the Kafka message
     * 
     * Event IDs are useful for tracking, correlation, and deduplication.
     */
    @Test
    void testSendSmsEvent_WithEventId() throws Exception {
        // Arrange: Create an event with a custom event ID
        // Event IDs can be used for tracking, correlation, or deduplication
        SmsEvent smsEvent = new SmsEvent("+1234567890", "Test", "successful");
        smsEvent.setEventId("evt-12345");
        SendResult<String, SmsEvent> sendResult = mock(SendResult.class);
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        future.set(sendResult);

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act: Send the event with custom ID
        smsEventProducer.sendSmsEvent(smsEvent);

        // Assert: Verify the event ID was preserved
        verify(kafkaTemplate, times(1)).send(eq("sms_events"), smsEventCaptor.capture());

        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals("evt-12345", capturedEvent.getEventId());
    }

    /**
     * Tests that multiple events can be produced sequentially.
     * 
     * This test verifies:
     * 1. The producer can handle multiple sequential event sends
     * 2. Each event is sent independently
     * 3. There are no side effects or interference between multiple sends
     * 4. Events with different statuses can all be sent
     * 
     * This ensures the producer works correctly in high-throughput scenarios.
     */
    @Test
    void testSendSmsEvent_MultipleEvents() throws Exception {
        // Arrange: Create multiple events with different statuses
        // This tests that the producer can handle multiple sequential sends
        SmsEvent event1 = new SmsEvent("+1111111111", "Message 1", "successful");
        SmsEvent event2 = new SmsEvent("+2222222222", "Message 2", "successful");
        SmsEvent event3 = new SmsEvent("+3333333333", "Message 3", "unsuccessful");
        
        SendResult<String, SmsEvent> sendResult = mock(SendResult.class);
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        future.set(sendResult);

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act: Send three events sequentially
        smsEventProducer.sendSmsEvent(event1);
        smsEventProducer.sendSmsEvent(event2);
        smsEventProducer.sendSmsEvent(event3);

        // Assert: Verify all three events were sent
        verify(kafkaTemplate, times(3)).send(eq("sms_events"), any(SmsEvent.class));
    }

    /**
     * Tests that events are sent to the correct Kafka topic.
     * 
     * This test verifies:
     * 1. Events are sent to the "sms_events" topic (not a different topic)
     * 2. The topic name is hardcoded correctly in the producer
     * 
     * This is critical - sending to the wrong topic would break downstream consumers.
     */
    @Test
    void testSendSmsEvent_VerifyTopicName() throws Exception {
        // Arrange: Create a test event
        SmsEvent smsEvent = new SmsEvent("+1234567890", "Test", "successful");
        SendResult<String, SmsEvent> sendResult = mock(SendResult.class);
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        future.set(sendResult);

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act: Send the event
        smsEventProducer.sendSmsEvent(smsEvent);

        // Assert: Verify the event was sent to the correct topic "sms_events"
        // Using eq() matcher ensures exact topic name match
        verify(kafkaTemplate).send(eq("sms_events"), any(SmsEvent.class));
    }

    /**
     * Tests that ExecutionException from Kafka is wrapped and rethrown as KafkaException.
     * 
     * This test verifies:
     * 1. When Kafka send fails with ExecutionException, it's caught and wrapped
     * 2. The exception message indicates the failure
     * 3. The producer doesn't silently swallow Kafka errors
     * 
     * ExecutionException can occur when Kafka broker is down, network issues, etc.
     */
    @Test
    void testSendSmsEvent_ThrowsKafkaExceptionOnExecutionException() throws Exception {
        // Arrange: Create a future that will throw ExecutionException
        // This simulates a Kafka send failure (e.g., broker down, network issue)
        SmsEvent smsEvent = new SmsEvent("+1234567890", "Test", "successful");
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        KafkaException kafkaException = new KafkaException("Kafka connection failed");
        future.setException(new ExecutionException(kafkaException));  // Wrap in ExecutionException

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act & Assert: Verify KafkaException is thrown
        // The producer should catch ExecutionException and wrap it in KafkaException
        KafkaException thrown = assertThrows(KafkaException.class, () -> {
            smsEventProducer.sendSmsEvent(smsEvent);
        });

        // Verify the exception message indicates the failure
        assertTrue(thrown.getMessage().contains("Failed to send SMS event to Kafka"));
        verify(kafkaTemplate, times(1)).send(eq("sms_events"), any(SmsEvent.class));
    }

    /**
     * Tests that timeout exceptions are handled correctly.
     * 
     * This test verifies:
     * 1. When Kafka send times out, TimeoutException is caught and wrapped
     * 2. The exception message indicates a timeout occurred
     * 3. The producer doesn't hang indefinitely waiting for Kafka
     * 
     * Timeouts can occur when Kafka is slow, overloaded, or network is congested.
     * The producer should have a timeout to prevent indefinite blocking.
     */
    @Test
    void testSendSmsEvent_ThrowsKafkaExceptionOnTimeout() throws Exception {
        // Arrange: Create a future that will never complete (simulates timeout)
        // This simulates Kafka being slow or unresponsive
        SmsEvent smsEvent = new SmsEvent("+1234567890", "Test", "successful");
        // Create a future that will timeout (never completes)
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        // Don't set the result, so .get() with timeout will throw TimeoutException

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act & Assert: Verify KafkaException is thrown after timeout
        // This will wait for the configured timeout (e.g., 5 seconds) then throw TimeoutException
        // The producer should catch it and wrap it in KafkaException
        KafkaException thrown = assertThrows(KafkaException.class, () -> {
            smsEventProducer.sendSmsEvent(smsEvent);
        });

        // Verify the exception message indicates a timeout
        assertTrue(thrown.getMessage().contains("Timeout") || thrown.getMessage().contains("timeout"));
        verify(kafkaTemplate, times(1)).send(eq("sms_events"), any(SmsEvent.class));
    }

    /**
     * Tests that InterruptedException is handled correctly.
     * 
     * This test verifies:
     * 1. When the thread is interrupted during Kafka send, InterruptedException is caught
     * 2. The exception is wrapped in KafkaException
     * 3. The interrupt flag is properly handled (implementation detail)
     * 
     * InterruptedException can occur when the thread is interrupted while waiting
     * for Kafka response (e.g., during shutdown or cancellation).
     */
    @Test
    void testSendSmsEvent_ThrowsKafkaExceptionOnInterruption() throws Exception {
        // Arrange: Create a future that will throw InterruptedException
        // This simulates the thread being interrupted while waiting for Kafka
        SmsEvent smsEvent = new SmsEvent("+1234567890", "Test", "successful");
        SettableListenableFuture<SendResult<String, SmsEvent>> future = new SettableListenableFuture<>();
        future.setException(new InterruptedException("Interrupted"));

        when(kafkaTemplate.send(eq("sms_events"), any(SmsEvent.class))).thenReturn(future);

        // Act & Assert: Verify KafkaException is thrown
        // The producer should catch InterruptedException and wrap it
        KafkaException thrown = assertThrows(KafkaException.class, () -> {
            smsEventProducer.sendSmsEvent(smsEvent);
        });

        assertTrue(thrown.getMessage().contains("Interrupted"));
        verify(kafkaTemplate, times(1)).send(eq("sms_events"), any(SmsEvent.class));
        // Note: The implementation sets interrupt flag, but we can't reliably test it here
        // as the test framework may clear it
    }

    /**
     * Tests that the SmsEventProducer can be instantiated.
     * 
     * This test verifies:
     * 1. The constructor accepts a KafkaTemplate parameter
     * 2. The producer can be created successfully
     * 
     * This is a basic sanity check for the constructor.
     */
    @Test
    void testConstructor() {
        // Act: Create a new producer instance
        SmsEventProducer producer = new SmsEventProducer(kafkaTemplate);

        // Assert: Verify the producer was created (not null)
        assertNotNull(producer);
    }

    /**
     * Clean up after each test.
     * 
     * This method restores the original System.out that was captured in setUp().
     * This ensures tests don't interfere with each other's console output.
     */
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        // Restore original System.out
        // This undoes the capture we did in setUp() to prevent test interference
        System.setOut(originalOut);
    }
}
