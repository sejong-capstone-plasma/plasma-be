package com.plasma.be.chat.controller;

import com.plasma.be.chat.dto.ChatMessageCreateRequest;
import com.plasma.be.chat.dto.ChatMessageSummaryResponse;
import com.plasma.be.chat.dto.ChatSessionSummaryResponse;
import com.plasma.be.chat.dto.ChatSessionsEndRequest;
import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.chat.exception.SessionAccessDeniedException;
import com.plasma.be.chat.service.ChatMessageService;
import com.plasma.be.extract.dto.ParametersResponse;
import com.plasma.be.extract.service.ExtractService;
import jakarta.servlet.http.HttpSession;
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

    // 채팅 메시지를 저장하고 AI 추출 결과를 함께 반환한다.
    @Override
    public ResponseEntity<ParametersResponse> createMessage(ChatMessageCreateRequest request, HttpSession browserSession) {
        ChatMessage savedMessage = chatMessageService.saveMessage(request, browserSession.getId());
        ParametersResponse response = extractService.extractAndSave(savedMessage);
        return ResponseEntity.ok(response);
    }

    // 사용자에게 보여지는 채팅 세션 목록을 조회한다.
    @Override
    public ResponseEntity<List<ChatSessionSummaryResponse>> getSessionList(HttpSession browserSession) {
        return ResponseEntity.ok(chatMessageService.findSessions(browserSession.getId()));
    }

    // 특정 세션에 속한 메시지 이력을 시간순으로 조회한다.
    @Override
    public ResponseEntity<List<ChatMessageSummaryResponse>> getMessageList(String sessionId, HttpSession browserSession) {
        return ResponseEntity.ok(chatMessageService.findMessagesBySessionId(sessionId, browserSession.getId()));
    }

    // 단일 세션을 종료 처리해 목록에서 숨긴다.
    @Override
    public ResponseEntity<Void> endSession(String sessionId, HttpSession browserSession) {
        chatMessageService.endSession(sessionId, browserSession.getId());
        return ResponseEntity.noContent().build();
    }

    // 여러 세션을 한 번에 종료 처리한다.
    @Override
    public ResponseEntity<Void> endSessions(ChatSessionsEndRequest request, HttpSession browserSession) {
        chatMessageService.endSessions(request == null ? List.of() : request.sessionIds(), browserSession.getId());
        return ResponseEntity.noContent().build();
    }

    // 잘못된 요청 값에 대해 400 응답을 내려준다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    // 현재 브라우저에 속하지 않는 세션 접근은 404로 감춘다.
    @ExceptionHandler(SessionAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleSessionAccessDeniedException(SessionAccessDeniedException exception) {
        return ResponseEntity.status(404).body(Map.of("message", exception.getMessage()));
    }

    // AI 서버 호출 실패를 500 응답으로 변환한다.
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> handleRestClientException(RestClientException exception) {
        return ResponseEntity.internalServerError().body(Map.of("message", "AI server error: " + exception.getMessage()));
    }
}
