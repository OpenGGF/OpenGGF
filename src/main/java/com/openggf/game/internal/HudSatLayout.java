package com.openggf.game.internal;

import com.openggf.level.render.SpriteMappingFrame;

import java.util.List;
import java.util.Objects;

/**
 * ROM-native HUD mapping footprints, before their art is expanded into individual tiles.
 * Numeric VRAM updates (including debug digits) do not change these SAT pieces.
 * The first four frames encode normal, ring warning, timer warning, and both warnings;
 * an optional final pair encodes the bonus-stage normal and ring-warning layouts.
 */
public final class HudSatLayout {
    private final List<SpriteMappingFrame> frames;

    public HudSatLayout(List<SpriteMappingFrame> frames) {
        Objects.requireNonNull(frames, "frames");
        if (frames.size() != 4 && frames.size() != 6) {
            throw new IllegalArgumentException("HUD SAT layouts require four normal frames and an optional bonus pair");
        }
        this.frames = frames.stream()
                .map(frame -> new SpriteMappingFrame(List.copyOf(
                        Objects.requireNonNull(frame, "frame").pieces())))
                .toList();
    }

    /** Immutable frames with ROM piece order, offsets, dimensions, tile words and flags preserved. */
    public List<SpriteMappingFrame> frames() {
        return frames;
    }

    public boolean supportsBonusStage() {
        return frames.size() == 6;
    }

    /**
     * Selects an already-decoded native footprint. Warning eligibility and blink phase belong
     * to the caller; this value has no clocks or gameplay state. A bonus layout has no timer.
     */
    public SpriteMappingFrame frame(boolean timerFlash, boolean ringFlash, boolean bonusStage) {
        if (bonusStage && supportsBonusStage()) {
            return frames.get(ringFlash ? 5 : 4);
        }
        return frames.get((timerFlash ? 2 : 0) | (ringFlash ? 1 : 0));
    }
}
