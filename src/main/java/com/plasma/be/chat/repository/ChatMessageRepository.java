package com.plasma.be.chat.repository;

import com.plasma.be.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 채팅 메시지 조회와 저장을 담당하는 JPA 레포지토리
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 세션 정보를 함께 읽어 메시지 목록을 생성 시각순으로 조회한다.
    @EntityGraph(attributePaths = "session")
    List<ChatMessage> findBySessionSessionIdAndSessionOwnerSessionKeyOrderByCreatedAtAsc(String sessionId,
                                                                                         String ownerSessionKey);
}
