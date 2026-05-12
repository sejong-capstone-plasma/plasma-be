package com.plasma.be.extract.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.chat.entity.MessageRole;
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
import com.plasma.be.predict.client.dto.PredictPipelineResponse;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExtractService {

    private static final List<ParameterDefinition> SUPPORTED_PARAMETERS = List.of(
            new ParameterDefinition("pressure", "Pressure", "mTorr", 0, 2.0, 10.0),
            new ParameterDefinition("source_power", "Source Power", "W", 1, 100.0, 500.0),
            new ParameterDefinition("bias_power", "Bias Power", "W", 2, 0.0, 1000.0)
    );

    private static final String SOURCE_AI_EXTRACT = "AI_EXTRACT";
    private static final String SOURCE_USER_CORRECTION = "USER_CORRECTION";
    private static final String VALIDATION_AI_ERROR = "AI_ERROR";
    private static final String VALIDATION_OUT_OF_RANGE = "OUT_OF_RANGE";
    private static final String DEFAULT_PROCESS_TYPE = "UNKNOWN";
    private static final String DEFAULT_TASK_TYPE = "UNSUPPORTED";
    private static final List<String> EMPTY_TEXT_MARKERS = List.of("无", "none", "null", "n/a", "na");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ExtractClient extractClient;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageValidationSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            List<Map<String, String>> history = buildHistory(message);
            ExtractedParameterData data = requestToAiServer(message.getInputText(), history);
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
                    Map.of(),
                    null
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
        List<MessageValidationSnapshot> snapshots = snapshotRepository.findByMessageMessageIdOrderByAttemptNoAsc(messageId);
        int attemptNo = snapshots.isEmpty() ? 1 : snapshots.get(snapshots.size() - 1).getAttemptNo() + 1;
        String processType = findLatestNonEmptyValue(snapshots, MessageValidationSnapshot::getProcessType);
        String taskType = findLatestNonEmptyValue(snapshots, MessageValidationSnapshot::getTaskType);
        String requestProcessType = processType != null ? processType : DEFAULT_PROCESS_TYPE;
        String requestTaskType = taskType != null ? taskType : DEFAULT_TASK_TYPE;

        if ("COMPARISON".equals(requestTaskType)) {
            return validateComparisonCorrection(message, attemptNo, requestProcessType, requestTaskType, snapshots, request);
        }

        Map<String, SubmittedParam> submittedParams = normalizeSubmittedParams(request);
        if (hasOutOfRangeSubmittedParameters(submittedParams)) {
            ExtractedParameterData data = buildOutOfRangeSubmittedData(requestProcessType, requestTaskType, submittedParams);
            MessageValidationSnapshot snapshot = createSnapshot(
                    message,
                    attemptNo,
                    SOURCE_USER_CORRECTION,
                    data,
                    submittedParams,
                    taskType
            );
            snapshotRepository.save(snapshot);
            return toResponse(snapshot);
        }

        Map<String, Double> paramValues = submittedParams.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().value()));
        Map<String, String> paramUnits  = submittedParams.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> sanitize(e.getValue().unit(), "")));

        try {
            ExtractedParameterData data = extractClient.requestValidation(
                    requestProcessType, requestTaskType, paramValues, paramUnits);
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
                    submittedParams,
                    taskType
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
        if (!canBeConfirmed(snapshot)) {
            throw new IllegalArgumentException("Only all-valid validation results can be confirmed.");
        }

        snapshotRepository.findByMessageMessageIdAndConfirmedTrue(messageId).stream()
                .filter(existing -> !existing.getValidationId().equals(validationId))
                .forEach(MessageValidationSnapshot::clearConfirmed);
        snapshot.markConfirmed();
        return Optional.of(toResponse(snapshot));
    }

    private boolean canBeConfirmed(MessageValidationSnapshot snapshot) {
        if (snapshot.isAllValid()) {
            return true;
        }
        if ("COMPARISON".equals(snapshot.getTaskType())) {
            return areComparisonConditionsValid(snapshot)
                    && !VALIDATION_AI_ERROR.equals(snapshot.getValidationStatus());
        }
        return ("QUESTION".equals(snapshot.getTaskType())
                || "UNSUPPORTED".equals(snapshot.getTaskType()))
                && !VALIDATION_AI_ERROR.equals(snapshot.getValidationStatus());
    }

    // requestId로 단건 조회한다.
    @Transactional(readOnly = true)
    public Optional<ParameterValidationResponse> findByValidationId(Long validationId) {
        return snapshotRepository.findByValidationId(validationId).map(this::toResponse);
    }

    @Transactional
    public ParameterValidationResponse storePredictionOutcome(Long messageId,
                                                              Long validationId,
                                                              PredictPipelineResponse prediction,
                                                              String predictionError) {
        MessageValidationSnapshot snapshot = snapshotRepository.findByValidationIdAndMessageMessageId(validationId, messageId)
                .orElseThrow(() -> new IllegalArgumentException("validationId is not associated with the message."));

        snapshot.storePrediction(
                prediction == null ? null : prediction.requestId(),
                prediction == null ? null : prediction.processType(),
                predictionValue(prediction, PredictPipelineResponse.PredictionResult::ionFlux),
                predictionUnit(prediction, PredictPipelineResponse.PredictionResult::ionFlux),
                predictionValue(prediction, PredictPipelineResponse.PredictionResult::ionEnergy),
                predictionUnit(prediction, PredictPipelineResponse.PredictionResult::ionEnergy),
                predictionValue(prediction, PredictPipelineResponse.PredictionResult::etchScore),
                predictionUnit(prediction, PredictPipelineResponse.PredictionResult::etchScore),
                prediction == null || prediction.explanation() == null ? null : sanitize(prediction.explanation().summary(), null),
                prediction == null || prediction.explanation() == null ? null : writeExplanationDetails(prediction.explanation().details()),
                sanitize(predictionError, null)
        );
        return toResponse(snapshot);
    }

    ExtractedParameterData requestToAiServer(String message, List<Map<String, String>> history) {
        return extractClient.requestExtraction(message, history);
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

    MessageValidationSnapshot createSnapshot(ChatMessage message,
                                             int attemptNo,
                                             String sourceType,
                                             ExtractedParameterData data,
                                             Map<String, SubmittedParam> submittedParams,
                                             String previousTaskType) {
        String validationStatus = sanitize(data.validationStatus(), "UNKNOWN");
        String requestId = sanitize(data.requestId(), UUID.randomUUID().toString());
        String requestedTaskType = sanitize(data.taskType(), null);
        boolean comparisonTask = "COMPARISON".equals(requestedTaskType);

        ExtractedParameterData.ValueWithUnit currentEr = null;
        if (data.currentOutputs() != null) {
            currentEr = data.currentOutputs().etchRate();
        }

        Map<String, ExtractedParameterData.ValidatedParam> aiParams = extractAiParams(data);
        List<MessageValidationParam> items = new ArrayList<>();
        boolean allParameterStatusesValid = true;

        if (comparisonTask && (data.conditionA() != null || data.conditionB() != null)) {
            items.addAll(buildComparisonPromptItems(data.conditionA(), data.conditionB()));
        } else {
            for (ParameterDefinition definition : SUPPORTED_PARAMETERS) {
                ExtractedParameterData.ValidatedParam aiParam = aiParams.get(definition.key());
                SubmittedParam submitted = submittedParams.get(definition.key());
                String parameterStatus = resolveStatus(definition, aiParam, submitted);
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
        }

        boolean comparisonConditionsValid = comparisonTask && areComparisonConditionsValid(data.conditionA(), data.conditionB());
        if (comparisonTask) {
            allParameterStatusesValid = comparisonConditionsValid;
        }

        String effectiveValidationStatus = resolveValidationStatus(
                sourceType,
                validationStatus,
                allParameterStatusesValid,
                comparisonConditionsValid
        );
        String effectiveTaskType = resolveTaskType(
                sourceType,
                requestedTaskType,
                sanitize(previousTaskType, null),
                allParameterStatusesValid
        );

        MessageValidationSnapshot snapshot = MessageValidationSnapshot.create(
                message,
                requestId,
                attemptNo,
                sourceType,
                effectiveValidationStatus,
                sanitize(data.processType(), null),
                effectiveTaskType,
                currentEr == null ? null : currentEr.value(),
                currentEr == null ? null : sanitize(currentEr.unit(), null),
                null,
                null,
                LocalDateTime.now()
        );
        snapshot.storeComparisonConditions(
                writeProcessParams(data.conditionA()),
                writeProcessParams(data.conditionB())
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
        if (snapshot.getCurrentErValue() != null) {
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
                buildComparisonConditionResponse("condition_a", snapshot.getConditionAJson()),
                buildComparisonConditionResponse("condition_b", snapshot.getConditionBJson()),
                currentEr,
                snapshot.isAllValid() || areComparisonConditionsValid(snapshot),
                snapshot.isConfirmed(),
                buildPrediction(snapshot),
                snapshot.getPredictionError(),
                snapshot.getFailureReason(),
                snapshot.getCreatedAt()
        );
    }

    private PredictPipelineResponse buildPrediction(MessageValidationSnapshot snapshot) {
        if (!StringUtils.hasText(snapshot.getPredictionRequestId())
                && !StringUtils.hasText(snapshot.getPredictionProcessType())
                && snapshot.getIonFluxValue() == null
                && snapshot.getIonEnergyValue() == null
                && snapshot.getEtchScoreValue() == null
                && !StringUtils.hasText(snapshot.getPredictionExplanationSummary())
                && !StringUtils.hasText(snapshot.getPredictionExplanationDetailsJson())) {
            return null;
        }

        PredictPipelineResponse.PredictionResult predictionResult = new PredictPipelineResponse.PredictionResult(
                valueWithUnit(snapshot.getIonFluxValue(), snapshot.getIonFluxUnit()),
                valueWithUnit(snapshot.getIonEnergyValue(), snapshot.getIonEnergyUnit()),
                valueWithUnit(snapshot.getEtchScoreValue(), snapshot.getEtchScoreUnit())
        );

        PredictPipelineResponse.Explanation explanation = null;
        if (StringUtils.hasText(snapshot.getPredictionExplanationSummary())
                || StringUtils.hasText(snapshot.getPredictionExplanationDetailsJson())) {
            explanation = new PredictPipelineResponse.Explanation(
                    snapshot.getPredictionExplanationSummary(),
                    readExplanationDetails(snapshot.getPredictionExplanationDetailsJson())
            );
        }

        return new PredictPipelineResponse(
                snapshot.getPredictionRequestId(),
                snapshot.getPredictionProcessType(),
                predictionResult,
                explanation,
                null
        );
    }

    private PredictPipelineResponse.ValueWithUnit valueWithUnit(Double value, String unit) {
        if (value == null && !StringUtils.hasText(unit)) {
            return null;
        }
        return new PredictPipelineResponse.ValueWithUnit(value, unit);
    }

    private Double predictionValue(PredictPipelineResponse prediction,
                                   java.util.function.Function<PredictPipelineResponse.PredictionResult, PredictPipelineResponse.ValueWithUnit> selector) {
        if (prediction == null || prediction.predictionResult() == null) {
            return null;
        }
        PredictPipelineResponse.ValueWithUnit valueWithUnit = selector.apply(prediction.predictionResult());
        return valueWithUnit == null ? null : valueWithUnit.value();
    }

    private String predictionUnit(PredictPipelineResponse prediction,
                                  java.util.function.Function<PredictPipelineResponse.PredictionResult, PredictPipelineResponse.ValueWithUnit> selector) {
        if (prediction == null || prediction.predictionResult() == null) {
            return null;
        }
        PredictPipelineResponse.ValueWithUnit valueWithUnit = selector.apply(prediction.predictionResult());
        return valueWithUnit == null ? null : sanitize(valueWithUnit.unit(), null);
    }

    private String writeExplanationDetails(List<String> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? List.of() : details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize prediction explanation details.", exception);
        }
    }

    private String writeProcessParams(ExtractedParameterData.ProcessParams processParams) {
        if (processParams == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(processParams);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize comparison conditions.", exception);
        }
    }

    private List<String> readExplanationDetails(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(detailsJson, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize prediction explanation details.", exception);
        }
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

    private String findLatestNonEmptyValue(List<MessageValidationSnapshot> snapshots,
                                           Function<MessageValidationSnapshot, String> selector) {
        for (int index = snapshots.size() - 1; index >= 0; index--) {
            String value = sanitize(selector.apply(snapshots.get(index)), null);
            if (value != null) {
                return value;
            }
        }
        return null;
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
        return normalizeSubmittedParams(request == null ? null : request.parameters(), true);
    }

    private Map<String, SubmittedParam> normalizeSubmittedParams(List<ParameterInputRequest> parameters,
                                                                 boolean requireAllParameters) {
        if (parameters == null || parameters.isEmpty()) {
            if (requireAllParameters) {
                throw new IllegalArgumentException("parameters are required.");
            }
            return Map.of();
        }

        Map<String, SubmittedParam> submitted = new LinkedHashMap<>();
        for (ParameterInputRequest parameter : parameters) {
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
            if (SUPPORTED_PARAMETERS.stream().noneMatch(definition -> definition.key().equals(key))) {
                throw new IllegalArgumentException("unsupported parameter key: " + key);
            }
            submitted.put(key, new SubmittedParam(key, parameter.value(), parameter.unit()));
        }

        if (!requireAllParameters) {
            return submitted;
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

    private ParameterValidationResponse validateComparisonCorrection(ChatMessage message,
                                                                     int attemptNo,
                                                                     String processType,
                                                                     String taskType,
                                                                     List<MessageValidationSnapshot> snapshots,
                                                                     ParameterValidationRequest request) {
        MessageValidationSnapshot latestSnapshot = snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1);
        if (latestSnapshot == null) {
            throw new IllegalArgumentException("Comparison correction requires an existing validation snapshot.");
        }

        ExtractedParameterData.ProcessParams baseConditionA = readProcessParams(latestSnapshot.getConditionAJson());
        ExtractedParameterData.ProcessParams baseConditionB = readProcessParams(latestSnapshot.getConditionBJson());
        if (baseConditionA == null || baseConditionB == null) {
            throw new IllegalArgumentException("Comparison correction requires two extracted conditions.");
        }

        ComparisonCorrection correction = normalizeComparisonCorrection(request);
        ExtractedParameterData.ProcessParams correctedConditionA = mergeComparisonCondition(
                baseConditionA,
                correction.sharedParameters(),
                correction.conditionAParameters()
        );
        ExtractedParameterData.ProcessParams correctedConditionB = mergeComparisonCondition(
                baseConditionB,
                correction.sharedParameters(),
                correction.conditionBParameters()
        );

        ExtractedParameterData correctedData = new ExtractedParameterData(
                UUID.randomUUID().toString(),
                areComparisonConditionsValid(correctedConditionA, correctedConditionB) ? "VALID" : "INVALID_FIELD",
                processType,
                taskType,
                null,
                null,
                correctedConditionA,
                correctedConditionB
        );

        MessageValidationSnapshot snapshot = createSnapshot(
                message,
                attemptNo,
                SOURCE_USER_CORRECTION,
                correctedData,
                Map.of(),
                taskType
        );
        snapshotRepository.save(snapshot);
        return toResponse(snapshot);
    }

    private ComparisonCorrection normalizeComparisonCorrection(ParameterValidationRequest request) {
        Map<String, SubmittedParam> sharedParameters = normalizeSubmittedParams(
                request == null ? null : request.parameters(),
                false
        );
        Map<String, SubmittedParam> conditionAParameters = normalizeComparisonConditionParameters(
                request == null ? null : request.conditionA()
        );
        Map<String, SubmittedParam> conditionBParameters = normalizeComparisonConditionParameters(
                request == null ? null : request.conditionB()
        );

        if (sharedParameters.isEmpty() && conditionAParameters.isEmpty() && conditionBParameters.isEmpty()) {
            throw new IllegalArgumentException("comparison correction parameters are required.");
        }

        return new ComparisonCorrection(sharedParameters, conditionAParameters, conditionBParameters);
    }

    private boolean hasOutOfRangeSubmittedParameters(Map<String, SubmittedParam> submittedParams) {
        return submittedParams.entrySet().stream()
                .anyMatch(entry -> {
                    ParameterDefinition definition = parameterDefinition(entry.getKey());
                    Double value = entry.getValue() == null ? null : entry.getValue().value();
                    return definition.isOutOfRange(value);
                });
    }

    private ExtractedParameterData buildOutOfRangeSubmittedData(String processType,
                                                                String taskType,
                                                                Map<String, SubmittedParam> submittedParams) {
        return new ExtractedParameterData(
                UUID.randomUUID().toString(),
                "INVALID_FIELD",
                processType,
                taskType,
                new ExtractedParameterData.ProcessParams(
                        submittedValidatedParam("pressure", submittedParams, "mTorr"),
                        submittedValidatedParam("source_power", submittedParams, "W"),
                        submittedValidatedParam("bias_power", submittedParams, "W")
                ),
                null,
                null,
                null
        );
    }

    private ExtractedParameterData.ValidatedParam submittedValidatedParam(String key,
                                                                          Map<String, SubmittedParam> submittedParams,
                                                                          String defaultUnit) {
        SubmittedParam submitted = submittedParams.get(key);
        if (submitted == null) {
            return new ExtractedParameterData.ValidatedParam(null, defaultUnit, "MISSING");
        }
        return new ExtractedParameterData.ValidatedParam(
                submitted.value(),
                sanitize(submitted.unit(), defaultUnit),
                "VALID"
        );
    }

    private Map<String, SubmittedParam> normalizeComparisonConditionParameters(ParameterValidationRequest.ComparisonConditionInput conditionInput) {
        return normalizeSubmittedParams(conditionInput == null ? null : conditionInput.parameters(), false);
    }

    private ExtractedParameterData.ProcessParams mergeComparisonCondition(ExtractedParameterData.ProcessParams base,
                                                                         Map<String, SubmittedParam> sharedParameters,
                                                                         Map<String, SubmittedParam> specificParameters) {
        return new ExtractedParameterData.ProcessParams(
                mergeComparisonParam("pressure", base == null ? null : base.pressure(), sharedParameters, specificParameters, "mTorr"),
                mergeComparisonParam("source_power", base == null ? null : base.sourcePower(), sharedParameters, specificParameters, "W"),
                mergeComparisonParam("bias_power", base == null ? null : base.biasPower(), sharedParameters, specificParameters, "W")
        );
    }

    private ExtractedParameterData.ValidatedParam mergeComparisonParam(String key,
                                                                       ExtractedParameterData.ValidatedParam baseParam,
                                                                       Map<String, SubmittedParam> sharedParameters,
                                                                       Map<String, SubmittedParam> specificParameters,
                                                                       String defaultUnit) {
        SubmittedParam specific = specificParameters.get(key);
        if (specific != null) {
            return new ExtractedParameterData.ValidatedParam(
                    specific.value(),
                    sanitize(specific.unit(), defaultUnit),
                    "VALID"
            );
        }

        SubmittedParam shared = sharedParameters.get(key);
        if (shared != null) {
            return new ExtractedParameterData.ValidatedParam(
                    shared.value(),
                    sanitize(shared.unit(), defaultUnit),
                    "VALID"
            );
        }

        if (baseParam == null) {
            return new ExtractedParameterData.ValidatedParam(null, defaultUnit, "MISSING");
        }
        return new ExtractedParameterData.ValidatedParam(
                baseParam.value(),
                resolveUnit(baseParam, null, defaultUnit),
                baseParam.value() == null ? "MISSING" : resolveStatus(parameterDefinition(key), baseParam, null)
        );
    }

    private List<MessageValidationParam> buildComparisonPromptItems(ExtractedParameterData.ProcessParams conditionA,
                                                                    ExtractedParameterData.ProcessParams conditionB) {
        Map<String, ExtractedParameterData.ValidatedParam> left = toValidatedParamMap(conditionA);
        Map<String, ExtractedParameterData.ValidatedParam> right = toValidatedParamMap(conditionB);
        List<MessageValidationParam> items = new ArrayList<>();

        for (ParameterDefinition definition : SUPPORTED_PARAMETERS) {
            ComparisonPromptField promptField = resolveComparisonPromptField(
                    definition,
                    left.get(definition.key()),
                    right.get(definition.key())
            );
            if (promptField == null) {
                continue;
            }
            items.add(MessageValidationParam.create(
                    definition.key(),
                    definition.label(),
                    promptField.value(),
                    promptField.unit(),
                    promptField.status(),
                    definition.displayOrder()
            ));
        }
        return items;
    }

    private ComparisonPromptField resolveComparisonPromptField(ParameterDefinition definition,
                                                               ExtractedParameterData.ValidatedParam left,
                                                               ExtractedParameterData.ValidatedParam right) {
        if (isSameValidValue(definition, left, right)) {
            return null;
        }
        if (isValidParam(definition, left) && isValidParam(definition, right)) {
            return null;
        }

        boolean leftOutOfRange = isOutOfRange(definition, left);
        boolean rightOutOfRange = isOutOfRange(definition, right);
        Double suggestedValue = leftOutOfRange ? left.value()
                : rightOutOfRange ? right.value()
                : firstNonNull(
                        left == null ? null : left.value(),
                        right == null ? null : right.value()
                );
        String suggestedUnit = leftOutOfRange ? resolveUnit(left, null, definition.defaultUnit())
                : rightOutOfRange ? resolveUnit(right, null, definition.defaultUnit())
                : firstNonBlank(
                        left == null ? null : left.unit(),
                        right == null ? null : right.unit(),
                        definition.defaultUnit()
                );
        return new ComparisonPromptField(
                suggestedValue,
                suggestedUnit,
                comparisonPromptStatus(definition, left, right)
        );
    }

    private Map<String, ExtractedParameterData.ValidatedParam> toValidatedParamMap(ExtractedParameterData.ProcessParams processParams) {
        if (processParams == null) {
            return Map.of();
        }
        Map<String, ExtractedParameterData.ValidatedParam> result = new LinkedHashMap<>();
        result.put("pressure", processParams.pressure());
        result.put("source_power", processParams.sourcePower());
        result.put("bias_power", processParams.biasPower());
        return result;
    }

    private boolean isSameValidValue(ParameterDefinition definition,
                                     ExtractedParameterData.ValidatedParam left,
                                     ExtractedParameterData.ValidatedParam right) {
        return isValidParam(definition, left)
                && isValidParam(definition, right)
                && java.util.Objects.equals(left.value(), right.value())
                && java.util.Objects.equals(resolveUnit(left, null, ""), resolveUnit(right, null, ""));
    }

    private boolean isValidParam(ParameterDefinition definition,
                                 ExtractedParameterData.ValidatedParam param) {
        return param != null
                && param.value() != null
                && "VALID".equals(resolveStatus(definition, param, null));
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = sanitize(value, null);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
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

    private String resolveStatus(ParameterDefinition definition,
                                 ExtractedParameterData.ValidatedParam aiParam,
                                 SubmittedParam submitted) {
        Double resolvedValue = resolveValue(aiParam, submitted);
        if (resolvedValue != null && definition.isOutOfRange(resolvedValue)) {
            return VALIDATION_OUT_OF_RANGE;
        }
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

    private String comparisonPromptStatus(ParameterDefinition definition,
                                          ExtractedParameterData.ValidatedParam left,
                                          ExtractedParameterData.ValidatedParam right) {
        if (isOutOfRange(definition, left) || isOutOfRange(definition, right)) {
            return VALIDATION_OUT_OF_RANGE;
        }
        return "MISSING";
    }

    private boolean isOutOfRange(ParameterDefinition definition,
                                 ExtractedParameterData.ValidatedParam param) {
        return param != null
                && param.value() != null
                && definition.isOutOfRange(param.value());
    }

    private ParameterDefinition parameterDefinition(String key) {
        return SUPPORTED_PARAMETERS.stream()
                .filter(definition -> definition.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported parameter key: " + key));
    }

    private String resolveValidationStatus(String sourceType,
                                           String validationStatus,
                                           boolean allParameterStatusesValid,
                                           boolean comparisonConditionsValid) {
        if (comparisonConditionsValid) {
            return "VALID";
        }
        if (!allParameterStatusesValid && "VALID".equals(validationStatus)) {
            return "INVALID_FIELD";
        }
        if (SOURCE_USER_CORRECTION.equals(sourceType) && allParameterStatusesValid) {
            // 원문이 의미 없어서 AI가 UNSUPPORTED를 반환해도,
            // 사용자가 모든 공정조건을 채워 넣고 각 파라미터가 VALID면 수정 결과는 성공으로 본다.
            return "VALID";
        }
        return validationStatus;
    }

    private String resolveTaskType(String sourceType,
                                   String taskType,
                                   String previousTaskType,
                                   boolean allParameterStatusesValid) {
        if (SOURCE_USER_CORRECTION.equals(sourceType) && StringUtils.hasText(previousTaskType)) {
            return previousTaskType;
        }
        if (SOURCE_USER_CORRECTION.equals(sourceType)
                && allParameterStatusesValid
                && "UNSUPPORTED".equals(taskType)) {
            return null;
        }
        return taskType;
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

    private ParameterValidationResponse.ComparisonConditionResponse buildComparisonConditionResponse(String label, String processParamsJson) {
        ExtractedParameterData.ProcessParams processParams = readProcessParams(processParamsJson);
        if (processParams == null) {
            return null;
        }
        return new ParameterValidationResponse.ComparisonConditionResponse(
                label,
                toParameterFields(processParams)
        );
    }

    private ExtractedParameterData.ProcessParams readProcessParams(String processParamsJson) {
        if (!StringUtils.hasText(processParamsJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(processParamsJson, ExtractedParameterData.ProcessParams.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize comparison conditions.", exception);
        }
    }

    private boolean areComparisonConditionsValid(MessageValidationSnapshot snapshot) {
        return areComparisonConditionsValid(
                readProcessParams(snapshot.getConditionAJson()),
                readProcessParams(snapshot.getConditionBJson())
        );
    }

    private boolean areComparisonConditionsValid(ExtractedParameterData.ProcessParams conditionA,
                                                 ExtractedParameterData.ProcessParams conditionB) {
        return isValidCondition(conditionA) && isValidCondition(conditionB);
    }

    private boolean isValidCondition(ExtractedParameterData.ProcessParams processParams) {
        if (processParams == null) {
            return false;
        }
        return toParameterFields(processParams).stream()
                .allMatch(parameter -> "VALID".equals(parameter.status()));
    }

    private List<ParameterFieldResponse> toParameterFields(ExtractedParameterData.ProcessParams processParams) {
        return List.of(
                comparisonField("pressure", "Pressure", "mTorr", processParams.pressure(), 0),
                comparisonField("source_power", "Source Power", "W", processParams.sourcePower(), 1),
                comparisonField("bias_power", "Bias Power", "W", processParams.biasPower(), 2)
        ).stream()
                .sorted(java.util.Comparator.comparingInt(IndexedParameter::order))
                .map(IndexedParameter::parameter)
                .toList();
    }

    private IndexedParameter comparisonField(String key,
                                             String label,
                                             String defaultUnit,
                                             ExtractedParameterData.ValidatedParam param,
                                             int order) {
        return new IndexedParameter(
                order,
                new ParameterFieldResponse(
                        key,
                        label,
                        param == null ? null : param.value(),
                        param == null ? defaultUnit : resolveUnit(param, null, defaultUnit),
                        param == null ? "MISSING" : resolveStatus(parameterDefinition(key), param, null)
                )
        );
    }

    private record ParameterDefinition(String key,
                                       String label,
                                       String defaultUnit,
                                       int displayOrder,
                                       double minValue,
                                       double maxValue) {
        private boolean isOutOfRange(Double value) {
            return value != null && (value < minValue || value > maxValue);
        }
    }

    private record IndexedParameter(int order, ParameterFieldResponse parameter) {
    }

    private record ComparisonPromptField(Double value, String unit, String status) {
    }

    private record ComparisonCorrection(Map<String, SubmittedParam> sharedParameters,
                                        Map<String, SubmittedParam> conditionAParameters,
                                        Map<String, SubmittedParam> conditionBParameters) {
    }

    private record SubmittedParam(String key, Double value, String unit) {
    }
}
