package com.smartirrigation.smartirrigation.controller.web;

import com.smartirrigation.smartirrigation.dto.IrrigationHistoryResponseDTO;
import com.smartirrigation.smartirrigation.dto.TelemetryResponseDTO;
import com.smartirrigation.smartirrigation.service.DeviceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/devices/{deviceId}")
@Tag(name = "History", description = "Series historicas de telemetria e irrigacao para o dashboard web")
public class HistoryController {

    private static final int DEFAULT_HOURS = 24;
    private static final int DEFAULT_TELEMETRY_LIMIT = 500;

    private final DeviceService deviceService;

    public HistoryController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/telemetry")
    public ResponseEntity<List<TelemetryResponseDTO>> getTelemetry(
            @PathVariable String deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit) {

        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(DEFAULT_HOURS, ChronoUnit.HOURS);
        int effectiveLimit = limit != null ? limit : DEFAULT_TELEMETRY_LIMIT;

        return ResponseEntity.ok(deviceService.getTelemetryHistory(deviceId, effectiveFrom, effectiveTo, effectiveLimit));
    }

    @GetMapping("/irrigation-history")
    public ResponseEntity<List<IrrigationHistoryResponseDTO>> getIrrigationHistory(
            @PathVariable String deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(30, ChronoUnit.DAYS);

        return ResponseEntity.ok(deviceService.getIrrigationHistory(deviceId, effectiveFrom, effectiveTo));
    }
}
