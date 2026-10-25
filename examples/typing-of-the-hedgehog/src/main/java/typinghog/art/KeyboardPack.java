package typinghog.art;

import com.openggf.mods.ui.PixelCanvas;
import typinghog.hub.model.TextArt;

/**
 * Sonic's keyboard (the Dreamcast nod), worn sideways along his back: a QWERTY board about his
 * height, its rows running down his back (number row outermost, space bar against him). Keys glow
 * as the player types: gold for a hit, red for a miss, fading over {@link #GLOW_ROWS} rows.
 *
 * <p>Drawn for a Sonic facing right; {@code mirrored} flips it for one facing left.
 */
public final class KeyboardPack {
    static final int ROW_COUNT = 4;
    static final int KEY = 2;
    static final int PITCH = 3;
    public static final int WIDTH = 2 + ROW_COUNT * PITCH + 3 + 2;
    public static final int HEIGHT = 2 + 10 * PITCH + 3 + 2;
    static final int GLOW_ROWS = 14;

    // A Dreamcast-style board: light grey case outlined in black, dark keycaps (so a lit key reads).
    static final int OUTLINE = 0xFF101018;
    static final int CASE = 0xFFB4B8C6;
    static final int CASE_SHADE = 0xFF7E8294;
    static final int KEYCAP = 0xFF3A3E50;
    static final int KEY_TOP = 0xFF62677C;
    static final int HIT = 0xFFFFD84A;
    static final int MISS = 0xFFFF4A4A;

    /** Keyboard rows, number row first; each is staggered one pixel further down the board. */
    private final String[] rows = {"1234567890", "qwertyuiop", "asdfghjkl;", "zxcvbnm,./"};
    private final int[] glow = new int[128];
    private final boolean[] missed = new boolean[128];
    private int spaceGlow;
    private boolean spaceMissed;

    /** A key the player pressed: {@code hit} when it was right. */
    public void press(int codePoint, boolean hit) {
        int c = Character.toLowerCase(codePoint);
        if (c == ' ') {
            spaceGlow = GLOW_ROWS;
            spaceMissed = !hit;
        } else if (c >= 0 && c < glow.length) {
            glow[c] = GLOW_ROWS;
            missed[c] = !hit;
        }
    }

    /** Fades the glow one row. */
    public void tick() {
        for (int i = 0; i < glow.length; i++) if (glow[i] > 0) glow[i]--;
        if (spaceGlow > 0) spaceGlow--;
    }

    /** Draws the board with its top-left corner at {@code x, y}. */
    public void draw(PixelCanvas canvas, int x, int y, boolean mirrored) {
        paint((px, py, w, h, argb) -> canvas.fill(mirrored ? x + WIDTH - px - w : x + px, y + py, w, h, argb));
    }

    /** The unlit board as a picture, for the hub's Sonic. */
    public static TextArt.Picture picture() {
        int[] argb = new int[WIDTH * HEIGHT];
        new KeyboardPack().paint((px, py, w, h, colour) -> {
            for (int j = py; j < py + h; j++) for (int i = px; i < px + w; i++) argb[j * WIDTH + i] = colour;
        });
        return new TextArt.Picture(WIDTH, HEIGHT, argb);
    }

    private interface Fill { void fill(int x, int y, int w, int h, int argb); }

    private void paint(Fill fill) {
        fill.fill(0, 0, WIDTH, HEIGHT, OUTLINE);
        fill.fill(1, 1, WIDTH - 2, HEIGHT - 2, CASE);
        fill.fill(WIDTH - 2, 2, 1, HEIGHT - 3, CASE_SHADE);
        fill.fill(2, HEIGHT - 2, WIDTH - 3, 1, CASE_SHADE);
        for (int row = 0; row < rows.length; row++) {
            int cx = 2 + row * PITCH;
            for (int k = 0; k < rows[row].length(); k++) {
                int cy = 2 + row + k * PITCH;
                if (cy + KEY > HEIGHT - 2) break;
                char c = rows[row].charAt(k);
                int colour = keyColour(glow[c], missed[c]);
                fill.fill(cx, cy, KEY, KEY, colour);
                if (glow[c] <= 0) fill.fill(cx, cy, 1, 1, KEY_TOP);
            }
        }
        // The space bar runs most of the board's length against Sonic's back, Enter beside the top.
        int sx = 2 + ROW_COUNT * PITCH;
        fill.fill(sx, 2 + 2 * PITCH, KEY, HEIGHT - 4 - 4 * PITCH, keyColour(spaceGlow, spaceMissed));
        fill.fill(sx, 2, KEY, PITCH + KEY, KEYCAP);
    }

    private static int keyColour(int rows, boolean miss) {
        if (rows <= 0) return KEYCAP;
        return blend(KEYCAP, miss ? MISS : HIT, rows / (double) GLOW_ROWS);
    }

    private static int blend(int from, int to, double t) {
        int r = (int) Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = (int) Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
