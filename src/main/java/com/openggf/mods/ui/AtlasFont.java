package com.openggf.mods.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Original text glyphs packed into one white atlas; colour, markup and game styling stay with the mod. */
@com.openggf.game.ModApi
public final class AtlasFont {
    private final SceneImage atlas;
    private final Map<Character, int[]> glyphs;
    private final int height;
    private AtlasFont(SceneImage atlas, Map<Character, int[]> glyphs, int height) {
        this.atlas = atlas; this.glyphs = Map.copyOf(glyphs); this.height = height;
    }
    /** UTF-8 blocks headed {@code = A}, followed by rows of {@code #} and {@code .}; blank lines separate glyphs. */
    public static AtlasFont parse(byte[] text, int height) {
        Objects.requireNonNull(text, "font text");
        if (text.length > 1_048_576 || height < 1 || height > 64) throw new IllegalArgumentException("Font limits exceeded");
        Map<Character, List<String>> rows = new LinkedHashMap<>();
        Character current = null;
        for (String raw : new String(text, StandardCharsets.UTF_8).split("\n")) {
            String line = raw.strip();
            if (line.startsWith("= ") && line.length() == 3) {
                current = Character.toUpperCase(line.charAt(2));
                if (rows.putIfAbsent(current, new ArrayList<>()) != null) throw new IllegalArgumentException("Duplicate glyph: " + current);
            } else if (line.isEmpty()) current = null;
            else if (current != null) rows.get(current).add(line);
            else if (!line.startsWith("#")) throw new IllegalArgumentException("Expected glyph header");
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("Font has no glyphs");
        int width = 0;
        for (var entry : rows.entrySet()) {
            List<String> glyph = entry.getValue();
            if (glyph.size() != height) throw new IllegalArgumentException("Glyph height: " + entry.getKey());
            int w = glyph.getFirst().length();
            if (w < 1 || w > 64) throw new IllegalArgumentException("Glyph width: " + entry.getKey());
            for (String row : glyph) {
                if (row.length() != w || !row.matches("[.#]+")) throw new IllegalArgumentException("Malformed glyph: " + entry.getKey());
            }
            width += w + 1;
            if (width > 4096) throw new IllegalArgumentException("Font atlas exceeds 4096 pixels");
        }
        Map<Character, int[]> glyphs = new LinkedHashMap<>();
        int[] pixels = new int[width * height];
        int x = 0;
        for (var entry : rows.entrySet()) {
            int w = entry.getValue().getFirst().length();
            for (int row = 0; row < height; row++) for (int column = 0; column < w; column++)
                if (entry.getValue().get(row).charAt(column) == '#') pixels[row * width + x + column] = 0xFFFFFFFF;
            glyphs.put(entry.getKey(), new int[]{x, w});
            x += w + 1;
        }
        return new AtlasFont(new SceneImage(width, height, pixels), glyphs, height);
    }
    public int height() { return height; }
    /** Unknown characters and spaces advance four pixels without drawing; there is no trailing gap. */
    public int width(String text) {
        Objects.requireNonNull(text, "text");
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            int[] glyph = glyphs.get(Character.toUpperCase(text.charAt(i)));
            width = Math.addExact(width, (glyph == null || text.charAt(i) == ' ' ? 3 : glyph[1]) + 1);
        }
        return Math.max(0, width - 1);
    }
    public void draw(SceneCanvas canvas, String text, int x, int y, int argb, int scale) {
        Objects.requireNonNull(canvas, "canvas"); Objects.requireNonNull(text, "text");
        if (scale < 1 || scale > 8) throw new IllegalArgumentException("Atlas scale must be 1..8");
        SceneDraw style = SceneDraw.plain().withTint(argb);
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            int[] glyph = text.charAt(i) == ' ' ? null : glyphs.get(Character.toUpperCase(text.charAt(i)));
            if (glyph != null) canvas.drawRegion(atlas, glyph[0], 0, glyph[1], height,
                    cx, y, glyph[1] * scale, height * scale, style);
            cx += (glyph == null ? 4 : glyph[1] + 1) * scale;
        }
    }
}
