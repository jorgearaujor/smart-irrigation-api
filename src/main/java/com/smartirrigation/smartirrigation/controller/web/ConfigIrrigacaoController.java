package com.smartirrigation.smartirrigation.controller.web;

import com.smartirrigation.smartirrigation.dto.ConfigIrrigacaoDTO;
import com.smartirrigation.smartirrigation.service.ConfigIrrigacaoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices/{deviceId}/config-irrigacao")
@Tag(name = "Config Irrigacao", description = "Parametros agronomicos usados no calculo de ETo")
public class ConfigIrrigacaoController {

    private final ConfigIrrigacaoService configIrrigacaoService;

    public ConfigIrrigacaoController(ConfigIrrigacaoService configIrrigacaoService) {
        this.configIrrigacaoService = configIrrigacaoService;
    }

    @GetMapping
    public ResponseEntity<ConfigIrrigacaoDTO> getConfig(@PathVariable String deviceId) {
        return ResponseEntity.ok(configIrrigacaoService.getConfig(deviceId));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateConfig(@PathVariable String deviceId, @RequestBody ConfigIrrigacaoDTO dto) {
        configIrrigacaoService.updateConfig(deviceId, dto);
        return ResponseEntity.noContent().build();
    }
}
