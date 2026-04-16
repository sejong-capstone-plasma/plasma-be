package com.plasma.be.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.TreeMap;

@RestController
public class HealthCheckController implements HealthCheckApi {

    @Value("${server.env}")
    private String env;

    @Value("${server.port}")
    private String serverPort;

    @Value("${server.serverAddress}")
    private String serverAddress;

    @Value("${serverName}")
    private String serverName;

    @Override
    public ResponseEntity<?> healthCheck() {
        Map<String, String> response = new TreeMap<>();
        response.put("serverName", serverName);
        response.put("serverAddress", serverAddress);
        response.put("serverPort", serverPort);
        response.put("env", env);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<String> home() {
        return ResponseEntity.ok("PLASMA BACKEND");
    }

    @Override
    public ResponseEntity<?> getEnv() {
        return ResponseEntity.ok(env);
    }
}
