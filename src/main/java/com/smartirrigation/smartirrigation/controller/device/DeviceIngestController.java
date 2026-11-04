package com.smartirrigation.smartirrigation.controller.device;

import com.smartirrigation.smartirrigation.dto.HistoryRequestDTO;
import com.smartirrigation.smartirrigation.dto.StatusRequestDTO;
import com.smartirrigation.smartirrigation.dto.TelemetryRequestDTO;
import com.smartirrigation.smartirrigation.service.DeviceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ingest")
@Tag(name = "Device Ingest", description = "Endpoints de ingestão de dados que o firmware do ESP32 chama via POST")
public class DeviceIngestController {

    private final DeviceService deviceService;

    public DeviceIngestController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/telemetry")
    public ResponseEntity<Void> telemetry(@RequestBody TelemetryRequestDTO dto) {
        deviceService.recordTelemetry(dto.getDeviceId(), dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/status")
    public ResponseEntity<Void> status(@RequestBody StatusRequestDTO dto) {
        deviceService.updateStatus(dto.getDeviceId(), dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/history")
    public ResponseEntity<Void> history(@RequestBody HistoryRequestDTO dto) {
        deviceService.recordHistory(dto.getDeviceId(), dto);
        return ResponseEntity.ok().build();
    }
}
