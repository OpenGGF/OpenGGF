package com.openggf.audio.session;

import java.util.Objects;

public interface SmpsPhysicalPolicy {
    record Identity(String value) {
        public Identity {
            value = Objects.requireNonNull(value, "value");
        }
    }

    Identity identity();

    SmpsWriteProgram boot();

    SmpsWriteProgram stopAll();

    /** ROM work performed when a non-immediate music load begins. */
    default SmpsWriteProgram beginMusicLoad() {
        return SmpsWriteProgram.EMPTY;
    }

    /** Transiently silences all three tone channels and the noise channel. */
    default SmpsWriteProgram silenceAllPsg() {
        return SmpsWriteProgram.SILENCE_ALL_PSG;
    }

    SmpsWriteProgram activateMusic(SmpsMusicActivation activation);
}
