package threeislands.view;

import com.openggf.mods.scene.SceneCanvas;
import java.util.List;
import threeislands.art.Font;

/** The game's window style: classic deep-blue JRPG panels with a light double border. */
public final class Ui {
    public static final int TEXT = 0xFFFFFFFF;
    public static final int DIM = 0xFF8090B0;
    public static final int GOLD = 0xFFFFD860;
    public static final int GOOD = 0xFF60F080;
    public static final int BAD = 0xFFFF6060;
    public static final int EP = 0xFF70B0FF;
    public static final int HIGHLIGHT = 0xFF2C4C9C;

    public final Font font;

    public Ui(Font font) {
        this.font = font;
    }

    public void window(SceneCanvas c, int x, int y, int w, int h) {
        window(c, x, y, w, h, 0xF0182868, 0xF0081030);
    }

    public void window(SceneCanvas c, int x, int y, int w, int h, int top, int bottom) {
        int bands = Math.max(1, h / 4);
        for (int i = 0; i < bands; i++) {
            int y0 = y + i * h / bands;
            int y1 = y + (i + 1) * h / bands;
            c.fill(x, y0, w, y1 - y0, mix(top, bottom, i / (double) Math.max(1, bands - 1)));
        }
        c.fill(x, y, w, 1, 0xFFE8F0FF);
        c.fill(x, y + h - 1, w, 1, 0xFFE8F0FF);
        c.fill(x, y, 1, h, 0xFFE8F0FF);
        c.fill(x + w - 1, y, 1, h, 0xFFE8F0FF);
        c.fill(x + 1, y + 1, w - 2, 1, 0xFF5070C0);
        c.fill(x + 1, y + h - 2, w - 2, 1, 0xFF203060);
        c.fill(x + 1, y + 1, 1, h - 2, 0xFF5070C0);
        c.fill(x + w - 2, y + 1, 1, h - 2, 0xFF203060);
    }

    public static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16
                | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    /** A meter with a dark trough, coloured by how full it is when {@code colour} is 0. */
    public void bar(SceneCanvas c, int x, int y, int w, int h, int value, int max, int colour) {
        c.fill(x, y, w, h, 0xFF101828);
        int fill = max <= 0 ? 0 : (int) Math.round(w * Math.max(0, Math.min(1, value / (double) max)));
        int col = colour;
        if (col == 0) col = value * 4 <= max ? BAD : value * 2 <= max ? GOLD : GOOD;
        if (fill > 0) {
            c.fill(x, y, fill, h, col);
            c.fill(x, y, fill, 1, mix(col, 0xFFFFFFFF, 0.45));
        }
    }

    /** A small right-pointing cursor arrow with its tip at (x, y + 4). */
    public void cursor(SceneCanvas c, int x, int y, long ticks) {
        int bob = (int) (ticks / 8 % 2);
        for (int i = 0; i < 4; i++) c.fill(x - 6 + bob + i, y + i, 1, 9 - 2 * i, GOLD);
    }

    /** A downward arrow over a target, bobbing. */
    public void pointer(SceneCanvas c, int x, int y, long ticks) {
        int bob = (int) Math.round(Math.sin(ticks / 5.0) * 2);
        for (int i = 0; i < 5; i++) c.fill(x - 4 + i, y + bob + i, 9 - 2 * i, 1, GOLD);
        c.fill(x - 4, y + bob - 1, 9, 1, 0xFF000000);
    }

    /**
     * A vertical list. Returns nothing; draws rows of {@code labels} with the cursor on
     * {@code selected}, disabled rows dimmed, scrolled to keep the selection visible.
     */
    public void list(SceneCanvas c, int x, int y, int w, int rows, List<String> labels, List<Boolean> enabled,
            int selected, List<String> right, long ticks) {
        int first = Math.max(0, Math.min(selected - rows / 2, labels.size() - rows));
        for (int r = 0; r < rows && first + r < labels.size(); r++) {
            int i = first + r;
            int ry = y + r * Font.LINE;
            if (i == selected) c.fill(x - 2, ry - 1, w + 4, Font.LINE, 0x603060C0);
            boolean on = enabled == null || enabled.get(i);
            font.draw(c, labels.get(i), x + 8, ry, on ? TEXT : DIM);
            if (right != null && right.get(i) != null) {
                String text = right.get(i);
                font.draw(c, text, x + w - font.width(text), ry, on ? GOLD : DIM);
            }
            if (i == selected) cursor(c, x + 5, ry, ticks);
        }
        if (first > 0) font.draw(c, "^", x + w - 6, y - 9, DIM);
    }

    /** Draws wrapped text and returns the number of lines used. */
    public int paragraph(SceneCanvas c, String text, int x, int y, int w, int colour, int maxLines) {
        List<String> lines = font.wrap(text, w);
        int n = Math.min(maxLines, lines.size());
        for (int i = 0; i < n; i++) font.draw(c, lines.get(i), x, y + i * Font.LINE, colour);
        return n;
    }
}
