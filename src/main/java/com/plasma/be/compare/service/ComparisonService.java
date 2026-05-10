package com.plasma.be.compare.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.compare.client.CompareClient;
import com.plasma.be.compare.client.dto.ComparisonPipelineAiResponse;
import com.plasma.be.compare.dto.ComparisonResponse;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.dto.ParameterFieldResponse;
import com.plasma.be.extract.dto.ParameterValidationResponse;
import com.plasma.be.extract.entity.MessageValidationSnapshot;
import com.plasma.be.extract.repository.MessageValidationSnapshotRepository;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ComparisonService {

    private static final String DEFAULT_PROCESS_TYPE = "ETCH";
    private static final Map<String, String> PARAMETER_LABELS = Map.of(
            "pressure", "Pressure",
            "source_power", "Source Power",
            "bias_power", "Bias Power"
    );
    private static final Map<String, String> DEFAULT_UNITS = Map.of(
            "pressure", "mTorr",
            "source_power", "W",
            "bias_power", "W"
    );

    private final MessageValidationSnapshotRepository snapshotRepository;
    private final CompareClient compareClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ComparisonService(MessageValidationSnapshotRepository snapshotRepository,
                             CompareClient compareClient) {
        this.snapshotRepository = snapshotRepository;
        this.compareClient = compareClient;
    }

    public ComparisonResponse compare(ChatMessage message, ParameterValidationResponse validation) {
        ParsedComparison parsed = parseFromExtract(validation);

        String processType = firstNonBlank(
                validation == null ? null : validation.processType(),
                parsed.left().processType(),
                parsed.right().processType(),
                DEFAULT_PROCESS_TYPE
        );
        Condition left = parsed.left().withProcessType(processType);
        Condition right = parsed.right().withProcessType(processType);

        ComparisonPipelineAiResponse aiResponse = compareClient.requestComparePipeline(
                processType,
                left.values(),
                left.units(),
                right.values(),
                right.units(),
                message.getInputText()
        );

        return buildResponse(aiResponse, left, right, processType);
    }

    private ParsedComparison parseFromExtract(ParameterValidationResponse validation) {
        if (validation == null || validation.validationId() == null) {
            throw new IllegalArgumentException("Comparison requires two resolved conditions from extract.");
        }

        Optional<MessageValidationSnapshot> snapshot = snapshotRepository.findByValidationId(validation.validationId());
        if (snapshot.isEmpty()) {
            throw new IllegalArgumentException("Comparison requires two resolved conditions from extract.");
        }

        ExtractedParameterData.ProcessParams conditionA = readProcessParams(snapshot.get().getConditionAJson());
        ExtractedParameterData.ProcessParams conditionB = readProcessParams(snapshot.get().getConditionBJson());
        if (conditionA == null || conditionB == null) {
            throw new IllegalArgumentException("Comparison requires two resolved conditions from extract.");
        }

        return new ParsedComparison(
                toCondition("left", conditionA),
                toCondition("right", conditionB)
        );
    }

    private ExtractedParameterData.ProcessParams readProcessParams(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ExtractedParameterData.ProcessParams.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read stored comparison conditions.", exception);
        }
    }

    private Condition toCondition(String label, ExtractedParameterData.ProcessParams processParams) {
        Map<String, Double> values = new LinkedHashMap<>();
        Map<String, String> units = new LinkedHashMap<>();
        putParam(values, units, "pressure", processParams.pressure());
        putParam(values, units, "source_power", processParams.sourcePower());
        putParam(values, units, "bias_power", processParams.biasPower());
        return new Condition(label, null, values, units);
    }

    private void putParam(Map<String, Double> values,
                          Map<String, String> units,
                          String key,
                          ExtractedParameterData.ValidatedParam param) {
        values.put(key, param == null ? null : param.value());
        units.put(key, param == null || !StringUtils.hasText(param.unit())
                ? DEFAULT_UNITS.getOrDefault(key, "")
                : param.unit());
    }

    private ComparisonResponse buildResponse(ComparisonPipelineAiResponse ai,
                                             Condition left,
                                             Condition right,
                                             String processType) {
        PredictPipelineResponse leftPrediction = toPredictResponse(ai, ai == null ? null : ai.conditionA());
        PredictPipelineResponse rightPrediction = toPredictResponse(ai, ai == null ? null : ai.conditionB());
        return new ComparisonResponse(
                buildConditionResult(left, processType, leftPrediction),
                buildConditionResult(right, processType, rightPrediction),
                calculateDifference(leftPrediction, rightPrediction),
                null
        );
    }

    private PredictPipelineResponse toPredictResponse(ComparisonPipelineAiResponse ai,
                                                       ComparisonPipelineAiResponse.ConditionResult c) {
        if (ai == null || c == null) return null;
        return new PredictPipelineResponse(
                ai.requestId(),
                ai.processType(),
                c.predictionResult(),
                c.explanation()
        );
    }

    private ComparisonResponse.Difference calculateDifference(PredictPipelineResponse left,
                                                               PredictPipelineResponse right) {
        if (left == null || right == null) return null;
        PredictPipelineResponse.PredictionResult l = left.predictionResult();
        PredictPipelineResponse.PredictionResult r = right.predictionResult();
        if (l == null || r == null) return null;
        return new ComparisonResponse.Difference(
                delta(l.ionFlux() != null ? l.ionFlux().value() : null,
                      r.ionFlux() != null ? r.ionFlux().value() : null),
                r.ionFlux() != null ? r.ionFlux().unit() : null,
                delta(l.ionEnergy() != null ? l.ionEnergy().value() : null,
                      r.ionEnergy() != null ? r.ionEnergy().value() : null),
                r.ionEnergy() != null ? r.ionEnergy().unit() : null,
                delta(l.etchScore() != null ? l.etchScore().value() : null,
                      r.etchScore() != null ? r.etchScore().value() : null),
                r.etchScore() != null ? r.etchScore().unit() : null
        );
    }

    private Double delta(Double left, Double right) {
        if (left == null || right == null) return null;
        return right - left;
    }

    private ComparisonResponse.ConditionResult buildConditionResult(Condition condition,
                                                                     String processType,
                                                                     PredictPipelineResponse prediction) {
        return new ComparisonResponse.ConditionResult(
                condition.label(),
                firstNonBlank(condition.processType(), processType),
                condition.toParameterResponses(),
                prediction
        );
    }

    @SafeVarargs
    private static <T> T firstNonBlank(T... candidates) {
        for (T candidate : candidates) {
            if (candidate instanceof String stringValue) {
                if (StringUtils.hasText(stringValue)) {
                    return candidate;
                }
                continue;
            }
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private record ParsedComparison(Condition left, Condition right) {
    }

    private record Condition(String label,
                             String processType,
                             Map<String, Double> values,
                             Map<String, String> units) {
        private Condition withProcessType(String processType) {
            return new Condition(label, processType, values, units);
        }

        private List<ParameterFieldResponse> toParameterResponses() {
            return List.of(
                    parameter("pressure"),
                    parameter("source_power"),
                    parameter("bias_power")
            );
        }

        private ParameterFieldResponse parameter(String key) {
            return new ParameterFieldResponse(
                    key,
                    PARAMETER_LABELS.get(key),
                    values.get(key),
                    units.getOrDefault(key, DEFAULT_UNITS.getOrDefault(key, "")),
                    "VALID"
            );
        }
    }
}
