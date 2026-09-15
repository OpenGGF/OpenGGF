package com.openggf.graphics;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** CPU sprite-table presentation. Contains logical ROM-cache addresses, never GL commands or atlas UVs. */
public final class SpritePresentation {
    public enum Layer {
        PLAYER, OBJECT, RINGS, HUD, HUD_COUNTERS;
        public boolean isHud() { return this == HUD || this == HUD_COUNTERS; }
    }

    public record Tile(Layer layer, int patternId, int palette, boolean hFlip, boolean vFlip,
                       boolean priority, float x, float y, float width, float height,
                       boolean priorityShader, int occlusionMask, boolean ghost, float ghostAlpha) { }

    /** A version of a mutable virtual DPLC slot, packed as four immutable groups of sixteen pixels. */
    public record PatternVersion(long a, long b, long c, long d) { }

    public record Attributes(int palette, boolean hFlip, boolean vFlip, boolean priority) { }

    public interface Geometry { GLCommandable command(int cameraX, int cameraY); }

    public record Primitive(int beforeTile, Layer layer, Geometry primitive) { }

    public record Frame(List<Tile> tiles, List<Primitive> primitives, Map<Integer, PatternVersion> patternVersions) {
        public Frame { tiles = List.copyOf(tiles); primitives = List.copyOf(primitives); patternVersions = Map.copyOf(patternVersions); }
        public Frame(List<Tile> tiles) { this(tiles, List.of(), Map.of()); }
        public static Frame empty() { return new Frame(List.of()); }
    }

    /** Mutable only while the loop-tail producer runs; finish makes a defensive immutable copy. */
    static final class Builder {
        final List<Tile> tiles = new ArrayList<>();
        final List<Primitive> primitives = new ArrayList<>();
        final Map<Integer, PatternVersion> patternVersions = new HashMap<>();
        final int cameraX, cameraY;
        final Function<Object, Attributes> descriptorDecoder;
        Layer layer = Layer.OBJECT;
        Builder(int cameraX, int cameraY, Function<Object, Attributes> descriptorDecoder) {
            this.cameraX = cameraX; this.cameraY = cameraY; this.descriptorDecoder = descriptorDecoder;
        }
        void add(GraphicsManager graphics, int id, Object descriptor,
                 float x, float y, float width, float height) {
            Attributes desc = descriptorDecoder.apply(descriptor);
            tiles.add(new Tile(layer, id, desc.palette(), desc.hFlip(), desc.vFlip(),
                    desc.priority(), x - cameraX, y - cameraY, width, height,
                    graphics.isUseSpritePriorityShader(), graphics.getCurrentSpriteTileOcclusionPaletteMask(),
                    graphics.isGhostRenderEffectActive(), graphics.getGhostRenderAlpha()));
        }
    }

    private SpritePresentation() { }

    /** The owning renderer decodes its opaque descriptor; this buffer knows only primitive attributes. */
    public static Frame prepare(GraphicsManager graphics, int cameraX, int cameraY, Runnable producer,
                                Function<Object, Attributes> descriptorDecoder) {
        if (graphics.spritePresentationBuilder != null || graphics.isSpriteSatCollectionActive())
            throw new IllegalStateException("nested sprite preparation");
        Builder builder = new Builder(cameraX, cameraY, descriptorDecoder);
        boolean priority = graphics.isUseSpritePriorityShader();
        int mask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
        boolean ghost = graphics.isGhostRenderEffectActive();
        float alpha = graphics.getGhostRenderAlpha();
        graphics.spritePresentationBuilder = builder;
        try {
            producer.run();
            // This is the displayed sprite table's art, not a snapshot of every
            // staging-bank slot. Unreferenced DPLC tails retain arbitrary older
            // animation data and cannot affect this frame's presentation.
            Map<Integer, PatternVersion> referencedPatterns = new HashMap<>();
            for (Tile tile : builder.tiles) {
                PatternVersion version = builder.patternVersions.get(tile.patternId());
                if (version != null) referencedPatterns.put(tile.patternId(), version);
            }
            return new Frame(builder.tiles, builder.primitives, referencedPatterns);
        } finally {
            graphics.spritePresentationBuilder = null;
            graphics.cancelSpritePresentationCollection();
            if (ghost) graphics.beginGhostRenderEffect(alpha); else graphics.endGhostRenderEffect();
            graphics.setUseSpritePriorityShader(priority);
            graphics.setCurrentSpriteTileOcclusionPaletteMask(mask);
        }
    }

    public static boolean isPreparing(GraphicsManager graphics) {
        return graphics.spritePresentationBuilder != null;
    }

    public static void bindPatternVersions(GraphicsManager graphics, Map<Integer, PatternVersion> versions) {
        Builder builder = graphics.spritePresentationBuilder;
        if (builder != null) builder.patternVersions.putAll(versions);
    }

    public static void layer(GraphicsManager graphics, Layer layer) {
        if (graphics.spritePresentationBuilder != null) graphics.spritePresentationBuilder.layer = layer;
    }

}
