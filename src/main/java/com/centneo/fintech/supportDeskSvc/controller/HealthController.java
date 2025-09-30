package com.centneo.fintech.supportDeskSvc.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("health")
public class HealthController {


    @GetMapping("primaryCheck")
    public ResponseEntity<Object> getHealthCheck() {
        return ResponseEntity.ok("Healthy OK");
    }
}
