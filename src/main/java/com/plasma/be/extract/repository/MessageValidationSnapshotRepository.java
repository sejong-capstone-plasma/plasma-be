package com.plasma.be.extract.repository;

import com.plasma.be.extract.entity.MessageValidationSnapshot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageValidationSnapshotRepository extends JpaRepository<MessageValidationSnapshot, Long> {

    @EntityGraph(attributePaths = {"message", "items"})
    Optional<MessageValidationSnapshot> findByValidationId(Long validationId);

    @EntityGraph(attributePaths = {"items"})
    Optional<MessageValidationSnapshot> findByValidationIdAndMessageMessageId(Long validationId, Long messageId);

    @EntityGraph(attributePaths = {"items"})
    List<MessageValidationSnapshot> findByMessageMessageIdOrderByAttemptNoAsc(Long messageId);

    @EntityGraph(attributePaths = {"items"})
    List<MessageValidationSnapshot> findByMessageMessageIdInOrderByMessageMessageIdAscAttemptNoAsc(Collection<Long> messageIds);

    Optional<MessageValidationSnapshot> findTopByMessageMessageIdOrderByAttemptNoDesc(Long messageId);

    List<MessageValidationSnapshot> findByMessageMessageIdAndConfirmedTrue(Long messageId);

    @EntityGraph(attributePaths = {"message", "items"})
    Optional<MessageValidationSnapshot> findTopByMessageSessionSessionIdAndConfirmedTrueAndMessageMessageIdNotOrderByCreatedAtDesc(
            String sessionId,
            Long messageId
    );
}
