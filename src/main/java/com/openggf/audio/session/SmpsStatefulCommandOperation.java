package com.openggf.audio.session;

import com.openggf.audio.driver.SmpsChannelOwnershipProjection;

import java.util.Objects;

/** A prepared host operation; Phase 1 exposes only the inert result. */
public record SmpsStatefulCommandOperation(
        Input input,
        boolean handled) {
    public SmpsStatefulCommandOperation {
        Objects.requireNonNull(input, "input");
        if (handled) {
            throw new IllegalArgumentException(
                    "Phase 1 stateful operations must remain inert");
        }
    }

    public static SmpsStatefulCommandOperation none(Input input) {
        return new SmpsStatefulCommandOperation(input, false);
    }

    public record Input(
            SmpsSessionCommand command,
            SmpsChannelOwnershipProjection ownership) {
        public Input {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(ownership, "ownership");
        }
    }
}
