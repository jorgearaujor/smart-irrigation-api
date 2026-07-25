package com.smartirrigation.smartirrigation.security;

import org.springframework.util.AntPathMatcher;

import java.util.List;

public final class DevicePaths {

    public static final List<String> PATTERNS = List.of(
            "/api/webhooks/**",
            "/api/devices/*/config",
            "/api/devices/*/commands/poll"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private DevicePaths() {
    }

    public static boolean matches(String cleanedUri) {
        return PATTERNS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, cleanedUri));
    }
}
