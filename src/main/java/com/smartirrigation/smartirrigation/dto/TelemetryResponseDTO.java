package com.smartirrigation.smartirrigation.dto;

public class TelemetryResponseDTO {
    private Double temperature;
    private Double humidity;
    private Double soilPercent;
    private Long timestamp;

    public TelemetryResponseDTO() {
    }

    public TelemetryResponseDTO(Double temperature, Double humidity, Double soilPercent, Long timestamp) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.soilPercent = soilPercent;
        this.timestamp = timestamp;
    }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Double getHumidity() { return humidity; }
    public void setHumidity(Double humidity) { this.humidity = humidity; }
    public Double getSoilPercent() { return soilPercent; }
    public void setSoilPercent(Double soilPercent) { this.soilPercent = soilPercent; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
