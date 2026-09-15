package com.openggf.graphics;

import com.openggf.level.PatternDesc;
import com.openggf.level.Pattern;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

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
    public record PatternVersion(long a, long b, long c, long d) {
        static PatternVersion copy(Pattern pattern) {
            long[] words = new long[4];
            for (int i = 0; i < 64; i++) words[i / 16] |= (long) (pattern.getPixel(i % 8, i / 8) & 15) << ((i % 16) * 4);
            return new PatternVersion(words[0], words[1], words[2], words[3]);
        }
        Pattern pattern() {
            var pattern = new Pattern();
            long[] words = {a, b, c, d};
            for (int i = 0; i < 64; i++) pattern.setPixel(i % 8, i / 8, (byte) ((words[i / 16] >>> ((i % 16) * 4)) & 15));
            return pattern;
        }
    }

    interface Geometry { GLCommandable command(int cameraX, int cameraY); }

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
        Layer layer = Layer.OBJECT;
        Builder(int cameraX, int cameraY) { this.cameraX = cameraX; this.cameraY = cameraY; }
        void add(GraphicsManager graphics, int id, PatternDesc desc,
                 float x, float y, float width, float height) {
            tiles.add(new Tile(layer, id, desc.getPaletteIndex(), desc.getHFlip(), desc.getVFlip(),
                    desc.getPriority(), x - cameraX, y - cameraY, width, height,
                    graphics.isUseSpritePriorityShader(), graphics.getCurrentSpriteTileOcclusionPaletteMask(),
                    graphics.isGhostRenderEffectActive(), graphics.getGhostRenderAlpha()));
        }
    }

    private SpritePresentation() { }

    public static Frame prepare(GraphicsManager graphics, int cameraX, int cameraY, Runnable producer) {
        if (graphics.spritePresentationBuilder != null || graphics.isSpriteSatCollectionActive())
            throw new IllegalStateException("nested sprite preparation");
        Builder builder = new Builder(cameraX, cameraY);
        boolean priority = graphics.isUseSpritePriorityShader();
        int mask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
        boolean ghost = graphics.isGhostRenderEffectActive();
        float alpha = graphics.getGhostRenderAlpha();
        graphics.spritePresentationBuilder = builder;
        try {
            producer.run();
            return new Frame(builder.tiles, builder.primitives, builder.patternVersions);
        } finally {
            graphics.spritePresentationBuilder = null;
            graphics.cancelSpritePresentationCollection();
            if (ghost) graphics.beginGhostRenderEffect(alpha); else graphics.endGhostRenderEffect();
            graphics.setUseSpritePriorityShader(priority);
            graphics.setCurrentSpriteTileOcclusionPaletteMask(mask);
        }
    }

    /** Virtual player banks are mutable staging buffers, unlike stable ROM-art cache addresses. */
    public static void bindPatternBank(GraphicsManager graphics, int base, Pattern[] patterns) {
        Builder builder = graphics.spritePresentationBuilder;
        if (builder == null) return;
        for (int i = 0; i < patterns.length; i++) builder.patternVersions.put(base + i, PatternVersion.copy(patterns[i]));
    }

    public static void layer(GraphicsManager graphics, Layer layer) {
        if (graphics.spritePresentationBuilder != null) graphics.spritePresentationBuilder.layer = layer;
    }

    /** Resolve stable art addresses in the cache and restore the published generation of mutable virtual banks. */
    public static void draw(GraphicsManager graphics, Frame frame, int cameraX, int cameraY,
                            Predicate<Layer> visible) {
        boolean priority = graphics.isUseSpritePriorityShader();
        int mask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
        boolean ghost = graphics.isGhostRenderEffectActive();
        float alpha = graphics.getGhostRenderAlpha();
        PatternDesc desc = new PatternDesc();
        graphics.beginPatternAtlasBatch();
        try {
            frame.patternVersions().forEach((id, version) -> graphics.cachePatternTexture(version.pattern(), id));
        } finally { graphics.endPatternAtlasBatch(); }
        Tile previous = null;
        graphics.flushPatternBatch();
        try {
            int primitiveIndex = 0;
            for (int index = 0; index <= frame.tiles().size(); index++) {
                while (primitiveIndex < frame.primitives().size()
                        && frame.primitives().get(primitiveIndex).beforeTile() == index) {
                    var primitive = frame.primitives().get(primitiveIndex++);
                    if (visible.test(primitive.layer())) {
                        graphics.flushPatternBatch();
                        graphics.enqueueDebugLineState();
                        graphics.registerCommand(primitive.primitive().command(cameraX, cameraY));
                        graphics.enqueueDefaultShaderState();
                        previous = null;
                    }
                }
                if (index == frame.tiles().size()) break;
                Tile tile = frame.tiles().get(index);
                if (!visible.test(tile.layer())) continue;
                if (previous == null || previous.priorityShader() != tile.priorityShader()
                        || previous.occlusionMask() != tile.occlusionMask()
                        || previous.ghost() != tile.ghost() || previous.ghostAlpha() != tile.ghostAlpha()) {
                    graphics.flushPatternBatch();
                    graphics.setUseSpritePriorityShader(tile.priorityShader());
                    graphics.setCurrentSpriteTileOcclusionPaletteMask(tile.occlusionMask());
                    if (tile.ghost()) graphics.beginGhostRenderEffect(tile.ghostAlpha());
                    else graphics.endGhostRenderEffect();
                    graphics.beginPatternBatch();
                }
                previous = tile;
                graphics.setUseSpritePriorityShader(tile.priorityShader());
                graphics.setCurrentSpriteTileOcclusionPaletteMask(tile.occlusionMask());
                if (tile.ghost()) graphics.beginGhostRenderEffect(tile.ghostAlpha());
                else graphics.endGhostRenderEffect();
                desc.setPaletteIndex(tile.palette());
                desc.setHFlip(tile.hFlip());
                desc.setVFlip(tile.vFlip());
                desc.setPriority(tile.priority());
                float x = tile.x() + cameraX, y = tile.y() + cameraY;
                if (tile.width() == 8 && tile.height() == 8 && x == (int) x && y == (int) y) {
                    graphics.renderPatternWithId(tile.patternId(), desc, (int) x, (int) y);
                } else {
                    graphics.renderPatternWithIdScaled(tile.patternId(), desc, x, y, tile.width(), tile.height());
                }
            }
        } finally {
            graphics.flushPatternBatch();
            graphics.setUseSpritePriorityShader(priority);
            graphics.setCurrentSpriteTileOcclusionPaletteMask(mask);
            if (ghost) graphics.beginGhostRenderEffect(alpha); else graphics.endGhostRenderEffect();
        }
    }
}
