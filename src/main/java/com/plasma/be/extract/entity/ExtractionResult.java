package com.plasma.be.extract.entity;

import com.plasma.be.chat.entity.ChatMessage;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "extraction_result")
public class ExtractionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "extraction_result_id")
    private Long extractionResultId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage chatMessage;

    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(name = "task_type", length = 30)
    private String taskType;

    @Column(name = "process_type", length = 30)
    private String processType;

    @ElementCollection
    @CollectionTable(
            name = "extraction_params",
            joinColumns = @JoinColumn(name = "extraction_result_id")
    )
    @MapKeyColumn(name = "param_name")
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "param_value")),
            @AttributeOverride(name = "unit",  column = @Column(name = "param_unit",   length = 20)),
            @AttributeOverride(name = "status", column = @Column(name = "param_status", length = 20))
    })
    private Map<String, ProcessParameter> processParams = new HashMap<>();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value",  column = @Column(name = "current_er_value")),
            @AttributeOverride(name = "unit",   column = @Column(name = "current_er_unit",   length = 20)),
            @AttributeOverride(name = "status", column = @Column(name = "current_er_status", length = 20))
    })
    private ProcessParameter currentEr;

    @Column(name = "extraction_status", length = 20)
    private String extractionStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected ExtractionResult() {
    }

    private ExtractionResult(ChatMessage chatMessage, String requestId, String taskType, String processType) {
        this.chatMessage = chatMessage;
        this.requestId = requestId;
        this.taskType = taskType;
        this.processType = processType;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // 채팅 메시지 기반의 초기 추출 결과 엔티티를 생성한다.
    public static ExtractionResult createExtractionResult(ChatMessage chatMessage, String requestId,
                                                          String taskType, String processType) {
        return new ExtractionResult(chatMessage, requestId, taskType, processType);
    }

    // 추출된 공정 파라미터를 이름 기준으로 저장한다.
    public void addProcessParam(String key, ProcessParameter param) {
        this.processParams.put(key, param);
    }

    // 현재 etch rate 값을 추출 결과에 반영한다.
    public void setCurrentEr(ProcessParameter param) {
        this.currentEr = param;
        this.updatedAt = LocalDateTime.now();
    }

    // 전체 추출 상태를 최신 값으로 갱신한다.
    public void updateStatus(String status) {
        this.extractionStatus = status;
        this.updatedAt = LocalDateTime.now();
    }

    public ProcessParameter getProcessParam(String key) {
        return processParams.get(key);
    }

    public Map<String, ProcessParameter> getAllParams() {
        return Map.copyOf(processParams);
    }

    public Long getExtractionResultId() {
        return extractionResultId;
    }

    public ChatMessage getChatMessage() {
        return chatMessage;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getProcessType() {
        return processType;
    }

    public Map<String, ProcessParameter> getProcessParams() {
        return processParams;
    }

    public ProcessParameter getCurrentEr() {
        return currentEr;
    }

    public String getExtractionStatus() {
        return extractionStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
