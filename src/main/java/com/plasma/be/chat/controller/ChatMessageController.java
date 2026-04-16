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

    // 채팅 메시지를 저장하고 AI 추출 결과를 함께 반환한다.
    @PostMapping
    public ResponseEntity<ExtractionResponse> createMessage(@RequestBody ChatMessageCreateRequest request) {
        ChatMessage savedMessage = chatMessageService.saveMessage(request);
        ExtractionResponse response = extractService.extractFromMessage(savedMessage);
        return ResponseEntity.ok(response);
    }

    // 사용자에게 보여지는 채팅 세션 목록을 조회한다.
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionSummaryResponse>> getSessionList() {
        return ResponseEntity.ok(chatMessageService.findSessions());
    }

    // 특정 세션에 속한 메시지 이력을 시간순으로 조회한다.
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<List<ChatMessageSummaryResponse>> getMessageList(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatMessageService.findMessagesBySessionId(sessionId));
    }

    // 단일 세션을 종료 처리해 목록에서 숨긴다.
    @PostMapping("/sessions/{sessionId}/end")
    public ResponseEntity<Void> endSession(@PathVariable String sessionId) {
        chatMessageService.endSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    // 여러 세션을 한 번에 종료 처리한다.
    @PostMapping("/sessions/end")
    public ResponseEntity<Void> endSessions(@RequestBody(required = false) ChatSessionsEndRequest request) {
        chatMessageService.endSessions(request == null ? List.of() : request.sessionIds());
        return ResponseEntity.noContent().build();
    }

    // 잘못된 요청 값에 대해 400 응답을 내려준다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    // AI 서버 호출 실패를 500 응답으로 변환한다.
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> handleRestClientException(RestClientException exception) {
        return ResponseEntity.internalServerError().body(Map.of("message", "AI server error: " + exception.getMessage()));
    }
}
