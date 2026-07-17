package com.openggf.tools.fbzvisual;

import com.openggf.graphics.RgbaImage;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Rejects state- or pixel-level title/fade/overlay contamination. */
final class FbzVisualVisibilityVerifier {

    private FbzVisualVisibilityVerifier() {
    }

    static void verifyState(Map<String, Object> state) {
        Objects.requireNonNull(state, "state");
        requireEquals(state, "game_mode", "LEVEL");
        requireEquals(state, "gameplay_context_active", true);
        requireEquals(state, "title_card_overlay_active", false);
        requireEquals(state, "title_card_complete", true);
        requireEquals(state, "fade_active", false);
        requireEquals(state, "overlays_disabled", true);
        Object alpha = state.get("fade_alpha");
        if (!(alpha instanceof Number number) || Float.compare(number.floatValue(), 0.0f) != 0) {
            throw new IllegalStateException("FBZ visual evidence requires fade_alpha=0, got " + alpha);
        }
    }

    static void verifyGameplayPixels(RgbaImage crop) {
        Objects.requireNonNull(crop, "crop");
        int dark = 0;
        Set<Integer> colours = new HashSet<>();
        for (int argb : crop.pixels()) {
            int rgb = argb & 0x00FFFFFF;
            colours.add(rgb);
            int r = (rgb >>> 16) & 0xFF;
            int g = (rgb >>> 8) & 0xFF;
            int b = rgb & 0xFF;
            if (r <= 8 && g <= 8 && b <= 8) dark++;
        }
        double darkRatio = (double) dark / crop.pixels().length;
        if (darkRatio >= 0.70) {
            throw new IllegalStateException("FBZ gameplay crop is title/fade-dark: ratio=" + darkRatio);
        }
        if (colours.size() < 16) {
            throw new IllegalStateException("FBZ gameplay crop lacks visible level detail: colours="
                    + colours.size());
        }
    }

    private static void requireEquals(Map<String, Object> state, String key, Object expected) {
        Object actual = state.get(key);
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException("FBZ visual evidence requires " + key + "="
                    + expected + ", got " + actual);
        }
    }
}
