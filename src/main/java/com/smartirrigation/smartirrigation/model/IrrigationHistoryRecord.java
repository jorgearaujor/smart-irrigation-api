package com.smartirrigation.smartirrigation.model;

import com.google.cloud.Timestamp;

public class IrrigationHistoryRecord {

    private Double umidadeAtual;
    private String tempo;
    private Double volumePorPlanta;
    private Double volumeTotal;
    private String data;
    private String hora;
    private String modo;
    private Timestamp recordedAt;

    public Double getUmidadeAtual() {
        return umidadeAtual;
    }

    public void setUmidadeAtual(Double umidadeAtual) {
        this.umidadeAtual = umidadeAtual;
    }

    public String getTempo() {
        return tempo;
    }

    public void setTempo(String tempo) {
        this.tempo = tempo;
    }

    public Double getVolumePorPlanta() {
        return volumePorPlanta;
    }

    public void setVolumePorPlanta(Double volumePorPlanta) {
        this.volumePorPlanta = volumePorPlanta;
    }

    public Double getVolumeTotal() {
        return volumeTotal;
    }

    public void setVolumeTotal(Double volumeTotal) {
        this.volumeTotal = volumeTotal;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getHora() {
        return hora;
    }

    public void setHora(String hora) {
        this.hora = hora;
    }

    public String getModo() {
        return modo;
    }

    public void setModo(String modo) {
        this.modo = modo;
    }

    public Timestamp getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Timestamp recordedAt) {
        this.recordedAt = recordedAt;
    }
}
