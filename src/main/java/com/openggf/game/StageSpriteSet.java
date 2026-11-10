package com.openggf.game;

import java.util.Map;
import java.util.Objects;

/**
 * Optional indexed frames keyed by unchanged native mapping IDs (0..255).
 * Orientation helpers add bounded compiler-only slots; native selectors never change.
 * The one-component record and original constructor remain source-compatible.
 */
@ModApi
public record StageSpriteSet(Map<Integer, Frame> frames) {
    @ModApi public enum Surface { S2_HALF_PIPE, S3K_BLUE_SPHERE, S3K_SLOT_MACHINE,
        /** Native miniature body mapping IDs; missing IDs retain archetype art. */
        S3K_COMPETITION,
        /** Native miniature appendage IDs; transparent authored frames suppress tails. */
        S3K_COMPETITION_TAIL }
    @ModApi public record Frame(CharacterPresentation.Image image, int hotspotX, int hotspotY) {
        public Frame {
            Objects.requireNonNull(image, "image");
            if (hotspotX < -256 || hotspotX > 256 || hotspotY < -256 || hotspotY > 256)
                throw new IllegalArgumentException("Stage hotspot outside bounded image space");
        }
    }
    /** Dedicated final-orientation drawings for phases otherwise mirrored horizontally. */
    public StageSpriteSet withMirroredFrames(Map<Integer, Frame> drawings) {
        return withOrientationFrames(true, false, drawings);
    }

    /**
     * Final screen-oriented pixels, already posed for these native flip flags.
     * The host keeps its native mapping ID/timer and suppresses only the authored
     * orientation's flips. Missing variants retain the original native draw rules.
     * Keys are native IDs; the engine assigns separate bounded compiler slots.
     */
    public StageSpriteSet withOrientationFrames(boolean horizontalFlip, boolean verticalFlip,
                                               Map<Integer, Frame> drawings) {
        Objects.requireNonNull(drawings, "drawings");
        if (!horizontalFlip && !verticalFlip)
            throw new IllegalArgumentException("Orientation override must name a flip");
        var combined = new java.util.HashMap<>(frames);
        int bank = (horizontalFlip ? 256 : 0) + (verticalFlip ? 512 : 0);
        drawings.forEach((id, frame) -> {
            if (id < 0 || id > 255 || !frames.containsKey(id))
                throw new IllegalArgumentException("Orientation drawing needs a native base ID");
            combined.put(bank + id, Objects.requireNonNull(frame, "frame"));
        });
        return new StageSpriteSet(combined);
    }

    public StageSpriteSet {
        var copied = Map.copyOf(frames);
        frames = copied;
        if (frames.isEmpty() || frames.size() > 1024 || frames.keySet().stream().anyMatch(i -> i < 0 || i > 1023
                || i >= 256 && !copied.containsKey(i & 255)))
            throw new IllegalArgumentException("Stage frames require native IDs 0..255; orientation slots need their base frame");
    }
}
