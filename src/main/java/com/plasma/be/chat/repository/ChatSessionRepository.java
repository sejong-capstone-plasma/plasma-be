package com.plasma.be.chat.repository;

import com.plasma.be.chat.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<Session, String> {

    List<Session> findAllByVisibleToUserTrueOrderByLastMessageAtDesc();
}
