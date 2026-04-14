package com.plasma.be.chat.repository;

import com.plasma.be.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @EntityGraph(attributePaths = "session")
    List<ChatMessage> findBySessionSessionIdOrderByCreatedAtAsc(String sessionId);
}
