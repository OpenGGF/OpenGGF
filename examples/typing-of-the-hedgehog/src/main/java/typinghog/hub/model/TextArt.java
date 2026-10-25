package typinghog.hub.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pixel art written as text, so the hub's original art (logo, keyboard backpack, emerald,
 * icons) can be edited in any editor and reviewed in a diff. Engine-free; the scene turns each
 * {@link Picture} into an image once.
 *
 * <pre>
 * # comment
 * palette
 * . 000000 0       a character, an RRGGBB colour and an optional alpha (0-255)
 * K 101010
 * sprite name
 * .KK.
 * KKKK
 * </pre>
 *
 * A {@code sprite} uses the palette above it; a later {@code palette} block replaces it. A
 * blank line or the next header ends a sprite; short rows are padded with transparency.
 */
public final class TextArt {
    /** Decoded pixels, {@code 0xAARRGGBB} row by row. */
    public record Picture(int width, int height, int[] argb) { }

    private TextArt() { }

    public static Map<String, Picture> parse(String text) {
        Map<Character, Integer> palette = new HashMap<>();
        palette.put('.', 0);
        Map<String, Picture> out = new LinkedHashMap<>();
        String mode = "";
        String name = null;
        List<String> rows = new ArrayList<>();
        int number = 0;
        for (String raw : (text == null ? "" : text).split("\n")) {
            number++;
            String line = raw.stripTrailing();
            if (line.startsWith("#")) continue;
            if (line.isBlank() || line.equals("palette") || line.startsWith("sprite ")) {
                if (name != null) {
                    out.put(name, build(rows, palette, name));
                    name = null;
                    rows.clear();
                }
                if (line.equals("palette")) {
                    mode = "palette";
                    palette = new HashMap<>();
                    palette.put('.', 0);
                } else if (line.startsWith("sprite ")) {
                    mode = "sprite";
                    name = line.substring(7).strip();
                    if (out.containsKey(name)) throw new IllegalArgumentException("Duplicate sprite " + name);
                }
                continue;
            }
            if (mode.equals("palette")) {
                String[] parts = line.strip().split("\\s+");
                if (parts.length < 2 || parts[0].length() != 1) {
                    throw new IllegalArgumentException("Palette line " + number + " needs 'c RRGGBB [alpha]'");
                }
                int rgb = Integer.parseInt(parts[1], 16) & 0xFFFFFF;
                int alpha = parts.length > 2 ? Integer.parseInt(parts[2]) : 255;
                palette.put(parts[0].charAt(0), (Math.max(0, Math.min(255, alpha)) << 24) | rgb);
            } else if (mode.equals("sprite")) {
                rows.add(line);
            }
        }
        if (name != null) out.put(name, build(rows, palette, name));
        return out;
    }

    private static Picture build(List<String> rows, Map<Character, Integer> palette, String name) {
        int width = 1;
        for (String row : rows) width = Math.max(width, row.length());
        int height = Math.max(1, rows.size());
        int[] pixels = new int[width * height];
        for (int y = 0; y < rows.size(); y++) {
            String row = rows.get(y);
            for (int x = 0; x < row.length(); x++) {
                Integer colour = palette.get(row.charAt(x));
                if (colour == null) {
                    throw new IllegalArgumentException("Sprite " + name + " uses '" + row.charAt(x) + "' outside its palette");
                }
                pixels[y * width + x] = colour;
            }
        }
        return new Picture(width, height, pixels);
    }
}
