package com.example.demo.model;

public class SmsResponse {
    private String result;
    private String messageId;

    public SmsResponse() {
    }
    public SmsResponse(String result) {
        this.result = result;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
