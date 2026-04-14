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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat/messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final ExtractService extractService;

    public ChatMessageController(ChatMessageService chatMessageService, ExtractService extractService) {
        this.chatMessageService = chatMessageService;
        this.extractService = extractService;
    }

    @PostMapping
    public ResponseEntity<ExtractionResponse> createMessage(@RequestBody ChatMessageCreateRequest request) {
        ChatMessage savedMessage = chatMessageService.saveMessage(request);
        ExtractionResponse response = extractService.extractFromMessage(savedMessage);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionSummaryResponse>> getSessionList() {
        return ResponseEntity.ok(chatMessageService.findSessions());
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<List<ChatMessageSummaryResponse>> getMessageList(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatMessageService.findMessagesBySessionId(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/end")
    public ResponseEntity<Void> endSession(@PathVariable String sessionId) {
        chatMessageService.endSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/end")
    public ResponseEntity<Void> endSessions(@RequestBody(required = false) ChatSessionsEndRequest request) {
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
