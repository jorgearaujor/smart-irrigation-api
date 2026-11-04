package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.*;
import com.smartirrigation.smartirrigation.exception.ConflictException;
import com.smartirrigation.smartirrigation.exception.NotFoundException;
import com.smartirrigation.smartirrigation.model.Device;
import com.smartirrigation.smartirrigation.model.IrrigationHistoryRecord;
import com.smartirrigation.smartirrigation.model.Telemetry;
import com.smartirrigation.smartirrigation.repository.DeviceRepository;
import com.smartirrigation.smartirrigation.repository.IrrigationHistoryRepository;
import com.smartirrigation.smartirrigation.repository.TelemetryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;
    private final IrrigationHistoryRepository irrigationHistoryRepository;
    private final CommandDispatchService commandDispatchService;

    public DeviceService(DeviceRepository deviceRepository, TelemetryRepository telemetryRepository,
                          IrrigationHistoryRepository irrigationHistoryRepository,
                          CommandDispatchService commandDispatchService) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
        this.irrigationHistoryRepository = irrigationHistoryRepository;
        this.commandDispatchService = commandDispatchService;
    }

    public void recordTelemetry(String deviceId, TelemetryRequestDTO dto) {
        Telemetry telemetry = new Telemetry();
        telemetry.setTemperature(dto.getTemperature());
        telemetry.setHumidity(dto.getHumidity());
        telemetry.setSoilPercent(dto.getSoilPercent());
        telemetry.setTimestamp(dto.getTimestamp());
        telemetryRepository.add(deviceId, telemetry);
    }

    public void updateStatus(String deviceId, StatusRequestDTO dto) {
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

    public void recordHistory(String deviceId, HistoryRequestDTO dto) {
        IrrigationHistoryRecord record = new IrrigationHistoryRecord();
        record.setUmidadeAtual(dto.getUmidadeAtual());
        record.setTempo(dto.getTempo());
        record.setVolumePorPlanta(dto.getVolumePorPlanta());
        record.setVolumeTotal(dto.getVolumeTotal());
        record.setData(dto.getData());
        record.setHora(dto.getHora());
        record.setModo(dto.getModo());
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

    public List<TelemetryResponseDTO> getTelemetryHistory(String deviceId, Instant from, Instant to, int limit) {
        List<Telemetry> readings = telemetryRepository.findBetween(deviceId, from, to, limit);
        return readings.stream()
                .map(t -> new TelemetryResponseDTO(t.getTemperature(), t.getHumidity(), t.getSoilPercent(), t.getTimestamp()))
                .collect(Collectors.toList());
    }

    public List<IrrigationHistoryResponseDTO> getIrrigationHistory(String deviceId, Instant from, Instant to) {
        List<IrrigationHistoryRecord> records = irrigationHistoryRepository.findBetween(deviceId, from, to);
        return records.stream().map(r -> {
            IrrigationHistoryResponseDTO dto = new IrrigationHistoryResponseDTO();
            dto.setUmidadeAtual(r.getUmidadeAtual());
            dto.setTempo(r.getTempo());
            dto.setVolumePorPlanta(r.getVolumePorPlanta());
            dto.setVolumeTotal(r.getVolumeTotal());
            dto.setData(r.getData());
            dto.setHora(r.getHora());
            dto.setModo(r.getModo());
            return dto;
        }).collect(Collectors.toList());
    }

    public void startIrrigation(String deviceId, Double quantidadeAgua) {
        Device device = findDevice(deviceId);
        device.setQuantidadeAgua(quantidadeAgua);
        device.setEmExecucao(true);
        deviceRepository.save(deviceId, device);

        Map<String, Object> payload = new HashMap<>();
        payload.put("quantidade_agua", quantidadeAgua);
        commandDispatchService.enqueue(deviceId, new CommandDispatchService.Command("START_IRRIGATION", payload));
    }

    public void stopIrrigation(String deviceId) {
        Device device = findDevice(deviceId);
        device.setEmExecucao(false);
        deviceRepository.save(deviceId, device);

        commandDispatchService.enqueue(deviceId, new CommandDispatchService.Command("STOP_IRRIGATION", Map.of()));
    }

    public void setModo(String deviceId, String modo) {
        Device device = findDevice(deviceId);
        if (Boolean.TRUE.equals(device.getEmExecucao())) {
            throw new ConflictException("Nao e possivel trocar o modo enquanto o dispositivo esta irrigando.");
        }
        device.setModo(modo);
        deviceRepository.save(deviceId, device);

        commandDispatchService.enqueue(deviceId, new CommandDispatchService.Command("MODE_CHANGE", Map.of("modo", modo)));
    }

    public void setAutorizado(String deviceId, Boolean autorizado) {
        Device device = findDevice(deviceId);
        device.setAutorizado(autorizado);
        deviceRepository.save(deviceId, device);

        commandDispatchService.enqueue(deviceId,
                new CommandDispatchService.Command("AUTHORIZATION_CHANGE", Map.of("autorizado", autorizado)));
    }

    public void setLatitude(String deviceId, Double latitude) {
        Device device = findDevice(deviceId);
        device.setLatitude(latitude);
        deviceRepository.save(deviceId, device);

        commandDispatchService.enqueue(deviceId,
                new CommandDispatchService.Command("LATITUDE", Map.of("latitude", latitude)));
    }

    private Device findDevice(String deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("Device nao encontrado: " + deviceId));
    }
}
