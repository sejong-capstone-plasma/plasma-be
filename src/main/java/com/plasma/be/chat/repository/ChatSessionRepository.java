package com.plasma.be.chat.repository;

import com.plasma.be.chat.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 채팅 세션 저장과 사용자 노출 세션 조회를 담당하는 JPA 레포지토리
public interface ChatSessionRepository extends JpaRepository<Session, String> {

    // 사용자에게 보여줄 세션만 최근 활동순으로 가져온다.
    List<Session> findAllByOwnerSessionKeyAndVisibleToUserTrueOrderByLastMessageAtDesc(String ownerSessionKey);

    Optional<Session> findBySessionIdAndOwnerSessionKey(String sessionId, String ownerSessionKey);
}
