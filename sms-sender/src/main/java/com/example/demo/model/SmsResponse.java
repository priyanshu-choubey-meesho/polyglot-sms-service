package com.example.demo.model;

public class SmsResponse {
    private String result;
    private String messageId;
    private String status;

    public SmsResponse() {
    }
    public SmsResponse(String result) {
        this.result = result;
        this.status = "success";
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
