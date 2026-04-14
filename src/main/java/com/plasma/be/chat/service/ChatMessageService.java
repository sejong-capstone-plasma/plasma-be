package com.plasma.be.chat.service;

import com.plasma.be.chat.dto.ChatMessageCreateRequest;
import com.plasma.be.chat.dto.ChatMessageCreateResponse;
import com.plasma.be.chat.dto.ChatMessageSummaryResponse;
import com.plasma.be.chat.dto.ChatSessionSummaryResponse;
import com.plasma.be.chat.entity.Message;
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

    @Transactional
    public ChatMessageCreateResponse create(ChatMessageCreateRequest request) {
        validate(request);

        String sessionId = request.sessionId().trim();
        String content = normalizeContent(request);
        MessageRole role = resolveRole(request.role());
        LocalDateTime now = LocalDateTime.now();

        Session session = chatSessionRepository.findById(sessionId)
                .orElseGet(() -> Session.create(sessionId, truncate(content), now));
        session.registerMessage(now);
        Session savedSession = chatSessionRepository.save(session);

        Message saved = chatMessageRepository.save(new Message(savedSession, role, content, now));

        return new ChatMessageCreateResponse(
                saved.getId(),
                saved.getSessionId(),
                saved.getRole().name(),
                saved.getContent(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ChatSessionSummaryResponse> getSessions() {
        return chatSessionRepository.findAllByVisibleToUserTrueOrderByLastMessageAtDesc().stream()
                .map(session -> new ChatSessionSummaryResponse(
                        session.getSessionId(),
                        session.getTitle(),
                        session.getLastMessageAt(),
                        session.getMessageCount()
                ))
                .toList();
    }

    @Transactional
    public void endSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId is required.");
        }

        chatSessionRepository.findById(sessionId.trim())
                .ifPresent(session -> session.end(LocalDateTime.now()));
    }

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

    @Transactional(readOnly = true)
    public List<ChatMessageSummaryResponse> getMessages(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId is required.");
        }

        return chatMessageRepository.findBySessionSessionIdOrderByCreatedAtAsc(sessionId.trim()).stream()
                .map(message -> new ChatMessageSummaryResponse(
                        message.getId(),
                        message.getSessionId(),
                        message.getRole().name(),
                        message.getContent(),
                        message.getCreatedAt()
                ))
                .toList();
    }

    private void validate(ChatMessageCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (!StringUtils.hasText(request.sessionId())) {
            throw new IllegalArgumentException("sessionId is required.");
        }
        if (!StringUtils.hasText(request.content()) && !StringUtils.hasText(request.inputText())) {
            throw new IllegalArgumentException("content is required.");
        }
    }

    private String normalizeContent(ChatMessageCreateRequest request) {
        if (StringUtils.hasText(request.content())) {
            return request.content().trim();
        }
        return request.inputText().trim();
    }

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

    private String truncate(String text) {
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 36) {
            return normalized;
        }
        return normalized.substring(0, 36) + "...";
    }
}
