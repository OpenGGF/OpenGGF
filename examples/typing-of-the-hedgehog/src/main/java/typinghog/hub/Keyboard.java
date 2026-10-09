package typinghog.hub;

import com.openggf.mods.scene.SceneCanvas;
import typinghog.hub.model.Profile;

/**
 * Tails' on-screen keyboard: a Sonic-blue QWERTY guide with gold-rimmed caps. It lights the
 * next key (and Shift when the next character needs it), flashes keys as they are pressed, and
 * can tint every cap by the player's lifetime accuracy on it (the per-key heatmap). It is a
 * guide only: typing reads the operating system's characters, so any layout works.
 */
final class Keyboard {
    private static final String ROW0 = "1234567890-";
    private static final String ROW1 = "qwertyuiop";
    private static final String ROW2 = "asdfghjkl;'";
    private static final String ROW3 = "zxcvbnm,./";

    private char flashKey;
    private boolean flashOk;
    private long flashTick = -100;

    /** Lights {@code key} for a moment: white when right, red when wrong. */
    void flash(int codePoint, boolean ok, long tick) {
        if (codePoint < 0) return;
        flashKey = base(codePoint);
        flashOk = ok;
        flashTick = tick;
    }

    static int width(int key) { return 11 * (key + 2) + key; }

    static int height(int keyH) { return 5 * (keyH + 2); }

    /**
     * Draws the keyboard with its top-left at {@code x, y}. {@code next} is the next character
     * to type (or -1); {@code heat}, when not null, tints caps by lifetime accuracy.
     */
    void draw(SceneCanvas c, HubFont font, int x, int y, int key, int keyH, int next, Profile heat, long ticks) {
        int pitch = key + 2;
        char target = next < 0 ? 0 : base(next);
        boolean shift = next >= 0 && needsShift(next);
        Gfx.panel(c, x - 6, y - 5, width(key) + 12, height(keyH) + 8, 0xE0182040, 0xFF4068C8);
        row(c, font, ROW0, x, y, key, keyH, target, heat, ticks);
        row(c, font, ROW1, x + pitch / 2, y + (keyH + 2), key, keyH, target, heat, ticks);
        row(c, font, ROW2, x + pitch * 3 / 4, y + 2 * (keyH + 2), key, keyH, target, heat, ticks);
        int r3 = x + pitch * 5 / 4;
        cap(c, font, "SH", x - 2, y + 3 * (keyH + 2), r3 - x - 2, keyH, shift, false, -1, ticks);
        row(c, font, ROW3, r3, y + 3 * (keyH + 2), key, keyH, target, heat, ticks);
        int r3end = r3 + ROW3.length() * pitch;
        cap(c, font, "SH", r3end, y + 3 * (keyH + 2), x + width(key) - r3end, keyH, shift, false, -1, ticks);
        int spaceX = x + pitch * 3, spaceW = pitch * 5;
        boolean spaceFlash = flashKey == ' ' && ticks - flashTick < 8;
        cap(c, font, "", spaceX, y + 4 * (keyH + 2), spaceW - 2, keyH, target == ' ', spaceFlash, 0, ticks);
    }

    private void row(SceneCanvas c, HubFont font, String keys, int x, int y, int key, int keyH, char target,
                     Profile heat, long ticks) {
        for (int i = 0; i < keys.length(); i++) {
            char k = keys.charAt(i);
            int kx = x + i * (key + 2);
            boolean flashing = k == flashKey && ticks - flashTick < 8;
            int heatColour = -1;
            if (heat != null) {
                double acc = heat.keyAccuracy(k);
                heatColour = acc < 0 ? 0xFF404858 : heatColour(acc);
            }
            capColoured(c, font, String.valueOf(Character.toUpperCase(k)), kx, y, key, keyH, k == target, flashing,
                    heatColour, ticks);
        }
    }

    private void cap(SceneCanvas c, HubFont font, String label, int x, int y, int w, int h, boolean lit,
                     boolean flashing, int ok, long ticks) {
        capColoured(c, font, label, x, y, w, h, lit, flashing, -1, ticks);
    }

    private void capColoured(SceneCanvas c, HubFont font, String label, int x, int y, int w, int h, boolean lit,
                             boolean flashing, int heatColour, long ticks) {
        int top, bottom, text = 0xFFFFFFFF;
        if (flashing) {
            top = flashOk ? 0xFFFFFFFF : 0xFFFF6050;
            bottom = flashOk ? 0xFFB0C8FF : 0xFFA02020;
            text = flashOk ? 0xFF1838A0 : 0xFFFFFFFF;
        } else if (lit) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(ticks * 0.25);
            top = Gfx.mix(0xFFFFE070, 0xFFFFFFFF, pulse * 0.5f);
            bottom = 0xFFE09000;
            text = 0xFF402000;
        } else if (heatColour != -1) {
            top = Gfx.mix(heatColour, 0xFFFFFFFF, 0.2f);
            bottom = Gfx.mix(heatColour, 0xFF000000, 0.35f);
        } else {
            top = 0xFF4C84F0;
            bottom = 0xFF2448B0;
        }
        c.fill(x, y + h - 2, w, 2, 0xFF0C1438);
        Gfx.gradient(c, x, y, w, h - 2, top, bottom);
        c.fill(x, y, w, 1, Gfx.mix(top, 0xFFFFFFFF, 0.4f));
        if (lit && !flashing) Gfx.outline(c, x - 1, y - 1, w + 2, h + 1, Gfx.GOLD);
        if (!label.isEmpty()) {
            font.draw(c, label, x + (w - font.width(label, 1)) / 2, y + (h - 2 - HubFont.H) / 2, text, 1);
        }
    }

    /** Red below 80% accuracy, through yellow at 90%, to green from 98%. */
    static int heatColour(double accuracy) {
        if (accuracy >= 0.98) return 0xFF30B048;
        if (accuracy >= 0.90) return Gfx.mix(0xFFE8C020, 0xFF30B048, (float) ((accuracy - 0.90) / 0.08));
        if (accuracy >= 0.80) return Gfx.mix(0xFFE03020, 0xFFE8C020, (float) ((accuracy - 0.80) / 0.10));
        return 0xFFC02018;
    }

    /** The unshifted key a character is typed on (US layout guide). */
    static char base(int cp) {
        char ch = (char) Character.toLowerCase(cp);
        return switch (ch) {
            case '!' -> '1';
            case '@' -> '2';
            case '#' -> '3';
            case '%' -> '5';
            case '&' -> '7';
            case '*' -> '8';
            case '(' -> '9';
            case ')' -> '0';
            case '_' -> '-';
            case ':' -> ';';
            case '"' -> '\'';
            case '<' -> ',';
            case '>' -> '.';
            case '?' -> '/';
            default -> ch;
        };
    }

    static boolean needsShift(int cp) {
        return Character.isUpperCase(cp) || "!@#%&*()_:\"<>?".indexOf(cp) >= 0;
    }
}
