package com.smartirrigation.smartirrigation.controller.web;

import com.smartirrigation.smartirrigation.dto.DeviceConfigResponseDTO;
import com.smartirrigation.smartirrigation.dto.DeviceStatusResponseDTO;
import com.smartirrigation.smartirrigation.service.DeviceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices/{deviceId}")
@Tag(name = "Status", description = "Snapshot atual do device para o dashboard web")
public class StatusController {

    private final DeviceService deviceService;

    public StatusController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/status")
    public ResponseEntity<DeviceStatusResponseDTO> getStatus(@PathVariable String deviceId) {
        DeviceConfigResponseDTO config = deviceService.getConfig(deviceId);

        DeviceStatusResponseDTO status = new DeviceStatusResponseDTO();
        status.setModo(config.getModo());
        status.setAutorizado(config.getAutorizado());
        status.setQuantidadeAgua(config.getQuantidadeAgua());
        status.setLatitude(config.getLatitude());
        return ResponseEntity.ok(status);
    }
}
