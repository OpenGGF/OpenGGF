package threeislands.art;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The game's mixed-case pixel font from {@code text/font.txt}, kept in one white atlas and
 * tinted when drawn. Unlike the shared upper-case {@code AtlasFont}, lower case keeps its own
 * glyphs, so dialogue reads naturally. Unknown characters draw as '?'.
 */
public final class Font {
    public static final int HEIGHT = 9;
    public static final int LINE = 11;
    private static final int SPACE = 3;

    private final SceneImage atlas;
    private final Map<Character, int[]> glyphs;

    public Font(byte[] text) {
        Map<Character, List<String>> rows = new LinkedHashMap<>();
        Character current = null;
        for (String raw : new String(text, StandardCharsets.UTF_8).split("\n")) {
            String line = raw.strip();
            if (line.startsWith("= ") && line.length() == 3) {
                current = line.charAt(2);
                if (rows.putIfAbsent(current, new ArrayList<>()) != null) throw new IllegalArgumentException("Duplicate glyph " + current);
            } else if (line.isEmpty()) {
                current = null;
            } else if (current != null) {
                rows.get(current).add(line);
            } else if (!line.startsWith("#")) {
                throw new IllegalArgumentException("Expected a glyph header");
            }
        }
        int width = 0;
        for (var entry : rows.entrySet()) {
            List<String> glyph = entry.getValue();
            if (glyph.size() != HEIGHT) throw new IllegalArgumentException("Glyph height: " + entry.getKey());
            for (String row : glyph) {
                if (row.length() != glyph.get(0).length() || !row.matches("[.#]+")) {
                    throw new IllegalArgumentException("Malformed glyph: " + entry.getKey());
                }
            }
            width += glyph.get(0).length() + 1;
        }
        int[] pixels = new int[width * HEIGHT];
        glyphs = new HashMap<>();
        int x = 0;
        for (var entry : rows.entrySet()) {
            int w = entry.getValue().get(0).length();
            for (int row = 0; row < HEIGHT; row++) {
                for (int column = 0; column < w; column++) {
                    if (entry.getValue().get(row).charAt(column) == '#') pixels[row * width + x + column] = 0xFFFFFFFF;
                }
            }
            glyphs.put(entry.getKey(), new int[] {x, w});
            x += w + 1;
        }
        atlas = new SceneImage(width, HEIGHT, pixels);
    }

    private int[] glyph(char c) {
        int[] glyph = glyphs.get(c);
        if (glyph == null) glyph = glyphs.get(Character.toUpperCase(c));
        return glyph == null ? glyphs.get('?') : glyph;
    }

    public int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            width += (c == ' ' ? SPACE : glyph(c)[1]) + 1;
        }
        return Math.max(0, width - 1);
    }

    public void draw(SceneCanvas canvas, String text, int x, int y, int argb) {
        draw(canvas, text, x, y, argb, 1);
    }

    public void draw(SceneCanvas canvas, String text, int x, int y, int argb, int scale) {
        SceneDraw style = SceneDraw.plain().withTint(argb);
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                cx += (SPACE + 1) * scale;
                continue;
            }
            int[] glyph = glyph(c);
            canvas.drawRegion(atlas, glyph[0], 0, glyph[1], HEIGHT, cx, y, glyph[1] * scale, HEIGHT * scale, style);
            cx += (glyph[1] + 1) * scale;
        }
    }

    /** Text with a one-pixel dark drop shadow, for drawing over scenery. */
    public void shadowed(SceneCanvas canvas, String text, int x, int y, int argb) {
        draw(canvas, text, x + 1, y + 1, 0xC0000000);
        draw(canvas, text, x, y, argb);
    }

    public void shadowed(SceneCanvas canvas, String text, int x, int y, int argb, int scale) {
        draw(canvas, text, x + scale, y + scale, 0xC0000000, scale);
        draw(canvas, text, x, y, argb, scale);
    }

    public void centered(SceneCanvas canvas, String text, int centreX, int y, int argb) {
        shadowed(canvas, text, centreX - width(text) / 2, y, argb);
    }

    /** Word-wraps {@code text} to {@code maxWidth} pixels. */
    public List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (width(candidate) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}
