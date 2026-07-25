package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.ConfigIrrigacaoDTO;
import com.smartirrigation.smartirrigation.model.ConfigIrrigacao;
import com.smartirrigation.smartirrigation.model.Device;
import com.smartirrigation.smartirrigation.repository.DeviceRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConfigIrrigacaoServiceTest {

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final CommandDispatchService commandDispatchService = mock(CommandDispatchService.class);
    private final ConfigIrrigacaoService service = new ConfigIrrigacaoService(deviceRepository, commandDispatchService);

    @Test
    void getConfig_mapsDeviceFieldsToDto() {
        Device device = new Device();
        device.getConfigIrrigacao().setKc(1.4);
        when(deviceRepository.findById("esp32-01")).thenReturn(Optional.of(device));

        ConfigIrrigacaoDTO result = service.getConfig("esp32-01");

        assertEquals(1.4, result.getKc());
    }

    @Test
    void updateConfig_savesDeviceAndEnqueuesCommand() {
        Device device = new Device();
        when(deviceRepository.findById("esp32-01")).thenReturn(Optional.of(device));

        ConfigIrrigacaoDTO dto = new ConfigIrrigacaoDTO();
        dto.setKc(1.6);
        dto.setLimiarUmidadeMinima(40.0);

        service.updateConfig("esp32-01", dto);

        verify(deviceRepository).save(eq("esp32-01"), any(Device.class));
        verify(commandDispatchService).enqueue(eq("esp32-01"), argThat(command ->
                command.type().equals("CONFIG_IRRIGACAO")
                        && command.payload().get("limiar_umidade_minima").equals(40.0)));
    }
}
