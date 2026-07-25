package com.smartirrigation.smartirrigation.dto;

public class DeviceStatusResponseDTO {
    private String modo;
    private Boolean autorizado;
    private Double quantidadeAgua;
    private Double tempMin;
    private Double tempMax;
    private Boolean emExecucao;
    private Double latitude;

    public String getModo() { return modo; }
    public void setModo(String modo) { this.modo = modo; }
    public Boolean getAutorizado() { return autorizado; }
    public void setAutorizado(Boolean autorizado) { this.autorizado = autorizado; }
    public Double getQuantidadeAgua() { return quantidadeAgua; }
    public void setQuantidadeAgua(Double quantidadeAgua) { this.quantidadeAgua = quantidadeAgua; }
    public Double getTempMin() { return tempMin; }
    public void setTempMin(Double tempMin) { this.tempMin = tempMin; }
    public Double getTempMax() { return tempMax; }
    public void setTempMax(Double tempMax) { this.tempMax = tempMax; }
    public Boolean getEmExecucao() { return emExecucao; }
    public void setEmExecucao(Boolean emExecucao) { this.emExecucao = emExecucao; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
}
