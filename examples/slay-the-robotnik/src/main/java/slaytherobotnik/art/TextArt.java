package slaytherobotnik.art;

import com.openggf.mods.scene.SceneImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pixel art written as text, so it can be edited in any editor and reviewed in a diff.
 *
 * <pre>
 * # comments start with #
 * palette
 * . 000000 0        &lt;- a character, an RRGGBB colour and an optional alpha (0-255)
 * K 000000
 * W FFFFFF
 * Y FFDA24
 *
 * sprite ring
 * .KKKK.
 * KYYYYK
 * </pre>
 *
 * Every {@code sprite} block uses the palette declared above it; a later {@code palette}
 * block replaces it. Rows may differ in length (short rows are padded with transparency).
 */
public final class TextArt {
    private TextArt() {
    }

    /** Parses every sprite in the file. */
    public static Map<String, SceneImage> parse(byte[] text) {
        Map<Character, Integer> palette = new HashMap<>();
        palette.put('.', 0);
        Map<String, SceneImage> out = new LinkedHashMap<>();
        String mode = "";
        String spriteName = null;
        List<String> rows = new ArrayList<>();
        for (String raw : new String(text, StandardCharsets.UTF_8).split("\n")) {
            String line = raw.stripTrailing();
            if (line.startsWith("#")) {
                continue;
            }
            if (line.isBlank()) {
                if (spriteName != null) {
                    out.put(spriteName, build(rows, palette));
                    spriteName = null;
                    rows.clear();
                }
                continue;
            }
            if (line.equals("palette")) {
                mode = "palette";
                palette = new HashMap<>();
                palette.put('.', 0);
                continue;
            }
            if (line.startsWith("sprite ")) {
                if (spriteName != null) {
                    out.put(spriteName, build(rows, palette));
                    rows.clear();
                }
                mode = "sprite";
                spriteName = line.substring(7).trim();
                continue;
            }
            if (mode.equals("palette")) {
                String[] parts = line.trim().split("\\s+");
                int rgb = Integer.parseInt(parts[1], 16);
                int alpha = parts.length > 2 ? Integer.parseInt(parts[2]) : 255;
                palette.put(parts[0].charAt(0), (alpha << 24) | rgb);
            } else if (mode.equals("sprite")) {
                rows.add(line);
            }
        }
        if (spriteName != null) {
            out.put(spriteName, build(rows, palette));
        }
        return out;
    }

    private static SceneImage build(List<String> rows, Map<Character, Integer> palette) {
        int width = 1;
        for (String row : rows) {
            width = Math.max(width, row.length());
        }
        int height = Math.max(1, rows.size());
        int[] pixels = new int[width * height];
        for (int y = 0; y < rows.size(); y++) {
            String row = rows.get(y);
            for (int x = 0; x < row.length(); x++) {
                Integer color = palette.get(row.charAt(x));
                pixels[y * width + x] = color == null ? 0 : color;
            }
        }
        return new SceneImage(width, height, pixels);
    }
}
