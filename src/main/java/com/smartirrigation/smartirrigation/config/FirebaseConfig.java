package com.smartirrigation.smartirrigation.config;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Configuration
public class FirebaseConfig {

    @Bean
    public FirebaseEnv firebaseEnv(Environment env) {
        return FirebaseEnv.fromEnvironment(env);
    }

    @Bean
    public FirebaseApp firebaseApp(FirebaseEnv firebaseEnv) {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        try (FileInputStream credentials = new FileInputStream(firebaseEnv.credentialsPath())) {
            // Forca o transporte HTTP baseado em java.net.HttpURLConnection em vez do
            // padrao (Apache HttpClient, via google-http-client-apache-v2). Nesta maquina,
            // o transporte Apache corrompe a resposta gzip ao buscar as chaves publicas do
            // Google para validar tokens Firebase (ZipException: "Not in GZIP format" em
            // GooglePublicKeysManager.refresh), mesmo com a rede/JDK funcionando bem via
            // NetHttpTransport puro (confirmado manualmente).
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .setProjectId(firebaseEnv.projectId())
                    .setHttpTransport(new NetHttpTransport())
                    .build();
            return FirebaseApp.initializeApp(options);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao carregar credenciais do Firebase de " + firebaseEnv.credentialsPath(), e);
        }
    }

    @Bean
    public Firestore firestore(FirebaseApp firebaseApp) {
        return FirestoreClient.getFirestore(firebaseApp);
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }
}
