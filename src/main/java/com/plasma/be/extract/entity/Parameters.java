package com.plasma.be.extract.entity;

import com.plasma.be.chat.entity.ChatMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "parameters")
public class Parameters {

    @Id
    @Column(name = "request_id", length = 36)
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage chatMessage;

    @Column(name = "pressure_mtorr")
    private Double pressureMtorr;

    @Column(name = "bias_power_w")
    private Double biasPowerW;

    @Column(name = "source_power_w")
    private Double sourcePowerW;

    @Column(name = "current_er")
    private Double currentEr;

    protected Parameters() {
    }

    private Parameters(String requestId, ChatMessage chatMessage,
                       Double pressureMtorr, Double sourcePowerW,
                       Double biasPowerW, Double currentEr) {
        this.requestId = requestId;
        this.chatMessage = chatMessage;
        this.pressureMtorr = pressureMtorr;
        this.sourcePowerW = sourcePowerW;
        this.biasPowerW = biasPowerW;
        this.currentEr = currentEr;
    }

    // AI 추출 결과를 기반으로 Parameters 엔티티를 생성한다.
    public static Parameters create(String requestId, ChatMessage chatMessage,
                                    Double pressureMtorr, Double sourcePowerW,
                                    Double biasPowerW, Double currentEr) {
        return new Parameters(requestId, chatMessage, pressureMtorr, sourcePowerW, biasPowerW, currentEr);
    }

    public String getRequestId() {
        return requestId;
    }

    public ChatMessage getChatMessage() {
        return chatMessage;
    }

    public Double getPressureMtorr() {
        return pressureMtorr;
    }

    public Double getBiasPowerW() {
        return biasPowerW;
    }

    public Double getSourcePowerW() {
        return sourcePowerW;
    }

    public Double getCurrentEr() {
        return currentEr;
    }
}
