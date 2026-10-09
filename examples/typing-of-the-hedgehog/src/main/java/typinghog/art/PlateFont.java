package typinghog.art;

import com.openggf.mods.ui.BitmapFont;
import com.openggf.mods.ui.PixelCanvas;

/**
 * The plate and HUD face: the original 5x9 glyphs from {@code art/plate-font.txt} with a
 * six-pixel advance. Characters it lacks draw as a blank cell.
 */
public final class PlateFont {
    public static final int WIDTH = 5;
    public static final int HEIGHT = 9;
    public static final int ADVANCE = 6;

    private final BitmapFont face;
    private final String alphabet;

    public PlateFont(String glyphText) {
        GlyphSheet sheet = GlyphSheet.parse(glyphText, WIDTH, HEIGHT);
        this.face = BitmapFont.binary(sheet.alphabet(), sheet.bits(), WIDTH, HEIGHT, ADVANCE);
        this.alphabet = sheet.alphabet();
    }

    public int width(String text) {
        return text.isEmpty() ? 0 : text.length() * ADVANCE - 1;
    }

    /** Draws one character at {@code x, y} (top of the cell). */
    public void glyph(PixelCanvas canvas, char c, int x, int y, int argb) {
        if (alphabet.indexOf(c) < 0 || c == ' ') return;
        face.draw(canvas, String.valueOf(c), x, y, 1, argb);
    }

    /** Draws text with a one-pixel drop shadow. */
    public void shadowed(PixelCanvas canvas, String text, int x, int y, int argb, int shadow) {
        draw(canvas, text, x + 1, y + 1, shadow);
        draw(canvas, text, x, y, argb);
    }

    public void draw(PixelCanvas canvas, String text, int x, int y, int argb) {
        for (int i = 0; i < text.length(); i++) glyph(canvas, text.charAt(i), x + i * ADVANCE, y, argb);
    }
}
