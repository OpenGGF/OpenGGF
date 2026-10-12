package towerdefense.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.ui.AtlasFont;

/** Original text glyphs shared with Slay; framework owns parsing, atlas packing and metrics. */
public final class PixelFont {
    private final AtlasFont font;
    public PixelFont(byte[] bytes) { font = AtlasFont.parse(bytes, 5); }
    public int width(String text) { return font.width(text); }
    public void text(SceneCanvas canvas, String text, int x, int y, int color) { text(canvas, text, x, y, color, 1); }
    public void text(SceneCanvas canvas, String text, int x, int y, int color, int scale) {
        font.draw(canvas, text, x, y, color, scale);
    }
    public void centered(SceneCanvas canvas, String text, int cx, int y, int color, int scale) {
        int x = cx - width(text) * scale / 2;
        text(canvas, text, x + scale, y + scale, 0xFF10151E, scale);
        text(canvas, text, x, y, color, scale);
    }
}
