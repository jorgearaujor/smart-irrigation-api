package com.smartirrigation.smartirrigation.model;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.Exclude;

public class Device {

    @Exclude
    private String deviceId;

    private String modo = "manual";
    private Boolean autorizado = false;
    private Double quantidadeAgua = 0.0;
    private Double tempMin;
    private Double tempMax;
    private Boolean emExecucao = false;
    private Double latitude = 0.0;
    private ConfigIrrigacao configIrrigacao = new ConfigIrrigacao();
    private Timestamp updatedAt;

    @Exclude
    public String getDeviceId() {
        return deviceId;
    }

    @Exclude
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getModo() {
        return modo;
    }

    public void setModo(String modo) {
        this.modo = modo;
    }

    public Boolean getAutorizado() {
        return autorizado;
    }

    public void setAutorizado(Boolean autorizado) {
        this.autorizado = autorizado;
    }

    public Double getQuantidadeAgua() {
        return quantidadeAgua;
    }

    public void setQuantidadeAgua(Double quantidadeAgua) {
        this.quantidadeAgua = quantidadeAgua;
    }

    public Double getTempMin() {
        return tempMin;
    }

    public void setTempMin(Double tempMin) {
        this.tempMin = tempMin;
    }

    public Double getTempMax() {
        return tempMax;
    }

    public void setTempMax(Double tempMax) {
        this.tempMax = tempMax;
    }

    public Boolean getEmExecucao() {
        return emExecucao;
    }

    public void setEmExecucao(Boolean emExecucao) {
        this.emExecucao = emExecucao;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public ConfigIrrigacao getConfigIrrigacao() {
        return configIrrigacao;
    }

    public void setConfigIrrigacao(ConfigIrrigacao configIrrigacao) {
        this.configIrrigacao = configIrrigacao;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
