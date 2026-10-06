package com.openggf.game.presentation;

import com.openggf.game.ModApi;

import java.util.List;
import java.util.Objects;

/** Complete immutable visible scene values. Art references resolve exclusively in the local ROM catalog. */
@ModApi
public record ScenePresentationFrame(long revision, int act, int width, int height,
                                     int cameraX, int cameraY, int backdropArgb,
                                     int[] paletteArgb, List<Tile> tiles, List<Primitive> primitives) {
    public static final int SCHEMA = 1;
    public static final int MAX_TILES = 65_536;
    public static final int MAX_PRIMITIVES = 4_096;

    @ModApi public enum Layer { BACKGROUND, FOREGROUND, PLAYER, OBJECT, RINGS }

    /** Stable decoding recipe and index in that recipe; never a host allocation or texture address. */
    @ModApi public record ArtReference(String recipe, int tile) {
        public ArtReference {
            Objects.requireNonNull(recipe, "recipe");
            if (!recipe.matches("[a-zA-Z0-9_.:/-]{1,96}") || tile < 0 || tile > 65_535) {
                throw new IllegalArgumentException("Invalid ROM art reference");
            }
        }
    }

    /** Painter-order tile, including VDP scanline clips and foreground occlusion semantics. */
    @ModApi public record Tile(Layer layer, ArtReference art, int palette,
                              boolean hFlip, boolean vFlip, boolean priority,
                              int x, int y, int width, int height,
                              int rowStart, int rowEnd, int occlusionMask, int alpha) {
        public Tile {
            Objects.requireNonNull(layer, "layer");
            Objects.requireNonNull(art, "art");
            if (palette < 0 || palette > 63 || x < -65_536 || x > 65_536 || y < -65_536 || y > 65_536
                    || width < 1 || width > 1_024 || height < 1 || height > 1_024
                    || rowStart < 0 || rowEnd > 8 || rowStart >= rowEnd
                    || occlusionMask < 0 || occlusionMask > 15 || alpha < 0 || alpha > 255) {
                throw new IllegalArgumentException("Invalid scene tile");
            }
        }

        public Tile translated(int dx, int dy) {
            return new Tile(layer, art, palette, hFlip, vFlip, priority, x + dx, y + dy,
                    width, height, rowStart, rowEnd, occlusionMask, alpha);
        }
    }

    @ModApi public enum PrimitiveKind { RECTANGLE, VERTEX }
    @ModApi public record Vertex(int x1, int y1, int x2, int y2, int argb) {
        public Vertex {
            if (x1 < -65_536 || x1 > 65_536 || y1 < -65_536 || y1 > 65_536
                    || x2 < -65_536 || x2 > 65_536 || y2 < -65_536 || y2 > 65_536) {
                throw new IllegalArgumentException("Invalid scene vertex");
            }
        }
    }

    /** Procedural geometry emitted by the same production object pass, inserted before a tile index. */
    @ModApi public record Primitive(int beforeTile, PrimitiveKind kind, int method, List<Vertex> vertices) {
        public Primitive {
            Objects.requireNonNull(kind, "kind");
            vertices = List.copyOf(vertices);
            if (beforeTile < 0 || beforeTile > MAX_TILES || method < 0 || method > 3
                    || vertices.isEmpty() || vertices.size() > 256) {
                throw new IllegalArgumentException("Invalid scene primitive");
            }
        }
    }

    public ScenePresentationFrame {
        if (revision < 0 || act < 0 || act > 255 || width < 1 || width > 800 || height < 1 || height > 512
                || cameraX < -65_536 || cameraX > 65_536 || cameraY < -65_536 || cameraY > 65_536) {
            throw new IllegalArgumentException("Invalid scene header");
        }
        paletteArgb = paletteArgb.clone();
        tiles = List.copyOf(tiles);
        primitives = List.copyOf(primitives);
        if (paletteArgb.length < 16 || paletteArgb.length > 1_024 || paletteArgb.length % 16 != 0
                || tiles.size() > MAX_TILES || primitives.size() > MAX_PRIMITIVES) {
            throw new IllegalArgumentException("Scene exceeds limits");
        }
        int previous = -1;
        for (Tile tile : tiles) {
            if (tile.palette() * 16 >= paletteArgb.length) throw new IllegalArgumentException("Missing palette");
        }
        for (Primitive primitive : primitives) {
            if (primitive.beforeTile() < previous || primitive.beforeTile() > tiles.size()) {
                throw new IllegalArgumentException("Unordered primitive insertion");
            }
            previous = primitive.beforeTile();
        }
    }

    @Override public int[] paletteArgb() { return paletteArgb.clone(); }
}
