package com.plasma.be.extract.entity;

import com.plasma.be.chat.entity.ChatMessage;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "message_validation_snapshot")
// 검증 결과 "한 번치 전체"를 저장하는 엔티티다.
// 예를 들어 최초 AI 추출 1회, 사용자 수정 후 재검증 1회가 각각 snapshot 1개가 된다.
public class MessageValidationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 검증 시도 1건의 고유 ID다.
    @Column(name = "validation_id")
    private Long validationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    // 이 검증이 어떤 메시지에 대해 수행됐는지 가리킨다.
    private ChatMessage message;

    // AI 서버 요청 단위를 추적하기 위한 ID다.
    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    // 같은 message 안에서 몇 번째 검증 시도인지 나타낸다.
    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    // 최초 AI 추출인지, 사용자 수정 후 재검증인지 같은 검증 출처를 저장한다.
    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    // 검증 전체 상태다. 예: VALID, INVALID_FIELD, AI_ERROR
    @Column(name = "validation_status", nullable = false, length = 30)
    private String validationStatus;

    // AI가 인식한 공정 타입이다. 없을 수도 있다.
    @Column(name = "process_type", length = 30)
    private String processType;

    // AI가 인식한 작업 타입이다. 없을 수도 있다.
    @Column(name = "task_type", length = 30)
    private String taskType;

    // current ER 결과가 있으면 함께 저장한다.
    @Column(name = "current_er_value")
    private Double currentErValue;

    @Column(name = "current_er_unit", length = 20)
    private String currentErUnit;

    @Column(name = "current_er_status", length = 30)
    private String currentErStatus;

    // 사용자가 최종적으로 채택한 검증 결과인지 표시한다.
    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;

    // AI 호출 실패나 예외 상황의 원인을 남긴다.
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "condition_a_json", columnDefinition = "TEXT")
    private String conditionAJson;

    @Column(name = "condition_b_json", columnDefinition = "TEXT")
    private String conditionBJson;

    // 예측 파이프라인 결과를 저장한다.
    @Column(name = "prediction_request_id", length = 36)
    private String predictionRequestId;

    @Column(name = "prediction_process_type", length = 30)
    private String predictionProcessType;

    @Column(name = "ion_flux_value")
    private Double ionFluxValue;

    @Column(name = "ion_flux_unit", length = 30)
    private String ionFluxUnit;

    @Column(name = "ion_energy_value")
    private Double ionEnergyValue;

    @Column(name = "ion_energy_unit", length = 30)
    private String ionEnergyUnit;

    @Column(name = "etch_score_value")
    private Double etchScoreValue;

    @Column(name = "etch_score_unit", length = 30)
    private String etchScoreUnit;

    @Column(name = "prediction_explanation_summary", columnDefinition = "TEXT")
    private String predictionExplanationSummary;

    @Column(name = "prediction_explanation_details_json", columnDefinition = "TEXT")
    private String predictionExplanationDetailsJson;

    @Column(name = "prediction_error", columnDefinition = "TEXT")
    private String predictionError;

    // 이 검증 결과가 저장된 시각이다.
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    // pressure, source_power, bias_power 같은 개별 파라미터 행들이다.
    private List<MessageValidationParam> items = new ArrayList<>();

    protected MessageValidationSnapshot() {
    }

    private MessageValidationSnapshot(ChatMessage message,
                                      String requestId,
                                      int attemptNo,
                                      String sourceType,
                                      String validationStatus,
                                      String processType,
                                      String taskType,
                                      Double currentErValue,
                                      String currentErUnit,
                                      String currentErStatus,
                                      String failureReason,
                                      LocalDateTime createdAt) {
        this.message = message;
        this.requestId = requestId;
        this.attemptNo = attemptNo;
        this.sourceType = sourceType;
        this.validationStatus = validationStatus;
        this.processType = processType;
        this.taskType = taskType;
        this.currentErValue = currentErValue;
        this.currentErUnit = currentErUnit;
        this.currentErStatus = currentErStatus;
        this.confirmed = false;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
    }

    public static MessageValidationSnapshot create(ChatMessage message,
                                                   String requestId,
                                                   int attemptNo,
                                                   String sourceType,
                                                   String validationStatus,
                                                   String processType,
                                                   String taskType,
                                                   Double currentErValue,
                                                   String currentErUnit,
                                                   String currentErStatus,
                                                   String failureReason,
                                                   LocalDateTime createdAt) {
        return new MessageValidationSnapshot(
                message,
                requestId,
                attemptNo,
                sourceType,
                validationStatus,
                processType,
                taskType,
                currentErValue,
                currentErUnit,
                currentErStatus,
                failureReason,
                createdAt
        );
    }

    public void addItem(MessageValidationParam item) {
        item.attachTo(this);
        this.items.add(item);
    }

    public void markConfirmed() {
        this.confirmed = true;
    }

    public void clearConfirmed() {
        this.confirmed = false;
    }

    // "검증 전체 상태"도 VALID이고 "개별 파라미터 상태"도 모두 VALID일 때만 true다.
    public boolean isAllValid() {
        return "VALID".equals(this.validationStatus)
                && !this.items.isEmpty()
                && this.items.stream().allMatch(item -> "VALID".equals(item.getParameterStatus()));
    }

    public Long getValidationId() {
        return validationId;
    }

    public ChatMessage getMessage() {
        return message;
    }

    public String getRequestId() {
        return requestId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public String getProcessType() {
        return processType;
    }

    public String getTaskType() {
        return taskType;
    }

    public Double getCurrentErValue() {
        return currentErValue;
    }

    public String getCurrentErUnit() {
        return currentErUnit;
    }

    public String getCurrentErStatus() {
        return currentErStatus;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void storeAssistantSummary(String summary) {
        this.predictionExplanationSummary = summary;
    }

    public void storeComparisonConditions(String conditionAJson, String conditionBJson) {
        this.conditionAJson = conditionAJson;
        this.conditionBJson = conditionBJson;
    }

    public String getConditionAJson() {
        return conditionAJson;
    }

    public String getConditionBJson() {
        return conditionBJson;
    }

    public void storePrediction(String predictionRequestId,
                                String predictionProcessType,
                                Double ionFluxValue,
                                String ionFluxUnit,
                                Double ionEnergyValue,
                                String ionEnergyUnit,
                                Double etchScoreValue,
                                String etchScoreUnit,
                                String predictionExplanationSummary,
                                String predictionExplanationDetailsJson,
                                String predictionError) {
        this.predictionRequestId = predictionRequestId;
        this.predictionProcessType = predictionProcessType;
        this.ionFluxValue = ionFluxValue;
        this.ionFluxUnit = ionFluxUnit;
        this.ionEnergyValue = ionEnergyValue;
        this.ionEnergyUnit = ionEnergyUnit;
        this.etchScoreValue = etchScoreValue;
        this.etchScoreUnit = etchScoreUnit;
        this.predictionExplanationSummary = predictionExplanationSummary;
        this.predictionExplanationDetailsJson = predictionExplanationDetailsJson;
        this.predictionError = predictionError;
    }

    public String getPredictionRequestId() {
        return predictionRequestId;
    }

    public String getPredictionProcessType() {
        return predictionProcessType;
    }

    public Double getIonFluxValue() {
        return ionFluxValue;
    }

    public String getIonFluxUnit() {
        return ionFluxUnit;
    }

    public Double getIonEnergyValue() {
        return ionEnergyValue;
    }

    public String getIonEnergyUnit() {
        return ionEnergyUnit;
    }

    public Double getEtchScoreValue() {
        return etchScoreValue;
    }

    public String getEtchScoreUnit() {
        return etchScoreUnit;
    }

    public String getPredictionExplanationSummary() {
        return predictionExplanationSummary;
    }

    public String getPredictionExplanationDetailsJson() {
        return predictionExplanationDetailsJson;
    }

    public String getPredictionError() {
        return predictionError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<MessageValidationParam> getItems() {
        // 화면에는 항상 정해진 순서로 보이도록 displayOrder 기준으로 정렬해서 반환한다.
        return items.stream()
                .sorted(Comparator.comparingInt(MessageValidationParam::getDisplayOrder))
                .toList();
    }
}
