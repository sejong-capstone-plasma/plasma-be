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
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private MessageRole role;

    @Column(name = "input_text", nullable = false, columnDefinition = "TEXT")
    private String inputText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ChatMessage() {
    }

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
