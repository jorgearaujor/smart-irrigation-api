package com.smartirrigation.smartirrigation.security;

import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Rotas usadas pelo firmware do ESP32, autenticadas pela X-Device-Key.
 *
 * <p>Algumas compartilham o caminho com rotas do painel e se diferenciam pelo
 * método HTTP: por exemplo, {@code POST /api/devices/{id}/telemetry} é chamada
 * pelo dispositivo, enquanto {@code GET /api/devices/{id}/telemetry} é chamada
 * pelo painel. Por isso a correspondência considera o método e o caminho.</p>
 */
public final class DevicePaths {

    public record DeviceRoute(HttpMethod method, String pattern) {
    }

    public static final List<DeviceRoute> ROUTES = List.of(
            new DeviceRoute(HttpMethod.POST, "/api/devices/*/telemetry"),
            new DeviceRoute(HttpMethod.POST, "/api/devices/*/status"),
            new DeviceRoute(HttpMethod.POST, "/api/devices/*/irrigation-history"),
            new DeviceRoute(HttpMethod.GET, "/api/devices/*/config"),
            new DeviceRoute(HttpMethod.GET, "/api/devices/*/commands/poll")
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private DevicePaths() {
    }

    public static boolean matches(String method, String cleanedUri) {
        return ROUTES.stream().anyMatch(route ->
                route.method().matches(method) && PATH_MATCHER.match(route.pattern(), cleanedUri));
    }
}
