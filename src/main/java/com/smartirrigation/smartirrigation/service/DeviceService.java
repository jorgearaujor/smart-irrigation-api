package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.*;
import com.smartirrigation.smartirrigation.exception.NotFoundException;
import com.smartirrigation.smartirrigation.model.Device;
import com.smartirrigation.smartirrigation.model.IrrigationHistoryRecord;
import com.smartirrigation.smartirrigation.model.Telemetry;
import com.smartirrigation.smartirrigation.repository.DeviceRepository;
import com.smartirrigation.smartirrigation.repository.IrrigationHistoryRepository;
import com.smartirrigation.smartirrigation.repository.TelemetryRepository;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;
    private final IrrigationHistoryRepository irrigationHistoryRepository;

    public DeviceService(DeviceRepository deviceRepository, TelemetryRepository telemetryRepository,
                          IrrigationHistoryRepository irrigationHistoryRepository) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
        this.irrigationHistoryRepository = irrigationHistoryRepository;
    }

    public void recordTelemetry(String deviceId, TelemetryWebhookRequestDTO dto) {
        Telemetry telemetry = new Telemetry();
        telemetry.setTemperature(dto.getTemperature());
        telemetry.setHumidity(dto.getHumidity());
        telemetry.setSoilPercent(dto.getSoilPercent());
        telemetry.setTimestamp(dto.getTimestamp());
        telemetryRepository.add(deviceId, telemetry);
    }

    public void updateStatus(String deviceId, StatusWebhookRequestDTO dto) {
        Device device = deviceRepository.findById(deviceId).orElseGet(Device::new);
        device.setModo(dto.getModo());
        device.setAutorizado(dto.getAutorizado());
        device.setQuantidadeAgua(dto.getQuantidadeAgua());
        device.setTempMin(dto.getTempMin());
        device.setTempMax(dto.getTempMax());
        device.setEmExecucao(dto.getEmExecucao());
        device.setLatitude(dto.getLatitude());
        deviceRepository.save(deviceId, device);
    }

    public void recordHistory(String deviceId, HistoryWebhookRequestDTO dto) {
        IrrigationHistoryRecord record = new IrrigationHistoryRecord();
        record.setUmidadeAtual(dto.getUmidadeAtual());
        record.setTempo(dto.getTempo());
        record.setVolumePorPlanta(dto.getVolumePorPlanta());
        record.setVolumeTotal(dto.getVolumeTotal());
        record.setData(dto.getData());
        record.setHora(dto.getHora());
        irrigationHistoryRepository.add(deviceId, record);
    }

    public DeviceConfigResponseDTO getConfig(String deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("Device nao encontrado: " + deviceId));

        DeviceConfigResponseDTO response = new DeviceConfigResponseDTO();
        response.setModo(device.getModo());
        response.setAutorizado(device.getAutorizado());
        response.setQuantidadeAgua(device.getQuantidadeAgua());
        response.setLatitude(device.getLatitude());

        ConfigIrrigacaoDTO configDto = new ConfigIrrigacaoDTO();
        configDto.setKc(device.getConfigIrrigacao().getKc());
        configDto.setEspacamentoPlantasM(device.getConfigIrrigacao().getEspacamentoPlantasM());
        configDto.setEspacamentoLinhasM(device.getConfigIrrigacao().getEspacamentoLinhasM());
        configDto.setVazaoGotejadorLph(device.getConfigIrrigacao().getVazaoGotejadorLph());
        configDto.setNumGotejadoresPlanta(device.getConfigIrrigacao().getNumGotejadoresPlanta());
        configDto.setNumeroTotalPlantas(device.getConfigIrrigacao().getNumeroTotalPlantas());
        configDto.setLimiarUmidadeMinima(device.getConfigIrrigacao().getLimiarUmidadeMinima());
        response.setConfigIrrigacao(configDto);

        return response;
    }

    public DeviceStatusResponseDTO getStatus(String deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("Device nao encontrado: " + deviceId));

        DeviceStatusResponseDTO response = new DeviceStatusResponseDTO();
        response.setModo(device.getModo());
        response.setAutorizado(device.getAutorizado());
        response.setQuantidadeAgua(device.getQuantidadeAgua());
        response.setTempMin(device.getTempMin());
        response.setTempMax(device.getTempMax());
        response.setEmExecucao(device.getEmExecucao());
        response.setLatitude(device.getLatitude());

        return response;
    }
}
