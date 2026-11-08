package com.openggf.game;

import java.util.Map;
import java.util.Objects;

/** Optional character-owned presentation. Null slots retain the native host presentation. */
@ModApi
public record CharacterPresentation(String displayName, String hudName,
        Map<Host, Art> art, Map<Font, Map<Character, Image>> supplementaryGlyphs,
        Map<StageSpriteSet.Surface, StageSpriteSet> stages,
        Map<EndingSpriteSet.Surface, EndingSpriteSet> endings) {
    public CharacterPresentation(String displayName, String hudName, Map<Host, Art> art,
            Map<Font, Map<Character, Image>> supplementaryGlyphs, Map<StageSpriteSet.Surface, StageSpriteSet> stages) {
        this(displayName, hudName, art, supplementaryGlyphs, stages, Map.of());
    }
    public CharacterPresentation(String displayName, String hudName, Map<Host, Art> art,
            Map<Font, Map<Character, Image>> supplementaryGlyphs) {
        this(displayName, hudName, art, supplementaryGlyphs, Map.of());
    }
    @ModApi public enum Host { S1, S2, S3K }
    @ModApi public enum Style { HUD, TITLE, RESULTS }
    @ModApi public record Font(Host host, Style style) {
        public Font { Objects.requireNonNull(host, "host"); Objects.requireNonNull(style, "style"); }
    }
    /** Indexed pixels use the player's palette line; zero is transparent. */
    @ModApi public record Image(int width, int height, byte[] pixels) {
        public Image {
            if (width < 1 || height < 1 || width > 256 || height > 256
                    || pixels.length != width * height) throw new IllegalArgumentException("Invalid image dimensions");
            pixels = pixels.clone();
            for (byte pixel : pixels) if (pixel < 0 || pixel > 15)
                throw new IllegalArgumentException("Presentation pixels must be palette indices 0..15");
        }
        @Override public byte[] pixels() { return pixels.clone(); }
    }
    /** Faces are indexed images, independent of native mapping/tile layouts. */
    @ModApi public record Art(Image hudLifeIcon, Image oneUpFace, Image signpostFace, Image endSignFace, Sign sign, PresentationFrameSet screenFrames) {
        public Art(Image hudLifeIcon, Image oneUpFace, Image signpostFace, Image endSignFace, Sign sign) {
            this(hudLifeIcon, oneUpFace, signpostFace, endSignFace, sign, null);
        }
        public Art(Image hudLifeIcon, Image oneUpFace, Image signpostFace, Image endSignFace) {
            this(hudLifeIcon, oneUpFace, signpostFace, endSignFace, null);
        }
        public Art {
            if (hudLifeIcon != null && (hudLifeIcon.width() != 16 || hudLifeIcon.height() != 16))
                throw new IllegalArgumentException("HUD life icon must be 16 by 16 pixels");
        }
    }
    /** Semantic sign poses; null poses retain the native frame. Spin images are already oriented. */
    @ModApi public record Sign(SignFrame face, SignFrame alternate, SignFrame spin1,
            SignFrame edge, SignFrame spin3) { }
    @ModApi public record SignFrame(Image face, Backing backing) {
        public SignFrame {
            Objects.requireNonNull(face, "face");
            if (face.width() > 48 || face.height() != 32 || face.width() % 8 != 0)
                throw new IllegalArgumentException("Sign poses must be tile-aligned, at most 48 by 32");
            if (backing != null && (backing.image().width() != face.width()
                    || backing.image().height() != face.height()))
                throw new IllegalArgumentException("Sign backing must match its face");
        }
    }
    /** Backing colours are matched to the native host palette line, never the character palette. */
    @ModApi public record Backing(Image image, int paletteLine, int[] rgb) {
        public Backing {
            Objects.requireNonNull(image, "image");
            if (paletteLine < 0 || paletteLine > 3 || rgb.length != 16)
                throw new IllegalArgumentException("Backing requires a native palette line and sixteen RGB colours");
            rgb = rgb.clone();
        }
        @Override public int[] rgb() { return rgb.clone(); }
    }
    public CharacterPresentation {
        if (displayName != null && (displayName.isBlank() || displayName.length() > 32))
            throw new IllegalArgumentException("Display name must contain 1..32 characters");
        if (hudName != null && (hudName.isBlank() || hudName.length() > 5))
            throw new IllegalArgumentException("HUD name must contain 1..5 characters");
        art = Map.copyOf(art);
        stages = Map.copyOf(stages);
        endings = Map.copyOf(endings);
        var copy = new java.util.LinkedHashMap<Font, Map<Character, Image>>();
        supplementaryGlyphs.forEach((font, glyphs) -> copy.put(Objects.requireNonNull(font), Map.copyOf(glyphs)));
        supplementaryGlyphs = Map.copyOf(copy);
    }
    public CharacterPresentation(String displayName) { this(displayName, null, Map.of(), Map.of()); }
}
