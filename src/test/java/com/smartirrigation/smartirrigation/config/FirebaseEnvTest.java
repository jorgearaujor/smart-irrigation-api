package com.smartirrigation.smartirrigation.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FirebaseEnvTest {

    @Test
    void fromEnvironment_readsAllRequiredValues() {
        Environment env = mock(Environment.class);
        when(env.getProperty("FIRESTORE_PROJECT_ID")).thenReturn("smart-irrigation-ifmaker");
        when(env.getProperty("GOOGLE_APPLICATION_CREDENTIALS")).thenReturn("./firebase.json");
        when(env.getProperty("DEVICE_API_KEY")).thenReturn("chave-secreta");
        when(env.getProperty("MYAPP_CORS_ALLOWED_ORIGINS", "http://localhost:4200"))
                .thenReturn("http://localhost:4200");

        FirebaseEnv result = FirebaseEnv.fromEnvironment(env);

        assertEquals("smart-irrigation-ifmaker", result.projectId());
        assertEquals("./firebase.json", result.credentialsPath());
        assertEquals("chave-secreta", result.deviceApiKey());
        assertEquals("http://localhost:4200", result.corsAllowedOrigins());
    }

    @Test
    void fromEnvironment_throwsWhenProjectIdMissing() {
        Environment env = mock(Environment.class);
        when(env.getProperty("FIRESTORE_PROJECT_ID")).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> FirebaseEnv.fromEnvironment(env));
    }

    @Test
    void fromEnvironment_throwsWhenDeviceApiKeyMissing() {
        Environment env = mock(Environment.class);
        when(env.getProperty("FIRESTORE_PROJECT_ID")).thenReturn("smart-irrigation-ifmaker");
        when(env.getProperty("GOOGLE_APPLICATION_CREDENTIALS")).thenReturn("./firebase.json");
        when(env.getProperty("DEVICE_API_KEY")).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> FirebaseEnv.fromEnvironment(env));
    }
}
