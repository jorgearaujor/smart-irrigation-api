package com.smartirrigation.smartirrigation.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.smartirrigation.smartirrigation.model.Usuario;
import com.smartirrigation.smartirrigation.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirebaseAuthFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_setsAdminAuthority_whenTipoIsAdministrador() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        FirebaseToken token = mock(FirebaseToken.class);
        FilterChain chain = mock(FilterChain.class);

        when(firebaseAuth.verifyIdToken("valid-token")).thenReturn(token);
        when(token.getUid()).thenReturn("uid-123");
        Usuario usuario = new Usuario();
        usuario.setTipo("Administrador");
        when(usuarioRepository.findById("uid-123")).thenReturn(Optional.of(usuario));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices/esp32-01/status");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository).doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_returns401_whenAuthorizationHeaderMissing() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices/esp32-01/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository).doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_skipsDeviceWebhookPaths() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhooks/telemetry");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(firebaseAuth);
    }
}
