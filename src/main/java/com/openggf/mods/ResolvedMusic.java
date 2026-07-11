package com.openggf.mods;

import java.util.Objects;

/** A stock logical music identity resolved to launch-prepared streamed PCM. */
public record ResolvedMusic(int logicalMusicId, PreparedTrack track) {
    public ResolvedMusic {
        if (logicalMusicId < 0) throw new IllegalArgumentException("Logical music id must be nonnegative");
        Objects.requireNonNull(track, "track");
    }
}
