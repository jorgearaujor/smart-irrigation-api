package com.smartirrigation.smartirrigation.model;

import com.google.cloud.Timestamp;

public class Telemetry {

    private Double temperature;
    private Double humidity;
    private Double soilPercent;
    private Long timestamp;
    private Timestamp recordedAt;

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getHumidity() {
        return humidity;
    }

    public void setHumidity(Double humidity) {
        this.humidity = humidity;
    }

    public Double getSoilPercent() {
        return soilPercent;
    }

    public void setSoilPercent(Double soilPercent) {
        this.soilPercent = soilPercent;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Timestamp getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Timestamp recordedAt) {
        this.recordedAt = recordedAt;
    }
}
