package com.smartirrigation.smartirrigation;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * FirebaseConfig's beans need a real project id and a real service account file to build
 * a real FirebaseApp/Firestore/FirebaseAuth, neither of which exist in this test environment.
 * The FirebaseEnv properties below are placeholders so FirebaseConfig#firebaseEnv() doesn't
 * throw; FirebaseApp/Firestore/FirebaseAuth are mocked so FirebaseConfig#firebaseApp() (which
 * would otherwise try to read a real credentials file) never runs.
 */
@SpringBootTest(properties = {
        "FIRESTORE_PROJECT_ID=test-project",
        "GOOGLE_APPLICATION_CREDENTIALS=test-credentials.json",
        "DEVICE_API_KEY=test-device-key",
        "MYAPP_CORS_ALLOWED_ORIGINS=http://localhost:4200"
})
class SmartIrrigationApplicationTests {

    @MockitoBean
    private FirebaseApp firebaseApp;

    @MockitoBean
    private Firestore firestore;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    @Test
    void contextLoads() {
    }

}
