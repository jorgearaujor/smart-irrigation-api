package com.smartirrigation.smartirrigation.controller.web;

import com.smartirrigation.smartirrigation.dto.AuthorizationUpdateRequestDTO;
import com.smartirrigation.smartirrigation.dto.IrrigationStartRequestDTO;
import com.smartirrigation.smartirrigation.dto.LatitudeUpdateRequestDTO;
import com.smartirrigation.smartirrigation.dto.ModeUpdateRequestDTO;
import com.smartirrigation.smartirrigation.service.DeviceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices/{deviceId}")
@Tag(name = "Device Commands (Web)", description = "Comandos de irrigacao disparados pelo dashboard web")
public class DeviceCommandController {

    private final DeviceService deviceService;

    public DeviceCommandController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/irrigation/start")
    public ResponseEntity<Void> startIrrigation(@PathVariable String deviceId, @RequestBody IrrigationStartRequestDTO dto) {
        deviceService.startIrrigation(deviceId, dto.getQuantidadeAgua());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/irrigation/stop")
    public ResponseEntity<Void> stopIrrigation(@PathVariable String deviceId) {
        deviceService.stopIrrigation(deviceId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/mode")
    public ResponseEntity<Void> updateMode(@PathVariable String deviceId, @RequestBody ModeUpdateRequestDTO dto) {
        deviceService.setModo(deviceId, dto.getModo());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/authorization")
    public ResponseEntity<Void> updateAuthorization(@PathVariable String deviceId, @RequestBody AuthorizationUpdateRequestDTO dto) {
        deviceService.setAutorizado(deviceId, dto.getAutorizado());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/latitude")
    public ResponseEntity<Void> updateLatitude(@PathVariable String deviceId, @RequestBody LatitudeUpdateRequestDTO dto) {
        deviceService.setLatitude(deviceId, dto.getLatitude());
        return ResponseEntity.ok().build();
    }
}
