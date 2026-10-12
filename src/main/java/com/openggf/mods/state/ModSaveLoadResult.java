package com.openggf.mods.state;

import java.util.Objects;
import java.util.Optional;

/** A validated value or an explicit reason why existing bytes must be preserved. */
@com.openggf.game.ModApi
public record ModSaveLoadResult<T>(Status status, Optional<T> value) {
    @com.openggf.game.ModApi
    public enum Status { ABSENT, VALID, RECOVERED, CORRUPT, INCOMPATIBLE, UNREADABLE }

    public ModSaveLoadResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(value, "value");
        if (value.isPresent() != (status == Status.VALID || status == Status.RECOVERED))
            throw new IllegalArgumentException("Only validated results carry a value");
    }

    public static <T> ModSaveLoadResult<T> valid(T value) {
        return new ModSaveLoadResult<>(Status.VALID, Optional.of(value));
    }

    public static <T> ModSaveLoadResult<T> rejected(Status status) {
        return new ModSaveLoadResult<>(status, Optional.empty());
    }
}
