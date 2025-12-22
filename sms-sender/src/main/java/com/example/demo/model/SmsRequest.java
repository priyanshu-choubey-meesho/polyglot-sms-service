package com.example.demo.model;

import jakarta.validation.constraints.NotBlank;

public class SmsRequest {
    @NotBlank(message = "Phone number is mandatory")
    private String phoneNumber;
    @NotBlank(message = "Message is mandatory")
    private String message;
    public SmsRequest() {}
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}