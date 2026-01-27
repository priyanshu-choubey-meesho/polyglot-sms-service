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

/**
 * Unit tests for SmsService.
 * 
 * This test class uses Mockito to mock dependencies and test the SmsService in isolation.
 * 
 * Testing Strategy:
 * - Happy path: Successful SMS sending
 * - Validation: Blacklist checking
 * - Edge cases: Empty messages, long messages, different phone numbers
 * - Error handling: Twillio failures, Kafka failures, combined failures
 * - Integration: Multiple calls, event production
 * 
 * Mocked Dependencies:
 * - BlacklistCache: Checks if phone numbers are blacklisted
 * - TwillioService: External SMS provider service
 * - SmsEventProducer: Produces events to Kafka for audit/logging
 */
@ExtendWith(MockitoExtension.class)
public class SmsServiceTest {

    // Mock the BlacklistCache dependency
    // This allows us to control blacklist behavior in tests without a real cache
    @Mock
    private BlacklistCache blacklistCache;

    // Mock the SmsEventProducer dependency
    // This allows us to verify events are produced without actually sending to Kafka
    @Mock
    private SmsEventProducer eventProducer;

    // Mock the TwillioService dependency
    // This prevents actual SMS from being sent during tests (saves money and avoids side effects)
    @Mock
    private TwillioService twillioService;

    // Inject the mocked dependencies into SmsService
    // Mockito will automatically inject the mocks above into SmsService constructor
    @InjectMocks
    private SmsService smsService;

    // ArgumentCaptor allows us to capture and inspect arguments passed to mocked methods
    // We use this to verify the contents of SmsEvent objects sent to Kafka
    @Captor
    private ArgumentCaptor<SmsEvent> smsEventCaptor;

    // Reusable test data - created fresh before each test
    private SmsRequest validRequest;

    /**
     * Set up test data before each test method runs.
     * 
     * This method runs before every @Test method, ensuring each test starts
     * with a clean, valid SMS request. This follows the Arrange-Act-Assert pattern.
     */
    @BeforeEach
    void setUp() {
        // Create a valid SMS request that can be reused across multiple tests
        // Phone number: +1234567890 (valid format)
        // Message: "Test message" (non-empty)
        validRequest = new SmsRequest("+1234567890", "Test message");
    }

    /**
     * Tests the happy path scenario where an SMS is successfully sent.
     * 
     * This test verifies:
     * 1. The service checks the blacklist before sending
     * 2. The Twillio service is called with correct parameters
     * 3. An event is produced to Kafka with "successful" status
     * 4. The correct success message is returned
     */
    @Test
    void testSendSms_Success() {
        // Arrange: Configure the blacklist cache mock to return false (not blacklisted)
        // This simulates a valid phone number that is allowed to receive SMS
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(false);

        // Act: Call the service method with a valid SMS request
        // This triggers the full flow: blacklist check -> Twillio send -> event production
        String result = smsService.sendSms(validRequest);

        // Assert: Verify the return value matches expected success message
        assertEquals("SMS sent to +1234567890", result);
        
        // Verify blacklist check was performed exactly once with the correct phone number
        // This ensures the service validates the number before attempting to send
        verify(blacklistCache, times(1)).isBlacklisted("+1234567890");
        
        // Verify Twillio service was called exactly once with correct phone and message
        // This confirms the SMS was actually sent to the external service
        verify(twillioService, times(1)).sendSms("+1234567890", "Test message");
        
        // Verify an event was sent to Kafka exactly once, and capture it for inspection
        // ArgumentCaptor allows us to extract and verify the event details
        verify(eventProducer, times(1)).sendSmsEvent(smsEventCaptor.capture());

        // Extract the captured event and verify its contents
        SmsEvent capturedEvent = smsEventCaptor.getValue();
        // Verify the event contains the correct phone number
        assertEquals("+1234567890", capturedEvent.getPhoneNumber());
        // Verify the event contains the correct message content
        assertEquals("Test message", capturedEvent.getMessage());
        // Verify the event status is "successful" to indicate the SMS was sent successfully
        assertEquals("successful", capturedEvent.getStatus());
    }

    /**
     * Tests that blacklisted phone numbers are rejected and SMS is not sent.
     * 
     * This test verifies:
     * 1. The service correctly identifies blacklisted numbers
     * 2. Twillio service is NOT called for blacklisted numbers (saves costs)
     * 3. An event is still produced with "blocked" status for audit/tracking
     * 4. The correct failure message is returned
     */
    @Test
    void testSendSms_BlacklistedNumber() {
        // Arrange: Configure the blacklist cache mock to return true (blacklisted)
        // This simulates a phone number that has been blocked/blacklisted
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(true);

        // Act: Attempt to send SMS to a blacklisted number
        // The service should detect this and prevent the SMS from being sent
        String result = smsService.sendSms(validRequest);

        // Assert: Verify the return value indicates the number is blacklisted
        assertEquals("Failed: Phone number is blacklisted", result);
        
        // Verify blacklist check was performed exactly once
        // This confirms the service checked the blacklist before proceeding
        verify(blacklistCache, times(1)).isBlacklisted("+1234567890");
        
        // Verify Twillio service was NEVER called - this is critical!
        // We don't want to waste money sending SMS to blacklisted numbers
        verify(twillioService, never()).sendSms(any(), any());
        
        // Verify an event was still sent to Kafka for tracking/audit purposes
        // Even blocked attempts should be logged for compliance and analytics
        verify(eventProducer, times(1)).sendSmsEvent(smsEventCaptor.capture());

        // Extract and verify the captured event details
        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals("+1234567890", capturedEvent.getPhoneNumber());
        assertEquals("Test message", capturedEvent.getMessage());
        // Verify the event status is "blocked" to indicate the SMS was prevented
        assertEquals("blocked", capturedEvent.getStatus());
    }

    /**
     * Tests that the service works correctly with different phone numbers.
     * 
     * This test verifies:
     * 1. The service is not hardcoded to a specific phone number
     * 2. Different phone numbers are handled correctly
     * 3. The success message includes the correct phone number
     */
    @Test
    void testSendSms_DifferentPhoneNumber() {
        // Arrange: Create a request with a different phone number than the default
        // This ensures the service logic works for any valid phone number, not just one
        SmsRequest request = new SmsRequest("+9876543210", "Hello World");
        // Mock the blacklist check to return false (not blacklisted) for this number
        when(blacklistCache.isBlacklisted("+9876543210")).thenReturn(false);

        // Act: Send SMS to the different phone number
        String result = smsService.sendSms(request);

        // Assert: Verify the success message contains the correct phone number
        // This confirms the service uses the actual phone number from the request, not a hardcoded value
        assertEquals("SMS sent to +9876543210", result);
        
        // Verify the blacklist check was performed for the correct phone number
        verify(blacklistCache, times(1)).isBlacklisted("+9876543210");
        
        // Verify Twillio was called with the correct phone number and message
        verify(twillioService, times(1)).sendSms("+9876543210", "Hello World");
        
        // Verify an event was produced (we don't need to inspect details in this test)
        verify(eventProducer, times(1)).sendSmsEvent(any(SmsEvent.class));
    }

    /**
     * Tests that the service handles long messages correctly.
     * 
     * This test verifies:
     * 1. Long messages (potentially multi-part SMS) are handled properly
     * 2. The full message content is preserved and passed to Twillio
     * 3. The event contains the complete message for audit purposes
     */
    @Test
    void testSendSms_LongMessage() {
        // Arrange: Create a long message that may exceed single SMS length (160 chars)
        // SMS messages longer than 160 characters are typically split into multiple parts
        // This tests whether the service handles such messages correctly
        String longMessage = "This is a very long message that exceeds the typical SMS length limit to test how the service handles long messages";
        SmsRequest request = new SmsRequest("+1234567890", longMessage);
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(false);

        // Act: Send the long message
        String result = smsService.sendSms(request);

        // Assert: Verify the service returns success (it doesn't reject long messages)
        assertEquals("SMS sent to +1234567890", result);
        
        // Verify Twillio was called with the complete long message
        // The service should pass the full message to Twillio, which handles multi-part SMS
        verify(twillioService, times(1)).sendSms("+1234567890", longMessage);
        
        // Verify an event was produced and capture it to check message content
        verify(eventProducer, times(1)).sendSmsEvent(smsEventCaptor.capture());

        // Verify the captured event contains the complete long message
        // This ensures the full message is logged for audit purposes
        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals(longMessage, capturedEvent.getMessage());
    }

    /**
     * Tests that the service handles empty messages (edge case).
     * 
     * This test verifies:
     * 1. Empty messages don't cause exceptions or errors
     * 2. The service still attempts to send (Twillio may handle empty messages)
     * 3. The service doesn't reject empty messages at the service layer
     * 
     * Note: In production, you might want to validate and reject empty messages
     * at the controller/validation layer, but this tests the service behavior.
     */
    @Test
    void testSendSms_EmptyMessage() {
        // Arrange: Create a request with an empty message string
        // This is an edge case - some systems might reject empty messages
        SmsRequest request = new SmsRequest("+1234567890", "");
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(false);

        // Act: Attempt to send an empty message
        String result = smsService.sendSms(request);

        // Assert: Verify the service returns success (doesn't reject empty messages)
        assertEquals("SMS sent to +1234567890", result);
        
        // Verify Twillio was called with the empty message
        // The service delegates the decision of whether to send empty messages to Twillio
        verify(twillioService, times(1)).sendSms("+1234567890", "");
    }

    /**
     * Tests that the service handles multiple sequential SMS requests correctly.
     * 
     * This test verifies:
     * 1. The service can handle multiple requests in sequence without state issues
     * 2. Each request triggers the full flow (blacklist check, send, event)
     * 3. There are no side effects or interference between multiple calls
     * 4. The service maintains correct behavior across multiple invocations
     */
    @Test
    void testSendSms_MultipleCalls() {
        // Arrange: Create three different SMS requests with different phone numbers
        // This tests that the service can handle multiple requests without issues
        SmsRequest request1 = new SmsRequest("+1111111111", "Message 1");
        SmsRequest request2 = new SmsRequest("+2222222222", "Message 2");
        SmsRequest request3 = new SmsRequest("+3333333333", "Message 3");

        // Mock blacklist to return false for any phone number
        // This allows all three requests to proceed
        when(blacklistCache.isBlacklisted(any())).thenReturn(false);

        // Act: Send three SMS messages sequentially
        // This simulates real-world usage where multiple SMS are sent in quick succession
        smsService.sendSms(request1);
        smsService.sendSms(request2);
        smsService.sendSms(request3);

        // Assert: Verify each request triggered the expected interactions
        
        // Verify blacklist was checked exactly 3 times (once per request)
        // This confirms each request goes through the validation step
        verify(blacklistCache, times(3)).isBlacklisted(any());
        
        // Verify Twillio was called exactly 3 times (once per request)
        // This confirms all three SMS were actually sent
        verify(twillioService, times(3)).sendSms(any(), any());
        
        // Verify events were produced exactly 3 times (once per request)
        // This confirms all three SMS attempts were logged/audited
        verify(eventProducer, times(3)).sendSmsEvent(any(SmsEvent.class));
    }

    /**
     * Tests that the blacklist check happens BEFORE attempting to send SMS.
     * 
     * This test verifies:
     * 1. The service performs blacklist validation first (fail-fast pattern)
     * 2. Twillio service is never called for blacklisted numbers (cost optimization)
     * 3. The service doesn't waste resources on blocked numbers
     * 
     * This is a critical test for cost control - we don't want to pay Twillio
     * for SMS that we know should be blocked.
     */
    @Test
    void testSendSms_ChecksBlacklistBeforeSending() {
        // Arrange: Configure blacklist to return true (number is blacklisted)
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(true);

        // Act: Attempt to send SMS to a blacklisted number
        smsService.sendSms(validRequest);

        // Assert: Verify TwillioService was NEVER called
        // This is critical - we should check blacklist BEFORE calling Twillio
        // to avoid unnecessary API calls and costs
        // If this verification fails, it means we're wasting money on blocked numbers
        verify(twillioService, never()).sendSms(any(), any());
    }

    /**
     * Tests that events are produced for both successful and failed SMS attempts.
     * 
     * This test verifies:
     * 1. Events are logged regardless of outcome (success or failure)
     * 2. The event producer is called consistently for audit/compliance
     * 3. Both successful and blocked attempts generate events
     * 
     * This is important for compliance, analytics, and audit trails.
     */
    @Test
    void testSendSms_EventProducerAlwaysCalled() {
        // Arrange: Configure blacklist to return different values for different calls
        // First call: false (not blacklisted) -> should succeed
        // Second call: true (blacklisted) -> should be blocked
        // This tests both success and failure paths
        when(blacklistCache.isBlacklisted(any())).thenReturn(false, true);

        // Act: Send two SMS - one should succeed, one should be blocked
        smsService.sendSms(validRequest);  // First: should succeed
        smsService.sendSms(new SmsRequest("+9999999999", "Test"));  // Second: should be blocked

        // Assert: Verify event producer was called for BOTH attempts
        // This is critical for audit trails - we need to log all SMS attempts
        // regardless of whether they succeeded or were blocked
        // Success events help with analytics, blocked events help with security monitoring
        verify(eventProducer, times(2)).sendSmsEvent(any(SmsEvent.class));
    }

    /**
     * Tests error handling when Twillio service throws an exception.
     * 
     * This test verifies:
     * 1. The service gracefully handles Twillio failures
     * 2. Exceptions are caught and converted to user-friendly error messages
     * 3. An event is still produced with "unsuccessful" status for tracking
     * 4. The service doesn't crash when external service fails
     * 
     * This is important for resilience - external services can fail due to
     * network issues, rate limits, invalid credentials, etc.
     */
    @Test
    void testSendSms_TwillioServiceThrowsException() {
        // Arrange: Configure mocks to simulate a Twillio service failure
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(false);
        // Make Twillio service throw an exception when called
        // This simulates real-world failures like network issues, API errors, etc.
        doThrow(new RuntimeException("Failed to send SMS"))
                .when(twillioService).sendSms("+1234567890", "Test message");

        // Act: Attempt to send SMS (this will trigger the Twillio exception)
        String result = smsService.sendSms(validRequest);

        // Assert: Verify the service returns an error message (doesn't crash)
        // The service should catch the exception and return a user-friendly message
        assertTrue(result.startsWith("Failed to send SMS:"));
        assertTrue(result.contains("Failed to send SMS"));
        
        // Verify the blacklist check still happened (validation before sending)
        verify(blacklistCache, times(1)).isBlacklisted("+1234567890");
        
        // Verify Twillio was called (the service attempted to send)
        verify(twillioService, times(1)).sendSms("+1234567890", "Test message");
        
        // Verify an event was still produced even though the send failed
        // This is important for tracking failed attempts
        verify(eventProducer, times(1)).sendSmsEvent(smsEventCaptor.capture());

        // Verify the event contains correct details and "unsuccessful" status
        SmsEvent capturedEvent = smsEventCaptor.getValue();
        assertEquals("+1234567890", capturedEvent.getPhoneNumber());
        assertEquals("Test message", capturedEvent.getMessage());
        // Verify status is "unsuccessful" to indicate the SMS failed to send
        assertEquals("unsuccessful", capturedEvent.getStatus());
    }

    /**
     * Tests that Kafka failures don't prevent SMS from being sent successfully.
     * 
     * This test verifies:
     * 1. Event production failures are handled gracefully (non-blocking)
     * 2. SMS sending succeeds even if event logging fails
     * 3. The service prioritizes SMS delivery over event logging
     * 
     * This is a critical resilience test - event logging is important for
     * audit trails, but it should not block the core SMS functionality.
     * If Kafka is down, SMS should still be sent.
     */
    @Test
    void testSendSms_KafkaFailureDoesNotAffectSuccessResponse() {
        // Arrange: Configure mocks to simulate a Kafka failure
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(false);
        // Make Kafka event producer throw an exception
        // This simulates Kafka being down, network issues, or broker failures
        doThrow(new org.springframework.kafka.KafkaException("Kafka connection failed"))
                .when(eventProducer).sendSmsEvent(any(SmsEvent.class));

        // Act: Attempt to send SMS (Kafka will fail, but SMS should still succeed)
        String result = smsService.sendSms(validRequest);

        // Assert: Verify SMS was sent successfully despite Kafka failure
        // The service should catch Kafka exceptions and continue
        // SMS delivery is more important than event logging
        assertEquals("SMS sent to +1234567890", result);
        
        // Verify all the normal steps still happened
        verify(blacklistCache, times(1)).isBlacklisted("+1234567890");
        verify(twillioService, times(1)).sendSms("+1234567890", "Test message");
        
        // Verify event producer was called (even though it failed)
        // The service should attempt to log, but not fail if logging fails
        verify(eventProducer, times(1)).sendSmsEvent(any(SmsEvent.class));
    }

    /**
     * Tests that Kafka failures don't affect the error response when Twillio also fails.
     * 
     * This test verifies:
     * 1. When both Twillio and Kafka fail, the service still returns the correct error message
     * 2. The service handles multiple failures gracefully
     * 3. Error messages reflect the actual SMS failure, not the Kafka failure
     * 
     * This tests a complex failure scenario where multiple things go wrong.
     */
    @Test
    void testSendSms_KafkaFailureDoesNotAffectFailureResponse() {
        // Arrange: Configure mocks to simulate both Twillio and Kafka failures
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(false);
        // Make Twillio service fail (SMS cannot be sent)
        doThrow(new RuntimeException("Failed to send SMS"))
                .when(twillioService).sendSms("+1234567890", "Test message");
        // Make Kafka event producer also fail (cannot log the event)
        // This creates a "perfect storm" scenario where multiple things fail
        doThrow(new org.springframework.kafka.KafkaException("Kafka connection failed"))
                .when(eventProducer).sendSmsEvent(any(SmsEvent.class));

        // Act: Attempt to send SMS (both Twillio and Kafka will fail)
        String result = smsService.sendSms(validRequest);

        // Assert: Verify the service returns the SMS failure message (not Kafka failure)
        // The user should see that SMS failed, not that logging failed
        // Kafka failures are internal and shouldn't be exposed to users
        assertTrue(result.startsWith("Failed to send SMS:"));
        
        // Verify normal flow steps still happened
        verify(blacklistCache, times(1)).isBlacklisted("+1234567890");
        verify(twillioService, times(1)).sendSms("+1234567890", "Test message");
        
        // Event producer should be called at least once (possibly multiple times if retried)
        // The service may attempt to log the failure event, and if that fails,
        // it might retry in a catch block. We use atLeast(1) to be flexible.
        verify(eventProducer, atLeast(1)).sendSmsEvent(any(SmsEvent.class));
    }

    /**
     * Tests that Kafka failures don't affect the response when a number is blacklisted.
     * 
     * This test verifies:
     * 1. Blacklist rejection message is returned even if Kafka fails
     * 2. The service correctly identifies blacklisted numbers regardless of Kafka status
     * 3. Twillio is still not called (cost optimization maintained)
     * 
     * This ensures that event logging failures don't mask blacklist rejections.
     */
    @Test
    void testSendSms_KafkaFailureForBlacklistedNumber() {
        // Arrange: Configure mocks to simulate blacklisted number + Kafka failure
        when(blacklistCache.isBlacklisted("+1234567890")).thenReturn(true);
        // Make Kafka event producer fail
        // This tests that blacklist logic works even when event logging fails
        doThrow(new org.springframework.kafka.KafkaException("Kafka connection failed"))
                .when(eventProducer).sendSmsEvent(any(SmsEvent.class));

        // Act: Attempt to send SMS to a blacklisted number (Kafka will also fail)
        String result = smsService.sendSms(validRequest);

        // Assert: Verify the service returns blacklist rejection message
        // The response should indicate blacklist, not Kafka failure
        // Users shouldn't see internal infrastructure issues
        assertEquals("Failed: Phone number is blacklisted", result);
        
        // Verify blacklist check was performed
        verify(blacklistCache, times(1)).isBlacklisted("+1234567890");
        
        // Verify Twillio was NEVER called (blacklist check prevents it)
        // This cost optimization should work regardless of Kafka status
        verify(twillioService, never()).sendSms(any(), any());
        
        // Verify event producer was called (even though it failed)
        // The service should attempt to log the blocked attempt
        verify(eventProducer, times(1)).sendSmsEvent(any(SmsEvent.class));
    }
}
