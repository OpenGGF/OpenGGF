package com.openggf.game;

/**
 * Immutable fresh-entry position in ROM-native centre coordinates.
 * Values are unsigned 16-bit words; this does not authorize terrain or checkpoint edits.
 */
@ModApi
public record LevelStartPosition(int centreX, int centreY) {
    public LevelStartPosition {
        if (centreX < 0 || centreX > 0xFFFF || centreY < 0 || centreY > 0xFFFF) {
            throw new IllegalArgumentException("Level start centre must fit unsigned native 16-bit coordinates");
        }
    }
}
