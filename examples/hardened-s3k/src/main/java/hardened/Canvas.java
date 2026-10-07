package hardened;

import com.openggf.debug.DebugColor;
import com.openggf.game.GameServices;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;

/** One screen-space presentation adapter. Uses the engine's UI font and GL command queue. */
final class Canvas implements AutoCloseable {
    private final PixelFontTextRenderer text = new PixelFontTextRenderer();

    private record Rect(int x, int y, int w, int h, int rgb, float alpha) implements GLCommandable {
        @Override public void execute(int cameraX, int cameraY, int width, int height) {
            new GLCommand(GLCommand.CommandType.RECTI, 0, GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                    (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f,
                    alpha, x, y, x + w, y + h).execute(0, 0, width, height);
        }
    }

    void rect(int x, int y, int w, int h, int rgb, float alpha) {
        if (w > 0 && h > 0) graphics().registerCommand(new Rect(x, y, w, h, rgb, Math.clamp(alpha, 0f, 1f)));
    }
    void rect(int x, int y, int w, int h, int rgb) { rect(x, y, w, h, rgb, 1); }
    void border(int x, int y, int w, int h, int rgb) {
        rect(x, y, w, 1, rgb); rect(x, y + h - 1, w, 1, rgb);
        rect(x, y, 1, h, rgb); rect(x + w - 1, y, 1, h, rgb);
    }
    void label(String value, int x, int y, int rgb, float scale) {
        var graphics = graphics();
        graphics.flushScreenSpace();
        var matrix = graphics.getProjectionMatrixBuffer();
        if (matrix != null) text.setProjectionMatrix(matrix);
        text.drawShadowedText(value, x, y, new DebugColor(0xff000000 | rgb), scale);
    }
    void center(String value, int y, int rgb, float scale) {
        label(value, (320 - text.measureWidth(value, scale)) / 2, y, rgb, scale);
    }
    void panel(int y, int height) {
        rect(10, y + 2, 300, height, 0x000000, .65f);
        rect(8, y, 304, height, 0x102326, .96f);
        border(8, y, 304, height, 0x87B89C);
    }
    /** UI-only warning silhouette; no ROM gameplay art is synthesized. */
    void warning(int x, int y, int tick, boolean danger) {
        int colour = danger ? 0xFFCE65 : 0xA4E4A6;
        int pulse = tick / 8 % 2;
        rect(x - 16 - pulse, y - 8, 32 + 2 * pulse, 4, colour);
        rect(x - 12, y - 12, 24, 4, colour);
        rect(x - 5, y - 4, 10, 13, 0x87B89C);
        rect(x - 1, y - 8, 2, 6, 0x102326);
        rect(x - 1, y, 2, 2, 0x102326);
    }
    private GraphicsManager graphics() { return GameServices.graphics(); }
    void flush() { graphics().flushScreenSpace(); }
    @Override public void close() { text.cleanup(); }
}
