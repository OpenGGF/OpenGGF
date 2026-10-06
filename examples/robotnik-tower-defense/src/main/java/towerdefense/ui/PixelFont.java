package towerdefense.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Original text glyphs shared with Slay the Robotnik; one cached atlas per scene. */
public final class PixelFont {
    private final Map<Character, int[]> glyphs = new LinkedHashMap<>();
    private final SceneImage atlas;

    public PixelFont(byte[] bytes) {
        Map<Character, List<String>> rows = new LinkedHashMap<>();
        Character current = null;
        for (String raw : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            String line = raw.strip();
            if (line.startsWith("= ") && line.length() >= 3) {
                current = line.charAt(2);
                rows.put(current, new ArrayList<>());
            } else if (current != null && !line.isEmpty()) {
                rows.get(current).add(line);
            } else if (line.isEmpty()) current = null;
        }
        int width = rows.values().stream().mapToInt(r -> r.getFirst().length() + 1).sum();
        int[] pixels = new int[width * 5];
        int x = 0;
        for (var entry : rows.entrySet()) {
            int w = entry.getValue().getFirst().length();
            for (int y = 0; y < 5; y++) {
                for (int col = 0; col < w; col++) {
                    if (entry.getValue().get(y).charAt(col) == '#') pixels[y * width + x + col] = 0xFFFFFFFF;
                }
            }
            glyphs.put(entry.getKey(), new int[] {x, w});
            x += w + 1;
        }
        atlas = new SceneImage(width, 5, pixels);
    }

    public int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            int[] g = glyphs.get(Character.toUpperCase(text.charAt(i)));
            width += (g == null ? 3 : g[1]) + 1;
        }
        return Math.max(0, width - 1);
    }

    public void text(SceneCanvas canvas, String text, int x, int y, int color) { text(canvas, text, x, y, color, 1); }

    public void text(SceneCanvas canvas, String text, int x, int y, int color, int scale) {
        SceneDraw style = SceneDraw.plain().withTint(color);
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            int[] g = glyphs.get(Character.toUpperCase(text.charAt(i)));
            if (g != null) canvas.drawRegion(atlas, g[0], 0, g[1], 5, cx, y, g[1] * scale, 5 * scale, style);
            cx += (g == null ? 4 : g[1] + 1) * scale;
        }
    }

    public void centered(SceneCanvas canvas, String text, int cx, int y, int color, int scale) {
        int x = cx - width(text) * scale / 2;
        text(canvas, text, x + scale, y + scale, 0xFF10151E, scale);
        text(canvas, text, x, y, color, scale);
    }
}
