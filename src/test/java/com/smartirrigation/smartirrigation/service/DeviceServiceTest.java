package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.DeviceStatusResponseDTO;
import com.smartirrigation.smartirrigation.dto.HistoryRequestDTO;
import com.smartirrigation.smartirrigation.dto.StatusRequestDTO;
import com.smartirrigation.smartirrigation.dto.TelemetryRequestDTO;
import com.smartirrigation.smartirrigation.exception.ConflictException;
import com.smartirrigation.smartirrigation.exception.NotFoundException;
import com.smartirrigation.smartirrigation.model.Device;
import com.smartirrigation.smartirrigation.model.IrrigationHistoryRecord;
import com.smartirrigation.smartirrigation.model.Telemetry;
import com.smartirrigation.smartirrigation.repository.DeviceRepository;
import com.smartirrigation.smartirrigation.repository.IrrigationHistoryRepository;
import com.smartirrigation.smartirrigation.repository.TelemetryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DeviceServiceTest {

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final TelemetryRepository telemetryRepository = mock(TelemetryRepository.class);
    private final IrrigationHistoryRepository irrigationHistoryRepository = mock(IrrigationHistoryRepository.class);
    private final CommandDispatchService commandDispatchService = mock(CommandDispatchService.class);
    private final DeviceService service =
            new DeviceService(deviceRepository, telemetryRepository, irrigationHistoryRepository, commandDispatchService);

    @Test
    void recordTelemetry_savesTemperatureHumiditySoilPercent() {
        TelemetryRequestDTO dto = new TelemetryRequestDTO();
        dto.setDeviceId("esp32-01");
        dto.setTemperature(24.5);
        dto.setHumidity(60.0);
        dto.setSoilPercent(45.0);
        dto.setTimestamp(1_700_000_000L);

        service.recordTelemetry("esp32-01", dto);

        ArgumentCaptor<Telemetry> captor = ArgumentCaptor.forClass(Telemetry.class);
        verify(telemetryRepository).add(eq("esp32-01"), captor.capture());
        assertEquals(24.5, captor.getValue().getTemperature());
        assertEquals(45.0, captor.getValue().getSoilPercent());
    }

    @Test
    void recordHistory_savesModoAlongWithOtherFields() {
        HistoryRequestDTO dto = new HistoryRequestDTO();
        dto.setDeviceId("esp32-01");
        dto.setUmidadeAtual(42.0);
        dto.setTempo("2min 30s");
        dto.setVolumePorPlanta(1.5);
        dto.setVolumeTotal(45.0);
        dto.setData("28/07/2026");
        dto.setHora("19:00:00");
        dto.setModo("automatico");

        service.recordHistory("esp32-01", dto);

        ArgumentCaptor<IrrigationHistoryRecord> captor = ArgumentCaptor.forClass(IrrigationHistoryRecord.class);
        verify(irrigationHistoryRepository).add(eq("esp32-01"), captor.capture());
        assertEquals("automatico", captor.getValue().getModo());
        assertEquals(45.0, captor.getValue().getVolumeTotal());
    }

    @Test
    void updateStatus_upsertsDeviceFields() {
        when(deviceRepository.findById("esp32-01")).thenReturn(Optional.of(new Device()));

        StatusRequestDTO dto = new StatusRequestDTO();
        dto.setDeviceId("esp32-01");
        dto.setModo("automatico");
        dto.setAutorizado(true);
        dto.setQuantidadeAgua(5.0);
        dto.setTempMin(18.0);
        dto.setTempMax(30.0);
        dto.setEmExecucao(false);
        dto.setLatitude(-23.5);

        service.updateStatus("esp32-01", dto);

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(eq("esp32-01"), captor.capture());
        assertEquals("automatico", captor.getValue().getModo());
        assertEquals(true, captor.getValue().getAutorizado());
    }

    @Test
    void getStatus_mapsAllSevenFieldsFromDevice() {
        Device device = new Device();
        device.setModo("automatico");
        device.setAutorizado(true);
        device.setQuantidadeAgua(12.5);
        device.setTempMin(18.0);
        device.setTempMax(32.0);
        device.setEmExecucao(true);
        device.setLatitude(-23.55);

        when(deviceRepository.findById("esp32-01")).thenReturn(Optional.of(device));

        DeviceStatusResponseDTO status = service.getStatus("esp32-01");

        assertEquals("automatico", status.getModo());
        assertEquals(true, status.getAutorizado());
        assertEquals(12.5, status.getQuantidadeAgua());
        assertEquals(18.0, status.getTempMin());
        assertEquals(32.0, status.getTempMax());
        assertEquals(true, status.getEmExecucao());
        assertEquals(-23.55, status.getLatitude());
    }

    @Test
    void setModo_updatesDeviceAndEnqueuesCommand_whenNotIrrigating() {
        Device device = new Device();
        device.setEmExecucao(false);
        when(deviceRepository.findById("esp32-01")).thenReturn(Optional.of(device));

        service.setModo("esp32-01", "automatico");

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(eq("esp32-01"), captor.capture());
        assertEquals("automatico", captor.getValue().getModo());
        verify(commandDispatchService).enqueue(eq("esp32-01"), argThat(command ->
                command.type().equals("MODE_CHANGE") && command.payload().get("modo").equals("automatico")));
    }

    @Test
    void setModo_throwsConflict_whenDeviceIsIrrigating() {
        Device device = new Device();
        device.setEmExecucao(true);
        when(deviceRepository.findById("esp32-01")).thenReturn(Optional.of(device));

        assertThrows(ConflictException.class, () -> service.setModo("esp32-01", "automatico"));

        verify(deviceRepository, never()).save(any(), any());
        verify(commandDispatchService, never()).enqueue(any(), any());
    }

    @Test
    void getStatus_throwsNotFound_whenDeviceMissing() {
        when(deviceRepository.findById("missing-device")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getStatus("missing-device"));
    }

    @Test
    void setLatitude_savesDeviceAndEnqueuesCommand() {
        when(deviceRepository.findById("esp32-01")).thenReturn(Optional.of(new Device()));

        service.setLatitude("esp32-01", -22.9);

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(eq("esp32-01"), captor.capture());
        assertEquals(-22.9, captor.getValue().getLatitude());

        verify(commandDispatchService).enqueue(eq("esp32-01"), argThat(command ->
                command.type().equals("LATITUDE") && command.payload().get("latitude").equals(-22.9)));
    }
}
