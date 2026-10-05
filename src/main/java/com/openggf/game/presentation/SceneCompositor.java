package com.openggf.game.presentation;

import java.util.Arrays;

/** Pure rasterizer for the engine's prepared tile values and native foreground priority mask. */
public final class SceneCompositor {
    private SceneCompositor() { }

    public static SceneImage compose(ScenePresentationFrame frame, RomSceneArtCatalog art, int offsetX, int offsetY) {
        if (offsetX < -32_768 || offsetX > 32_768 || offsetY < -32_768 || offsetY > 32_768) {
            throw new IllegalArgumentException("Survey offset exceeds limits");
        }
        int[] pixels = new int[frame.width() * frame.height()];
        Arrays.fill(pixels, frame.backdropArgb());
        int[] priorities = new int[pixels.length]; Arrays.fill(priorities, -1);
        int[] palette = frame.paletteArgb();
        int primitive = 0;
        for (int index = 0; index <= frame.tiles().size(); index++) {
            while (primitive < frame.primitives().size() && frame.primitives().get(primitive).beforeTile() == index) {
                drawPrimitive(frame.primitives().get(primitive++), frame.width(), frame.height(), pixels, offsetX, offsetY);
            }
            if (index == frame.tiles().size()) break;
            var tile = frame.tiles().get(index);
            var pattern = art.resolve(tile.art());
            int left = tile.x() - offsetX, top = tile.y() - offsetY;
            int startX = Math.max(0, left), endX = Math.min(frame.width(), left + tile.width());
            int startY = Math.max(0, top + (tile.rowStart() * tile.height() + 7) / 8);
            int endY = Math.min(frame.height(), top + (tile.rowEnd() * tile.height() + 7) / 8);
            boolean terrain = tile.layer() == ScenePresentationFrame.Layer.BACKGROUND
                    || tile.layer() == ScenePresentationFrame.Layer.FOREGROUND;
            for (int y = startY; y < endY; y++) {
                int py = (y - top) * 8 / tile.height(); if (tile.vFlip()) py = 7 - py;
                for (int x = startX; x < endX; x++) {
                    int px = (x - left) * 8 / tile.width(); if (tile.hFlip()) px = 7 - px;
                    int color = RomSceneArtCatalog.pixel(pattern, px, py);
                    if (color == 0) continue;
                    int destination = y * frame.width() + x;
                    if (!terrain && !tile.priority() && priorities[destination] >= 0
                            && (tile.occlusionMask() & (1 << priorities[destination])) != 0) continue;
                    pixels[destination] = blend(pixels[destination], palette[tile.palette() * 16 + color], tile.alpha());
                    if (tile.layer() == ScenePresentationFrame.Layer.FOREGROUND && tile.priority()) {
                        priorities[destination] = tile.palette() & 3;
                    }
                }
            }
        }
        return new SceneImage(frame.width(), frame.height(), pixels);
    }

    private static void drawPrimitive(ScenePresentationFrame.Primitive primitive, int width, int height,
                                      int[] pixels, int dx, int dy) {
        if (primitive.kind() == ScenePresentationFrame.PrimitiveKind.RECTANGLE) {
            for (var vertex : primitive.vertices()) {
                int x0 = Math.max(0, Math.min(vertex.x1(), vertex.x2()) - dx);
                int x1 = Math.min(width, Math.max(vertex.x1(), vertex.x2()) - dx);
                int y0 = Math.max(0, Math.min(vertex.y1(), vertex.y2()) - dy);
                int y1 = Math.min(height, Math.max(vertex.y1(), vertex.y2()) - dy);
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    int index = y * width + x; pixels[index] = blend(pixels[index], vertex.argb(), vertex.argb() >>> 24);
                }
            }
            return;
        }
        var vertices = primitive.vertices();
        for (int i = 0; i < vertices.size(); i++) {
            var vertex = vertices.get(i);
            if (primitive.method() == 0) {
                plot(pixels, width, height, vertex.x1() - dx, vertex.y1() - dy, vertex.argb());
            } else if ((primitive.method() == 1 && i % 2 == 1) || ((primitive.method() == 2 || primitive.method() == 3) && i > 0)) {
                var previous = vertices.get(i - 1);
                line(pixels, width, height, previous.x1() - dx, previous.y1() - dy,
                        vertex.x1() - dx, vertex.y1() - dy, vertex.argb());
            } else if (primitive.method() > 3) {
                throw new IllegalStateException("Unsupported procedural scene primitive method " + primitive.method());
            }
        }
        if (primitive.method() == 2 && vertices.size() > 1) {
            var first = vertices.getFirst(); var last = vertices.getLast();
            line(pixels, width, height, last.x1() - dx, last.y1() - dy, first.x1() - dx, first.y1() - dy, first.argb());
        }
    }

    private static void line(int[] pixels, int width, int height, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1, error = dx + dy;
        while (true) {
            plot(pixels, width, height, x0, y0, color);
            if (x0 == x1 && y0 == y1) return;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
    }
    private static void plot(int[] pixels, int width, int height, int x, int y, int color) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            int index = y * width + x; pixels[index] = blend(pixels[index], color, color >>> 24);
        }
    }
    private static int blend(int background, int foreground, int alpha) {
        if (alpha == 255) return foreground | 0xFF000000;
        int inverse = 255 - alpha;
        int red = (((foreground >>> 16) & 255) * alpha + ((background >>> 16) & 255) * inverse + 127) / 255;
        int green = (((foreground >>> 8) & 255) * alpha + ((background >>> 8) & 255) * inverse + 127) / 255;
        int blue = ((foreground & 255) * alpha + (background & 255) * inverse + 127) / 255;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
