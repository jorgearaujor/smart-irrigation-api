package com.smartirrigation.smartirrigation.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.smartirrigation.smartirrigation.model.IrrigationHistoryRecord;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class IrrigationHistoryRepository {

    private static final String COLLECTION = "devices";
    private static final String SUBCOLLECTION = "irrigationHistory";

    private final Firestore firestore;

    public IrrigationHistoryRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public void add(String deviceId, IrrigationHistoryRecord record) {
        record.setRecordedAt(Timestamp.now());
        try {
            firestore.collection(COLLECTION).document(deviceId)
                    .collection(SUBCOLLECTION).document()
                    .set(record)
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao gravar historico de irrigacao do device " + deviceId, e);
        }
    }

    public List<IrrigationHistoryRecord> findBetween(String deviceId, Instant from, Instant to) {
        Query query = firestore.collection(COLLECTION).document(deviceId).collection(SUBCOLLECTION)
                .whereGreaterThanOrEqualTo("recordedAt", Timestamp.of(java.util.Date.from(from)))
                .whereLessThanOrEqualTo("recordedAt", Timestamp.of(java.util.Date.from(to)))
                .orderBy("recordedAt", Query.Direction.DESCENDING);
        try {
            List<QueryDocumentSnapshot> docs = query.get().get().getDocuments();
            return docs.stream().map(d -> d.toObject(IrrigationHistoryRecord.class)).collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao consultar historico de irrigacao do device " + deviceId, e);
        }
    }
}
