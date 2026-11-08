package com.openggf.game;

import java.util.Map;
import java.util.Objects;

/** Optional character-owned presentation. Null slots retain the native host presentation. */
@ModApi
public record CharacterPresentation(String displayName, String hudName,
        Map<Host, Art> art, Map<Font, Map<Character, Image>> supplementaryGlyphs) {
    @ModApi public enum Host { S1, S2, S3K }
    @ModApi public enum Style { HUD, TITLE, RESULTS }
    @ModApi public record Font(Host host, Style style) {
        public Font { Objects.requireNonNull(host, "host"); Objects.requireNonNull(style, "style"); }
    }
    /** Indexed pixels use the player's palette line; zero is transparent. */
    @ModApi public record Image(int width, int height, byte[] pixels) {
        public Image {
            if (width < 1 || height < 1 || width > 256 || height > 64
                    || pixels.length != width * height) throw new IllegalArgumentException("Invalid image dimensions");
            pixels = pixels.clone();
            for (byte pixel : pixels) if (pixel < 0 || pixel > 15)
                throw new IllegalArgumentException("Presentation pixels must be palette indices 0..15");
        }
        @Override public byte[] pixels() { return pixels.clone(); }
    }
    /** Faces are indexed images, independent of native mapping/tile layouts. */
    @ModApi public record Art(Image hudLifeIcon, Image oneUpFace, Image signpostFace, Image endSignFace) {
        public Art {
            if (hudLifeIcon != null && (hudLifeIcon.width() != 16 || hudLifeIcon.height() != 16))
                throw new IllegalArgumentException("HUD life icon must be 16 by 16 pixels");
        }
    }
    public CharacterPresentation {
        if (displayName != null && (displayName.isBlank() || displayName.length() > 32))
            throw new IllegalArgumentException("Display name must contain 1..32 characters");
        if (hudName != null && (hudName.isBlank() || hudName.length() > 5))
            throw new IllegalArgumentException("HUD name must contain 1..5 characters");
        art = Map.copyOf(art);
        var copy = new java.util.LinkedHashMap<Font, Map<Character, Image>>();
        supplementaryGlyphs.forEach((font, glyphs) -> copy.put(Objects.requireNonNull(font), Map.copyOf(glyphs)));
        supplementaryGlyphs = Map.copyOf(copy);
    }
    public CharacterPresentation(String displayName) { this(displayName, null, Map.of(), Map.of()); }
}
