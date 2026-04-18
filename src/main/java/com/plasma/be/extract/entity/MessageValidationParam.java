package com.plasma.be.extract.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "message_validation_param")
// 검증 카드 안에 들어가는 "파라미터 한 줄"이다.
// 예: pressure 한 줄, source_power 한 줄, bias_power 한 줄
public class MessageValidationParam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 개별 파라미터 행의 고유 ID다.
    @Column(name = "validation_param_id")
    private Long validationParamId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "validation_id", nullable = false)
    // 이 파라미터가 어느 검증 결과(snapshot)에 속하는지 가리킨다.
    private MessageValidationSnapshot snapshot;

    // 내부적으로 파라미터를 구분하는 키다. 예: pressure, source_power
    @Column(name = "parameter_key", nullable = false, length = 50)
    private String parameterKey;

    // 화면에 보여줄 사람이 읽는 이름이다. 예: Pressure, Source Power
    @Column(name = "parameter_label", nullable = false, length = 80)
    private String parameterLabel;

    // 실제 값이다. AI가 못 읽었거나 비어 있으면 null일 수 있다.
    @Column(name = "parameter_value")
    private Double parameterValue;

    // 단위다. 예: mTorr, W
    @Column(name = "parameter_unit", length = 20)
    private String parameterUnit;

    // 이 값의 상태다. 예: VALID, MISSING, UNCONFIRMED, AI_ERROR
    @Column(name = "parameter_status", nullable = false, length = 30)
    private String parameterStatus;

    // 화면에서 pressure -> source_power -> bias_power 순으로 보여주기 위한 순서값이다.
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected MessageValidationParam() {
    }

    private MessageValidationParam(String parameterKey,
                                   String parameterLabel,
                                   Double parameterValue,
                                   String parameterUnit,
                                   String parameterStatus,
                                   int displayOrder) {
        this.parameterKey = parameterKey;
        this.parameterLabel = parameterLabel;
        this.parameterValue = parameterValue;
        this.parameterUnit = parameterUnit;
        this.parameterStatus = parameterStatus;
        this.displayOrder = displayOrder;
    }

    public static MessageValidationParam create(String parameterKey,
                                                String parameterLabel,
                                                Double parameterValue,
                                                String parameterUnit,
                                                String parameterStatus,
                                                int displayOrder) {
        return new MessageValidationParam(
                parameterKey,
                parameterLabel,
                parameterValue,
                parameterUnit,
                parameterStatus,
                displayOrder
        );
    }

    void attachTo(MessageValidationSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public Long getValidationParamId() {
        return validationParamId;
    }

    public String getParameterKey() {
        return parameterKey;
    }

    public String getParameterLabel() {
        return parameterLabel;
    }

    public Double getParameterValue() {
        return parameterValue;
    }

    public String getParameterUnit() {
        return parameterUnit;
    }

    public String getParameterStatus() {
        return parameterStatus;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
