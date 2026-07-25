package com.smartirrigation.smartirrigation.dto;

public class DeviceConfigResponseDTO {
    private String modo;
    private Boolean autorizado;
    private Double quantidadeAgua;
    private Double latitude;
    private ConfigIrrigacaoDTO configIrrigacao;

    public String getModo() { return modo; }
    public void setModo(String modo) { this.modo = modo; }
    public Boolean getAutorizado() { return autorizado; }
    public void setAutorizado(Boolean autorizado) { this.autorizado = autorizado; }
    public Double getQuantidadeAgua() { return quantidadeAgua; }
    public void setQuantidadeAgua(Double quantidadeAgua) { this.quantidadeAgua = quantidadeAgua; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public ConfigIrrigacaoDTO getConfigIrrigacao() { return configIrrigacao; }
    public void setConfigIrrigacao(ConfigIrrigacaoDTO configIrrigacao) { this.configIrrigacao = configIrrigacao; }
}
