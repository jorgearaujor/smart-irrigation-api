package com.smartirrigation.smartirrigation.model;

import com.google.cloud.firestore.annotation.PropertyName;

public class ConfigIrrigacao {

    private Double kc = 1.40;
    private String cultura = "";
    private Integer faseCrescimento = 1;
    private Double espacamentoPlantasM = 0.3;
    private Double espacamentoLinhasM = 0.3;
    private Double vazaoGotejadorLph = 2.0;
    private Integer numGotejadoresPlanta = 1;
    private Integer numeroTotalPlantas = 30;
    private Double limiarUmidadeMinima = 50.0;

    public Double getKc() {
        return kc;
    }

    public void setKc(Double kc) {
        this.kc = kc;
    }

    public String getCultura() {
        return cultura;
    }

    public void setCultura(String cultura) {
        this.cultura = cultura;
    }

    public Integer getFaseCrescimento() {
        return faseCrescimento;
    }

    public void setFaseCrescimento(Integer faseCrescimento) {
        this.faseCrescimento = faseCrescimento;
    }

    @PropertyName("espacamento_plantas_m")
    public Double getEspacamentoPlantasM() {
        return espacamentoPlantasM;
    }

    @PropertyName("espacamento_plantas_m")
    public void setEspacamentoPlantasM(Double espacamentoPlantasM) {
        this.espacamentoPlantasM = espacamentoPlantasM;
    }

    @PropertyName("espacamento_linhas_m")
    public Double getEspacamentoLinhasM() {
        return espacamentoLinhasM;
    }

    @PropertyName("espacamento_linhas_m")
    public void setEspacamentoLinhasM(Double espacamentoLinhasM) {
        this.espacamentoLinhasM = espacamentoLinhasM;
    }

    @PropertyName("vazao_gotejador_lph")
    public Double getVazaoGotejadorLph() {
        return vazaoGotejadorLph;
    }

    @PropertyName("vazao_gotejador_lph")
    public void setVazaoGotejadorLph(Double vazaoGotejadorLph) {
        this.vazaoGotejadorLph = vazaoGotejadorLph;
    }

    @PropertyName("num_gotejadores_planta")
    public Integer getNumGotejadoresPlanta() {
        return numGotejadoresPlanta;
    }

    @PropertyName("num_gotejadores_planta")
    public void setNumGotejadoresPlanta(Integer numGotejadoresPlanta) {
        this.numGotejadoresPlanta = numGotejadoresPlanta;
    }

    @PropertyName("numero_total_plantas")
    public Integer getNumeroTotalPlantas() {
        return numeroTotalPlantas;
    }

    @PropertyName("numero_total_plantas")
    public void setNumeroTotalPlantas(Integer numeroTotalPlantas) {
        this.numeroTotalPlantas = numeroTotalPlantas;
    }

    @PropertyName("limiar_umidade_minima")
    public Double getLimiarUmidadeMinima() {
        return limiarUmidadeMinima;
    }

    @PropertyName("limiar_umidade_minima")
    public void setLimiarUmidadeMinima(Double limiarUmidadeMinima) {
        this.limiarUmidadeMinima = limiarUmidadeMinima;
    }
}
