package com.smartirrigation.smartirrigation.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404WithMessage() {
        ResponseEntity<ErrorResponseDTO> response =
                handler.handleNotFound(new NotFoundException("device esp32-01 nao encontrado"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("device esp32-01 nao encontrado", response.getBody().getMessage());
        assertEquals(404, response.getBody().getStatus());
    }

    @Test
    void handleConflict_returns409() {
        ResponseEntity<ErrorResponseDTO> response =
                handler.handleConflict(new ConflictException("device ja cadastrado"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
    }

    @Test
    void handleUnauthorized_returns401() {
        ResponseEntity<ErrorResponseDTO> response =
                handler.handleUnauthorized(new UnauthorizedException("token invalido"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().getStatus());
    }

    @Test
    void handleAccessDenied_returns403() {
        ResponseEntity<ErrorResponseDTO> response =
                handler.handleAccessDenied(new AccessDeniedException("Access is denied"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, response.getBody().getStatus());
        assertEquals("Acesso negado: permissao insuficiente para este recurso.", response.getBody().getMessage());
    }

    @Test
    void handleIllegalArgument_returns400() {
        ResponseEntity<ErrorResponseDTO> response =
                handler.handleIllegalArgument(new IllegalArgumentException("nome e obrigatorio"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("nome e obrigatorio", response.getBody().getMessage());
    }
}
