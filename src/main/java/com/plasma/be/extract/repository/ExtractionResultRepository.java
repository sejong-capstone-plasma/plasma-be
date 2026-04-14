package com.plasma.be.extract.repository;

import com.plasma.be.extract.entity.ExtractionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtractionResultRepository extends JpaRepository<ExtractionResult, Long> {

    List<ExtractionResult> findByChatMessageMessageId(Long messageId);
}
