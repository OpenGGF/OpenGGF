package eggsky.ui;

import com.openggf.mods.scene.SceneCanvas;
import eggsky.core.Colour;

/**
 * Shared drawing for menus and the HUD, in the look of the Sonic 3 data-select screens: navy
 * panels with a bevelled edge, gold headings and chunky bars.
 */
public final class Ui {
    public static final int NAVY = 0xE0101C48;
    public static final int NAVY_SOLID = 0xFF101C48;
    public static final int EDGE_LIGHT = 0xFF6888D8;
    public static final int EDGE_DARK = 0xFF040818;
    public static final int GOLD = 0xFFFFD848;
    public static final int GOLD_DARK = 0xFFD07010;
    public static final int CYAN = 0xFF48E0FF;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GREY = 0xFFA0A8C0;
    public static final int DIM = 0xFF6870A0;
    public static final int RED = 0xFFFF5050;
    public static final int GREEN = 0xFF60FF70;
    public static final int ORANGE = 0xFFFFA040;
    public static final int PINK = 0xFFFF70D0;

    private Ui() {
    }

    /** A bevelled panel. */
    public static void panel(SceneCanvas c, int x, int y, int w, int h) {
        panel(c, x, y, w, h, NAVY);
    }

    public static void panel(SceneCanvas c, int x, int y, int w, int h, int fill) {
        c.fill(x, y, w, h, fill);
        c.fill(x, y, w, 1, EDGE_LIGHT);
        c.fill(x, y, 1, h, EDGE_LIGHT);
        c.fill(x, y + h - 1, w, 1, EDGE_DARK);
        c.fill(x + w - 1, y, 1, h, EDGE_DARK);
        c.fill(x + 1, y + 1, w - 2, 1, Colour.alpha(EDGE_LIGHT, 0x60));
    }

    /** A highlighted (focused) frame around a rectangle, pulsing with {@code ticks}. */
    public static void focus(SceneCanvas c, int x, int y, int w, int h, long ticks) {
        int a = 160 + (int) (Math.sin(ticks * 0.15) * 80);
        int col = Colour.alpha(CYAN, a);
        c.fill(x - 1, y - 1, w + 2, 1, col);
        c.fill(x - 1, y + h, w + 2, 1, col);
        c.fill(x - 1, y, 1, h, col);
        c.fill(x + w, y, 1, h, col);
        c.fill(x, y, w, h, Colour.alpha(CYAN, 0x28));
    }

    /** A meter: dark trough, coloured fill with a lighter top line, optional warning flash. */
    public static void bar(SceneCanvas c, int x, int y, int w, int h, float fraction, int colour, boolean flash) {
        fraction = Math.max(0, Math.min(1, fraction));
        c.fill(x - 1, y - 1, w + 2, h + 2, 0xFF000000);
        c.fill(x, y, w, h, 0xFF202838);
        int fw = Math.round(w * fraction);
        int col = flash ? 0xFFFFFFFF : colour;
        if (fw > 0) {
            c.fill(x, y, fw, h, col);
            c.fill(x, y, fw, 1, Colour.lerp(col, 0xFFFFFFFF, 0.45f));
            if (h > 2) {
                c.fill(x, y + h - 1, fw, 1, Colour.scale(col, 0.6f));
            }
        }
    }

    /** A thin horizontal rule in gold. */
    public static void rule(SceneCanvas c, int x, int y, int w) {
        c.fill(x, y, w, 1, GOLD_DARK);
        c.fill(x, y + 1, w, 1, Colour.alpha(0xFF000000, 0x80));
    }

    /** Full-screen dimming. */
    public static void dim(SceneCanvas c, int alpha) {
        c.fill(0, 0, c.width(), c.height(), Colour.alpha(0xFF000010, alpha));
    }

    /** A shaded disc, lit from the upper left (from {@code light} to {@code dark}). */
    public static void disc(SceneCanvas c, int cx, int cy, int r, int light, int dark) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            int bands = 6;
            for (int b = 0; b < bands; b++) {
                int x0 = cx - half + b * 2 * half / bands;
                int x1 = cx - half + (b + 1) * 2 * half / bands;
                float t = (b / (float) bands + (dy + r) / (2f * r)) / 2f;
                c.fill(x0, cy + dy, Math.max(1, x1 - x0), 1, Colour.lerp(light, dark, t));
            }
        }
    }

    /** "1,234" style number. */
    public static String num(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                sb.append(',');
            }
            sb.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + sb;
    }

    /** Word-wraps {@code text} to lines of at most {@code chars} characters. */
    public static java.util.List<String> wrap(String text, int chars) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > chars) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }
}
