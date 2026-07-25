package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.ConfigIrrigacaoDTO;
import com.smartirrigation.smartirrigation.exception.NotFoundException;
import com.smartirrigation.smartirrigation.model.ConfigIrrigacao;
import com.smartirrigation.smartirrigation.model.Device;
import com.smartirrigation.smartirrigation.repository.DeviceRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ConfigIrrigacaoService {

    private final DeviceRepository deviceRepository;
    private final CommandDispatchService commandDispatchService;

    public ConfigIrrigacaoService(DeviceRepository deviceRepository, CommandDispatchService commandDispatchService) {
        this.deviceRepository = deviceRepository;
        this.commandDispatchService = commandDispatchService;
    }

    public ConfigIrrigacaoDTO getConfig(String deviceId) {
        Device device = findDevice(deviceId);
        ConfigIrrigacao config = device.getConfigIrrigacao();

        ConfigIrrigacaoDTO dto = new ConfigIrrigacaoDTO();
        dto.setKc(config.getKc());
        dto.setEspacamentoPlantasM(config.getEspacamentoPlantasM());
        dto.setEspacamentoLinhasM(config.getEspacamentoLinhasM());
        dto.setVazaoGotejadorLph(config.getVazaoGotejadorLph());
        dto.setNumGotejadoresPlanta(config.getNumGotejadoresPlanta());
        dto.setNumeroTotalPlantas(config.getNumeroTotalPlantas());
        dto.setLimiarUmidadeMinima(config.getLimiarUmidadeMinima());
        return dto;
    }

    public void updateConfig(String deviceId, ConfigIrrigacaoDTO dto) {
        Device device = findDevice(deviceId);
        ConfigIrrigacao config = device.getConfigIrrigacao();

        config.setKc(dto.getKc());
        config.setEspacamentoPlantasM(dto.getEspacamentoPlantasM());
        config.setEspacamentoLinhasM(dto.getEspacamentoLinhasM());
        config.setVazaoGotejadorLph(dto.getVazaoGotejadorLph());
        config.setNumGotejadoresPlanta(dto.getNumGotejadoresPlanta());
        config.setNumeroTotalPlantas(dto.getNumeroTotalPlantas());
        config.setLimiarUmidadeMinima(dto.getLimiarUmidadeMinima());

        deviceRepository.save(deviceId, device);

        Map<String, Object> payload = new HashMap<>();
        payload.put("kc", dto.getKc());
        payload.put("espacamento_plantas_m", dto.getEspacamentoPlantasM());
        payload.put("espacamento_linhas_m", dto.getEspacamentoLinhasM());
        payload.put("vazao_gotejador_lph", dto.getVazaoGotejadorLph());
        payload.put("num_gotejadores_planta", dto.getNumGotejadoresPlanta());
        payload.put("numero_total_plantas", dto.getNumeroTotalPlantas());
        payload.put("limiar_umidade_minima", dto.getLimiarUmidadeMinima());

        commandDispatchService.enqueue(deviceId, new CommandDispatchService.Command("CONFIG_IRRIGACAO", payload));
    }

    private Device findDevice(String deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("Device nao encontrado: " + deviceId));
    }
}
