package com.plasma.be.plasma.controller;

import com.plasma.be.plasma.dto.ExtractParametersRequest;
import com.plasma.be.plasma.dto.ExtractParametersResponse;
import com.plasma.be.plasma.service.PlasmaAIService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/plasma")
public class PlasmaAIController {

    private final PlasmaAIService plasmaAIService;

    public PlasmaAIController(PlasmaAIService plasmaAIService) {
        this.plasmaAIService = plasmaAIService;
    }

    @PostMapping("/extract")
    public ResponseEntity<ExtractParametersResponse> extractParameters(@RequestBody ExtractParametersRequest request) {
        return ResponseEntity.ok(plasmaAIService.extractParameters(request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> handleRestClientException(RestClientException exception) {
        return ResponseEntity.internalServerError().body(Map.of("message", "Plasma AI server error: " + exception.getMessage()));
    }
}
