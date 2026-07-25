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
}
