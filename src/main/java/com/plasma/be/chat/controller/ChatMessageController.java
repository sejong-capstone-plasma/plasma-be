package com.plasma.be.chat.controller;

import com.plasma.be.chat.dto.ChatMessageCreateRequest;
import com.plasma.be.chat.dto.ChatMessageSummaryResponse;
import com.plasma.be.chat.dto.ChatSessionSummaryResponse;
import com.plasma.be.chat.dto.ChatSessionsEndRequest;
import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.chat.service.ChatMessageService;
import com.plasma.be.extract.dto.ExtractionResponse;
import com.plasma.be.extract.service.ExtractService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@RestController
public class ChatMessageController implements ChatMessageApi {

    private final ChatMessageService chatMessageService;
    private final ExtractService extractService;

    public ChatMessageController(ChatMessageService chatMessageService, ExtractService extractService) {
        this.chatMessageService = chatMessageService;
        this.extractService = extractService;
    }

    @Override
    public ResponseEntity<ExtractionResponse> createMessage(ChatMessageCreateRequest request) {
        ChatMessage savedMessage = chatMessageService.saveMessage(request);
        ExtractionResponse response = extractService.extractFromMessage(savedMessage);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<ChatSessionSummaryResponse>> getSessionList() {
        return ResponseEntity.ok(chatMessageService.findSessions());
    }

    @Override
    public ResponseEntity<List<ChatMessageSummaryResponse>> getMessageList(String sessionId) {
        return ResponseEntity.ok(chatMessageService.findMessagesBySessionId(sessionId));
    }

    @Override
    public ResponseEntity<Void> endSession(String sessionId) {
        chatMessageService.endSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> endSessions(ChatSessionsEndRequest request) {
        chatMessageService.endSessions(request == null ? List.of() : request.sessionIds());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> handleRestClientException(RestClientException exception) {
        return ResponseEntity.internalServerError().body(Map.of("message", "AI server error: " + exception.getMessage()));
    }
}
