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

    /**
     * ROM work performed on entry to the driver's DAC idle loop, after the
     * one-shot driver init has completed.
     *
     * <p>S3K's {@code zInitAudioDriver} ends with {@code ei} and
     * {@code jp zPlayDigitalAudio} (Sound/Z80 Sound Driver.asm:550-551), so
     * the init service's own last write is {@code zStopAllSound}'s 27h.
     * Whatever {@code zPlayDigitalAudio} writes as it is entered belongs to
     * the following service window, not to the init. Drivers without a
     * distinct entry block return an empty program.</p>
     */
    default SmpsWriteProgram enterDacIdleLoop() {
        return SmpsWriteProgram.EMPTY;
    }

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
