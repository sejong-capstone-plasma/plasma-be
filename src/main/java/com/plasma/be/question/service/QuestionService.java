package com.plasma.be.question.service;

import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.chat.entity.MessageRole;
import com.plasma.be.chat.repository.ChatMessageRepository;
import com.plasma.be.extract.entity.MessageValidationSnapshot;
import com.plasma.be.extract.repository.MessageValidationSnapshotRepository;
import com.plasma.be.question.client.QuestionClient;
import com.plasma.be.question.client.dto.QuestionAnswerResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class QuestionService {

    private final QuestionClient questionClient;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageValidationSnapshotRepository snapshotRepository;

    public QuestionService(QuestionClient questionClient,
                           ChatMessageRepository chatMessageRepository,
                           MessageValidationSnapshotRepository snapshotRepository) {
        this.questionClient = questionClient;
        this.chatMessageRepository = chatMessageRepository;
        this.snapshotRepository = snapshotRepository;
    }

    public QuestionAnswerResponse answer(ChatMessage message) {
        String question = message.getInputText();
        QuestionAnswerResponse localAnswer = answerSystemQuestion(question);
        if (localAnswer != null) {
            return localAnswer;
        }
        return questionClient.requestAnswer(question, buildHistory(message));
    }

    private QuestionAnswerResponse answerSystemQuestion(String question) {
        String normalized = normalize(question);

        if (normalized.contains("압력") && normalized.contains("범위")) {
            return systemAnswer(
                    "현재 백엔드 기준 입력 파라미터는 pressure(mTorr), source_power(W), bias_power(W) 3개입니다. "
                            + "pressure 값의 최소/최대 범위는 현재 백엔드에서 별도로 강제하지 않습니다."
            );
        }

        if (normalized.contains("지원") && normalized.contains("공정")) {
            return systemAnswer("현재 백엔드에서 기본적으로 지원하는 process_type은 ETCH입니다.");
        }

        if ((normalized.contains("지원") || normalized.contains("입력")) && normalized.contains("파라미터")) {
            return systemAnswer("현재 입력 가능한 파라미터는 pressure(mTorr), source_power(W), bias_power(W)입니다.");
        }

        return null;
    }

    private QuestionAnswerResponse systemAnswer(String answerText) {
        return new QuestionAnswerResponse(
                UUID.randomUUID().toString(),
                answerText,
                "SYSTEM",
                List.of()
        );
    }

    private List<Map<String, String>> buildHistory(ChatMessage currentMessage) {
        String sessionId = currentMessage.getSession().getSessionId();
        Long currentMessageId = currentMessage.getMessageId();

        List<ChatMessage> priorMessages = chatMessageRepository
                .findBySessionSessionIdAndMessageIdLessThanOrderByCreatedAtAsc(sessionId, currentMessageId);

        List<Map<String, String>> history = new ArrayList<>();
        for (ChatMessage prior : priorMessages) {
            if (prior.getRole() != MessageRole.USER) {
                continue;
            }
            history.add(Map.of("role", "user", "content", prior.getInputText()));

            snapshotRepository.findByMessageMessageIdAndConfirmedTrue(prior.getMessageId()).stream()
                    .findFirst()
                    .map(MessageValidationSnapshot::getPredictionExplanationSummary)
                    .filter(StringUtils::hasText)
                    .ifPresent(summary -> history.add(Map.of("role", "assistant", "content", summary)));
        }
        return history;
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
