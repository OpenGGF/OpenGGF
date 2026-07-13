package com.openggf.mods;

import java.io.IOException;
import java.util.Objects;

/** Shared pure validation for loop points after an audio asset has been decoded. */
public final class ModAudioAssetValidation {
    private ModAudioAssetValidation() {
    }

    /** Decodes one bounded source and validates its loop points without retaining PCM. */
    public static long decodeAndValidate(ModAudioTrack track, byte[] encoded,
                                         com.openggf.io.ModInputLimits limits) throws IOException {
        Objects.requireNonNull(encoded, "encoded");
        PcmData decoded = new PcmDecoder().decode(track.assetPath(), encoded,
                Objects.requireNonNull(limits, "limits"));
        validateLoop(track, decoded.frameCount());
        return decoded.frameCount();
    }

    /** Decodes one bounded one-shot source without retaining PCM. */
    public static long decodeAndValidate(ModAudioSfx sfx, byte[] encoded,
                                         com.openggf.io.ModInputLimits limits) throws IOException {
        Objects.requireNonNull(sfx, "sfx");
        Objects.requireNonNull(encoded, "encoded");
        PcmData decoded = new PcmDecoder().decodeSfx(sfx.assetPath(), encoded,
                Objects.requireNonNull(limits, "limits"));
        return decoded.frameCount();
    }

    public static void validateLoop(ModAudioTrack track, long decodedSourceFrames) throws IOException {
        Objects.requireNonNull(track, "track");
        if (decodedSourceFrames < 0) {
            throw new IllegalArgumentException("decodedSourceFrames must be nonnegative");
        }
        if (!track.loop()) {
            return;
        }
        long end = track.loopEndFrame().orElse(decodedSourceFrames);
        if (track.loopStartFrame() >= end || end > decodedSourceFrames) {
            throw new IOException("Loop metadata exceeds decoded source frames");
        }
    }
}
