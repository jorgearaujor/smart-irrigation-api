package com.smartirrigation.smartirrigation.repository;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.smartirrigation.smartirrigation.model.Usuario;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Repository
public class UsuarioRepository {

    private static final String COLLECTION = "usuarios";

    private final Firestore firestore;

    public UsuarioRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<Usuario> findById(String uid) {
        try {
            DocumentSnapshot snapshot = firestore.collection(COLLECTION).document(uid).get().get();
            if (!snapshot.exists()) {
                return Optional.empty();
            }
            return Optional.ofNullable(snapshot.toObject(Usuario.class));
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao ler usuario " + uid + " do Firestore", e);
        }
    }

    public Usuario save(String uid, Usuario usuario) {
        try {
            firestore.collection(COLLECTION).document(uid).set(usuario).get();
            return usuario;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao salvar usuario " + uid + " no Firestore", e);
        }
    }
}
