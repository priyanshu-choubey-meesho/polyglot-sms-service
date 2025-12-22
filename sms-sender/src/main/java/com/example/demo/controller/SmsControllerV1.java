package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.SmsResponse;
import com.example.demo.service.SmsService;
import com.example.demo.model.SmsRequest;

@RestController
@RequestMapping("v1/sms/send")
public class SmsControllerV1 {
    private final SmsService service;

    @Autowired //used to inject SmsService
    public SmsControllerV1(SmsService service) {
        this.service = service;
    }
    @PostMapping
    public ResponseEntity<SmsResponse> sendSmsRequest(@RequestBody SmsRequest request) {
        String result= service.sendSms(request);
        return ResponseEntity.ok(new SmsResponse(result));    
    }
}
