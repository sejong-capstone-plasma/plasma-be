package com.plasma.be.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_session")
// 브라우저 사용자 기준의 채팅방 1개를 나타낸다.
// 하나의 Session 안에 여러 ChatMessage가 쌓인다.
public class Session {

    @Id
    // 프론트가 들고 있는 채팅방 식별자다.
    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    // 같은 sessionId라도 다른 브라우저에서 접근하지 못하도록 소유권을 구분한다.
    @Column(name = "owner_session_key", nullable = false, length = 120)
    private String ownerSessionKey;

    // 세션 목록에 보여줄 짧은 제목이다.
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 마지막 메시지 시각을 저장해 최근 대화 순으로 세션 목록을 정렬한다.
    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    // 이 세션에 속한 메시지 개수다.
    @Column(name = "message_count", nullable = false)
    private int messageCount;

    // 종료된 세션은 false로 바꿔 사용자 목록에서 숨긴다.
    @Column(name = "visible_to_user", nullable = false)
    private boolean visibleToUser;

    // 세션을 종료한 시각이다. 아직 열려 있으면 null이다.
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    protected Session() {
    }

    private Session(String sessionId, String ownerSessionKey, String title, LocalDateTime createdAt) {
        this.sessionId = sessionId;
        this.ownerSessionKey = ownerSessionKey;
        this.title = title;
        this.createdAt = createdAt;
        this.lastMessageAt = createdAt;
        this.messageCount = 0;
        this.visibleToUser = true;
        this.endedAt = null;
    }

    // 새 채팅 세션을 생성한다.
    public static Session create(String sessionId, String ownerSessionKey, String title, LocalDateTime createdAt) {
        return new Session(sessionId, ownerSessionKey, title, createdAt);
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

    public String getOwnerSessionKey() {
        return ownerSessionKey;
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
