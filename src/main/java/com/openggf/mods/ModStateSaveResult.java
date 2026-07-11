package com.openggf.mods;

import java.util.Objects;

/** Result of atomically persisting pending mod state. */
public sealed interface ModStateSaveResult permits ModStateSaveResult.Saved, ModStateSaveResult.Failed {
    record Saved() implements ModStateSaveResult {
    }

    record Failed(String message) implements ModStateSaveResult {
        public Failed {
            Objects.requireNonNull(message, "message");
            if (message.isBlank()) {
                throw new IllegalArgumentException("Save failure message must be nonblank");
            }
        }
    }
}
