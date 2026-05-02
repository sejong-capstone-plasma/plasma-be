package com.plasma.be.extract.controller;

import com.plasma.be.chat.dto.ChatMessageCreateRequest;
import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.chat.service.ChatMessageService;
import com.plasma.be.extract.client.ExtractClient;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.dto.ExtractTestRequest;
import com.plasma.be.extract.dto.ParameterValidationResponse;
import com.plasma.be.extract.service.ExtractService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ExtractTestController implements ExtractTestApi {

    private final ExtractClient extractClient;
    private final ExtractService extractService;
    private final ChatMessageService chatMessageService;

    public ExtractTestController(ExtractClient extractClient,
                                 ExtractService extractService,
                                 ChatMessageService chatMessageService) {
        this.extractClient = extractClient;
        this.extractService = extractService;
        this.chatMessageService = chatMessageService;
    }

    @Override
    public ResponseEntity<?> ping() {
        try {
            extractClient.requestExtraction("테스트: 압력 50mTorr, 소스파워 500W, 바이어스파워 50W", List.of());
            return ResponseEntity.ok(Map.of("status", "ok", "message", "AI server is reachable"));
        } catch (RestClientException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "AI server error: " + e.getMessage()));
        }
    }

    @Override
    public ResponseEntity<ExtractedParameterData> extractRaw(ExtractTestRequest request) {
        validateInput(request);
        ExtractedParameterData data = extractClient.requestExtraction(request.userInput().trim(), List.of());
        return ResponseEntity.ok(data);
    }

    @Override
    public ResponseEntity<ParameterValidationResponse> extractAndSave(ExtractTestRequest request) {
        validateInput(request);
        String testSessionId = "test-" + UUID.randomUUID().toString().substring(0, 8);
        ChatMessage savedMessage = chatMessageService.saveMessage(
                new ChatMessageCreateRequest(testSessionId, "USER", null, request.userInput().trim()),
                "test-browser"
        );
        return ResponseEntity.ok(extractService.extractFromMessage(savedMessage.getMessageId()));
    }

    @Override
    public ResponseEntity<ParameterValidationResponse> getValidation(Long validationId) {
        return extractService.findByValidationId(validationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<List<ParameterValidationResponse>> getValidationsByMessageId(Long messageId) {
        return ResponseEntity.ok(extractService.findByMessageId(messageId));
    }

    private void validateInput(ExtractTestRequest request) {
        if (request == null || request.userInput() == null || request.userInput().isBlank()) {
            throw new IllegalArgumentException("userInput is required.");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException e) {
        return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> handleRestClientException(RestClientException e) {
        return ResponseEntity.internalServerError().body(Map.of("message", "AI server error: " + e.getMessage()));
    }
}
