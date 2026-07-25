package com.smartirrigation.smartirrigation.controller.device;

import com.smartirrigation.smartirrigation.dto.CommandResponseDTO;
import com.smartirrigation.smartirrigation.service.CommandDispatchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping("/api/devices/{deviceId}")
@Tag(name = "Device Commands", description = "Long-polling de comandos consumido pelo ESP32")
public class CommandPollController {

    private static final long POLL_TIMEOUT_MILLIS = 30_000;

    private final CommandDispatchService commandDispatchService;

    public CommandPollController(CommandDispatchService commandDispatchService) {
        this.commandDispatchService = commandDispatchService;
    }

    @GetMapping("/commands/poll")
    public DeferredResult<ResponseEntity<CommandResponseDTO>> poll(@PathVariable String deviceId) {
        DeferredResult<ResponseEntity<CommandResponseDTO>> deferredResult =
                new DeferredResult<>(POLL_TIMEOUT_MILLIS + 5000, ResponseEntity.noContent().build());

        commandDispatchService.poll(deviceId, POLL_TIMEOUT_MILLIS).thenAccept(maybeCommand ->
                deferredResult.setResult(maybeCommand
                        .map(command -> ResponseEntity.ok(new CommandResponseDTO(command.type(), command.payload())))
                        .orElseGet(() -> ResponseEntity.noContent().build())));

        return deferredResult;
    }
}
