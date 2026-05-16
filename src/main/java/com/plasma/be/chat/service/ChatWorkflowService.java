package com.plasma.be.chat.service;

import com.plasma.be.chat.dto.ChatMessageCreateRequest;
import com.plasma.be.chat.dto.ChatMessageSummaryResponse;
import com.plasma.be.chat.dto.ConfirmOptimizationResponse;
import com.plasma.be.chat.dto.ConfirmRequest;
import com.plasma.be.chat.dto.ConfirmResponse;
import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.compare.dto.ComparisonResponse;
import com.plasma.be.compare.service.ComparisonService;
import com.plasma.be.extract.dto.ParameterFieldResponse;
import com.plasma.be.extract.dto.ParameterValidationRequest;
import com.plasma.be.extract.dto.ParameterValidationResponse;
import com.plasma.be.extract.service.ExtractService;
import com.plasma.be.optimize.client.OptimizeClient;
import com.plasma.be.optimize.client.ParameterImpactClient;
import com.plasma.be.optimize.client.dto.OptimizePipelineResponse;
import com.plasma.be.optimize.client.dto.ParameterImpactResponse;
import com.plasma.be.optimize.dto.OptimizeRequest;
import com.plasma.be.plasma.dto.PlasmaDistributionResponse;
import com.plasma.be.plasma.service.PlasmaDistributionService;
import com.plasma.be.predict.client.PredictClient;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;
import com.plasma.be.question.client.dto.QuestionAnswerResponse;
import com.plasma.be.question.service.QuestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatWorkflowService {

    private static final int MAX_OPTIMIZATION_CANDIDATES = 3;
    private static final Logger log = LoggerFactory.getLogger(ChatWorkflowService.class);

    private final ChatMessageService chatMessageService;
    private final ExtractService extractService;
    private final PredictClient predictClient;
    private final OptimizeClient optimizeClient;
    private final ComparisonService comparisonService;
    private final QuestionService questionService;
    private final ParameterImpactClient parameterImpactClient;
    private final PlasmaDistributionService plasmaDistributionService;

    public ChatWorkflowService(ChatMessageService chatMessageService,
                               ExtractService extractService,
                               PredictClient predictClient,
                               OptimizeClient optimizeClient,
                               ComparisonService comparisonService,
                               QuestionService questionService,
                               ParameterImpactClient parameterImpactClient,
                               PlasmaDistributionService plasmaDistributionService) {
        this.chatMessageService = chatMessageService;
        this.extractService = extractService;
        this.predictClient = predictClient;
        this.optimizeClient = optimizeClient;
        this.comparisonService = comparisonService;
        this.questionService = questionService;
        this.parameterImpactClient = parameterImpactClient;
        this.plasmaDistributionService = plasmaDistributionService;
    }

    // 사용자 메시지를 저장하고 첫 번째 AI 추출 결과를 응답으로 반환한다.
    public ChatMessageSummaryResponse createMessageAndExtract(ChatMessageCreateRequest request, String ownerSessionKey) {
        ChatMessage message = chatMessageService.saveMessage(request, ownerSessionKey);
        ParameterValidationResponse validation = extractService.extractFromMessage(message.getMessageId());
        return chatMessageService.toResponse(message, List.of(validation));
    }

    // 메시지별 검증 이력을 포함한 채팅 목록을 조회한다.
    public List<ChatMessageSummaryResponse> findMessagesWithValidations(String sessionId, String ownerSessionKey) {
        List<ChatMessage> messages = chatMessageService.findMessageEntitiesBySessionId(sessionId, ownerSessionKey);
        Map<Long, List<ParameterValidationResponse>> validationsByMessageId = extractService.findByMessageIds(
                messages.stream().map(ChatMessage::getMessageId).toList()
        ).stream().collect(Collectors.groupingBy(
                ParameterValidationResponse::messageId,
                Collectors.mapping(Function.identity(), Collectors.toList())
        ));

        return messages.stream()
                .map(message -> chatMessageService.toResponse(
                        message,
                        validationsByMessageId.getOrDefault(message.getMessageId(), List.of())
                ))
                .toList();
    }

    // 사용자가 수정한 파라미터를 기존 메시지에 연결된 새 검증 시도로 저장한다.
    public ParameterValidationResponse validateCorrection(Long messageId,
                                                          ParameterValidationRequest request,
                                                          String ownerSessionKey) {
        ChatMessage message = chatMessageService.findOwnedMessage(messageId, ownerSessionKey);
        return extractService.validateCorrection(message.getMessageId(), request);
    }

    // 최종 검증 결과를 확정하고, task_type이 PREDICTION이면 예측 파이프라인을 실행한다.
    public ConfirmResponse confirmValidation(Long messageId,
                                             Long validationId,
                                             String ownerSessionKey,
                                             ConfirmRequest request) {
        ChatMessage message = chatMessageService.findOwnedMessage(messageId, ownerSessionKey);
        ParameterValidationResponse validation = resolveValidationForConfirm(messageId, validationId, request);
        String requestedTaskType = request == null ? null : request.requestedTaskType();

        validation = extractService.confirmValidation(messageId, validation.validationId())
                .orElseThrow(() -> new NoSuchElementException("validationId is not associated with the message."));

        String taskType = resolveTaskType(validation.taskType(), requestedTaskType);
        if ("COMPARISON".equals(taskType)) {
            try {
                ComparisonResponse comparison = comparisonService.compare(message, validation);
                String summary = buildComparisonSummary(comparison);
                if (summary != null) {
                    extractService.storeAssistantSummary(messageId, validation.validationId(), summary);
                }
                return new ConfirmResponse(validation, null, null, null, comparison, null, null, null);
            } catch (RestClientException e) {
                return new ConfirmResponse(validation, null, null, null, null, null, null, e.getMessage());
            }
        }
        if ("OPTIMIZATION".equals(taskType)) {
            try {
                PredictPipelineResponse currentPrediction = runPredictPipeline(message, validation);
                OptimizePipelineResponse optimizeResult = runOptimizePipeline(message, validation);
                ConfirmOptimizationResponse optimization = toConfirmOptimizationResponse(
                        optimizeResult, validation, currentPrediction);
                String summary = buildOptimizationSummary(optimizeResult);
                if (summary != null) {
                    extractService.storeAssistantSummary(messageId, validation.validationId(), summary);
                }
                return new ConfirmResponse(validation, null, null, optimization, null, null, null, null);
            } catch (RestClientException e) {
                return new ConfirmResponse(validation, null, null, null, null, null, null, e.getMessage());
            }
        }
        if ("QUESTION".equals(taskType)) {
            try {
                QuestionAnswerResponse question = questionService.answer(message);
                if (question != null && StringUtils.hasText(question.answerText())) {
                    extractService.storeAssistantSummary(messageId, validation.validationId(), question.answerText());
                }
                return new ConfirmResponse(validation, null, null, null, null, question, null, null);
            } catch (RestClientException e) {
                return new ConfirmResponse(validation, null, null, null, null, null, null, e.getMessage());
            }
        }
        if ("UNSUPPORTED".equals(taskType)) {
            return new ConfirmResponse(validation, null, null, null, null, null, null, null);
        }
        if (!StringUtils.hasText(taskType)) {
            throw new IllegalArgumentException("requestedTaskType is required when taskType is not inferred.");
        }
        if (!"PREDICTION".equals(taskType)) {
            return new ConfirmResponse(validation, null, null, null, null, null, null, null);
        }

        try {
            PredictPipelineResponse prediction = runPredictPipeline(message, validation);
            PlasmaDistributionResponse plasmaDistribution = fetchPlasmaDistribution(validation);
            ParameterValidationResponse updatedValidation =
                    extractService.storePredictionOutcome(messageId, validationId, prediction, null);
            return new ConfirmResponse(updatedValidation, prediction, plasmaDistribution, null, null, null, null, null);
        } catch (RestClientException e) {
            ParameterValidationResponse updatedValidation =
                    extractService.storePredictionOutcome(messageId, validationId, null, e.getMessage());
            return new ConfirmResponse(updatedValidation, null, null, null, null, null, e.getMessage(), e.getMessage());
        }
    }

    private ParameterValidationResponse resolveValidationForConfirm(Long messageId,
                                                                     Long validationId,
                                                                     ConfirmRequest request) {
        ParameterValidationResponse validation = extractService.findValidation(messageId, validationId)
                .orElseThrow(() -> new NoSuchElementException("validationId is not associated with the message."));

        String requestedTaskType = request == null ? null : request.requestedTaskType();
        String taskType = resolveTaskType(validation.taskType(), requestedTaskType);
        if (!"COMPARISON".equals(taskType) || request == null || !request.hasComparisonOverrides()) {
            return validation;
        }

        ParameterValidationRequest correction = new ParameterValidationRequest(
                request.parameters(),
                request.normalizedConditionA(),
                request.normalizedConditionB()
        );
        return extractService.validateCorrection(messageId, correction);
    }

    private String resolveTaskType(String inferredTaskType, String requestedTaskType) {
        String normalizedRequested = normalizeRequestedTaskType(requestedTaskType);
        String normalizedInferred = StringUtils.hasText(inferredTaskType)
                ? inferredTaskType.trim().toUpperCase()
                : null;

        if (normalizedRequested != null) {
            return normalizedRequested;
        }
        if (StringUtils.hasText(normalizedInferred)) {
            return normalizedInferred;
        }
        return normalizedRequested;
    }

    private String normalizeRequestedTaskType(String requestedTaskType) {
        if (!StringUtils.hasText(requestedTaskType)) {
            return null;
        }
        String normalized = requestedTaskType.trim().toUpperCase();
        if (!List.of("PREDICTION", "OPTIMIZATION", "COMPARISON", "QUESTION").contains(normalized)) {
            throw new IllegalArgumentException("requestedTaskType must be PREDICTION, OPTIMIZATION, COMPARISON, or QUESTION.");
        }
        return normalized;
    }

    private PredictPipelineResponse runPredictPipeline(ChatMessage message,
                                                       ParameterValidationResponse validation) {
        Map<String, Double> paramValues = validation.parameters().stream()
                .filter(p -> p.value() != null)
                .collect(Collectors.toMap(ParameterFieldResponse::key, ParameterFieldResponse::value));
        Map<String, String> paramUnits = validation.parameters().stream()
                .filter(p -> p.unit() != null)
                .collect(Collectors.toMap(ParameterFieldResponse::key, ParameterFieldResponse::unit));

        String normalizedProcessType = validation.processType();
        if (!StringUtils.hasText(normalizedProcessType) || "UNKNOWN".equalsIgnoreCase(normalizedProcessType)) {
            normalizedProcessType = "ETCH";
        }

        return predictClient.requestPredictPipeline(
                normalizedProcessType,
                paramValues,
                paramUnits,
                message.getInputText()
        );
    }

    private OptimizePipelineResponse runOptimizePipeline(ChatMessage message,
                                                         ParameterValidationResponse validation) {
        Map<String, Object> processParams = new LinkedHashMap<>();
        for (ParameterFieldResponse parameter : validation.parameters()) {
            processParams.put(parameter.key(), Map.of(
                    "value", parameter.value(),
                    "unit", parameter.unit() == null ? "" : parameter.unit()
            ));
        }

        Map<String, Object> currentOutputs = null;
        if (validation.currentEr() != null && validation.currentEr().value() != null) {
            currentOutputs = Map.of(
                    "etch_rate", Map.of(
                            "value", validation.currentEr().value(),
                            "unit", validation.currentEr().unit() == null ? "" : validation.currentEr().unit()
                    )
            );
        }

        return optimizeClient.requestOptimizePipeline(new OptimizeRequest(
                message.getInputText(),
                validation.processType() != null ? validation.processType() : "ETCH",
                processParams,
                currentOutputs
        ));
    }

    private ConfirmOptimizationResponse toConfirmOptimizationResponse(
            OptimizePipelineResponse optimization,
            ParameterValidationResponse validation,
            PredictPipelineResponse currentPrediction) {

        if (optimization == null || optimization.optimizationResult() == null) {
            return null;
        }

        ConfirmOptimizationResponse.Current current = new ConfirmOptimizationResponse.Current(
                buildProcessParams(validation),
                currentPrediction != null ? currentPrediction.predictionResult() : null,
                fetchPlasmaDistribution(validation)
        );

        List<ConfirmOptimizationResponse.Candidate> candidates = optimization
                .optimizationResult()
                .optimizationCandidates()
                .stream()
                .sorted(Comparator.comparingDouble(this::candidateEtchScore).reversed())
                .limit(MAX_OPTIMIZATION_CANDIDATES)
                .map(c -> {
                    ConfirmOptimizationResponse.ProcessParams params = toConfirmProcessParams(c.processParams());
                    ConfirmOptimizationResponse.ParameterImpact impact =
                            fetchParameterImpact(optimization.processType(), params);
                    return new ConfirmOptimizationResponse.Candidate(
                            (long) c.rank(), params, c.predictionResult(), impact, fetchPlasmaDistribution(params));
                })
                .toList();

        return new ConfirmOptimizationResponse(current, candidates);
    }

    private ConfirmOptimizationResponse.ProcessParams buildProcessParams(ParameterValidationResponse validation) {
        Map<String, ParameterFieldResponse> paramMap = validation.parameters().stream()
                .collect(Collectors.toMap(ParameterFieldResponse::key, Function.identity()));
        return new ConfirmOptimizationResponse.ProcessParams(
                toParamValue(paramMap.get("pressure")),
                toParamValue(paramMap.get("source_power")),
                toParamValue(paramMap.get("bias_power"))
        );
    }

    private ConfirmOptimizationResponse.ParamValue toParamValue(ParameterFieldResponse field) {
        if (field == null) return null;
        return new ConfirmOptimizationResponse.ParamValue(field.value(), field.unit());
    }

    private ConfirmOptimizationResponse.ProcessParams toConfirmProcessParams(
            OptimizePipelineResponse.ProcessParams params) {
        if (params == null) return null;
        return new ConfirmOptimizationResponse.ProcessParams(
                toConfirmParamValue(params.pressure()),
                toConfirmParamValue(params.sourcePower()),
                toConfirmParamValue(params.biasPower())
        );
    }

    private ConfirmOptimizationResponse.ParamValue toConfirmParamValue(
            OptimizePipelineResponse.ValueWithUnit v) {
        if (v == null) return null;
        return new ConfirmOptimizationResponse.ParamValue(v.value(), v.unit());
    }

    private PlasmaDistributionResponse fetchPlasmaDistribution(ParameterValidationResponse validation) {
        if (validation == null || validation.parameters() == null) {
            return null;
        }
        Map<String, ParameterFieldResponse> paramMap = validation.parameters().stream()
                .collect(Collectors.toMap(ParameterFieldResponse::key, Function.identity(), (left, right) -> right));
        return fetchPlasmaDistribution(
                valueOf(paramMap.get("pressure")),
                valueOf(paramMap.get("source_power")),
                valueOf(paramMap.get("bias_power"))
        );
    }

    private PlasmaDistributionResponse fetchPlasmaDistribution(ConfirmOptimizationResponse.ProcessParams params) {
        if (params == null) {
            return null;
        }
        return fetchPlasmaDistribution(
                valueOf(params.pressure()),
                valueOf(params.sourcePower()),
                valueOf(params.biasPower())
        );
    }

    private PlasmaDistributionResponse fetchPlasmaDistribution(Double pressure,
                                                               Double sourcePower,
                                                               Double biasPower) {
        if (pressure == null || sourcePower == null || biasPower == null) {
            return null;
        }
        try {
            return plasmaDistributionService.findClosest(pressure, sourcePower, biasPower)
                    .map(plasmaDistributionService::toResponse)
                    .orElse(null);
        } catch (RuntimeException exception) {
            log.warn("Failed to load plasma distribution for pressure={}, sourcePower={}, biasPower={}",
                    pressure, sourcePower, biasPower, exception);
            return null;
        }
    }

    private Double valueOf(ParameterFieldResponse field) {
        return field == null ? null : field.value();
    }

    private Double valueOf(ConfirmOptimizationResponse.ParamValue field) {
        return field == null ? null : field.value();
    }

    private ConfirmOptimizationResponse.ParameterImpact fetchParameterImpact(
            String processType, ConfirmOptimizationResponse.ProcessParams params) {
        try {
            ParameterImpactResponse response = parameterImpactClient.requestParameterImpact(processType, params);
            if (response == null) return null;
            return new ConfirmOptimizationResponse.ParameterImpact(
                    toImpactPoints(response.pressureImpact()),
                    toImpactPoints(response.sourcePowerImpact()),
                    toImpactPoints(response.biasPowerImpact())
            );
        } catch (RestClientException e) {
            return null;
        }
    }

    private List<ConfirmOptimizationResponse.ImpactPoint> toImpactPoints(
            List<ParameterImpactResponse.ImpactPoint> points) {
        if (points == null) return List.of();
        return points.stream()
                .map(p -> new ConfirmOptimizationResponse.ImpactPoint(p.x(), p.y()))
                .toList();
    }

    private String buildOptimizationSummary(OptimizePipelineResponse result) {
        if (result == null) {
            return null;
        }
        if (result.explanation() != null && StringUtils.hasText(result.explanation().summary())) {
            return result.explanation().summary();
        }
        if (result.optimizationResult() == null
                || result.optimizationResult().optimizationCandidates() == null
                || result.optimizationResult().optimizationCandidates().isEmpty()) {
            return null;
        }
        OptimizePipelineResponse.OptimizationCandidate top = result.optimizationResult()
                .optimizationCandidates().stream()
                .max(Comparator.comparingDouble(this::candidateEtchScore))
                .orElse(null);
        if (top == null || top.processParams() == null) {
            return null;
        }
        OptimizePipelineResponse.ProcessParams p = top.processParams();
        StringBuilder sb = new StringBuilder();
        sb.append("최적화 후보 ")
                .append(result.optimizationResult().candidateCount())
                .append("개가 도출됐습니다. 1순위 조건: ");
        if (p.pressure() != null) sb.append("pressure=").append(p.pressure().value()).append(" ").append(p.pressure().unit()).append(", ");
        if (p.sourcePower() != null) sb.append("source_power=").append(p.sourcePower().value()).append(" ").append(p.sourcePower().unit()).append(", ");
        if (p.biasPower() != null) sb.append("bias_power=").append(p.biasPower().value()).append(" ").append(p.biasPower().unit());
        if (top.predictionResult() != null && top.predictionResult().etchScore() != null
                && top.predictionResult().etchScore().value() != null) {
            sb.append(" (etch score: ").append(top.predictionResult().etchScore().value()).append(")");
        }
        return sb.toString();
    }

    private String buildComparisonSummary(ComparisonResponse comparison) {
        if (comparison == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        appendConditionSummary(sb, "조건 A", comparison.left());
        sb.append(" vs ");
        appendConditionSummary(sb, "조건 B", comparison.right());
        if (comparison.difference() != null && comparison.difference().etchScoreDelta() != null) {
            sb.append(": etch score 차이 ");
            double delta = comparison.difference().etchScoreDelta();
            sb.append(delta >= 0 ? "+" : "").append(delta);
        }
        return sb.toString();
    }

    private void appendConditionSummary(StringBuilder sb, String label, ComparisonResponse.ConditionResult condition) {
        sb.append(label).append("(");
        if (condition != null && condition.parameters() != null) {
            condition.parameters().forEach(p -> sb.append(p.key()).append("=").append(p.value()).append(" ").append(p.unit()).append(", "));
            if (!condition.parameters().isEmpty()) {
                sb.setLength(sb.length() - 2);
            }
        }
        sb.append(")");
    }

    private double candidateEtchScore(OptimizePipelineResponse.OptimizationCandidate candidate) {
        if (candidate == null
                || candidate.predictionResult() == null
                || candidate.predictionResult().etchScore() == null
                || candidate.predictionResult().etchScore().value() == null) {
            return Double.NEGATIVE_INFINITY;
        }
        return candidate.predictionResult().etchScore().value();
    }
}
