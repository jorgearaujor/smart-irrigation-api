package com.smartirrigation.smartirrigation.config;

import org.springframework.core.env.Environment;

public record FirebaseEnv(String projectId, String credentialsPath, String deviceApiKey, String corsAllowedOrigins) {

    public static FirebaseEnv fromEnvironment(Environment env) {
        String projectId = require(env, "FIRESTORE_PROJECT_ID");
        String credentialsPath = require(env, "GOOGLE_APPLICATION_CREDENTIALS");
        String deviceApiKey = require(env, "DEVICE_API_KEY");
        String corsAllowedOrigins = env.getProperty("MYAPP_CORS_ALLOWED_ORIGINS", "http://localhost:4200");
        return new FirebaseEnv(projectId, credentialsPath, deviceApiKey, corsAllowedOrigins);
    }

    private static String require(Environment env, String key) {
        String value = env.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variavel de ambiente obrigatoria ausente: " + key);
        }
        return value;
    }
}
