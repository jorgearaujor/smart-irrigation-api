package com.smartirrigation.smartirrigation.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandDispatchServiceTest {

    @Test
    void poll_resolvesImmediately_whenCommandAlreadyEnqueued() throws Exception {
        CommandDispatchService service = new CommandDispatchService();
        CommandDispatchService.Command command =
                new CommandDispatchService.Command("DEFINIR_MODO", Map.of("modo", "automatico"));

        service.enqueue("esp32-01", command);
        Optional<CommandDispatchService.Command> result = service.poll("esp32-01", 1000).get(2, TimeUnit.SECONDS);

        assertTrue(result.isPresent());
        assertEquals("DEFINIR_MODO", result.get().type());
    }

    @Test
    void poll_resolvesWhenCommandArrivesWhileWaiting() throws Exception {
        CommandDispatchService service = new CommandDispatchService();
        CompletableFuture<Optional<CommandDispatchService.Command>> future = service.poll("esp32-01", 5000);

        service.enqueue("esp32-01", new CommandDispatchService.Command("LATITUDE", Map.of("latitude", -23.5)));

        Optional<CommandDispatchService.Command> result = future.get(2, TimeUnit.SECONDS);
        assertTrue(result.isPresent());
        assertEquals("LATITUDE", result.get().type());
    }

    @Test
    void poll_resolvesEmpty_afterTimeout() throws Exception {
        CommandDispatchService service = new CommandDispatchService();

        Optional<CommandDispatchService.Command> result = service.poll("esp32-01", 200).get(2, TimeUnit.SECONDS);

        assertTrue(result.isEmpty());
    }

    @Test
    void enqueue_skipsStaleTimedOutWaiter_andResolvesLiveWaiterBehindIt() throws Exception {
        CommandDispatchService service = new CommandDispatchService();

        // First waiter: let it time out and complete empty, but leave it sitting in the
        // waiters queue (this reproduces the bug: nothing removes a timed-out future from
        // the queue on its own, so it lingers ahead of any later waiter for the same device).
        CompletableFuture<Optional<CommandDispatchService.Command>> staleFuture =
                service.poll("esp32-01", 100);
        Optional<CommandDispatchService.Command> staleResult = staleFuture.get(2, TimeUnit.SECONDS);
        assertTrue(staleResult.isEmpty());

        // Second waiter: a genuinely live long-poll registered right after the stale one.
        CompletableFuture<Optional<CommandDispatchService.Command>> liveFuture =
                service.poll("esp32-01", 5000);

        service.enqueue("esp32-01", new CommandDispatchService.Command("LIGAR_BOMBA", Map.of("zona", 1)));

        Optional<CommandDispatchService.Command> liveResult = liveFuture.get(2, TimeUnit.SECONDS);
        assertTrue(liveResult.isPresent());
        assertEquals("LIGAR_BOMBA", liveResult.get().type());
    }
}
