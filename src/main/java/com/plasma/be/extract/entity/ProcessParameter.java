package com.plasma.be.extract.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProcessParameter {

    @Column(name = "value")
    private Double value;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "status", length = 20)
    private String status;

    protected ProcessParameter() {
    }

    private ProcessParameter(Double value, String unit, String status) {
        this.value = value;
        this.unit = unit;
        this.status = status;
    }

    public static ProcessParameter createParameter(Double value, String unit, String status) {
        return new ProcessParameter(value, unit, status);
    }

    public void updateValue(Double value) {
        this.value = value;
    }

    public void updateUnit(String unit) {
        this.unit = unit;
    }

    public void updateStatus(String status) {
        this.status = status;
    }

    public boolean isValid() {
        return "VALID".equals(this.status);
    }

    public Double getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public String getStatus() {
        return status;
    }
}
