package com.smartirrigation.smartirrigation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ConfigIrrigacaoDTO {
    private Double kc;
    @JsonProperty("espacamento_plantas_m")
    private Double espacamentoPlantasM;
    @JsonProperty("espacamento_linhas_m")
    private Double espacamentoLinhasM;
    @JsonProperty("vazao_gotejador_lph")
    private Double vazaoGotejadorLph;
    @JsonProperty("num_gotejadores_planta")
    private Integer numGotejadoresPlanta;
    @JsonProperty("numero_total_plantas")
    private Integer numeroTotalPlantas;
    @JsonProperty("limiar_umidade_minima")
    private Double limiarUmidadeMinima;

    public Double getKc() { return kc; }
    public void setKc(Double kc) { this.kc = kc; }
    public Double getEspacamentoPlantasM() { return espacamentoPlantasM; }
    public void setEspacamentoPlantasM(Double espacamentoPlantasM) { this.espacamentoPlantasM = espacamentoPlantasM; }
    public Double getEspacamentoLinhasM() { return espacamentoLinhasM; }
    public void setEspacamentoLinhasM(Double espacamentoLinhasM) { this.espacamentoLinhasM = espacamentoLinhasM; }
    public Double getVazaoGotejadorLph() { return vazaoGotejadorLph; }
    public void setVazaoGotejadorLph(Double vazaoGotejadorLph) { this.vazaoGotejadorLph = vazaoGotejadorLph; }
    public Integer getNumGotejadoresPlanta() { return numGotejadoresPlanta; }
    public void setNumGotejadoresPlanta(Integer numGotejadoresPlanta) { this.numGotejadoresPlanta = numGotejadoresPlanta; }
    public Integer getNumeroTotalPlantas() { return numeroTotalPlantas; }
    public void setNumeroTotalPlantas(Integer numeroTotalPlantas) { this.numeroTotalPlantas = numeroTotalPlantas; }
    public Double getLimiarUmidadeMinima() { return limiarUmidadeMinima; }
    public void setLimiarUmidadeMinima(Double limiarUmidadeMinima) { this.limiarUmidadeMinima = limiarUmidadeMinima; }
}
