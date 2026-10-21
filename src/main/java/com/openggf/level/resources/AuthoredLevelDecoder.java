package com.openggf.level.resources;

import com.openggf.level.Level;

import java.io.IOException;
import java.util.Objects;

/** Engine-internal format provider for independently decoded authored terrain. */
public interface AuthoredLevelDecoder {
    /** Canonical format name, independent of a caller's path or asset ownership. */
    String format();

    /** Decode bounded bytes without resolving paths, invoking creators or changing a live world. */
    Decoded decode(byte[] bytes) throws IOException;

    /** Caller-owned decoded terrain. No editor document, savepoint or UI state crosses this port. */
    record Decoded(String gameId, Level level) {
        public Decoded {
            Objects.requireNonNull(gameId, "gameId");
            Objects.requireNonNull(level, "level");
            if (gameId.isBlank() || gameId.length() > 64) throw new IllegalArgumentException("Invalid authored level game identity");
        }
    }
}
