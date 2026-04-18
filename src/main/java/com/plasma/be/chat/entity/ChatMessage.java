package com.plasma.be.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message")
// 사용자가 세션 안에서 남긴 메시지 1건이다.
// 하나의 ChatMessage에 대해 여러 번의 검증 결과(MessageValidationSnapshot)가 생길 수 있다.
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // DB가 발급하는 메시지 고유 ID다.
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    // 이 메시지가 어느 채팅 세션에 속하는지 가리킨다.
    private Session session;

    @Enumerated(EnumType.STRING)
    // USER / ASSISTANT / SYSTEM 같은 메시지 작성 주체다.
    @Column(name = "role", length = 20)
    private MessageRole role;

    // 사용자가 실제로 입력한 자연어 문장이다.
    @Column(name = "input_text", nullable = false, columnDefinition = "TEXT")
    private String inputText;

    // 메시지가 생성된 시각이다.
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ChatMessage() {
    }

    // 채팅 세션에 속한 단일 메시지 엔티티를 생성한다.
    public ChatMessage(Session session, MessageRole role, String inputText, LocalDateTime createdAt) {
        this.session = session;
        this.role = role;
        this.inputText = inputText;
        this.createdAt = createdAt;
    }

    public Long getMessageId() {
        return messageId;
    }

    public String getSessionId() {
        return session.getSessionId();
    }

    public Session getSession() {
        return session;
    }

    public MessageRole getRole() {
        return role == null ? MessageRole.USER : role;
    }

    public String getInputText() {
        return inputText;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
