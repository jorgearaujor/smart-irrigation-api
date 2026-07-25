package com.smartirrigation.smartirrigation.controller.device;

import com.smartirrigation.smartirrigation.dto.DeviceConfigResponseDTO;
import com.smartirrigation.smartirrigation.service.DeviceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices/{deviceId}")
@Tag(name = "Device Config", description = "Configuracao inicial buscada pelo ESP32 no boot")
public class DeviceConfigController {

    private final DeviceService deviceService;

    public DeviceConfigController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/config")
    public ResponseEntity<DeviceConfigResponseDTO> getConfig(@PathVariable String deviceId) {
        return ResponseEntity.ok(deviceService.getConfig(deviceId));
    }
}
