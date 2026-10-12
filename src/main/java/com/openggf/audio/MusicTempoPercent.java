package com.openggf.audio;

/** Bounds for creator-controlled ROM music pacing; gameplay and SFX are unaffected. */
public final class MusicTempoPercent {
    private MusicTempoPercent() { }

    public static void requireValid(int percent) {
        if (percent < 25 || percent > 100) {
            throw new IllegalArgumentException("music tempo must be between 25 and 100 percent");
        }
    }
}
