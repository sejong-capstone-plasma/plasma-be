package com.plasma.be.chat.repository;

import com.plasma.be.chat.entity.Message;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<Message, Long> {

    @EntityGraph(attributePaths = "session")
    List<Message> findBySessionSessionIdOrderByCreatedAtAsc(String sessionId);
}
