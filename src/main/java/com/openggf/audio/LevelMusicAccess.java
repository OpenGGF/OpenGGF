package com.openggf.audio;

import java.util.Objects;

/** Internal automatic-playlist admission; direct music cues and native SFX retain their routes. */
public final class LevelMusicAccess {
    private LevelMusicAccess() { }

    /** A director owns this scope across native producer replacements, and closes it on stop. */
    public static Control suppressAutomatic(AudioManager audio) {
        return new Control(Objects.requireNonNull(audio));
    }

    public static boolean allowsAutomatic(AudioManager audio) {
        synchronized (audio) { return audio.levelMusicControls == 0; }
    }

    public static final class Control implements AutoCloseable {
        private final AudioManager audio;
        private boolean closed;

        private Control(AudioManager audio) {
            this.audio = audio;
            synchronized (audio) { audio.levelMusicControls++; }
        }

        @Override public void close() {
            synchronized (audio) {
                if (!closed) { closed = true; audio.levelMusicControls--; }
            }
        }
    }
}
