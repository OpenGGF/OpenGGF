package com.openggf.mods;

import java.util.Objects;

/** Prepared PCM; loop (0,0) means nonlooping, otherwise [start,end) is the loop. */
public record PreparedTrack(TrackKey key, PcmData pcm, long loopStartFrame, long loopEndFrame,
                            float gain, boolean tempoEffects, String sourceSha256) {
    public PreparedTrack {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(pcm, "pcm");
        boolean nonLooping = loopStartFrame == 0 && loopEndFrame == 0;
        if (!nonLooping && (loopStartFrame < 0 || loopEndFrame <= loopStartFrame
                || loopEndFrame > pcm.frameCount())) {
            throw new IllegalArgumentException("Prepared loop range must lie within decoded PCM");
        }
        ModAudioValidation.requireGain(gain);
        Objects.requireNonNull(sourceSha256, "sourceSha256");
        if (!sourceSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid source SHA-256");
    }

    public boolean looping() { return loopEndFrame > loopStartFrame; }
}
