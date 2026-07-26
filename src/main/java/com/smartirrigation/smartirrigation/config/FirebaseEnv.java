package com.smartirrigation.smartirrigation.config;

import org.springframework.core.env.Environment;

public record FirebaseEnv(String projectId, String credentialsPath, String deviceApiKey, String corsAllowedOrigins,
                           boolean dashboardAuthEnabled) {

    public static FirebaseEnv fromEnvironment(Environment env) {
        String projectId = require(env, "FIRESTORE_PROJECT_ID");
        String credentialsPath = require(env, "GOOGLE_APPLICATION_CREDENTIALS");
        String deviceApiKey = require(env, "DEVICE_API_KEY");
        String corsAllowedOrigins = env.getProperty("MYAPP_CORS_ALLOWED_ORIGINS", "http://localhost:4200");
        // Web dashboard login (Firebase Auth) is implemented but switched off for now;
        // set DASHBOARD_AUTH_ENABLED=true to require a Bearer token + registered
        // usuarios/{uid} doc again on every /api/devices/**, /api/culturas, /api/usuarios route.
        boolean dashboardAuthEnabled = Boolean.parseBoolean(env.getProperty("DASHBOARD_AUTH_ENABLED", "false"));
        return new FirebaseEnv(projectId, credentialsPath, deviceApiKey, corsAllowedOrigins, dashboardAuthEnabled);
    }

    private static String require(Environment env, String key) {
        String value = env.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variavel de ambiente obrigatoria ausente: " + key);
        }
        return value;
    }
}
