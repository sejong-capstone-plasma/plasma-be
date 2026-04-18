package com.plasma.be.extract.service;

import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.chat.repository.ChatMessageRepository;
import com.plasma.be.extract.client.ExtractClient;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.dto.ParameterFieldResponse;
import com.plasma.be.extract.dto.ParameterInputRequest;
import com.plasma.be.extract.dto.ParameterValidationRequest;
import com.plasma.be.extract.dto.ParameterValidationResponse;
import com.plasma.be.extract.entity.MessageValidationParam;
import com.plasma.be.extract.entity.MessageValidationSnapshot;
import com.plasma.be.extract.repository.MessageValidationSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExtractService {

    private static final List<ParameterDefinition> SUPPORTED_PARAMETERS = List.of(
            new ParameterDefinition("pressure", "Pressure", "mTorr", 0),
            new ParameterDefinition("source_power", "Source Power", "W", 1),
            new ParameterDefinition("bias_power", "Bias Power", "W", 2)
    );

    private static final String SOURCE_AI_EXTRACT = "AI_EXTRACT";
    private static final String SOURCE_USER_CORRECTION = "USER_CORRECTION";
    private static final String VALIDATION_AI_ERROR = "AI_ERROR";
    private static final List<String> EMPTY_TEXT_MARKERS = List.of("无", "none", "null", "n/a", "na");

    private final ExtractClient extractClient;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageValidationSnapshotRepository snapshotRepository;

    public ExtractService(ExtractClient extractClient,
                          ChatMessageRepository chatMessageRepository,
                          MessageValidationSnapshotRepository snapshotRepository) {
        this.extractClient = extractClient;
        this.chatMessageRepository = chatMessageRepository;
        this.snapshotRepository = snapshotRepository;
    }

    // 저장된 사용자 메시지에서 초기 파라미터 추출을 수행하고 검증 스냅샷을 저장한다.
    @Transactional(noRollbackFor = {RestClientException.class, IllegalStateException.class})
    public ParameterValidationResponse extractFromMessage(Long messageId) {
        ChatMessage message = getManagedMessage(messageId);
        int attemptNo = nextAttemptNo(messageId);

        try {
            ExtractedParameterData data = requestToAiServer(message.getInputText());
            if (data == null) {
                MessageValidationSnapshot failure = createFailureSnapshot(
                        message,
                        attemptNo,
                        SOURCE_AI_EXTRACT,
                        "No response received from AI server.",
                        Map.of()
                );
                snapshotRepository.save(failure);
                throw new IllegalStateException("No response received from AI server.");
            }

            MessageValidationSnapshot snapshot = createSnapshot(
                    message,
                    attemptNo,
                    SOURCE_AI_EXTRACT,
                    data,
                    Map.of()
            );
            snapshotRepository.save(snapshot);
            return toResponse(snapshot);
        } catch (RestClientException exception) {
            MessageValidationSnapshot failure = createFailureSnapshot(
                    message,
                    attemptNo,
                    SOURCE_AI_EXTRACT,
                    exception.getMessage(),
                    Map.of()
            );
            snapshotRepository.save(failure);
            throw exception;
        }
    }

    // 사용자가 수정한 파라미터를 다시 AI에 검증받고 새 검증 시도로 저장한다.
    @Transactional(noRollbackFor = {RestClientException.class, IllegalStateException.class})
    public ParameterValidationResponse validateCorrection(Long messageId, ParameterValidationRequest request) {
        ChatMessage message = getManagedMessage(messageId);
        Map<String, SubmittedParam> submittedParams = normalizeSubmittedParams(request);
        int attemptNo = nextAttemptNo(messageId);

        try {
            ExtractedParameterData data = requestToAiServer(buildValidationPrompt(message, submittedParams));
            if (data == null) {
                MessageValidationSnapshot failure = createFailureSnapshot(
                        message,
                        attemptNo,
                        SOURCE_USER_CORRECTION,
                        "No response received from AI server.",
                        submittedParams
                );
                snapshotRepository.save(failure);
                throw new IllegalStateException("No response received from AI server.");
            }

            MessageValidationSnapshot snapshot = createSnapshot(
                    message,
                    attemptNo,
                    SOURCE_USER_CORRECTION,
                    data,
                    submittedParams
            );
            snapshotRepository.save(snapshot);
            return toResponse(snapshot);
        } catch (RestClientException exception) {
            MessageValidationSnapshot failure = createFailureSnapshot(
                    message,
                    attemptNo,
                    SOURCE_USER_CORRECTION,
                    exception.getMessage(),
                    submittedParams
            );
            snapshotRepository.save(failure);
            throw exception;
        }
    }

    // 메시지에 연결된 모든 검증 이력을 조회한다.
    @Transactional(readOnly = true)
    public List<ParameterValidationResponse> findByMessageId(Long messageId) {
        return snapshotRepository.findByMessageMessageIdOrderByAttemptNoAsc(messageId).stream()
                .map(this::toResponse)
                .toList();
    }

    // 여러 메시지의 검증 이력을 한 번에 조회한다.
    @Transactional(readOnly = true)
    public List<ParameterValidationResponse> findByMessageIds(Collection<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return List.of();
        }
        return snapshotRepository.findByMessageMessageIdInOrderByMessageMessageIdAscAttemptNoAsc(messageIds).stream()
                .map(this::toResponse)
                .toList();
    }

    // 검증 결과를 사용자가 확정한 상태로 표시한다.
    @Transactional
    public Optional<ParameterValidationResponse> confirmValidation(Long messageId, Long validationId) {
        Optional<MessageValidationSnapshot> snapshotOptional =
                snapshotRepository.findByValidationIdAndMessageMessageId(validationId, messageId);
        if (snapshotOptional.isEmpty()) {
            return Optional.empty();
        }

        MessageValidationSnapshot snapshot = snapshotOptional.get();
        if (!snapshot.isAllValid()) {
            throw new IllegalArgumentException("Only all-valid validation results can be confirmed.");
        }

        snapshotRepository.findByMessageMessageIdAndConfirmedTrue(messageId).stream()
                .filter(existing -> !existing.getValidationId().equals(validationId))
                .forEach(MessageValidationSnapshot::clearConfirmed);
        snapshot.markConfirmed();
        return Optional.of(toResponse(snapshot));
    }

    // requestId로 단건 조회한다.
    @Transactional(readOnly = true)
    public Optional<ParameterValidationResponse> findByValidationId(Long validationId) {
        return snapshotRepository.findByValidationId(validationId).map(this::toResponse);
    }

    ExtractedParameterData requestToAiServer(String message) {
        return extractClient.requestExtraction(message);
    }

    MessageValidationSnapshot createSnapshot(ChatMessage message,
                                             int attemptNo,
                                             String sourceType,
                                             ExtractedParameterData data,
                                             Map<String, SubmittedParam> submittedParams) {
        String validationStatus = sanitize(data.validationStatus(), "UNKNOWN");
        String requestId = sanitize(data.requestId(), UUID.randomUUID().toString());

        ExtractedParameterData.ValidatedParam currentEr = null;
        if (data.currentOutputs() != null) {
            currentEr = data.currentOutputs().etchRate();
        }

        Map<String, ExtractedParameterData.ValidatedParam> aiParams = extractAiParams(data);
        List<MessageValidationParam> items = new ArrayList<>();
        boolean allParameterStatusesValid = true;

        for (ParameterDefinition definition : SUPPORTED_PARAMETERS) {
            ExtractedParameterData.ValidatedParam aiParam = aiParams.get(definition.key());
            SubmittedParam submitted = submittedParams.get(definition.key());
            String parameterStatus = resolveStatus(aiParam, submitted);
            if (!"VALID".equals(parameterStatus)) {
                allParameterStatusesValid = false;
            }

            items.add(MessageValidationParam.create(
                    definition.key(),
                    definition.label(),
                    resolveValue(aiParam, submitted),
                    resolveUnit(aiParam, submitted, definition.defaultUnit()),
                    parameterStatus,
                    definition.displayOrder()
            ));
        }

        String effectiveValidationStatus = resolveValidationStatus(
                sourceType,
                validationStatus,
                allParameterStatusesValid
        );

        MessageValidationSnapshot snapshot = MessageValidationSnapshot.create(
                message,
                requestId,
                attemptNo,
                sourceType,
                effectiveValidationStatus,
                sanitize(data.processType(), null),
                sanitize(data.taskType(), null),
                currentEr == null ? null : currentEr.value(),
                currentEr == null ? null : sanitize(currentEr.unit(), null),
                currentEr == null ? null : sanitize(currentEr.status(), null),
                null,
                LocalDateTime.now()
        );

        items.forEach(snapshot::addItem);
        return snapshot;
    }

    private MessageValidationSnapshot createFailureSnapshot(ChatMessage message,
                                                            int attemptNo,
                                                            String sourceType,
                                                            String failureReason,
                                                            Map<String, SubmittedParam> submittedParams) {
        MessageValidationSnapshot snapshot = MessageValidationSnapshot.create(
                message,
                UUID.randomUUID().toString(),
                attemptNo,
                sourceType,
                VALIDATION_AI_ERROR,
                null,
                null,
                null,
                null,
                null,
                failureReason,
                LocalDateTime.now()
        );

        for (ParameterDefinition definition : SUPPORTED_PARAMETERS) {
            SubmittedParam submitted = submittedParams.get(definition.key());
            snapshot.addItem(MessageValidationParam.create(
                    definition.key(),
                    definition.label(),
                    submitted == null ? null : submitted.value(),
                    submitted == null ? definition.defaultUnit() : sanitize(submitted.unit(), definition.defaultUnit()),
                    VALIDATION_AI_ERROR,
                    definition.displayOrder()
            ));
        }
        return snapshot;
    }

    ParameterValidationResponse toResponse(MessageValidationSnapshot snapshot) {
        List<ParameterFieldResponse> parameters = snapshot.getItems().stream()
                .map(item -> new ParameterFieldResponse(
                        item.getParameterKey(),
                        item.getParameterLabel(),
                        item.getParameterValue(),
                        item.getParameterUnit(),
                        item.getParameterStatus()
                ))
                .toList();

        ParameterFieldResponse currentEr = null;
        if (snapshot.getCurrentErValue() != null || StringUtils.hasText(snapshot.getCurrentErStatus())) {
            currentEr = new ParameterFieldResponse(
                    "current_er",
                    "Current ER",
                    snapshot.getCurrentErValue(),
                    snapshot.getCurrentErUnit(),
                    snapshot.getCurrentErStatus()
            );
        }

        return new ParameterValidationResponse(
                snapshot.getValidationId(),
                snapshot.getRequestId(),
                snapshot.getMessage().getMessageId(),
                snapshot.getAttemptNo(),
                snapshot.getSourceType(),
                snapshot.getValidationStatus(),
                snapshot.getProcessType(),
                snapshot.getTaskType(),
                parameters,
                currentEr,
                snapshot.isAllValid(),
                snapshot.isConfirmed(),
                snapshot.getFailureReason(),
                snapshot.getCreatedAt()
        );
    }

    private ChatMessage getManagedMessage(Long messageId) {
        return chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("messageId does not exist."));
    }

    private int nextAttemptNo(Long messageId) {
        return snapshotRepository.findTopByMessageMessageIdOrderByAttemptNoDesc(messageId)
                .map(snapshot -> snapshot.getAttemptNo() + 1)
                .orElse(1);
    }

    private Map<String, ExtractedParameterData.ValidatedParam> extractAiParams(ExtractedParameterData data) {
        Map<String, ExtractedParameterData.ValidatedParam> result = new LinkedHashMap<>();
        if (data.processParams() == null) {
            return result;
        }
        result.put("pressure", data.processParams().pressure());
        result.put("source_power", data.processParams().sourcePower());
        result.put("bias_power", data.processParams().biasPower());
        return result;
    }

    private Map<String, SubmittedParam> normalizeSubmittedParams(ParameterValidationRequest request) {
        if (request == null || request.parameters() == null || request.parameters().isEmpty()) {
            throw new IllegalArgumentException("parameters are required.");
        }

        Map<String, SubmittedParam> submitted = new LinkedHashMap<>();
        for (ParameterInputRequest parameter : request.parameters()) {
            if (parameter == null || !StringUtils.hasText(parameter.key())) {
                throw new IllegalArgumentException("parameter key is required.");
            }
            if (parameter.value() == null) {
                throw new IllegalArgumentException("parameter value is required.");
            }
            String key = parameter.key().trim();
            if (submitted.containsKey(key)) {
                throw new IllegalArgumentException("duplicate parameter key: " + key);
            }
            submitted.put(key, new SubmittedParam(key, parameter.value(), parameter.unit()));
        }

        List<String> missingKeys = SUPPORTED_PARAMETERS.stream()
                .map(ParameterDefinition::key)
                .filter(key -> !submitted.containsKey(key))
                .toList();
        if (!missingKeys.isEmpty()) {
            throw new IllegalArgumentException("missing parameters: " + String.join(", ", missingKeys));
        }
        return submitted;
    }

    private String buildValidationPrompt(ChatMessage message, Map<String, SubmittedParam> submittedParams) {
        String parameterText = SUPPORTED_PARAMETERS.stream()
                .map(definition -> {
                    SubmittedParam submitted = submittedParams.get(definition.key());
                    return definition.key() + "=" + submitted.value() + " " + sanitize(submitted.unit(), definition.defaultUnit());
                })
                .collect(Collectors.joining(", "));

        return message.getInputText() + System.lineSeparator()
                + "사용자가 수정한 공정 조건은 다음과 같습니다: " + parameterText + ". "
                + "이 값들을 기준으로 pressure, source_power, bias_power를 다시 검증해줘.";
    }

    private Double resolveValue(ExtractedParameterData.ValidatedParam aiParam, SubmittedParam submitted) {
        if (aiParam != null && aiParam.value() != null) {
            return aiParam.value();
        }
        return submitted == null ? null : submitted.value();
    }

    private String resolveUnit(ExtractedParameterData.ValidatedParam aiParam,
                               SubmittedParam submitted,
                               String defaultUnit) {
        if (aiParam != null) {
            String normalizedAiUnit = sanitize(aiParam.unit(), null);
            if (normalizedAiUnit != null) {
                return normalizedAiUnit;
            }
        }
        if (submitted != null) {
            String normalizedSubmittedUnit = sanitize(submitted.unit(), null);
            if (normalizedSubmittedUnit != null) {
                return normalizedSubmittedUnit;
            }
        }
        return defaultUnit;
    }

    private String resolveStatus(ExtractedParameterData.ValidatedParam aiParam, SubmittedParam submitted) {
        if (aiParam != null) {
            String normalizedAiStatus = sanitize(aiParam.status(), null);
            if (normalizedAiStatus != null) {
                return normalizedAiStatus;
            }
        }
        if (submitted != null && submitted.value() != null) {
            return "UNCONFIRMED";
        }
        return "MISSING";
    }

    private String resolveValidationStatus(String sourceType,
                                           String validationStatus,
                                           boolean allParameterStatusesValid) {
        if (SOURCE_USER_CORRECTION.equals(sourceType) && allParameterStatusesValid) {
            // 원문이 의미 없어서 AI가 UNSUPPORTED를 반환해도,
            // 사용자가 모든 공정조건을 채워 넣고 각 파라미터가 VALID면 수정 결과는 성공으로 본다.
            return "VALID";
        }
        return validationStatus;
    }

    private String sanitize(String value, String defaultValue) {
        String normalized = normalizeText(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmed = value.replaceAll("[\\u200B-\\u200D\\uFEFF]", "").trim();
        if (EMPTY_TEXT_MARKERS.contains(trimmed.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return trimmed;
    }

    private record ParameterDefinition(String key, String label, String defaultUnit, int displayOrder) {
    }

    private record SubmittedParam(String key, Double value, String unit) {
    }
}
