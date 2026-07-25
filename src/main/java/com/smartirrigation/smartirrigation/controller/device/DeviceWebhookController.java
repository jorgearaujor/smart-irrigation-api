package com.smartirrigation.smartirrigation.controller.device;

import com.smartirrigation.smartirrigation.dto.HistoryWebhookRequestDTO;
import com.smartirrigation.smartirrigation.dto.StatusWebhookRequestDTO;
import com.smartirrigation.smartirrigation.dto.TelemetryWebhookRequestDTO;
import com.smartirrigation.smartirrigation.service.DeviceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Device Webhooks", description = "Endpoints que o firmware do ESP32 chama via POST")
public class DeviceWebhookController {

    private final DeviceService deviceService;

    public DeviceWebhookController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/telemetry")
    public ResponseEntity<Void> telemetry(@RequestBody TelemetryWebhookRequestDTO dto) {
        deviceService.recordTelemetry(dto.getDeviceId(), dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/status")
    public ResponseEntity<Void> status(@RequestBody StatusWebhookRequestDTO dto) {
        deviceService.updateStatus(dto.getDeviceId(), dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/history")
    public ResponseEntity<Void> history(@RequestBody HistoryWebhookRequestDTO dto) {
        deviceService.recordHistory(dto.getDeviceId(), dto);
        return ResponseEntity.ok().build();
    }
}
