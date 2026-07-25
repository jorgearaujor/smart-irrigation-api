package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.StatusWebhookRequestDTO;
import com.smartirrigation.smartirrigation.dto.TelemetryWebhookRequestDTO;
import com.smartirrigation.smartirrigation.model.Device;
import com.smartirrigation.smartirrigation.model.Telemetry;
import com.smartirrigation.smartirrigation.repository.DeviceRepository;
import com.smartirrigation.smartirrigation.repository.IrrigationHistoryRepository;
import com.smartirrigation.smartirrigation.repository.TelemetryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DeviceServiceTest {

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final TelemetryRepository telemetryRepository = mock(TelemetryRepository.class);
    private final IrrigationHistoryRepository irrigationHistoryRepository = mock(IrrigationHistoryRepository.class);
    private final DeviceService service = new DeviceService(deviceRepository, telemetryRepository, irrigationHistoryRepository);

    @Test
    void recordTelemetry_savesTemperatureHumiditySoilPercent() {
        TelemetryWebhookRequestDTO dto = new TelemetryWebhookRequestDTO();
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
    void updateStatus_upsertsDeviceFields() {
        when(deviceRepository.findById("esp32-01")).thenReturn(Optional.of(new Device()));

        StatusWebhookRequestDTO dto = new StatusWebhookRequestDTO();
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
}
