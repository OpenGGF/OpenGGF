package com.openggf.game.presentation;

import com.openggf.debug.DebugColor;
import com.openggf.game.MenuChrome;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;

import java.util.List;
import java.util.regex.Pattern;

/** Native-pixel transport plates for gameplay; contains no input or transport state. */
public final class TransportHud {
    private static final int WIDTH = 144;
    private static final int MARGIN = 8;
    private static final float TEXT_SCALE = 1;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public enum Placement { ABOVE_LIVES, BOTTOM_RIGHT }

    public enum Status {
        REWIND(MenuChrome.CYAN), HOLD(MenuChrome.GOLD), RECORD(MenuChrome.RED),
        PLAY(MenuChrome.CYAN), PLAY_WARNING(MenuChrome.AMBER), STOP(MenuChrome.MUTED),
        WARNING(MenuChrome.AMBER), ERROR(MenuChrome.RED);

        private final float[] color;

        Status(float[] color) {
            this.color = color;
        }
    }

    /** A negative progress value omits the hold meter. */
    public record Message(Status status, String title, String detail, String note, int progressPercent) { }

    record Layout(int x, int y, int width, int height, String title, List<String> details, String note) { }

    private TransportHud() { }

    public static void render(PixelFontTextRenderer text, GraphicsManager graphics, int viewportWidth,
                              Placement placement, Message message) {
        if (text == null) return;
        Layout layout = layout(text, viewportWidth, placement, message);
        if (graphics != null && graphics.isGlInitialized()) {
            rect(graphics, layout.x, layout.y, WIDTH, layout.height, MenuChrome.EDGE, 255);
            rect(graphics, layout.x + 1, layout.y + 1, WIDTH - 2, layout.height - 2, MenuChrome.NAVY, 245);
            icon(graphics, layout.x + 8, layout.y + 7, message.status);
            if (message.progressPercent >= 0) {
                int trackWidth = WIDTH - 16;
                int y = layout.y + layout.height - 6;
                rect(graphics, layout.x + 8, y, trackWidth, 2, MenuChrome.EDGE, 255);
                int filled = trackWidth * Math.clamp(message.progressPercent, 0, 100) / 100;
                if (filled > 0) rect(graphics, layout.x + 8, y, filled, 2, message.status.color, 255);
            }
            // Text batches draw immediately; complete their backing and glyphs first.
            graphics.flushScreenSpace();
        }
        text.beginBatch();
        try {
            float[] titleColor = message.status == Status.ERROR || message.status == Status.WARNING
                    || message.status == Status.PLAY_WARNING
                    ? message.status.color : MenuChrome.TEXT;
            text.drawShadowedText(layout.title, layout.x + 22, layout.y + 6, color(titleColor), TEXT_SCALE);
            int y = layout.y + 20;
            for (String detail : layout.details) {
                text.drawShadowedText(detail, layout.x + 8, y, color(MenuChrome.MUTED), TEXT_SCALE);
                y += 14;
            }
            if (!layout.note.isEmpty()) {
                text.drawShadowedText(layout.note, layout.x + 8, y, color(MenuChrome.DIM), TEXT_SCALE);
            }
        } finally {
            text.endBatch();
        }
    }

    static Layout layout(PixelFontTextRenderer text, int viewportWidth, Placement placement, Message message) {
        String note = fit(text, message.note, WIDTH - 16);
        List<String> details = (message.detail == null ? "" : message.detail).lines().limit(2)
                .map(line -> fit(text, line, WIDTH - 16)).toList();
        int height = 22 + 14 * (details.size() + (note.isEmpty() ? 0 : 1))
                + (message.progressPercent >= 0 ? 4 : 0);
        // All three games place lives at y=200 and score/time/rings at the upper left.
        // Separate horizontal lanes also leave room for recording while live rewind is held.
        int x = placement == Placement.ABOVE_LIVES
                ? Math.max(0, (viewportWidth - 320) / 2) + MARGIN : viewportWidth - MARGIN - WIDTH;
        int bottom = placement == Placement.ABOVE_LIVES ? 196 : 216;
        return new Layout(x, bottom - height, WIDTH, height,
                fit(text, message.title, WIDTH - 30), details, note);
    }

    private static String fit(PixelFontTextRenderer text, String value, int width) {
        if (value == null || value.isBlank()) return "";
        String line = WHITESPACE.matcher(value).replaceAll(" ").strip();
        if (text.measureWidth(line, TEXT_SCALE) <= width) return line;
        int end = line.length();
        while (end > 0 && text.measureWidth(line.substring(0, end) + "...", TEXT_SCALE) > width) end--;
        return line.substring(0, end) + "...";
    }

    private static void icon(GraphicsManager graphics, int x, int y, Status status) {
        // Authored 9x8 masks; every set bit is exactly one projection pixel.
        int[] rows = switch (status) {
            case REWIND -> new int[] {0x22, 0x66, 0xEE, 0x1FE, 0xEE, 0x66, 0x22, 0};
            case HOLD -> new int[] {0x38, 0x44, 0x82, 0x82, 0x82, 0x44, 0x38, 0};
            case RECORD -> new int[] {0x38, 0x7C, 0xFE, 0xFE, 0xFE, 0x7C, 0x38, 0};
            case PLAY, PLAY_WARNING -> new int[] {0x80, 0xC0, 0xE0, 0xF0, 0xE0, 0xC0, 0x80, 0};
            case STOP -> new int[] {0xFE, 0xFE, 0xFE, 0xFE, 0xFE, 0xFE, 0xFE, 0};
            case WARNING, ERROR -> new int[] {0x10, 0x10, 0x10, 0x10, 0, 0x10, 0x10, 0};
        };
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < 9; column++) {
                if ((rows[row] & (1 << (8 - column))) != 0) {
                    rect(graphics, x + column, y + row, 1, 1, status.color, 255);
                }
            }
        }
    }

    private static DebugColor color(float[] rgb) {
        return new DebugColor(Math.round(rgb[0] * 255), Math.round(rgb[1] * 255), Math.round(rgb[2] * 255));
    }

    private static void rect(GraphicsManager graphics, int x, int y, int width, int height, float[] rgb, int alpha) {
        graphics.registerCommand(GLCommand.screenSpaceRect(graphics, 224, x, y, width, height,
                alpha << 24 | Math.round(rgb[0] * 255) << 16 | Math.round(rgb[1] * 255) << 8 | Math.round(rgb[2] * 255)));
    }
}
