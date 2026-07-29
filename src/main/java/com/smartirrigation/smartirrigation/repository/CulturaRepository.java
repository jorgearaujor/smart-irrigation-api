package com.smartirrigation.smartirrigation.repository;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.smartirrigation.smartirrigation.model.CoeficienteCultura;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class CulturaRepository {

    private static final String COLLECTION = "coeficientes";

    private final Firestore firestore;

    public CulturaRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<CoeficienteCultura> findByName(String cultura) {
        try {
            DocumentSnapshot snapshot = firestore.collection(COLLECTION).document(cultura).get().get();
            if (!snapshot.exists()) {
                return Optional.empty();
            }
            return Optional.ofNullable(snapshot.toObject(CoeficienteCultura.class));
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao ler cultura " + cultura + " do Firestore", e);
        }
    }

    public List<CoeficienteCultura> findAll() {
        try {
            List<QueryDocumentSnapshot> docs = firestore.collection(COLLECTION).get().get().getDocuments();
            return docs.stream().map(d -> d.toObject(CoeficienteCultura.class)).collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao listar culturas", e);
        }
    }

    public void save(String nome, CoeficienteCultura cultura) {
        try {
            firestore.collection(COLLECTION).document(nome).set(cultura).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao salvar cultura " + nome + " no Firestore", e);
        }
    }
}

