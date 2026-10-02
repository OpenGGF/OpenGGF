package infinite;

import com.openggf.control.InputHandler;
import com.openggf.game.TitleScreenProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;

/**
 * Wraps the stock Sonic 1 title and paints a code-drawn "INFINITE" wordmark in the
 * strip above the emblem (the wings and TitleSonic's head start near screen Y 30).
 * After Sonic rises it streaks in from the right, then glints periodically.
 * Everything else delegates to the ROM-driven title unchanged.
 */
public final class TitleWordmark implements TitleScreenProvider {
    static final String WORD = "INFINITE";
    static final int SCALE = 3;
    static final int GLYPH_W = 5, GLYPH_H = 7;
    static final int ADVANCE = (GLYPH_W + 1) * SCALE;
    static final int HEIGHT = GLYPH_H * SCALE;
    /** Rows lean right by one pixel per three rows: a 6 px italic lean for speed. */
    static final int LEAN = (HEIGHT - 1) / SCALE;
    /** Eight letters of WORD; the mod format only allows literal static constants. */
    static final int WIDTH = 8 * ADVANCE - SCALE + LEAN;
    static final int TOP = 4;
    /** TitleSonic waits 30 frames, then rises; land the word once he has emerged. */
    static final int ENTRY_DELAY = 40, ENTRY_FRAMES = 18;
    static final int GLINT_PERIOD = 180, GLINT_FRAMES = 26, TWINKLE_FRAMES = 14;

    private static final String GLYPHS =
            "11111" + "00100" + "00100" + "00100" + "00100" + "00100" + "11111"     // I
            + "10001" + "11001" + "11101" + "10111" + "10011" + "10001" + "10001"   // N
            + "11111" + "10000" + "10000" + "11110" + "10000" + "10000" + "10000"   // F
            + "11111" + "00100" + "00100" + "00100" + "00100" + "00100" + "00100"   // T
            + "11111" + "10000" + "10000" + "11110" + "10000" + "10000" + "11111";  // E
    private static final String LETTERS = "INFTE";
    /** Fire gradient, top to bottom, with a chrome horizon line like the SONIC letters. */
    private final int[][] stops = {
            {0, 0xFFFFFF}, {6, 0xFFE840}, {10, 0xFFA800}, {12, 0xE84800}, {20, 0xA80818}};
    private static final int HORIZON_ROW = 11, HORIZON = 0xFFF8C8;
    private static final int OUTLINE = 0x101848;

    final boolean[][] fill = rasterise();
    final boolean[][] edge = dilate(fill, 2);
    private final float[][] rowColours = rowColours();

    private final TitleScreenProvider base;
    private int activeFrames;

    public TitleWordmark(TitleScreenProvider base) { this.base = base; }

    TitleScreenProvider base() { return base; }

    @Override public void update(InputHandler input) {
        base.update(input);
        State state = base.getState();
        activeFrames = state == State.ACTIVE || state == State.EXITING ? activeFrames + 1 : 0;
    }

    @Override public void draw() {
        base.draw();
        if (activeFrames <= ENTRY_DELAY) return;
        GraphicsManager graphics = GraphicsManager.getInstance();
        int viewport = graphics.getProjectionWidth() > 0 ? graphics.getProjectionWidth() : 320;
        int frame = activeFrames - ENTRY_DELAY;
        int left = (viewport - WIDTH) / 2 + entryOffset(frame, viewport);
        boolean landed = frame >= ENTRY_FRAMES;
        drawStreaks(graphics, left, frame, landed);
        drawMask(graphics, edge, left + 1, TOP + 1, 0, 0, 0, 0.45f);
        drawMask(graphics, edge, left - 2, TOP - 2, r(OUTLINE), g(OUTLINE), b(OUTLINE), 1f);
        int glint = landed ? glintPosition(frame - ENTRY_FRAMES) : Integer.MIN_VALUE;
        for (int y = 0; y < HEIGHT; y++) {
            boolean[] row = fill[y];
            float[] c = rowColours[y];
            int band = glint - y / 2;
            for (int x = 0; x < WIDTH; x++) {
                if (!row[x]) continue;
                int end = x;
                while (end < WIDTH && row[end]) end++;
                int glintStart = Math.max(x, band), glintEnd = Math.min(end, band + 5);
                if (glintStart < glintEnd) {
                    rect(graphics, left + x, TOP + y, glintStart - x, c[0], c[1], c[2], 1f);
                    rect(graphics, left + glintStart, TOP + y, glintEnd - glintStart, 1f, 1f, 1f, 1f);
                    rect(graphics, left + glintEnd, TOP + y, end - glintEnd, c[0], c[1], c[2], 1f);
                } else {
                    rect(graphics, left + x, TOP + y, end - x, c[0], c[1], c[2], 1f);
                }
                x = end;
            }
        }
        if (landed) drawTwinkle(graphics, left, frame - ENTRY_FRAMES);
        graphics.flushScreenSpace();
    }

    /** Ease-out-back from off the right edge, overshooting a few pixels before settling. */
    static int entryOffset(int frame, int viewport) {
        if (frame >= ENTRY_FRAMES) return 0;
        double t = (double) frame / ENTRY_FRAMES - 1, back = 1.6;
        double eased = 1 + t * t * ((back + 1) * t + back);
        return (int) Math.round((1 - eased) * (viewport + WIDTH) / 2);
    }

    /** Glint column (at the word's top row) for frames since landing; off-word between sweeps. */
    static int glintPosition(int sinceLanding) {
        int phase = sinceLanding % GLINT_PERIOD;
        if (phase >= GLINT_FRAMES) return Integer.MIN_VALUE;
        return -10 + phase * (WIDTH + 20) / GLINT_FRAMES;
    }

    private static void drawStreaks(GraphicsManager graphics, int left, int frame, boolean landed) {
        int[] rows = {3, 9, 15};
        for (int i = 0; i < rows.length; i++) {
            int wobble = (frame * 3 + i * 11) % 14;
            int length = landed ? 18 + wobble + i * 6 : 70 + i * 20;
            int end = left - 4 + (HEIGHT - rows[i]) / SCALE;
            for (int segment = 0; segment < 3; segment++) {
                int segmentLength = length / 3;
                int start = end - (segment + 1) * segmentLength;
                float alpha = 0.85f - segment * 0.28f;
                rect(graphics, start, TOP + rows[i], segmentLength - 1, 0.62f, 0.9f, 1f, alpha);
                rect(graphics, start, TOP + rows[i] + 1, segmentLength - 1, 0.2f, 0.45f, 0.95f, alpha * 0.7f);
            }
        }
    }

    /** A four-point star on the last E's upper-right corner as each glint leaves the word. */
    private static void drawTwinkle(GraphicsManager graphics, int left, int sinceLanding) {
        int phase = sinceLanding % GLINT_PERIOD - GLINT_FRAMES + 4;
        if (phase < 0 || phase >= TWINKLE_FRAMES) return;
        int arm = phase < TWINKLE_FRAMES / 2 ? 1 + phase / 2 : 1 + (TWINKLE_FRAMES - phase) / 2;
        int cx = left + WIDTH - 2, cy = TOP + 1;
        rect(graphics, cx - arm, cy, arm * 2 + 1, 1f, 1f, 1f, 1f);
        for (int dy = -arm; dy <= arm; dy++) rect(graphics, cx, cy + dy, 1, 1f, 1f, 1f, 1f);
        rect(graphics, cx - 1, cy - 1, 3, 1f, 1f, 0.75f, 0.8f);
        rect(graphics, cx - 1, cy + 1, 3, 1f, 1f, 0.75f, 0.8f);
    }

    private static void drawMask(GraphicsManager graphics, boolean[][] mask, int left, int top,
                                 float r, float g, float b, float alpha) {
        for (int y = 0; y < mask.length; y++) {
            boolean[] row = mask[y];
            for (int x = 0; x < row.length; x++) {
                if (!row[x]) continue;
                int end = x;
                while (end < row.length && row[end]) end++;
                rect(graphics, left + x, top + y, end - x, r, g, b, alpha);
                x = end;
            }
        }
    }

    private static void rect(GraphicsManager graphics, int x, int y, int width,
                             float r, float g, float b, float alpha) {
        if (width <= 0) return;
        graphics.registerCommand(alpha >= 1f
                ? new GLCommand(GLCommand.CommandType.RECTI, 0, r, g, b, x, y, x + width, y + 1)
                : new GLCommand(GLCommand.CommandType.RECTI, 0, GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                        r, g, b, alpha, x, y, x + width, y + 1));
    }

    static boolean[][] rasterise() {
        boolean[][] mask = new boolean[HEIGHT][WIDTH];
        for (int letter = 0; letter < WORD.length(); letter++) {
            int glyph = LETTERS.indexOf(WORD.charAt(letter)) * GLYPH_W * GLYPH_H;
            for (int y = 0; y < HEIGHT; y++) {
                int lean = (HEIGHT - 1 - y) / SCALE;
                for (int gx = 0; gx < GLYPH_W; gx++) {
                    if (GLYPHS.charAt(glyph + y / SCALE * GLYPH_W + gx) != '1') continue;
                    for (int dx = 0; dx < SCALE; dx++) mask[y][letter * ADVANCE + gx * SCALE + dx + lean] = true;
                }
            }
        }
        return mask;
    }

    /** Grows a mask by {@code radius} pixels in every direction, padding its bounds to match. */
    static boolean[][] dilate(boolean[][] mask, int radius) {
        int height = mask.length, width = mask[0].length;
        boolean[][] grown = new boolean[height + radius * 2][width + radius * 2];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!mask[y][x]) continue;
                for (int dy = 0; dy <= radius * 2; dy++) {
                    for (int dx = 0; dx <= radius * 2; dx++) {
                        // Trim the corners so the outline reads rounded rather than boxy.
                        if (Math.abs(dy - radius) + Math.abs(dx - radius) > radius + 1) continue;
                        grown[y + dy][x + dx] = true;
                    }
                }
            }
        }
        return grown;
    }

    private float[][] rowColours() {
        float[][] colours = new float[HEIGHT][];
        for (int y = 0; y < HEIGHT; y++) {
            int rgb = y == HORIZON_ROW ? HORIZON : gradient(y);
            colours[y] = new float[]{r(rgb), g(rgb), b(rgb)};
        }
        return colours;
    }

    private int gradient(int y) {
        for (int i = 1; i < stops.length; i++) {
            if (y > stops[i][0]) continue;
            int[] from = stops[i - 1], to = stops[i];
            double t = (double) (y - from[0]) / (to[0] - from[0]);
            int rgb = 0;
            for (int shift = 16; shift >= 0; shift -= 8) {
                int a = from[1] >> shift & 0xFF, b = to[1] >> shift & 0xFF;
                rgb |= (int) Math.round(a + (b - a) * t) << shift;
            }
            return rgb;
        }
        return stops[stops.length - 1][1];
    }

    private static float r(int rgb) { return (rgb >> 16 & 0xFF) / 255f; }
    private static float g(int rgb) { return (rgb >> 8 & 0xFF) / 255f; }
    private static float b(int rgb) { return (rgb & 0xFF) / 255f; }

    @Override public void initialize() { activeFrames = 0; base.initialize(); }
    @Override public void reset() { activeFrames = 0; base.reset(); }
    @Override public void setClearColor() { base.setClearColor(); }
    @Override public State getState() { return base.getState(); }
    @Override public boolean isExiting() { return base.isExiting(); }
    @Override public boolean isActive() { return base.isActive(); }
    @Override public boolean supportsLevelSelectOverlay() { return base.supportsLevelSelectOverlay(); }
    @Override public void drawFrozenForLevelSelect() { base.drawFrozenForLevelSelect(); }
    @Override public TitleScreenAction consumeExitAction() { return base.consumeExitAction(); }
    @Override public void setExitToLevelHandler(Runnable handler) { base.setExitToLevelHandler(handler); }
}
