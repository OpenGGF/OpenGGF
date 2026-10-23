package openggf.timeattack.ui;

import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.PixelCanvas;

/** {@link NameFont} text at whole-pixel scales, for name previews that should read at a glance. */
public final class NameText {
    private NameText() { }

    /** Draws {@code text} with the line box's top-left at {@code x, y}, each font pixel {@code scale} wide. */
    public static void draw(PixelCanvas canvas, String text, int x, int y, int scale, int argb) {
        if (scale < 1 || scale > 8) throw new IllegalArgumentException("scale must be 1..8");
        NameFont.glyphs(text, 0, 0, (rx, ry, w, h) -> canvas.fill(x + rx * scale, y + ry * scale,
                w * scale, h * scale, argb));
    }

    /** {@link NameFont#draw} over a one-pixel shadow, for names drawn on busy or translucent backgrounds. */
    public static void shadowed(PixelCanvas canvas, String text, int x, int y, int argb) {
        NameFont.draw(canvas, text, x + 1, y + 1, 0xC0000000);
        NameFont.draw(canvas, text, x, y, argb);
    }
}
