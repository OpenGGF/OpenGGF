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

    /**
     * One prepared tile. {@code rowStart}/{@code rowEnd} are the visible pixel rows of an
     * 8x8 tile in screen order ({@code 0}/{@code 8} when whole): a VDP sprite mask can
     * hide single scanlines.
     */
    public record Tile(Layer layer, int patternId, int palette, boolean hFlip, boolean vFlip,
                       boolean priority, float x, float y, float width, float height,
                       boolean priorityShader, int occlusionMask, boolean ghost, float ghostAlpha,
                       int rowStart, int rowEnd) {
        public Tile(Layer layer, int patternId, int palette, boolean hFlip, boolean vFlip,
                    boolean priority, float x, float y, float width, float height,
                    boolean priorityShader, int occlusionMask, boolean ghost, float ghostAlpha) {
            this(layer, patternId, palette, hFlip, vFlip, priority, x, y, width, height,
                    priorityShader, occlusionMask, ghost, ghostAlpha, 0, 8);
        }

        public boolean rowClipped() {
            return rowStart > 0 || rowEnd < 8;
        }
    }

    /** A version of a mutable virtual DPLC slot, packed as four immutable groups of sixteen pixels. */
    public record PatternVersion(long a, long b, long c, long d) { }

    public record Attributes(int palette, boolean hFlip, boolean vFlip, boolean priority) { }

    public interface Geometry {
        GLCommandable command(int cameraX, int cameraY);
        /** Reconstruct for the supplied render host; existing geometry keeps its legacy dispatch. */
        default GLCommandable command(GraphicsManager graphics, int cameraX, int cameraY) {
            return command(cameraX, cameraY);
        }
    }

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
        int firstPatternVersion = Integer.MAX_VALUE;
        int lastPatternVersion = Integer.MIN_VALUE;
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
        void addRows(GraphicsManager graphics, int id, Object descriptor,
                     int x, int y, int rowStart, int rowEnd) {
            Attributes desc = descriptorDecoder.apply(descriptor);
            tiles.add(new Tile(layer, id, desc.palette(), desc.hFlip(), desc.vFlip(),
                    desc.priority(), x - cameraX, y - cameraY, 8, 8,
                    graphics.isUseSpritePriorityShader(), graphics.getCurrentSpriteTileOcclusionPaletteMask(),
                    graphics.isGhostRenderEffectActive(), graphics.getGhostRenderAlpha(), rowStart, rowEnd));
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
                // Most tiles reference static ROM art. Avoid boxing every static
                // address merely to discover it has no mutable DPLC generation.
                int id = tile.patternId();
                if (id < builder.firstPatternVersion || id > builder.lastPatternVersion) continue;
                PatternVersion version = builder.patternVersions.get(id);
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

    /** Draws the visible pixel rows of a prepared 8x8 tile whose top is at {@code y}. */
    public static void renderTileRows(GraphicsManager graphics, Tile tile, Object descriptor, int x, int y) {
        graphics.renderPatternRowsWithId(tile.patternId(), descriptor, tile.palette(), tile.hFlip(), tile.vFlip(),
                tile.priority(), x, y, tile.rowStart(), tile.rowEnd());
    }

    public static boolean isPreparing(GraphicsManager graphics) {
        return graphics.spritePresentationBuilder != null;
    }

    public static void bindPatternVersions(GraphicsManager graphics, Map<Integer, PatternVersion> versions) {
        Builder builder = graphics.spritePresentationBuilder;
        if (builder != null) {
            builder.patternVersions.putAll(versions);
            for (int id : versions.keySet()) {
                builder.firstPatternVersion = Math.min(builder.firstPatternVersion, id);
                builder.lastPatternVersion = Math.max(builder.lastPatternVersion, id);
            }
        }
    }

    /** Bind directly into the current producer, without an intermediate bank map. */
    public static void bindPatternVersion(GraphicsManager graphics, int id, PatternVersion version) {
        Builder builder = graphics.spritePresentationBuilder;
        if (builder != null) {
            builder.patternVersions.put(id, version);
            builder.firstPatternVersion = Math.min(builder.firstPatternVersion, id);
            builder.lastPatternVersion = Math.max(builder.lastPatternVersion, id);
        }
    }

    public static void layer(GraphicsManager graphics, Layer layer) {
        if (graphics.spritePresentationBuilder != null) graphics.spritePresentationBuilder.layer = layer;
    }

    /** Engine presentation bridge; atlas residency is not part of the creator API. */
    public static PatternVersion patternSample(GraphicsManager graphics, int patternId) {
        return graphics.scenePatternSample(patternId);
    }

    /** Graphics-owned CPU geometry; game/network presenters choose their own wire representation. */
    public enum PrimitiveKind { RECTANGLE, VERTEX }
    public record Vertex(int x1, int y1, int x2, int y2, int argb) { }
    public record PrimitiveGeometry(PrimitiveKind kind, int method, List<Vertex> vertices) {
        public PrimitiveGeometry { vertices = List.copyOf(vertices); }
    }

    /** GL command records stay package-private and outside the creator contract. */
    public static PrimitiveGeometry geometry(Primitive primitive) {
        int method;
        List<GLCommand.PresentationPrimitive> values;
        if (primitive.primitive() instanceof GLCommand.PresentationPrimitive value) {
            method = Math.max(0, value.method()); values = List.of(value);
        } else if (primitive.primitive() instanceof GLCommandGroup.PresentationGroup group) {
            method = group.method(); values = group.vertices();
        } else throw new IllegalStateException("Opaque geometry cannot be projected");
        var kind = values.getFirst().type() == GLCommand.CommandType.RECTI
                ? PrimitiveKind.RECTANGLE : PrimitiveKind.VERTEX;
        var vertices = new ArrayList<Vertex>();
        for (var value : values) {
            if (value.blend() != GLCommand.BlendType.SOLID && value.blend() != GLCommand.BlendType.ONE_MINUS_SRC_ALPHA) {
                throw new IllegalStateException("Unsupported scene primitive blend");
            }
            int color = Math.round(value.alpha() * 255) << 24 | Math.round(value.red() * 255) << 16
                    | Math.round(value.green() * 255) << 8 | Math.round(value.blue() * 255);
            vertices.add(new Vertex(value.x1(), value.y1(), value.x2(), value.y2(), color));
        }
        return new PrimitiveGeometry(kind, method, vertices);
    }

    /** Samples the graphics upload's existing 64 palette-index bytes, independent of gameplay types. */
    static PatternVersion patternVersion(byte[] pixels) {
        return new PatternVersion(pack(pixels, 0), pack(pixels, 16), pack(pixels, 32), pack(pixels, 48));
    }

    private static long pack(byte[] pixels, int first) {
        long value = 0;
        for (int i = 0; i < 16; i++) value |= (long) (pixels[first + i] & 15) << (4 * i);
        return value;
    }
}
