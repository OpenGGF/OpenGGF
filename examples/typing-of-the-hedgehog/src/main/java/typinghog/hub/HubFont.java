package typinghog.hub;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import java.util.HashMap;
import java.util.Map;
import typinghog.art.GlyphSheet;
import typinghog.art.PlateFont;

/**
 * The hub's text: the mod's own 5x9 plate face (the same letters badniks carry in play), plus
 * a few extra menu glyphs, packed into one white atlas so each character is one tinted,
 * batched quad. Case-sensitive, fixed {@link PlateFont#ADVANCE}-pixel advance, any integer scale.
 */
final class HubFont {
    static final int W = PlateFont.WIDTH;
    static final int H = PlateFont.HEIGHT;
    static final int ADVANCE = PlateFont.ADVANCE;

    private final SceneImage atlas;
    private final Map<Character, Integer> column = new HashMap<>();

    HubFont(String plateFont, String extra) {
        GlyphSheet base = GlyphSheet.parse(plateFont, W, H);
        StringBuilder alphabet = new StringBuilder(base.alphabet());
        StringBuilder bits = new StringBuilder(base.bits());
        if (extra != null && !extra.isBlank()) {
            GlyphSheet more = GlyphSheet.parse(extra, W, H);
            for (int i = 0; i < more.alphabet().length(); i++) {
                char c = more.alphabet().charAt(i);
                if (alphabet.indexOf(String.valueOf(c)) >= 0) continue;
                alphabet.append(c);
                bits.append(more.bits(), i * W * H, (i + 1) * W * H);
            }
        }
        int count = alphabet.length();
        int width = count * (W + 1);
        int[] pixels = new int[width * H];
        for (int g = 0; g < count; g++) {
            column.put(alphabet.charAt(g), g * (W + 1));
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    if (bits.charAt(g * W * H + y * W + x) == '1') pixels[y * width + g * (W + 1) + x] = 0xFFFFFFFF;
                }
            }
        }
        atlas = new SceneImage(width, H, pixels);
    }

    int width(String text, int scale) {
        return text.isEmpty() ? 0 : (text.length() * ADVANCE - 1) * scale;
    }

    void draw(SceneCanvas c, String text, int x, int y, int argb, int scale) {
        if ((argb >>> 24) == 0) return;
        SceneDraw style = SceneDraw.plain().withTint(argb);
        for (int i = 0; i < text.length(); i++) {
            Integer at = column.get(text.charAt(i));
            if (at != null && text.charAt(i) != ' ') {
                c.drawRegion(atlas, at, 0, W, H, x + i * ADVANCE * scale, y, W * scale, H * scale, style);
            }
        }
    }

    /** Text with a drop shadow one scaled pixel down-right. */
    void shadow(SceneCanvas c, String text, int x, int y, int argb, int scale) {
        int alpha = Math.min(0xC0, (argb >>> 24));
        draw(c, text, x + scale, y + scale, alpha << 24, scale);
        draw(c, text, x, y, argb, scale);
    }

    /** Text with a full outline, for headings over busy backgrounds. */
    void outline(SceneCanvas c, String text, int x, int y, int argb, int outline, int scale) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dy != 0) draw(c, text, x + dx * scale, y + dy * scale, outline, scale);
            }
        }
        draw(c, text, x + scale, y + 2 * scale, outline, scale);
        draw(c, text, x, y, argb, scale);
    }

    void centred(SceneCanvas c, String text, int cx, int y, int argb, int scale) {
        shadow(c, text, cx - width(text, scale) / 2, y, argb, scale);
    }

    void right(SceneCanvas c, String text, int rightX, int y, int argb, int scale) {
        shadow(c, text, rightX - width(text, scale), y, argb, scale);
    }

    /** Wraps text to lines no wider than {@code maxWidth} pixels at {@code scale}. */
    java.util.List<String> wrap(String text, int maxWidth, int scale) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) continue;
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && width(candidate, scale) > maxWidth) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
