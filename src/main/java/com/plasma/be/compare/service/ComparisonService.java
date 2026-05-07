package com.plasma.be.compare.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.compare.client.CompareClient;
import com.plasma.be.compare.dto.ComparisonResponse;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.dto.ParameterFieldResponse;
import com.plasma.be.extract.dto.ParameterValidationResponse;
import com.plasma.be.extract.entity.MessageValidationParam;
import com.plasma.be.extract.entity.MessageValidationSnapshot;
import com.plasma.be.extract.repository.MessageValidationSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern CONDITION_PATTERN = Pattern.compile(
            "압력\\s*(-?\\d+(?:\\.\\d+)?)\\s*(?:mTorr)?[\\s,]*.*?소스\\s*파워\\s*(-?\\d+(?:\\.\\d+)?)\\s*(?:W)?[\\s,]*.*?바이어스\\s*파워\\s*(-?\\d+(?:\\.\\d+)?)\\s*(?:W)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<PatchRule> PATCH_RULES = List.of(
            new PatchRule("pressure", Pattern.compile(
                    "압력(?:이)?\\s*(-?\\d+(?:\\.\\d+)?)\\s*(?:mTorr)?\\s*(높아졌|올라갔|올라간|올려|증가|낮아졌|내려갔|내려간|내려|감소|줄어)",
                    Pattern.CASE_INSENSITIVE
            )),
            new PatchRule("source_power", Pattern.compile(
                    "소스\\s*파워(?:가)?\\s*(-?\\d+(?:\\.\\d+)?)\\s*(?:W)?\\s*(높아졌|올라갔|올라간|올려|증가|낮아졌|내려갔|내려간|내려|감소|줄어)",
                    Pattern.CASE_INSENSITIVE
            )),
            new PatchRule("bias_power", Pattern.compile(
                    "바이어스\\s*파워(?:가)?\\s*(-?\\d+(?:\\.\\d+)?)\\s*(?:W)?\\s*(높아졌|올라갔|올라간|올려|증가|낮아졌|내려갔|내려간|내려|감소|줄어)",
                    Pattern.CASE_INSENSITIVE
            ))
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
        ParsedComparison parsed = parse(message, validation);

        Condition left = resolveCondition(parsed.left(), message);
        Condition right = resolveCondition(parsed.right(), message);

        String processType = firstNonBlank(
                validation.processType(),
                left.processType(),
                right.processType(),
                DEFAULT_PROCESS_TYPE
        );
        left = left.withProcessType(processType);
        right = right.withProcessType(processType);

        ComparisonResponse upstream = compareClient.requestComparePipeline(
                processType,
                left.values(),
                left.units(),
                right.values(),
                right.units(),
                message.getInputText()
        );

        return mergeResponse(upstream, left, right, processType);
    }

    private ParsedComparison parse(ChatMessage message, ParameterValidationResponse validation) {
        ParsedComparison parsedFromExtract = parseFromExtract(message.getInputText(), validation);
        if (parsedFromExtract != null) {
            return parsedFromExtract;
        }
        return parseLegacy(message.getInputText());
    }

    private ParsedComparison parseFromExtract(String inputText, ParameterValidationResponse validation) {
        if (validation == null || validation.validationId() == null) {
            return null;
        }

        Optional<MessageValidationSnapshot> snapshot = snapshotRepository.findByValidationId(validation.validationId());
        if (snapshot.isEmpty()) {
            return null;
        }

        ExtractedParameterData.ProcessParams conditionA = readProcessParams(snapshot.get().getConditionAJson());
        ExtractedParameterData.ProcessParams conditionB = readProcessParams(snapshot.get().getConditionBJson());
        if (conditionA == null && conditionB == null) {
            return null;
        }

        if (conditionA != null && conditionB != null) {
            return new ParsedComparison(
                    Operand.inline(toCondition("condition_a", conditionA)),
                    Operand.inline(toCondition("condition_b", conditionB))
            );
        }

        int latestRefIndex = safeIndexOf(inputText, "그 조건");
        if (latestRefIndex < 0) {
            return null;
        }

        Condition extractedCondition = toCondition(
                conditionA != null ? "condition_a" : "condition_b",
                conditionA != null ? conditionA : conditionB
        );
        List<InlineConditionMatch> inlineConditions = parseInlineConditions(inputText);
        if (!inlineConditions.isEmpty() && latestRefIndex < inlineConditions.get(0).startIndex()) {
            return new ParsedComparison(Operand.latestConfirmed(), Operand.inline(extractedCondition));
        }
        return new ParsedComparison(Operand.inline(extractedCondition), Operand.latestConfirmed());
    }

    private ParsedComparison parseLegacy(String inputText) {
        String text = inputText == null ? "" : inputText;
        List<InlineConditionMatch> inlineConditions = parseInlineConditions(text);
        int latestRefIndex = safeIndexOf(text, "그 조건");

        if (inlineConditions.size() >= 2) {
            return new ParsedComparison(
                    Operand.inline(inlineConditions.get(0).condition()),
                    Operand.inline(inlineConditions.get(1).condition())
            );
        }

        if (latestRefIndex >= 0 && !inlineConditions.isEmpty()) {
            InlineConditionMatch inline = inlineConditions.get(0);
            if (latestRefIndex < inline.startIndex()) {
                return new ParsedComparison(Operand.latestConfirmed(), Operand.inline(inline.condition()));
            }
            return new ParsedComparison(Operand.inline(inline.condition()), Operand.latestConfirmed());
        }

        if (latestRefIndex >= 0) {
            ParameterPatch patch = parsePatch(text);
            if (patch != null) {
                return new ParsedComparison(Operand.latestConfirmed(), Operand.patchedLatest(patch));
            }
        }

        throw new IllegalArgumentException(
                "Unsupported comparison request. Provide two explicit conditions or compare against the latest confirmed condition."
        );
    }

    private int safeIndexOf(String text, String token) {
        return text == null ? -1 : text.indexOf(token);
    }

    private List<InlineConditionMatch> parseInlineConditions(String text) {
        List<InlineConditionMatch> matches = new ArrayList<>();
        Matcher matcher = CONDITION_PATTERN.matcher(text);
        while (matcher.find()) {
            Map<String, Double> values = new LinkedHashMap<>();
            values.put("pressure", Double.parseDouble(matcher.group(1)));
            values.put("source_power", Double.parseDouble(matcher.group(2)));
            values.put("bias_power", Double.parseDouble(matcher.group(3)));

            matches.add(new InlineConditionMatch(
                    matcher.start(),
                    new Condition("inline", null, values, new LinkedHashMap<>(DEFAULT_UNITS))
            ));
        }
        return matches;
    }

    private ParameterPatch parsePatch(String text) {
        for (PatchRule rule : PATCH_RULES) {
            Matcher matcher = rule.pattern().matcher(text);
            if (!matcher.find()) {
                continue;
            }

            double amount = Double.parseDouble(matcher.group(1));
            String verb = matcher.group(2).toLowerCase(Locale.ROOT);
            double delta = isPositiveVerb(verb) ? amount : -amount;
            return new ParameterPatch(rule.parameterKey(), delta);
        }
        return null;
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

    private boolean isPositiveVerb(String verb) {
        return verb.contains("높")
                || verb.contains("올라")
                || verb.contains("올려")
                || verb.contains("증가");
    }

    private Condition resolveCondition(Operand operand, ChatMessage message) {
        return switch (operand.type()) {
            case INLINE -> operand.condition();
            case LATEST_CONFIRMED -> loadLatestConfirmedCondition(message);
            case PATCHED_LATEST -> loadLatestConfirmedCondition(message).applyPatch(operand.patch());
        };
    }

    private Condition loadLatestConfirmedCondition(ChatMessage message) {
        Optional<MessageValidationSnapshot> snapshot = snapshotRepository
                .findTopByMessageSessionSessionIdAndConfirmedTrueAndMessageMessageIdNotOrderByCreatedAtDesc(
                        message.getSessionId(),
                        message.getMessageId()
                );
        if (snapshot.isEmpty()) {
            throw new IllegalArgumentException("No confirmed condition is available in the current session.");
        }

        MessageValidationSnapshot latest = snapshot.get();
        Map<String, Double> values = new LinkedHashMap<>();
        Map<String, String> units = new LinkedHashMap<>();
        for (MessageValidationParam item : latest.getItems()) {
            values.put(item.getParameterKey(), item.getParameterValue());
            units.put(item.getParameterKey(), StringUtils.hasText(item.getParameterUnit())
                    ? item.getParameterUnit()
                    : DEFAULT_UNITS.getOrDefault(item.getParameterKey(), ""));
        }

        return new Condition("latest_confirmed", firstNonBlank(latest.getProcessType(), DEFAULT_PROCESS_TYPE), values, units);
    }

    private ComparisonResponse mergeResponse(ComparisonResponse upstream,
                                             Condition left,
                                             Condition right,
                                             String processType) {
        return new ComparisonResponse(
                mergeConditionResult(left, processType, upstream == null ? null : upstream.left()),
                mergeConditionResult(right, processType, upstream == null ? null : upstream.right()),
                upstream == null ? null : upstream.difference(),
                upstream == null ? null : upstream.summary()
        );
    }

    private ComparisonResponse.ConditionResult mergeConditionResult(Condition condition,
                                                                   String processType,
                                                                   ComparisonResponse.ConditionResult upstream) {
        return new ComparisonResponse.ConditionResult(
                condition.label(),
                firstNonBlank(condition.processType(), upstream == null ? null : upstream.processType(), processType),
                condition.toParameterResponses(),
                upstream == null ? null : upstream.prediction()
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

    private enum OperandType {
        INLINE,
        LATEST_CONFIRMED,
        PATCHED_LATEST
    }

    private record ParsedComparison(Operand left, Operand right) {
    }

    private record Operand(OperandType type, Condition condition, ParameterPatch patch) {
        private static Operand inline(Condition condition) {
            return new Operand(OperandType.INLINE, condition, null);
        }

        private static Operand latestConfirmed() {
            return new Operand(OperandType.LATEST_CONFIRMED, null, null);
        }

        private static Operand patchedLatest(ParameterPatch patch) {
            return new Operand(OperandType.PATCHED_LATEST, null, patch);
        }
    }

    private record ParameterPatch(String parameterKey, double delta) {
    }

    private record PatchRule(String parameterKey, Pattern pattern) {
    }

    private record InlineConditionMatch(int startIndex, Condition condition) {
    }

    private record Condition(String label,
                             String processType,
                             Map<String, Double> values,
                             Map<String, String> units) {
        private Condition withProcessType(String processType) {
            return new Condition(label, processType, values, units);
        }

        private Condition applyPatch(ParameterPatch patch) {
            Map<String, Double> patchedValues = new LinkedHashMap<>(values);
            Double baseValue = patchedValues.get(patch.parameterKey());
            if (baseValue == null) {
                throw new IllegalArgumentException("Latest confirmed condition is missing " + patch.parameterKey() + ".");
            }
            patchedValues.put(patch.parameterKey(), baseValue + patch.delta());
            return new Condition("patched_latest", processType, patchedValues, new LinkedHashMap<>(units));
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
