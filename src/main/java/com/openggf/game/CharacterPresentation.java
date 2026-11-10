package com.openggf.game;

import java.util.Map;
import java.util.Objects;

/** Optional character-owned presentation. Null slots retain the native host presentation. */
@ModApi
public record CharacterPresentation(String displayName, String hudName,
        Map<Host, Art> art, Map<Font, Map<Character, Image>> supplementaryGlyphs,
        Map<StageSpriteSet.Surface, StageSpriteSet> stages,
        Map<EndingSpriteSet.Surface, EndingSpriteSet> endings, Map<Host, ReelFace> reelFaces,
        Map<CompetitionSurface, CompetitionPortrait> competitionPortraits, Map<Host, TitleMascot> titleMascots) {
    public CharacterPresentation(String displayName, String hudName, Map<Host, Art> art,
            Map<Font, Map<Character, Image>> supplementaryGlyphs, Map<StageSpriteSet.Surface, StageSpriteSet> stages,
            Map<EndingSpriteSet.Surface, EndingSpriteSet> endings, Map<Host, ReelFace> reelFaces) {
        this(displayName, hudName, art, supplementaryGlyphs, stages, endings, reelFaces, Map.of(), Map.of());
    }
    public CharacterPresentation withTitleMascots(Map<Host, TitleMascot> mascots) {
        return new CharacterPresentation(displayName, hudName, art, supplementaryGlyphs, stages, endings, reelFaces,
                competitionPortraits, mascots);
    }
    public CharacterPresentation withCompetitionPortraits(Map<CompetitionSurface, CompetitionPortrait> portraits) {
        return new CharacterPresentation(displayName, hudName, art, supplementaryGlyphs, stages, endings, reelFaces, portraits,
                titleMascots);
    }
    @ModApi public enum CompetitionSurface { S3K_PLAYER, S3K_RECORD }
    /** Native origin relative to image top-left; an independent sixteen-colour palette. */
    @ModApi public record CompetitionPortrait(PresentationFrameSet.Pose pose, int[] rgb) {
        public CompetitionPortrait {
            Objects.requireNonNull(pose, "pose");
            if (pose.image().width() > 64 || pose.image().height() > 64)
                throw new IllegalArgumentException("Competition portraits must fit 64 by 64 pixels");
            if (rgb.length != 16) throw new IllegalArgumentException("Sixteen portrait colours required");
            rgb = rgb.clone();
        }
        @Override public int[] rgb() { return rgb.clone(); }
    }
    public CharacterPresentation(String displayName, String hudName, Map<Host, Art> art,
            Map<Font, Map<Character, Image>> supplementaryGlyphs, Map<StageSpriteSet.Surface, StageSpriteSet> stages,
            Map<EndingSpriteSet.Surface, EndingSpriteSet> endings) {
        this(displayName, hudName, art, supplementaryGlyphs, stages, endings, Map.of());
    }
    public CharacterPresentation withReelFaces(Map<Host, ReelFace> faces) {
        return new CharacterPresentation(displayName, hudName, art, supplementaryGlyphs, stages, endings, faces,
                competitionPortraits, titleMascots);
    }
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
    /** Head portraits in native 32 by 32 reel cells. Palette is local to these portraits.
     * A null powered face retains the native powered symbol; rewards remain host-owned. */
    @ModApi public record ReelFace(Image face, Image poweredFace, int[] rgb, int[] poweredRgb) {
        public ReelFace {
            Objects.requireNonNull(face, "face");
            for (Image image : new Image[] {face, poweredFace})
                if (image != null && (image.width() != 32 || image.height() != 32))
                    throw new IllegalArgumentException("Reel faces must be 32 by 32 pixels");
            if (rgb.length != 16) throw new IllegalArgumentException("Sixteen reel colours required");
            rgb = rgb.clone();
            if (poweredFace != null && (poweredRgb == null || poweredRgb.length != 16))
                throw new IllegalArgumentException("Sixteen powered reel colours required");
            poweredRgb = poweredRgb == null ? null : poweredRgb.clone();
        }
        @Override public int[] rgb() { return rgb.clone(); }
        @Override public int[] poweredRgb() { return poweredRgb == null ? null : poweredRgb.clone(); }
    }
    /** Native title selectors and hotspots; timing, logo and occlusion stay host-owned.
     * S1 uses BODY 0..7. S2 uses BODY and HAND with native Map_0E selectors.
     * S3K uses PLANE_LEFT/PLANE_RIGHT 1..13 (160x224 halves) and FINGER/WINK native sprite selectors. */
    @ModApi public enum TitlePart { BODY, HAND, PLANE_LEFT, PLANE_RIGHT, FINGER, WINK }
    /** Native title bank. swapS2Slots requests that a configured creator pair exchange the native S2
     * Sonic/Tails presentation slots. Only the lead bank requests the exchange;
     * both banks must exist. Selectors, hotspots, rise paths and occlusion then
     * belong to the destination slot. Other hosts ignore this option. */
    @ModApi public record TitleMascot(Map<TitlePart, StageSpriteSet> parts, Map<Integer, TitlePlaneMask> planeMasks, int[] rgb, boolean swapS2Slots) {
        public TitleMascot(Map<TitlePart, StageSpriteSet> parts, Map<Integer, TitlePlaneMask> planeMasks, int[] rgb) {
            this(parts, planeMasks, rgb, false);
        }
        public TitleMascot {
            parts = Map.copyOf(parts);
            planeMasks = Map.copyOf(planeMasks);
            if (rgb.length != 16) throw new IllegalArgumentException("Sixteen title colours required");
            rgb = rgb.clone();
        }
        @Override public int[] rgb() { return rgb.clone(); }
    }
    /** Native pixels to replace in a 320x224 title plane; zero mask bytes keep host art.
     * The replacement halves must leave logo/emblem pixels transparent. */
    @ModApi public record TitlePlaneMask(byte[] pixels, byte[] clearedPixels) {
        /** Existing masks clear to transparency. Replacement indices refer to each native
         * tile's palette line; they may retain an opaque aperture while clearing outside it. */
        public TitlePlaneMask(byte[] pixels) { this(pixels, new byte[320*224]); }
        public TitlePlaneMask {
            if (pixels.length != 320*224 || clearedPixels.length != 320*224)
                throw new IllegalArgumentException("Title masks must be 320x224");
            pixels = pixels.clone();
            clearedPixels = clearedPixels.clone();
            for (int i=0;i<pixels.length;i++) {
                if (pixels[i] != 0 && pixels[i] != 1)
                    throw new IllegalArgumentException("Title ownership mask is binary");
                if (clearedPixels[i] < 0 || clearedPixels[i] > 15)
                    throw new IllegalArgumentException("Title cleared pixel indices must be 0..15");
                if (pixels[i] == 0 && clearedPixels[i] != 0)
                    throw new IllegalArgumentException("Unowned title pixels cannot have a replacement index");
            }
        }
        @Override public byte[] pixels() { return pixels.clone(); }
        @Override public byte[] clearedPixels() { return clearedPixels.clone(); }
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
        reelFaces = Map.copyOf(reelFaces);
        competitionPortraits = Map.copyOf(competitionPortraits);
        titleMascots = Map.copyOf(titleMascots);
        var copy = new java.util.LinkedHashMap<Font, Map<Character, Image>>();
        supplementaryGlyphs.forEach((font, glyphs) -> copy.put(Objects.requireNonNull(font), Map.copyOf(glyphs)));
        supplementaryGlyphs = Map.copyOf(copy);
    }
    public CharacterPresentation(String displayName) { this(displayName, null, Map.of(), Map.of()); }
}
