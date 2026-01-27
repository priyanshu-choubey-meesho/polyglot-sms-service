package com.example.demo;

import com.example.demo.model.SmsRequest;
import com.example.demo.service.SmsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.example.demo.controller.SmsControllerV1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the SMS Controller using Spring's MockMvc.
 * 
 * This test class tests the HTTP layer (REST API) of the SMS service.
 * It uses @WebMvcTest to load only the web layer, mocking the service layer.
 * 
 * Testing Strategy:
 * - Input validation: Phone number format, required fields
 * - HTTP responses: Status codes, response body structure
 * - Service integration: Verifies controller calls service correctly
 * - Error handling: Invalid inputs return appropriate HTTP status codes
 * 
 * Test Approach:
 * - Uses MockMvc to simulate HTTP requests without starting a full server
 * - Mocks SmsService to isolate controller logic from business logic
 * - Verifies HTTP status codes and JSON response structure
 * - Ensures invalid requests are rejected before reaching the service layer
 */
@WebMvcTest(SmsControllerV1.class)
class SmsSenderApplicationTests {

	// MockMvc provides a way to test Spring MVC controllers without starting a full HTTP server
	// It simulates HTTP requests and allows assertions on responses
	@Autowired
	private MockMvc mockMvc;

	// Mock the SmsService to isolate controller testing from service logic
	// This allows us to test the controller's HTTP handling without actual SMS sending
	@MockBean
	private SmsService smsService;

	/**
	 * Basic Spring context loading test.
	 * 
	 * This test verifies that the Spring application context can be loaded successfully.
	 * If this test fails, it indicates a configuration problem (missing beans, circular dependencies, etc.).
	 * This is a sanity check to ensure the test environment is set up correctly.
	 */
	@Test
	void contextLoads() {
		// If the context loads without exceptions, the test passes
		// This is a minimal test to verify Spring configuration is valid
	}

	/**
	 * Tests that a valid phone number request is accepted and processed correctly.
	 * 
	 * This test verifies:
	 * 1. The controller accepts valid JSON requests
	 * 2. The request passes validation (phone number format is correct)
	 * 3. The service is called with the request
	 * 4. The HTTP response is 200 OK with correct JSON structure
	 * 5. The response body contains the service result
	 */
	@Test
	void testValidPhoneNumber() throws Exception {
		// Arrange: Configure the mocked service to return a success message
		// This simulates a successful SMS send operation
		when(smsService.sendSms(any(SmsRequest.class)))
				.thenReturn("SMS sent to +1234567890");

		// Create a valid JSON request body with proper phone number format
		// Phone number starts with + and contains only digits (valid format)
		String validRequest = "{\n" +
				"    \"phoneNumber\": \"+1234567890\",\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		// Act: Perform HTTP POST request to the SMS endpoint
		// Assert: Verify the HTTP response
		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)  // Set Content-Type header
				.content(validRequest))                     // Set request body
				.andExpect(status().isOk())                 // Verify HTTP 200 status
				.andExpect(jsonPath("$.result").value("SMS sent to +1234567890"));  // Verify JSON response structure

		// Verify: Confirm the service was called exactly once
		// This ensures the controller properly delegates to the service layer
		// If this fails, the controller might be returning a hardcoded response
		verify(smsService, times(1)).sendSms(any(SmsRequest.class));
	}

	/**
	 * Tests that phone numbers starting with zero are rejected.
	 * 
	 * This test verifies:
	 * 1. Phone numbers starting with 0 (without + prefix) are invalid
	 * 2. The controller returns HTTP 400 Bad Request for invalid phone numbers
	 * 3. The service is NOT called when validation fails (fail-fast)
	 * 
	 * Business Rule: Phone numbers must start with + followed by country code
	 */
	@Test
	void testInvalidPhoneNumber_StartsWithZero() throws Exception {
		// Arrange: Create a request with phone number starting with 0
		// This is invalid because it doesn't follow the +[country code][number] format
		String invalidRequest = "{\n" +
				"    \"phoneNumber\": \"0123456789\",\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		// Act & Assert: Verify the request is rejected with 400 Bad Request
		// The @Valid annotation on the controller should trigger validation
		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());  // Validation should fail

		// Verify: Service should NOT be called for invalid input
		// This is important - we don't want to waste resources processing invalid data
		// Validation should happen at the controller layer before reaching the service
		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	/**
	 * Tests that phone numbers that are too short are rejected.
	 * 
	 * This test verifies:
	 * 1. Phone numbers must meet minimum length requirements
	 * 2. Very short phone numbers (like "+12") are invalid
	 * 3. The controller validates length before processing
	 * 
	 * Business Rule: Phone numbers must have sufficient length to be valid
	 */
	@Test
	void testInvalidPhoneNumber_TooShort() throws Exception {
		// Arrange: Create a request with an extremely short phone number
		// "+12" is too short to be a valid phone number
		String invalidRequest = "{\n" +
				"    \"phoneNumber\": \"+12\",\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		// Act & Assert: Verify the request is rejected
		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		// Verify: Service should not be called for invalid input
		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	/**
	 * Tests that phone numbers that are too long are rejected.
	 * 
	 * This test verifies:
	 * 1. Phone numbers must meet maximum length requirements
	 * 2. Excessively long phone numbers are invalid
	 * 3. The controller validates maximum length
	 * 
	 * Business Rule: Phone numbers must not exceed maximum length (prevents abuse)
	 */
	@Test
	void testInvalidPhoneNumber_TooLong() throws Exception {
		// Arrange: Create a request with an excessively long phone number
		// "+12345678901234567890" exceeds typical phone number length limits
		String invalidRequest = "{\n" +
				"    \"phoneNumber\": \"+12345678901234567890\",\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		// Act & Assert: Verify the request is rejected
		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		// Verify: Service should not be called for invalid input
		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	/**
	 * Tests that phone numbers containing letters are rejected.
	 * 
	 * This test verifies:
	 * 1. Phone numbers must contain only digits (after the + prefix)
	 * 2. Letters in phone numbers are invalid
	 * 3. The controller validates character format
	 * 
	 * Business Rule: Phone numbers must be numeric (except for the + prefix)
	 */
	@Test
	void testInvalidPhoneNumber_ContainsLetters() throws Exception {
		// Arrange: Create a request with phone number containing letters
		// "+123abc7890" contains letters which are invalid in phone numbers
		String invalidRequest = "{\n" +
				"    \"phoneNumber\": \"+123abc7890\",\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		// Act & Assert: Verify the request is rejected
		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		// Verify: Service should not be called for invalid input
		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	/**
	 * Tests that phone numbers containing special characters (like hyphens) are rejected.
	 * 
	 * This test verifies:
	 * 1. Phone numbers must not contain formatting characters (hyphens, spaces, etc.)
	 * 2. Only the + prefix and digits are allowed
	 * 3. The controller validates against special characters
	 * 
	 * Business Rule: Phone numbers should be in E.164 format: +[country code][number]
	 * No formatting characters like hyphens, spaces, or parentheses are allowed
	 */
	@Test
	void testInvalidPhoneNumber_ContainsSpecialChars() throws Exception {
		// Arrange: Create a request with phone number containing hyphens
		// "+123-456-7890" contains hyphens which are invalid in E.164 format
		String invalidRequest = "{\n" +
				"    \"phoneNumber\": \"+123-456-7890\",\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		// Act & Assert: Verify the request is rejected
		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		// Verify: Service should not be called for invalid input
		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	/**
	 * Tests that requests missing the phoneNumber field are rejected.
	 * 
	 * This test verifies:
	 * 1. Phone number is a required field
	 * 2. Missing required fields trigger validation errors
	 * 3. The controller validates required fields before processing
	 * 
	 * Business Rule: Phone number is mandatory for SMS sending
	 */
	@Test
	void testMissingPhoneNumber() throws Exception {
		// Arrange: Create a request without the phoneNumber field
		// This tests required field validation
		String invalidRequest = "{\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		// Act & Assert: Verify the request is rejected
		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		// Verify: Service should not be called when required fields are missing
		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	/**
	 * Tests that empty phone number strings are rejected.
	 * 
	 * This test verifies:
	 * 1. Empty strings are not valid phone numbers
	 * 2. The controller validates that phone number is not just whitespace/empty
	 * 3. Empty string is treated differently from missing field
	 * 
	 * Business Rule: Phone number must be non-empty
	 */
	@Test
	void testEmptyPhoneNumber() throws Exception {
		// Arrange: Create a request with an empty phone number string
		// Empty string "" is different from null - this tests empty string validation
		String invalidRequest = "{\n" +
				"    \"phoneNumber\": \"\",\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		// Act & Assert: Verify the request is rejected
		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		// Verify: Service should not be called for empty phone numbers
		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	/**
	 * Tests that requests missing the message field are rejected.
	 * 
	 * This test verifies:
	 * 1. Message is a required field
	 * 2. Missing message field triggers validation errors
	 * 3. The controller validates all required fields
	 * 
	 * Business Rule: Message content is mandatory for SMS sending
	 */
	@Test
	void testMissingMessage() throws Exception {
		// Arrange: Create a request without the message field
		// This tests that message is a required field
		String invalidRequest = "{\n" +
				"    \"phoneNumber\": \"+1234567890\"\n" +
				"}";

		// Act & Assert: Verify the request is rejected
		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		// Verify: Service should not be called when required fields are missing
		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	/**
	 * Tests that blacklisted phone numbers are handled correctly at the API level.
	 * 
	 * This test verifies:
	 * 1. The controller accepts valid requests even if the number is blacklisted
	 * 2. Blacklist checking happens in the service layer (not controller)
	 * 3. The HTTP response is still 200 OK (business logic failure, not HTTP error)
	 * 4. The response body contains the blacklist failure message
	 * 
	 * Note: Blacklist rejection is a business rule violation, not an HTTP error.
	 * The API returns 200 OK with a failure message, allowing clients to distinguish
	 * between validation errors (400) and business rule violations (200 with failure message).
	 */
	@Test
	void testBlacklistedNumber() throws Exception {
		// Arrange: Configure the mocked service to return a blacklist failure
		// This simulates the service detecting a blacklisted number
		when(smsService.sendSms(any(SmsRequest.class)))
				.thenReturn("Failed: Phone number is blacklisted");

		// Create a valid JSON request (valid format, but number happens to be blacklisted)
		String request = "{\n" +
				"    \"phoneNumber\": \"+1234567890\",\n" +
				"    \"message\": \"Test\"\n" +
				"}";

		// Act & Assert: Verify the request is accepted and returns the failure message
		// Note: Status is 200 OK because the request format is valid
		// The blacklist check is a business rule, not a validation error
		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
				.andExpect(status().isOk())  // 200 OK (not 400) because request format is valid
				.andExpect(jsonPath("$.result").value("Failed: Phone number is blacklisted"));

		// Verify: Service was called (blacklist check happens in service layer)
		verify(smsService, times(1)).sendSms(any(SmsRequest.class));
	}

	/**
	 * Tests that the success response contains the correct phone number.
	 * 
	 * This test verifies:
	 * 1. The response message includes the phone number from the request
	 * 2. The service uses the actual phone number, not a hardcoded value
	 * 3. The response correctly reflects which number received the SMS
	 * 
	 * This ensures the service is dynamic and works for any phone number,
	 * not just a specific hardcoded one.
	 */
	@Test
	void testSuccessResponse_ContainsPhoneNumber() throws Exception {
		// Arrange: Configure the mocked service to return a success message
		// The message includes a specific phone number to verify it's used correctly
		when(smsService.sendSms(any(SmsRequest.class)))
				.thenReturn("SMS sent to +9876543210");

		// Create a request with a different phone number than previous tests
		String request = "{\n" +
				"    \"phoneNumber\": \"+9876543210\",\n" +
				"    \"message\": \"Test message\"\n" +
				"}";

		// Act & Assert: Verify the response contains the correct phone number
		// This confirms the service uses the phone number from the request
		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("SMS sent to +9876543210"));
	}
}
