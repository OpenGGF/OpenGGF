package typinghog.hub;

import com.openggf.mods.scene.SceneCanvas;

/**
 * The hub's drawing kit, built from fills: gradients, Sonic-blue panels with gold rims,
 * word plates (the same look as the badniks' plates in play) and small effects. Colours are
 * ARGB.
 */
final class Gfx {
    static final int GOLD = 0xFFFFD040;
    static final int GOLD_DARK = 0xFFB07800;
    static final int WHITE = 0xFFFFFFFF;
    static final int TEXT = 0xFFE8ECF8;
    static final int DIM = 0xFF8890A8;
    static final int TYPED = 0xFFFFC000;
    static final int RED = 0xFFFF5848;
    static final int GREEN = 0xFF60E070;
    static final int BLUE = 0xFF3C78F0;
    static final int NAVY = 0xE0101A40;
    static final int PANEL = 0xD8142454;
    static final int PANEL_EDGE = 0xFF5A8CF0;
    static final int BOX = 0xC0101828;

    private Gfx() { }

    static int alpha(int argb, float a) {
        int alpha = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * a)));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16
                | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    /** A vertical gradient in 2-pixel bands. */
    static void gradient(SceneCanvas c, int x, int y, int w, int h, int top, int bottom) {
        for (int row = 0; row < h; row += 2) {
            c.fill(x, y + row, w, Math.min(2, h - row), mix(top, bottom, row / (float) Math.max(1, h - 1)));
        }
    }

    static void outline(SceneCanvas c, int x, int y, int w, int h, int argb) {
        c.fill(x, y, w, 1, argb);
        c.fill(x, y + h - 1, w, 1, argb);
        c.fill(x, y + 1, 1, h - 2, argb);
        c.fill(x + w - 1, y + 1, 1, h - 2, argb);
    }

    /** A Sonic-style panel: navy body, bright rim, a lighter top edge and a dark lip. */
    static void panel(SceneCanvas c, int x, int y, int w, int h) {
        panel(c, x, y, w, h, PANEL, PANEL_EDGE);
    }

    static void panel(SceneCanvas c, int x, int y, int w, int h, int fill, int edge) {
        c.fill(x + 1, y + 1, w - 2, h - 2, fill);
        c.fill(x + 2, y, w - 4, 1, edge);
        c.fill(x + 2, y + h - 1, w - 4, 1, edge);
        c.fill(x, y + 2, 1, h - 4, edge);
        c.fill(x + w - 1, y + 2, 1, h - 4, edge);
        c.fill(x + 1, y + 1, 1, 1, edge);
        c.fill(x + w - 2, y + 1, 1, 1, edge);
        c.fill(x + 1, y + h - 2, 1, 1, edge);
        c.fill(x + w - 2, y + h - 2, 1, 1, edge);
        c.fill(x + 2, y + 1, w - 4, 1, 0x30FFFFFF);
        c.fill(x + 2, y + h - 2, w - 4, 1, 0x50000000);
    }

    /** A highlighted row: a blue gradient bar with a pulsing gold rim. */
    static void highlight(SceneCanvas c, int x, int y, int w, int h, long ticks) {
        gradient(c, x, y, w, h, 0xF03C6CDA, 0xF01A3890);
        float pulse = 0.65f + 0.35f * (float) Math.sin(ticks * 0.15);
        outline(c, x, y, w, h, alpha(GOLD, pulse));
        c.fill(x + 1, y + 1, w - 2, 1, 0x40FFFFFF);
    }

    /** A thin bar meter. */
    static void meter(SceneCanvas c, int x, int y, int w, int h, double fraction, int colour) {
        c.fill(x - 1, y - 1, w + 2, h + 2, 0xFF000000);
        c.fill(x, y, w, h, 0xFF202838);
        int filled = (int) Math.round(w * Math.max(0, Math.min(1, fraction)));
        c.fill(x, y, filled, h, colour);
        c.fill(x, y, filled, 1, 0x50FFFFFF);
    }

    /** Twinkling stars for skies without a ROM backdrop. */
    static void stars(SceneCanvas c, long ticks) {
        for (int i = 0; i < 48; i++) {
            int x = (i * 97 + 13) % c.width();
            int y = (i * 53 + 7) % 150;
            boolean bright = ((ticks / 8 + i * 5) % 23) == 0;
            c.fill(x, y, 1, 1, bright ? WHITE : 0x90C0D0FF);
        }
    }

    /** The scrolling Sonic 3 data-select style checkerboard. */
    static void checker(SceneCanvas c, int x, int y, int w, int h, int size, int a, int b, long scroll) {
        c.fill(x, y, w, h, a);
        int offset = (int) Math.floorMod(scroll, size * 2L);
        for (int row = -size * 2; row < h + size * 2; row += size) {
            for (int col = -size * 2; col < w + size * 2; col += size) {
                if (((Math.floorDiv(col, size) + Math.floorDiv(row, size)) & 1) != 0) continue;
                int fx = Math.max(x, x + col + offset), fy = Math.max(y, y + row + offset);
                int fw = Math.min(x + w, x + col + offset + size) - fx;
                int fh = Math.min(y + h, y + row + offset + size) - fy;
                if (fw > 0 && fh > 0) c.fill(fx, fy, fw, fh, b);
            }
        }
    }

    /**
     * A word plate centred at {@code cx} with its top at {@code y}: dark box, coloured rim, the
     * text with its first {@code typed} characters lit gold. {@code scale} enlarges the face.
     */
    static int plate(SceneCanvas c, HubFont font, String text, int typed, int cx, int y, int rim, int scale,
                     int shake) {
        int tw = font.width(text, scale);
        int w = tw + 6 * scale;
        int h = (HubFont.H + 4) * scale;
        int x = cx - w / 2 + shake;
        c.fill(x + 1, y, w - 2, h, BOX);
        c.fill(x, y + 1, 1, h - 2, BOX);
        c.fill(x + w - 1, y + 1, 1, h - 2, BOX);
        c.fill(x + 1, y, w - 2, scale, rim);
        c.fill(x + 1, y + h - scale, w - 2, scale, rim);
        int tx = x + 3 * scale, ty = y + 2 * scale;
        if (typed > 0) font.draw(c, text.substring(0, Math.min(typed, text.length())), tx, ty, TYPED, scale);
        if (typed < text.length()) {
            font.draw(c, text.substring(typed), tx + typed * HubFont.ADVANCE * scale, ty, WHITE, scale);
        }
        return w;
    }

    /** Plate height at a scale. */
    static int plateHeight(int scale) { return (HubFont.H + 4) * scale; }

    /** A black fade over the whole screen. */
    static void fade(SceneCanvas c, float amount) {
        if (amount > 0) c.fill(0, 0, c.width(), c.height(), alpha(0xFF000000, Math.min(1, amount)));
    }
}
