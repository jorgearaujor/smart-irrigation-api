package com.smartirrigation.smartirrigation.controller.web;

import com.smartirrigation.smartirrigation.dto.CriarUsuarioRequestDTO;
import com.smartirrigation.smartirrigation.dto.UsuarioResponseDTO;
import com.smartirrigation.smartirrigation.service.UsuarioService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Perfil do usuario logado e cadastro de novos usuarios (somente Administrador)")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> getUsuarioAtual(Authentication authentication) {
        return ResponseEntity.ok(usuarioService.getUsuarioAtual(authentication.getName()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponseDTO> criarUsuario(@RequestBody CriarUsuarioRequestDTO dto) {
        UsuarioResponseDTO created = usuarioService.criarUsuario(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
