package com.openggf.game.arena;

import java.util.Objects;

/** Arena index is the game's native menu index, never its internal zone ID. */
public record HostedArenaRequest(int arenaIndex, String character, boolean itemsEnabled) {
    public HostedArenaRequest {
        if (arenaIndex < 0) throw new IllegalArgumentException("Negative arena index");
        Objects.requireNonNull(character);
    }
}
