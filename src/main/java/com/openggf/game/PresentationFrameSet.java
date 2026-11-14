package com.openggf.game;

import java.util.Map;
import java.util.Objects;

/** Optional native-frame replacements. Banks distinguish screen-local mappings from player mappings. */
@ModApi
public record PresentationFrameSet(Map<String, Map<Integer, Pose>> banks, int[] rgb) {
    @ModApi
    public record Pose(CharacterPresentation.Image image, int hotspotX, int hotspotY) {
        public Pose {
            Objects.requireNonNull(image, "image");
            if (hotspotX < 0 || hotspotX > image.width() || hotspotY < 0 || hotspotY > image.height())
                throw new IllegalArgumentException("Hotspot outside image");
        }
    }
    public PresentationFrameSet {
        var copy = new java.util.LinkedHashMap<String, Map<Integer, Pose>>();
        banks.forEach((key, value) -> copy.put(Objects.requireNonNull(key), Map.copyOf(value)));
        banks = Map.copyOf(copy);
        if (rgb.length != 16) throw new IllegalArgumentException("Sixteen palette colours required");
        rgb = rgb.clone();
    }
    @Override public int[] rgb() { return rgb.clone(); }
}
