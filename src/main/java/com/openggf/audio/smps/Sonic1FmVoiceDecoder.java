package com.openggf.audio.smps;

/** Converts a copied S1 ROM instrument into the sequencer operator order. */
public final class Sonic1FmVoiceDecoder {
    private Sonic1FmVoiceDecoder() {}

    public static void normalizeInPlace(byte[] voice) {
        // S1 stores Op4,Op3,Op2,Op1; the sequencer consumes Op4,Op2,Op3,Op1.
        for (int group = 1; group < 25; group += 4) {
            byte middle = voice[group + 1];
            voice[group + 1] = voice[group + 2];
            voice[group + 2] = middle;
        }
    }
}
