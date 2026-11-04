package com.smartirrigation.smartirrigation.security;

import com.smartirrigation.smartirrigation.config.FirebaseEnv;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;

class DeviceKeyFilterTest {

    private final FirebaseEnv env = new FirebaseEnv("proj", "./creds.json", "chave-secreta", "http://localhost:4200", false);

    @Test
    void doFilter_passesThrough_whenKeyMatches() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingest/telemetry");
        request.addHeader("X-Device-Key", "chave-secreta");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new DeviceKeyFilter(env).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_returns401_whenKeyMissingOrWrong() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingest/telemetry");
        request.addHeader("X-Device-Key", "chave-errada");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new DeviceKeyFilter(env).doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_ignoresNonDevicePaths() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices/esp32-01/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new DeviceKeyFilter(env).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_doesNotTreatPathTraversalUriAsDevicePath() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // Raw URI contains a literal ".." segment that, without normalization, tokenizes
        // under AntPathMatcher such that "/api/ingest/**" would opaquely swallow it -
        // even though the normalized path is not actually a device-only path. Since this
        // isn't a true device path, DeviceKeyFilter must do nothing and let the rest of
        // the chain (FirebaseAuthFilter) handle it, rather than enforcing X-Device-Key.
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/ingest/../devices/esp32-01/config-irrigacao");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new DeviceKeyFilter(env).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
