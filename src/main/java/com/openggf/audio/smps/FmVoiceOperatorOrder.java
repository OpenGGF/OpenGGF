package com.openggf.audio.smps;

/** Converts a copied S1 ROM instrument into the sequencer operator order. */
public final class FmVoiceOperatorOrder {
    private FmVoiceOperatorOrder() {}

    public static void swapMiddleOperatorsInPlace(byte[] voice) {
        // Swap positions 2 and 3 in each group; callers select the required source order.
        for (int group = 1; group < 25; group += 4) {
            byte middle = voice[group + 1];
            voice[group + 1] = voice[group + 2];
            voice[group + 2] = middle;
        }
    }
}
