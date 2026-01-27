package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import com.example.demo.model.SmsResponse;
import com.example.demo.service.SmsService;
import com.example.demo.model.SmsRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("v1/sms/send")
public class SmsControllerV1 {
    private final SmsService service;

    @Autowired // used to inject SmsService
    public SmsControllerV1(SmsService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SmsResponse> sendSmsRequest(@Valid @RequestBody SmsRequest request) {
        String result = null;
        try {
            result = service.sendSms(request);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new SmsResponse("Server error kindly try again later"));
        }
        return ResponseEntity.ok(new SmsResponse(result));
    }
}
