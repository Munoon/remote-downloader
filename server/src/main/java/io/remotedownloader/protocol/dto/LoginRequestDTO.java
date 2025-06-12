package io.remotedownloader.protocol.dto;

public record LoginRequestDTO(
        String username,
        String password
) {
}
