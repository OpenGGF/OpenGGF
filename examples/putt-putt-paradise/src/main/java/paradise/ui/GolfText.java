package paradise.ui;

import com.openggf.graphics.GLCommand;
import com.openggf.mods.ui.CompactFont;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;

import java.util.Objects;

/** Small code-drawn 5x7 font and screen primitives. No bitmap/ROM payloads or camera mutation. */
public final class GolfText {
    public static final int CREAM = 0xFFF3CB;
    public static final int GOLD = 0xFFD45B;
    public static final int INK = 0x123C48;
    public static final int BLUE = 0x205B73;
    public static final int GREEN = 0x278B69;
    public static final int CYAN = 0x61D7ED;
    public static final int PINK = 0xFA72AC;

    private GolfText() { }

    /** Immutable queued screen rectangle; always executes with camera origin zero. */
    public record Rect(int x, int y, int width, int height, int rgb, float alpha) implements GLCommandable {
        public Rect {
            if (width < 1 || height < 1 || !Float.isFinite(alpha) || alpha < 0 || alpha > 1) {
                throw new IllegalArgumentException("Invalid screen rectangle");
            }
        }
        @Override public void execute(int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
            new GLCommand(GLCommand.CommandType.RECTI, 0, GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                    ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha,
                    x, y, x + width, y + height).execute(0, 0, cameraWidth, cameraHeight);
        }
    }

    public static void panel(GraphicsManager graphics, int x, int y, int width, int height, int rgb, float alpha) {
        Objects.requireNonNull(graphics, "graphics");
        if (width > 0 && height > 0 && alpha > 0) graphics.registerCommand(new Rect(x, y, width, height, rgb, alpha));
    }

    public static void frame(GraphicsManager graphics, int x, int y, int width, int height, int rgb) {
        panel(graphics, x, y, width, 1, rgb, 1); panel(graphics, x, y + height - 1, width, 1, rgb, 1);
        panel(graphics, x, y, 1, height, rgb, 1); panel(graphics, x + width - 1, y, 1, height, rgb, 1);
    }

    public static void line(GraphicsManager graphics, int x1, int y1, int x2, int y2, int rgb) {
        int dx = Math.abs(x2 - x1), sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1), sy = y1 < y2 ? 1 : -1, error = dx + dy;
        while (true) {
            panel(graphics, x1, y1, 1, 1, rgb, 1);
            if (x1 == x2 && y1 == y2) break;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x1 += sx; }
            if (twice <= dx) { error += dx; y1 += sy; }
        }
    }

    public static int width(String text, int scale) {
        return CompactFont.width(text, scale);
    }

    public static String fit(String text, int pixels, int scale) {
        return CompactFont.fit(text, pixels, scale);
    }

    public static void draw(GraphicsManager graphics, String text, int x, int y) { draw(graphics, text, x, y, 1, CREAM); }

    public static void draw(GraphicsManager graphics, String text, int x, int y, int scale, int rgb) {
        Objects.requireNonNull(graphics, "graphics");
        glyphs(text, x, y, scale, (rx, ry, width, height) -> panel(graphics, rx, ry, width, height, rgb, 1));
    }

    /** Receives one horizontal run of lit glyph pixels. */
    @FunctionalInterface
    public interface RunSink { void run(int x, int y, int width, int height); }

    /** Rasterizes code-drawn glyph rows into runs; callers choose clipping, colour and alpha. */
    public static void glyphs(String text, int x, int y, int scale, RunSink sink) {
        CompactFont.glyphs(text, x, y, scale, sink::run);
    }

    public static void centered(GraphicsManager graphics, String text, int viewportWidth, int y, int scale, int rgb) {
        draw(graphics, text, (viewportWidth - width(text, scale)) / 2, y, scale, rgb);
    }

    public static void meter(GraphicsManager graphics, int x, int y, int width, int value, int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Positive meter maximum required");
        panel(graphics, x, y, width, 8, INK, 1);
        int fill = (width - 2) * Math.clamp(value, 0, maximum) / maximum;
        panel(graphics, x + 1, y + 1, fill, 6, GOLD, 1);
        frame(graphics, x, y, width, 8, CREAM);
    }
}
