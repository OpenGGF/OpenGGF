package com.openggf.game;

import java.util.Map;
import java.util.Objects;

/** Optional indexed frames keyed by the host stage's unchanged mapping-frame IDs. */
@ModApi
public record StageSpriteSet(Map<Integer, Frame> frames) {
    @ModApi public enum Surface { S2_HALF_PIPE, S3K_BLUE_SPHERE, S3K_SLOT_MACHINE }
    @ModApi public record Frame(CharacterPresentation.Image image, int hotspotX, int hotspotY) {
        public Frame {
            Objects.requireNonNull(image, "image");
            if (hotspotX < -256 || hotspotX > 256 || hotspotY < -256 || hotspotY > 256)
                throw new IllegalArgumentException("Stage hotspot outside bounded image space");
        }
    }
    public StageSpriteSet {
        frames = Map.copyOf(frames);
        if (frames.isEmpty() || frames.size() > 256 || frames.keySet().stream().anyMatch(i -> i < 0 || i > 255))
            throw new IllegalArgumentException("Stage frames require native IDs 0..255");
    }
}
