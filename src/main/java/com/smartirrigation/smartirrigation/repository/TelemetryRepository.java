package com.smartirrigation.smartirrigation.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.smartirrigation.smartirrigation.model.Telemetry;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class TelemetryRepository {

    private static final String COLLECTION = "devices";
    private static final String SUBCOLLECTION = "telemetry";

    private final Firestore firestore;

    public TelemetryRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public void add(String deviceId, Telemetry telemetry) {
        telemetry.setRecordedAt(Timestamp.now());
        try {
            firestore.collection(COLLECTION).document(deviceId)
                    .collection(SUBCOLLECTION).document()
                    .set(telemetry)
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao gravar telemetria do device " + deviceId, e);
        }
    }

    public List<Telemetry> findBetween(String deviceId, Instant from, Instant to, int limit) {
        Query query = firestore.collection(COLLECTION).document(deviceId).collection(SUBCOLLECTION)
                .whereGreaterThanOrEqualTo("recordedAt", Timestamp.of(java.util.Date.from(from)))
                .whereLessThanOrEqualTo("recordedAt", Timestamp.of(java.util.Date.from(to)))
                .orderBy("recordedAt", Query.Direction.DESCENDING)
                .limit(limit);
        try {
            List<QueryDocumentSnapshot> docs = query.get().get().getDocuments();
            return docs.stream().map(d -> d.toObject(Telemetry.class)).collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao consultar telemetria do device " + deviceId, e);
        }
    }
}
