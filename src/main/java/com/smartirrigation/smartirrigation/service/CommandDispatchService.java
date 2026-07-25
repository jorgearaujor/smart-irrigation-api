package com.smartirrigation.smartirrigation.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class CommandDispatchService {

    public record Command(String type, Map<String, Object> payload) {
    }

    private final Map<String, ConcurrentLinkedQueue<CompletableFuture<Optional<Command>>>> waiters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(1);

    public void enqueue(String deviceId, Command command) {
        ConcurrentLinkedQueue<CompletableFuture<Optional<Command>>> queue = waiters.get(deviceId);
        CompletableFuture<Optional<Command>> waiting = (queue == null) ? null : queue.poll();
        if (waiting != null && waiting.complete(Optional.of(command))) {
            return;
        }
        // Nao ha long-poll pendente agora; o proximo poll() buscara este comando via a fila abaixo.
        pending.computeIfAbsent(deviceId, id -> new ConcurrentLinkedQueue<>()).add(command);
    }

    private final Map<String, ConcurrentLinkedQueue<Command>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<Optional<Command>> poll(String deviceId, long timeoutMillis) {
        ConcurrentLinkedQueue<Command> pendingQueue = pending.get(deviceId);
        Command alreadyPending = (pendingQueue == null) ? null : pendingQueue.poll();
        if (alreadyPending != null) {
            return CompletableFuture.completedFuture(Optional.of(alreadyPending));
        }

        CompletableFuture<Optional<Command>> future = new CompletableFuture<>();
        waiters.computeIfAbsent(deviceId, id -> new ConcurrentLinkedQueue<>()).add(future);

        timeoutExecutor.schedule(() -> future.complete(Optional.empty()), timeoutMillis, TimeUnit.MILLISECONDS);
        return future;
    }
}
