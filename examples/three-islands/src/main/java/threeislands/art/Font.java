package threeislands.art;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.ui.AtlasFont;
import java.util.ArrayList;
import java.util.List;

/**
 * The game's mixed-case pixel font from {@code text/font.txt}, kept in one white atlas and
 * tinted when drawn. The shared atlas uses exact-first lookup: lowercase keeps its own
 * glyphs, absent lowercase uses uppercase, and unknown characters draw as '?'.
 */
public final class Font {
    public static final int HEIGHT = 9;
    public static final int LINE = 11;
    private final AtlasFont atlas;

    public Font(byte[] text) {
        atlas = AtlasFont.parse(text, HEIGHT, AtlasFont.GlyphCase.EXACT_FIRST, '?');
    }

    public int width(String text) { return atlas.width(text); }

    public void draw(SceneCanvas canvas, String text, int x, int y, int argb) {
        draw(canvas, text, x, y, argb, 1);
    }

    public void draw(SceneCanvas canvas, String text, int x, int y, int argb, int scale) {
        atlas.draw(canvas, text, x, y, argb, scale);
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
