package com.openggf.mods.scene;

/**
 * One actual ROM stream attack, before a mod curates lanes and difficulty.
 * Melodic pitch is the ROM note byte plus the effective key/base offset;
 * DAC pitch is its raw ROM sample-note ID; raw-frequency melodic units use -1.
 * Source offset follows the parsed unit.
 */
@com.openggf.game.ModApi
public record SceneNoteEvent(int trackIndex, Kind kind, int channel, int pitch,
                             int sourceOffset, long onsetSamples, long durationSamples) {
    @com.openggf.game.ModApi
    public enum Kind { FM, PSG, DAC }

    public SceneNoteEvent {
        java.util.Objects.requireNonNull(kind, "kind");
        if (trackIndex < 0 || channel < 0 || onsetSamples < 0 || durationSamples < 0) {
            throw new IllegalArgumentException("invalid ROM note event");
        }
    }
}
