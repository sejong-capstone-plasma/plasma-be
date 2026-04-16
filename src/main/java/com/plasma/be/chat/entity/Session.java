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

    @Column(name = "visible_to_user", nullable = false)
    private boolean visibleToUser;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    protected Session() {
    }

    private Session(String sessionId, String title, LocalDateTime createdAt) {
        this.sessionId = sessionId;
        this.title = title;
        this.createdAt = createdAt;
        this.lastMessageAt = createdAt;
        this.messageCount = 0;
        this.visibleToUser = true;
        this.endedAt = null;
    }

    // 새 채팅 세션을 생성한다.
    public static Session create(String sessionId, String title, LocalDateTime createdAt) {
        return new Session(sessionId, title, createdAt);
    }

    // 새 메시지 도착 시 세션의 최근 활동 정보와 노출 상태를 갱신한다.
    public void registerMessage(LocalDateTime createdAt) {
        this.lastMessageAt = createdAt;
        this.messageCount += 1;
        this.visibleToUser = true;
        this.endedAt = null;
    }

    // 세션을 종료 상태로 바꾸고 사용자 목록에서 숨긴다.
    public void end(LocalDateTime endedAt) {
        this.visibleToUser = false;
        this.endedAt = endedAt;
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

    public boolean isVisibleToUser() {
        return visibleToUser;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }
}
