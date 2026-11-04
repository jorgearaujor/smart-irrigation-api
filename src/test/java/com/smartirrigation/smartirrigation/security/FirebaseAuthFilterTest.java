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
import java.util.concurrent.atomic.AtomicReference;

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

        // The filter now clears the SecurityContext in a finally block once the chain
        // returns, so the authentication set for this request must be captured *during*
        // the chain.doFilter callback rather than read back afterward.
        AtomicReference<Authentication> authDuringChain = new AtomicReference<>();
        doAnswer(invocation -> {
            authDuringChain.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(chain).doFilter(any(), any());

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository, true).doFilter(request, response, chain);

        Authentication auth = authDuringChain.get();
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

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository, true).doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_returns403_whenUsuarioNotRegistered() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        FirebaseToken token = mock(FirebaseToken.class);
        FilterChain chain = mock(FilterChain.class);

        when(firebaseAuth.verifyIdToken("valid-token")).thenReturn(token);
        when(token.getUid()).thenReturn("uid-789");
        when(usuarioRepository.findById("uid-789")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices/esp32-01/status");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository, true).doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_skipsDeviceIngestPaths() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingest/telemetry");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository, true).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(firebaseAuth);
    }

    @Test
    void doFilter_doesNotTreatPathTraversalUriAsDevicePath() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        FilterChain chain = mock(FilterChain.class);

        // Raw URI contains a literal ".." segment that, without normalization, tokenizes
        // under AntPathMatcher such that "/api/ingest/**" would opaquely swallow it -
        // even though the normalized path is not actually a device-only path.
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/ingest/../devices/esp32-01/config-irrigacao");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository, true).doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(firebaseAuth);
    }

    @Test
    void doFilter_clearsSecurityContext_afterSuccessfulAuthentication() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        FirebaseToken token = mock(FirebaseToken.class);
        FilterChain chain = mock(FilterChain.class);

        when(firebaseAuth.verifyIdToken("valid-token")).thenReturn(token);
        when(token.getUid()).thenReturn("uid-456");
        when(usuarioRepository.findById("uid-456")).thenReturn(Optional.of(new Usuario()));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices/esp32-01/status");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository, true).doFilter(request, response, chain);

        // Asserted here, before the @AfterEach teardown runs, to prove the filter itself
        // clears the context rather than relying on test teardown to hide the fact that
        // it never did.
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "FirebaseAuthFilter must clear the SecurityContext itself once the chain returns");
    }

    @Test
    void doFilter_passesThrough_forActualCorsPreflightRequest() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/devices/esp32-01/status");
        request.addHeader("Origin", "http://localhost:4200");
        request.addHeader("Access-Control-Request-Method", "GET");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository, true).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(firebaseAuth);
    }

    @Test
    void doFilter_skipsAuthEntirely_whenDashboardAuthDisabled() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices/esp32-01/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository, false).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(firebaseAuth);
    }

    @Test
    void doFilter_passesThrough_forSwaggerAndApiDocsPaths() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);

        FilterChain apiDocsChain = mock(FilterChain.class);
        MockHttpServletRequest apiDocsRequest = new MockHttpServletRequest("GET", "/v3/api-docs");
        MockHttpServletResponse apiDocsResponse = new MockHttpServletResponse();
        new FirebaseAuthFilter(firebaseAuth, usuarioRepository, true).doFilter(apiDocsRequest, apiDocsResponse, apiDocsChain);
        verify(apiDocsChain).doFilter(apiDocsRequest, apiDocsResponse);

        FilterChain swaggerChain = mock(FilterChain.class);
        MockHttpServletRequest swaggerRequest = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        MockHttpServletResponse swaggerResponse = new MockHttpServletResponse();
        new FirebaseAuthFilter(firebaseAuth, usuarioRepository, true).doFilter(swaggerRequest, swaggerResponse, swaggerChain);
        verify(swaggerChain).doFilter(swaggerRequest, swaggerResponse);

        verifyNoInteractions(firebaseAuth);
    }
}
