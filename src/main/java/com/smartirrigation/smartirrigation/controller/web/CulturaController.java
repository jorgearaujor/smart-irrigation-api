package com.smartirrigation.smartirrigation.controller.web;

import com.smartirrigation.smartirrigation.dto.CulturaCreateRequestDTO;
import com.smartirrigation.smartirrigation.dto.CulturaDTO;
import com.smartirrigation.smartirrigation.service.CulturaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/culturas")
@Tag(name = "Culturas", description = "Catalogo de culturas cadastradas e seus coeficientes por fase")
public class CulturaController {

    private final CulturaService culturaService;

    public CulturaController(CulturaService culturaService) {
        this.culturaService = culturaService;
    }

    @GetMapping
    public ResponseEntity<List<CulturaDTO>> listCulturas() {
        return ResponseEntity.ok(culturaService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CulturaDTO> criarCultura(@RequestBody CulturaCreateRequestDTO dto) {
        CulturaDTO created = culturaService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
