package com.plasma.be.chat.controller;

import com.plasma.be.chat.dto.ChatMessageCreateRequest;
import com.plasma.be.chat.dto.ChatMessageCreateResponse;
import com.plasma.be.chat.dto.ChatMessageSummaryResponse;
import com.plasma.be.chat.dto.ChatSessionSummaryResponse;
import com.plasma.be.chat.dto.ChatSessionsEndRequest;
import com.plasma.be.chat.service.ChatMessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat/messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    public ChatMessageController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    @PostMapping
    public ResponseEntity<ChatMessageCreateResponse> create(@RequestBody ChatMessageCreateRequest request) {
        return ResponseEntity.ok(chatMessageService.create(request));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionSummaryResponse>> getSessions() {
        return ResponseEntity.ok(chatMessageService.getSessions());
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

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<List<ChatMessageSummaryResponse>> getMessages(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatMessageService.getMessages(sessionId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
