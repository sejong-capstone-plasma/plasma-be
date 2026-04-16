package com.plasma.be.chat.service;

import com.plasma.be.chat.dto.ChatMessageCreateRequest;
import com.plasma.be.chat.dto.ChatMessageSummaryResponse;
import com.plasma.be.chat.dto.ChatSessionSummaryResponse;
import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.chat.entity.MessageRole;
import com.plasma.be.chat.entity.Session;
import com.plasma.be.chat.repository.ChatMessageRepository;
import com.plasma.be.chat.repository.ChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;

    public ChatMessageService(ChatMessageRepository chatMessageRepository,
                              ChatSessionRepository chatSessionRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatSessionRepository = chatSessionRepository;
    }

    // 요청을 검증한 뒤 세션을 갱신하고 메시지를 저장한다.
    @Transactional
    public ChatMessage saveMessage(ChatMessageCreateRequest request) {
        validateRequest(request);

        String sessionId = request.sessionId().trim();
        String inputText = normalizeInputText(request);
        MessageRole role = resolveRole(request.role());
        LocalDateTime now = LocalDateTime.now();

        Session session = chatSessionRepository.findById(sessionId)
                .orElseGet(() -> Session.create(sessionId, truncate(inputText), now));
        session.registerMessage(now);
        chatSessionRepository.save(session);

        return chatMessageRepository.save(new ChatMessage(session, role, inputText, now));
    }

    // 사용자에게 보이는 세션만 최근 메시지 순으로 조회한다.
    @Transactional(readOnly = true)
    public List<ChatSessionSummaryResponse> findSessions() {
        return chatSessionRepository.findAllByVisibleToUserTrueOrderByLastMessageAtDesc().stream()
                .map(session -> new ChatSessionSummaryResponse(
                        session.getSessionId(),
                        session.getTitle(),
                        session.getLastMessageAt(),
                        session.getMessageCount()
                ))
                .toList();
    }

    // 세션 ID로 메시지 목록을 조회해 응답 DTO로 변환한다.
    @Transactional(readOnly = true)
    public List<ChatMessageSummaryResponse> findMessagesBySessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId is required.");
        }

        return chatMessageRepository.findBySessionSessionIdOrderByCreatedAtAsc(sessionId.trim()).stream()
                .map(message -> new ChatMessageSummaryResponse(
                        message.getMessageId(),
                        message.getSessionId(),
                        message.getRole().name(),
                        message.getInputText(),
                        message.getCreatedAt()
                ))
                .toList();
    }

    // 단일 세션을 종료 처리해 더 이상 목록에 노출되지 않게 한다.
    @Transactional
    public void endSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId is required.");
        }
        chatSessionRepository.findById(sessionId.trim())
                .ifPresent(session -> session.end(LocalDateTime.now()));
    }

    // 전달받은 여러 세션 ID를 중복 제거 후 일괄 종료한다.
    @Transactional
    public void endSessions(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        sessionIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .forEach(sessionId -> chatSessionRepository.findById(sessionId)
                        .ifPresent(session -> session.end(now)));
    }

    // 메시지 저장 전 필수 입력값을 검증한다.
    public boolean validateRequest(ChatMessageCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (!StringUtils.hasText(request.sessionId())) {
            throw new IllegalArgumentException("sessionId is required.");
        }
        if (!StringUtils.hasText(request.inputText())) {
            throw new IllegalArgumentException("inputText is required.");
        }
        return true;
    }

    // 사용자 입력의 앞뒤 공백을 정리한다.
    private String normalizeInputText(ChatMessageCreateRequest request) {
        return request.inputText().trim();
    }

    // 문자열 role 값을 enum으로 안전하게 변환한다.
    private MessageRole resolveRole(String role) {
        if (!StringUtils.hasText(role)) {
            return MessageRole.USER;
        }
        try {
            return MessageRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("role must be one of USER, ASSISTANT, SYSTEM.");
        }
    }

    // 세션 제목으로 사용할 수 있도록 긴 입력을 짧게 요약한다.
    private String truncate(String text) {
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 36) {
            return normalized;
        }
        return normalized.substring(0, 36) + "...";
    }
}
