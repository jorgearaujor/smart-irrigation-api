package com.smartirrigation.smartirrigation.controller.device;

import com.smartirrigation.smartirrigation.dto.HistoryRequestDTO;
import com.smartirrigation.smartirrigation.dto.StatusRequestDTO;
import com.smartirrigation.smartirrigation.dto.TelemetryRequestDTO;
import com.smartirrigation.smartirrigation.service.DeviceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices/{deviceId}")
@Tag(name = "Device Data", description = "Endpoints que o firmware do ESP32 chama via POST para enviar dados")
public class DeviceDataController {

    private final DeviceService deviceService;

    public DeviceDataController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/telemetry")
    public ResponseEntity<Void> telemetry(@PathVariable String deviceId, @RequestBody TelemetryRequestDTO dto) {
        deviceService.recordTelemetry(deviceId, dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/status")
    public ResponseEntity<Void> status(@PathVariable String deviceId, @RequestBody StatusRequestDTO dto) {
        deviceService.updateStatus(deviceId, dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/irrigation-history")
    public ResponseEntity<Void> history(@PathVariable String deviceId, @RequestBody HistoryRequestDTO dto) {
        deviceService.recordHistory(deviceId, dto);
        return ResponseEntity.ok().build();
    }
}
