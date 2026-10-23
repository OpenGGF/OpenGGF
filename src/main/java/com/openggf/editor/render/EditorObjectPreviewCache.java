package com.openggf.editor.render;

import com.openggf.editor.EditorStockObjectPalette;
import com.openggf.level.LevelObjectArt;
import com.openggf.level.Level;
import com.openggf.level.metadata.ObjectPreview;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingRasterizer;
import org.lwjgl.system.MemoryUtil;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * Detached ROM mapping thumbnails shared by editor front ends. Reuses the production CPU
 * rasterizer; never constructs gameplay objects, admits PLC jobs, or writes world/rewind state.
 * Textures belong to the drawing front end's GL context and are released before it closes.
 */
public final class EditorObjectPreviewCache implements AutoCloseable {
    public record Sprite(int texture, int width, int height, int originX, int originY) { }
    private record Key(Resolved pose, boolean hFlip, boolean vFlip) { }
    private static final int LIMIT = 512;
    private final Map<Key, Sprite> sprites = new LinkedHashMap<>(32, .75f, true);
    private final java.util.List<Integer> retired = new java.util.ArrayList<>();
    private Level level;
    private Map<String, ObjectSpriteSheet> art = Map.of();
    private EditorStockObjectPalette palette;
    private int[] colors;
    private ObjectSpriteSheet ringSheet;

    public void refresh(Level level, EditorStockObjectPalette palette) {
        // ImGui/HUD commands retain texture handles until their frame is submitted.
        for (int texture : retired) glDeleteTextures(texture);
        retired.clear();
        var art = LevelObjectArt.sheets(level);
        int[] next = new int[64];
        if (level != null) for (int line = 0; line < Math.min(4, level.getPaletteCount()); line++)
            for (int color = 0; color < 16; color++) {
                var rgb = level.getPalette(line).getColor(color);
                next[line * 16 + color] = 0xff000000 | (rgb.r & 255) << 16 | (rgb.g & 255) << 8 | rgb.b & 255;
            }
        if (this.level != level || this.art != art || !java.util.Arrays.equals(colors, next)) {
            close();
            var rings = level == null ? null : level.getRingSpriteSheet();
            ringSheet = rings == null || rings.getFrameCount() == 0 ? null : new ObjectSpriteSheet(rings.getPatterns(),
                    java.util.List.of(new com.openggf.level.render.SpriteMappingFrame(rings.getFrame(0).pieces().stream()
                            .map(p -> new com.openggf.level.render.SpriteMappingPiece(p.xOffset(), p.yOffset(), p.widthTiles(),
                                    p.heightTiles(), p.tileIndex(), p.hFlip(), p.vFlip(), p.paletteIndex())).toList())),
                    rings.getPaletteIndex(), 0);
        }
        this.level = level;
        this.art = art;
        this.palette = palette;
        colors = next;
    }

    public boolean available(EditorStockObjectPalette.Entry entry) {
        // Some providers eagerly load other-zone sheets whose palette or mappings do not
        // describe a placeable object here. Keep those in the explicit all/internal view.
        if (entry.stockObjectId() != null && entry.previewArtKey() == null
                && !LevelObjectArt.sourceIds(level).contains(entry.stockObjectId())) return false;
        return palette != null && resolve(palette.previewSpawn(entry, 0, 0)) != null;
    }

    private record Resolved(ObjectPreview pose, java.util.List<ObjectSpriteSheet> sheets) { }
    private Resolved resolve(ObjectSpawn spawn) {
        if (palette == null) return null;
        var pose = palette.preview(spawn);
        if (pose.parts().isEmpty()) return null;
        var sheets = new java.util.ArrayList<ObjectSpriteSheet>();
        for (var part : pose.parts()) {
            var sheet = "ring".equals(part.artKey()) ? ringSheet : art.get(part.artKey());
            if (sheet == null || part.frame() < 0 || part.frame() >= sheet.getFrameCount()
                    || sheet.getFrame(part.frame()).pieces().isEmpty()) return null;
            sheets.add(sheet);
        }
        return new Resolved(pose, java.util.List.copyOf(sheets));
    }

    public Sprite ring() {
        return ringSheet == null ? null : sprite(new Resolved(ObjectPreview.frame("ring", 0), java.util.List.of(ringSheet)), 0);
    }
    public Sprite sprite(ObjectSpawn spawn) { return sprite(resolve(spawn), spawn.renderFlags()); }
    private Sprite sprite(Resolved resolved, int flags) {
        if (resolved == null) return null;
        var key = new Key(resolved, (flags & 1) != 0, (flags & 2) != 0);
        if (sprites.containsKey(key)) return sprites.get(key);
        var pieces = new java.util.ArrayList<com.openggf.level.render.SpriteMappingPiece>();
        var tiles = new java.util.ArrayList<com.openggf.level.Pattern>();
        var offsets = new java.util.IdentityHashMap<ObjectSpriteSheet, Integer>();
        for (int i = 0; i < resolved.pose().parts().size(); i++) {
            var part = resolved.pose().parts().get(i);
            var sheet = resolved.sheets().get(i);
            int offset = offsets.computeIfAbsent(sheet, source -> {
                int base = tiles.size(); java.util.Collections.addAll(tiles, source.getPatterns()); return base;
            });
            int paletteLine = part.paletteLine() < 0 ? sheet.getPaletteIndex() : part.paletteLine();
            for (var piece : sheet.getFrame(part.frame()).pieces()) {
                int x = part.x() + (part.hFlip() ? -piece.xOffset() - piece.widthTiles() * 8 : piece.xOffset());
                int y = part.y() + (part.vFlip() ? -piece.yOffset() - piece.heightTiles() * 8 : piece.yOffset());
                pieces.add(new com.openggf.level.render.SpriteMappingPiece(x, y, piece.widthTiles(), piece.heightTiles(),
                        piece.tileIndex() + offset, part.hFlip() ^ piece.hFlip(), part.vFlip() ^ piece.vFlip(),
                        (paletteLine + piece.paletteIndex()) & 3, piece.priority()));
            }
        }
        var pixels = SpriteMappingRasterizer.rasterize(new com.openggf.level.render.SpriteMappingFrame(pieces),
                tiles.toArray(com.openggf.level.Pattern[]::new), colors, 0, 0);
        if (pixels.width() > 2048 || pixels.height() > 2048) return null;
        boolean opaque = false;
        var rgba = MemoryUtil.memAlloc(pixels.width() * pixels.height() * 4);
        try {
            for (int y = 0; y < pixels.height(); y++) for (int x = 0; x < pixels.width(); x++) {
                int color = pixels.argb()[(key.vFlip() ? pixels.height() - 1 - y : y) * pixels.width()
                        + (key.hFlip() ? pixels.width() - 1 - x : x)];
                opaque |= (color >>> 24) != 0;
                rgba.put((byte) (color >> 16)).put((byte) (color >> 8)).put((byte) color).put((byte) (color >>> 24));
            }
            if (!opaque) return null;
            rgba.flip();
            int oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
            int texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, pixels.width(), pixels.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindTexture(GL_TEXTURE_2D, oldTexture);
            var sprite = new Sprite(texture, pixels.width(), pixels.height(),
                    key.hFlip() ? pixels.width() - pixels.originX() : pixels.originX(),
                    key.vFlip() ? pixels.height() - pixels.originY() : pixels.originY());
            sprites.put(key, sprite);
            while (sprites.size() > LIMIT) {
                var first = sprites.entrySet().iterator();
                var removed = first.next().getValue(); first.remove();
                if (removed != null) retired.add(removed.texture());
            }
            return sprite;
        } finally { MemoryUtil.memFree(rgba); }
    }

    @Override public void close() {
        for (int texture : retired) glDeleteTextures(texture);
        retired.clear();
        for (var sprite : sprites.values()) if (sprite != null) glDeleteTextures(sprite.texture());
        sprites.clear();
    }
}
