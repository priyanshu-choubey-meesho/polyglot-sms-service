package com.example.demo.model;

public class SmsEvent {
    private String phoneNumber;
    private String message;
    private String status;
    private String eventId;
    public SmsEvent() {
    }
    public SmsEvent(String phoneNumber, String message, String status) {
        this.phoneNumber = phoneNumber;
        this.message = message;
        this.status = status;
    }
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
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public String getEventId() {
        return eventId;
    }
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
}
