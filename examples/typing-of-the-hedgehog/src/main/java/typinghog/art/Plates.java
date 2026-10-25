package typinghog.art;

import com.openggf.mods.ui.PixelCanvas;
import typinghog.core.Prompt;

/**
 * Draws word plates: a dark translucent box with a coloured rim, the prompt's display text
 * with typed characters lit, skipped punctuation dimmed, and a corner tick toward the target.
 * Colours are exact ARGB, independent of the zone palette.
 */
public final class Plates {
    /** What kind of target a plate belongs to; it picks the rim colour. */
    public enum Style {
        NORMAL(0xFFE0E0E0), LOCKED(0xFFFFD000), ELITE(0xFFFF6040), DECOY(0xFFFF2020), RESCUE(0xFF60E060),
        POWER(0xFF40C0FF), BOSS(0xFFFF60C0);
        final int rim;
        Style(int rim) { this.rim = rim; }
        public int rimColour() { return rim; }
    }

    public static final int TEXT = 0xFFFFFFFF;
    public static final int TYPED = 0xFFFFC000;
    public static final int DIM = 0xFF808898;
    public static final int BOX = 0xC0101828;
    public static final int SHADOW = 0xFF000000;
    public static final int HEIGHT = PlateFont.HEIGHT + 4;

    private final PlateFont font;

    public Plates(PlateFont font) {
        this.font = font;
    }

    public PlateFont font() { return font; }

    /** Pixel width of a plate for {@code prompt}. */
    public int width(Prompt prompt) {
        return font.width(prompt.display()) + 6;
    }

    /**
     * Draws a plate whose bottom-centre sits at {@code (cx, bottom)}; {@code progress} is how
     * many characters of the typed form are done. {@code shake} offsets it sideways (an error).
     */
    public void draw(PixelCanvas canvas, Prompt prompt, int progress, Style style, int cx, int bottom,
                     boolean punctuation, int shake) {
        String display = prompt.display();
        int w = width(prompt);
        // Keep the plate on screen; its pointer stays over the target.
        int x = Math.max(4, Math.min(canvas.width() - w - 4, cx - w / 2)) + shake;
        int y = bottom - HEIGHT;
        canvas.fill(x + 1, y, w - 2, HEIGHT, BOX);
        canvas.fill(x, y + 1, 1, HEIGHT - 2, BOX);
        canvas.fill(x + w - 1, y + 1, 1, HEIGHT - 2, BOX);
        canvas.fill(x + 1, y, w - 2, 1, style.rim);
        canvas.fill(x + 1, y + HEIGHT - 1, w - 2, 1, style.rim);
        canvas.fill(cx - 1, bottom, 3, 1, style.rim);
        canvas.fill(cx, bottom + 1, 1, 1, style.rim);
        // Walk display characters, advancing the typed index only for characters that are typed.
        int typedIndex = 0;
        int textX = x + 3;
        for (int i = 0; i < display.length(); i++) {
            char c = display.charAt(i);
            boolean skipped = Prompt.skipped(c, punctuation);
            int colour;
            if (skipped) {
                colour = DIM;
            } else {
                colour = typedIndex < progress ? TYPED : TEXT;
                typedIndex++;
            }
            if (c == ' ' && !skipped && typedIndex - 1 == progress && style == Style.LOCKED) {
                canvas.fill(textX + 1, y + 2 + PlateFont.HEIGHT - 2, 3, 1, TYPED);
            }
            font.glyph(canvas, c, textX, y + 2, colour);
            textX += PlateFont.ADVANCE;
        }
    }

    /** A spinning-ring reticle around a locked target: four corner brackets that breathe. */
    public static void reticle(PixelCanvas canvas, int cx, int cy, int radius, int tick, int argb) {
        int r = radius + (tick / 4 % 4 == 0 ? 1 : 0);
        int arm = Math.max(3, radius / 3);
        int[][] corners = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int[] c : corners) {
            int x = cx + c[0] * r;
            int y = cy + c[1] * r;
            canvas.fill(c[0] < 0 ? x : x - arm + 1, y, arm, 1, argb);
            canvas.fill(x, c[1] < 0 ? y : y - arm + 1, 1, arm, argb);
        }
    }
}
