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

@WebMvcTest(SmsControllerV1.class)
class SmsSenderApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private SmsService smsService;

	@Test
	void contextLoads() {
	}

	@Test
	void testValidPhoneNumber() throws Exception {
		// Arrange - Mock service behavior
		when(smsService.sendSms(any(SmsRequest.class)))
				.thenReturn("SMS sent to +1234567890");

		String validRequest = "{\n" +
				"    \"phoneNumber\": \"+1234567890\",\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		// Act & Assert
		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("SMS sent to +1234567890"));

		// Verify service was called
		verify(smsService, times(1)).sendSms(any(SmsRequest.class));
	}

	@Test
	void testInvalidPhoneNumber_StartsWithZero() throws Exception {
		String invalidRequest = "{\n" +
				"    \"phoneNumber\": \"0123456789\",\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		// Service should not be called for invalid input
		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	@Test
	void testInvalidPhoneNumber_TooShort() throws Exception {
		String invalidRequest = "{\n" +
				"    \"phoneNumber\": \"+12\",\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	@Test
	void testInvalidPhoneNumber_TooLong() throws Exception {
		String invalidRequest = "{\n" +
				"    \"phoneNumber\": \"+12345678901234567890\",\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	@Test
	void testInvalidPhoneNumber_ContainsLetters() throws Exception {
		String invalidRequest = "{\n" +
				"    \"phoneNumber\": \"+123abc7890\",\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	@Test
	void testInvalidPhoneNumber_ContainsSpecialChars() throws Exception {
		String invalidRequest = "{\n" +
				"    \"phoneNumber\": \"+123-456-7890\",\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	@Test
	void testMissingPhoneNumber() throws Exception {
		String invalidRequest = "{\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	@Test
	void testEmptyPhoneNumber() throws Exception {
		String invalidRequest = "{\n" +
				"    \"phoneNumber\": \"\",\n" +
				"    \"message\": \"Hello World\"\n" +
				"}";

		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	@Test
	void testMissingMessage() throws Exception {
		String invalidRequest = "{\n" +
				"    \"phoneNumber\": \"+1234567890\"\n" +
				"}";

		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
				.andExpect(status().isBadRequest());

		verify(smsService, never()).sendSms(any(SmsRequest.class));
	}

	@Test
	void testBlacklistedNumber() throws Exception {
		// Arrange - Mock service returning blacklist failure
		when(smsService.sendSms(any(SmsRequest.class)))
				.thenReturn("Failed: Phone number is blacklisted");

		String request = "{\n" +
				"    \"phoneNumber\": \"+1234567890\",\n" +
				"    \"message\": \"Test\"\n" +
				"}";

		// Act & Assert
		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("Failed: Phone number is blacklisted"));

		verify(smsService, times(1)).sendSms(any(SmsRequest.class));
	}

	@Test
	void testSuccessResponse_ContainsPhoneNumber() throws Exception {
		// Arrange
		when(smsService.sendSms(any(SmsRequest.class)))
				.thenReturn("SMS sent to +9876543210");

		String request = "{\n" +
				"    \"phoneNumber\": \"+9876543210\",\n" +
				"    \"message\": \"Test message\"\n" +
				"}";

		// Act & Assert
		mockMvc.perform(post("/v1/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("SMS sent to +9876543210"));
	}
}
