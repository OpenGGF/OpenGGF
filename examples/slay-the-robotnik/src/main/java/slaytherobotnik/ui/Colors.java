package slaytherobotnik.ui;

/**
 * The game's colour palette ({@code 0xAARRGGBB}), chosen from Mega Drive-reachable tones
 * (multiples of 0x24) so the UI sits naturally beside ROM sprites.
 */
public final class Colors {
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;
    public static final int SHADOW = 0xC0000000;
    public static final int TEXT = 0xFFEEEEEE;
    public static final int TEXT_DIM = 0xFF928CA0;
    public static final int TEXT_GOOD = 0xFF6CFF48;
    public static final int TEXT_BAD = 0xFFFF4848;
    public static final int TEXT_KEYWORD = 0xFFFFDA24;
    public static final int GOLD = 0xFFFFDA24;
    public static final int RING = 0xFFFFDA00;
    public static final int HP_RED = 0xFFDA2424;
    public static final int HP_DARK = 0xFF481010;
    public static final int BLOCK_BLUE = 0xFF4890FF;
    public static final int ENERGY = 0xFF48FF90;
    public static final int PANEL = 0xF0102048;
    public static final int PANEL_DARK = 0xF00A1028;
    public static final int PANEL_EDGE = 0xFF6C90DA;
    public static final int PANEL_EDGE_DARK = 0xFF243C6C;
    public static final int FOCUS = 0xFFFFFF6C;
    public static final int OVERLAY = 0xB0000010;

    private Colors() {
    }

    /** Linear blend between two ARGB colours, {@code t} in 0..1. */
    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** The colour with its alpha replaced. */
    public static int alpha(int argb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** {@code 0xRRGGBB} to opaque ARGB. */
    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
