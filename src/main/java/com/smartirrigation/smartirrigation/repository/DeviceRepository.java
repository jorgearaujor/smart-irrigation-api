package com.smartirrigation.smartirrigation.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.smartirrigation.smartirrigation.model.Device;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Repository
public class DeviceRepository {

    private static final String COLLECTION = "devices";

    private final Firestore firestore;

    public DeviceRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<Device> findById(String deviceId) {
        try {
            DocumentSnapshot snapshot = firestore.collection(COLLECTION).document(deviceId).get().get();
            if (!snapshot.exists()) {
                return Optional.empty();
            }
            Device device = snapshot.toObject(Device.class);
            if (device != null) {
                device.setDeviceId(deviceId);
            }
            return Optional.ofNullable(device);
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao ler device " + deviceId + " do Firestore", e);
        }
    }

    public Device save(String deviceId, Device device) {
        device.setUpdatedAt(Timestamp.now());
        try {
            firestore.collection(COLLECTION).document(deviceId).set(device).get();
            device.setDeviceId(deviceId);
            return device;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao salvar device " + deviceId + " no Firestore", e);
        }
    }
}
