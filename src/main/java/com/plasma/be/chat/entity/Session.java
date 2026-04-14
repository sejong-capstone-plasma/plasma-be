package com.plasma.be.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_session")
public class Session {

    @Id
    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    protected Session() {
    }

    private Session(String sessionId, String title, LocalDateTime createdAt) {
        this.sessionId = sessionId;
        this.title = title;
        this.createdAt = createdAt;
        this.lastMessageAt = createdAt;
        this.messageCount = 0;
    }

    public static Session create(String sessionId, String title, LocalDateTime createdAt) {
        return new Session(sessionId, title, createdAt);
    }

    public void registerMessage(LocalDateTime createdAt) {
        this.lastMessageAt = createdAt;
        this.messageCount += 1;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public int getMessageCount() {
        return messageCount;
    }
}
