package com.plasma.be.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.entity.MessageValidationParam;
import com.plasma.be.extract.entity.MessageValidationSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class ValidationHistoryFormatter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<MessageValidationSnapshot> selectLatestReusableSnapshot(List<MessageValidationSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Optional.empty();
        }

        return snapshots.stream()
                .filter(this::isReusableForHistory)
                .max(Comparator.comparingInt(MessageValidationSnapshot::getAttemptNo));
    }

    public String format(MessageValidationSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        List<String> sections = new ArrayList<>();
        String confirmedConditions = formatConditions(snapshot);
        if (StringUtils.hasText(confirmedConditions)) {
            sections.add(confirmedConditions);
        }

        String predictionSummary = sanitize(snapshot.getPredictionExplanationSummary());
        if (StringUtils.hasText(predictionSummary)
                && (!"QUESTION".equals(snapshot.getTaskType())
                || !predictionSummary.equals(sanitize(snapshot.getQuestionAnswerText())))) {
            sections.add(predictionSummary);
        }

        String detailsJson = snapshot.getPredictionExplanationDetailsJson();
        if (StringUtils.hasText(detailsJson) && !"{}".equals(detailsJson.trim())) {
            sections.add(detailsJson);
        }

        if (sections.isEmpty() && StringUtils.hasText(snapshot.getQuestionAnswerText())) {
            sections.add(snapshot.getQuestionAnswerText().trim());
        }

        return sections.isEmpty() ? null : String.join(" ", sections);
    }

    private boolean isReusableForHistory(MessageValidationSnapshot snapshot) {
        if (snapshot == null || "AI_ERROR".equals(snapshot.getValidationStatus())) {
            return false;
        }
        if (snapshot.isConfirmed()) {
            return true;
        }
        if ("QUESTION".equals(snapshot.getTaskType())) {
            return StringUtils.hasText(snapshot.getQuestionAnswerText());
        }
        if ("COMPARISON".equals(snapshot.getTaskType())) {
            return "VALID".equals(snapshot.getValidationStatus())
                    && hasCompleteComparisonConditions(snapshot);
        }
        return snapshot.isAllValid();
    }

    private String formatConditions(MessageValidationSnapshot snapshot) {
        if ("COMPARISON".equals(snapshot.getTaskType())) {
            String left = formatComparisonCondition("조건 A", readProcessParams(snapshot.getConditionAJson()));
            String right = formatComparisonCondition("조건 B", readProcessParams(snapshot.getConditionBJson()));
            if (StringUtils.hasText(left) || StringUtils.hasText(right)) {
                return List.of(left, right).stream()
                        .filter(StringUtils::hasText)
                        .reduce((first, second) -> first + " " + second)
                        .orElse(null);
            }
        }

        String parameters = snapshot.getItems().stream()
                .sorted(Comparator.comparingInt(MessageValidationParam::getDisplayOrder))
                .map(this::formatParam)
                .filter(StringUtils::hasText)
                .reduce((first, second) -> first + ", " + second)
                .orElse(null);

        if (!StringUtils.hasText(parameters)) {
            return null;
        }
        return "직전 확정 조건: " + parameters + ".";
    }

    private boolean hasCompleteComparisonConditions(MessageValidationSnapshot snapshot) {
        return isCompleteCondition(readProcessParams(snapshot.getConditionAJson()))
                && isCompleteCondition(readProcessParams(snapshot.getConditionBJson()));
    }

    private boolean isCompleteCondition(ExtractedParameterData.ProcessParams processParams) {
        if (processParams == null) {
            return false;
        }
        return isPresent(processParams.pressure())
                && isPresent(processParams.sourcePower())
                && isPresent(processParams.biasPower());
    }

    private boolean isPresent(ExtractedParameterData.ValidatedParam param) {
        return param != null && param.value() != null && "VALID".equals(sanitize(param.status()));
    }

    private String formatComparisonCondition(String label, ExtractedParameterData.ProcessParams processParams) {
        if (processParams == null) {
            return null;
        }

        List<String> params = new ArrayList<>();
        addParam(params, "pressure", processParams.pressure());
        addParam(params, "source_power", processParams.sourcePower());
        addParam(params, "bias_power", processParams.biasPower());
        if (params.isEmpty()) {
            return null;
        }
        return "직전 비교 " + label + ": " + String.join(", ", params) + ".";
    }

    private void addParam(List<String> params, String key, ExtractedParameterData.ValidatedParam param) {
        if (param == null || param.value() == null) {
            return;
        }
        params.add(formatEntry(key, param.value(), param.unit()));
    }

    private String formatParam(MessageValidationParam param) {
        if (param == null || param.getParameterValue() == null) {
            return null;
        }
        return formatEntry(param.getParameterKey(), param.getParameterValue(), param.getParameterUnit());
    }

    private String formatEntry(String key, Double value, String unit) {
        String normalizedUnit = sanitize(unit);
        if (!StringUtils.hasText(normalizedUnit)) {
            return key + "=" + value;
        }
        return key + "=" + value + " " + normalizedUnit;
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

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
