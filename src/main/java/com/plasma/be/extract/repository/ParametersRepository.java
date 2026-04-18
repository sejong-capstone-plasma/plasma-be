package com.plasma.be.extract.repository;

import com.plasma.be.extract.entity.Parameters;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 추출된 공정 파라미터 저장과 조회를 담당하는 JPA 레포지토리
public interface ParametersRepository extends JpaRepository<Parameters, String> {

    // 특정 채팅 메시지에 연결된 파라미터 목록을 조회한다.
    List<Parameters> findByChatMessageMessageId(Long messageId);
}
