package com.openggf.game.presentation;

import com.openggf.debug.DebugColor;
import com.openggf.game.MenuChrome;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;

import java.util.regex.Pattern;

/** Native-pixel transport plates for gameplay; contains no input or transport state. */
public final class TransportHud {
    // Gameplay overlay budget: one row, a second only for an exception, plate sized to content.
    private static final int MAX_WIDTH = 144;
    private static final int MARGIN = 8;
    private static final int PAD_X = 4;
    private static final int PAD_Y = 2;
    private static final int ROW = 12;
    private static final int TEXT_X = PAD_X + 9 + 4;
    // Match the font's word space: a smaller custom gap visually attaches F to REC/REW.
    private static final int VALUE_GAP = com.openggf.graphics.PixelFont.glyphWidth();
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

    /**
     * {@code title} and {@code value} share the only routine row; {@code note} adds a second row
     * for an exception. A negative progress value omits the hold meter.
     */
    public record Message(Status status, String title, String value, String note, int progressPercent) { }

    record Layout(int x, int y, int width, int height, String title, String value, String note) { }

    private TransportHud() { }

    public static void render(PixelFontTextRenderer text, GraphicsManager graphics, int viewportWidth,
                              Placement placement, Message message) {
        if (text == null) return;
        Layout layout = layout(text, viewportWidth, placement, message);
        if (graphics != null && graphics.isGlInitialized()) {
            // Match the shader picker's translucent navy. Only the one-pixel edge is opaque;
            // a filled opaque frame underneath would erase the scenery before alpha blending.
            rect(graphics, layout.x + 1, layout.y + 1, layout.width - 2, layout.height - 2, MenuChrome.NAVY, 209);
            rect(graphics, layout.x, layout.y, layout.width, 1, MenuChrome.EDGE, 255);
            rect(graphics, layout.x, layout.y + layout.height - 1, layout.width, 1, MenuChrome.EDGE, 255);
            rect(graphics, layout.x, layout.y + 1, 1, layout.height - 2, MenuChrome.EDGE, 255);
            rect(graphics, layout.x + layout.width - 1, layout.y + 1, 1, layout.height - 2, MenuChrome.EDGE, 255);
            if (!usesWarningGlyph(message.status)) {
                icon(graphics, layout.x + PAD_X, layout.y + PAD_Y + 2, message.status);
            }
            if (message.progressPercent >= 0) {
                int trackWidth = layout.width - 2 * PAD_X;
                int y = layout.y + layout.height - 4;
                rect(graphics, layout.x + PAD_X, y, trackWidth, 2, MenuChrome.EDGE, 255);
                int filled = trackWidth * Math.clamp(message.progressPercent, 0, 100) / 100;
                if (filled > 0) rect(graphics, layout.x + PAD_X, y, filled, 2, message.status.color, 255);
            }
            // Text batches draw immediately; complete their backing and glyphs first.
            graphics.flushScreenSpace();
        }
        text.beginBatch();
        try {
            int x = layout.x + TEXT_X;
            int y = layout.y + PAD_Y + 1;
            if (usesWarningGlyph(message.status)) {
                text.drawShadowedText("!", layout.x + PAD_X, y, color(message.status.color), TEXT_SCALE);
            }
            text.drawShadowedText(layout.title, x, y, color(message.status.color), TEXT_SCALE);
            if (!layout.value.isEmpty()) {
                text.drawShadowedText(layout.value, x + text.measureWidth(layout.title, TEXT_SCALE) + VALUE_GAP, y,
                        color(MenuChrome.TEXT), TEXT_SCALE);
            }
            if (!layout.note.isEmpty()) {
                boolean alert = message.status == Status.ERROR || message.status == Status.WARNING
                        || message.status == Status.PLAY_WARNING;
                text.drawShadowedText(layout.note, x, y + ROW,
                        color(alert ? message.status.color : MenuChrome.MUTED), TEXT_SCALE);
            }
        } finally {
            text.endBatch();
        }
    }

    static Layout layout(PixelFontTextRenderer text, int viewportWidth, Placement placement, Message message) {
        int textRoom = MAX_WIDTH - TEXT_X - PAD_X;
        String title = fit(text, message.title, textRoom);
        int titleWidth = text.measureWidth(title, TEXT_SCALE);
        String value = fit(text, message.value, textRoom - titleWidth - VALUE_GAP);
        String note = fit(text, message.note, textRoom);
        int rowWidth = titleWidth + (value.isEmpty() ? 0 : VALUE_GAP + text.measureWidth(value, TEXT_SCALE));
        int width = TEXT_X + Math.max(rowWidth, text.measureWidth(note, TEXT_SCALE)) + PAD_X;
        int height = 2 * PAD_Y + ROW * (note.isEmpty() ? 1 : 2) + (message.progressPercent >= 0 ? 3 : 0);
        // All three games place lives at y=200 and score/time/rings at the upper left.
        // Separate horizontal lanes also leave room for recording while live rewind is held.
        int x = placement == Placement.ABOVE_LIVES
                ? Math.max(0, (viewportWidth - 320) / 2) + MARGIN : viewportWidth - MARGIN - width;
        int bottom = placement == Placement.ABOVE_LIVES ? 196 : 216;
        return new Layout(x, bottom - height, width, height, title, value, note);
    }

    private static String fit(PixelFontTextRenderer text, String value, int width) {
        if (value == null || value.isBlank()) return "";
        String line = WHITESPACE.matcher(value).replaceAll(" ").strip();
        if (text.measureWidth(line, TEXT_SCALE) <= width) return line;
        if (text.measureWidth("...", TEXT_SCALE) > width) return "";
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
            case WARNING, ERROR -> throw new IllegalArgumentException("Warnings use the font's ! glyph");
        };
        // One-pixel silhouette like the font's own outline. Horizontal runs keep both
        // passes cheaper than submitting each filled icon pixel as a separate rectangle.
        for (int row = -1; row <= rows.length; row++) {
            int neighbours = 0;
            for (int dy = -1; dy <= 1; dy++) {
                int source = row + dy;
                if (source >= 0 && source < rows.length) neighbours |= rows[source];
            }
            int mask = neighbours << 1;
            mask |= (mask << 1) | (mask >>> 1);
            iconRow(graphics, x - 1, y + row, mask, 11, MenuChrome.INK);
        }
        for (int row = 0; row < rows.length; row++) {
            iconRow(graphics, x, y + row, rows[row], 9, status.color);
        }
    }

    private static void iconRow(GraphicsManager graphics, int x, int y, int mask, int width, float[] rgb) {
        int column = 0;
        while (column < width) {
            if ((mask & (1 << (width - column - 1))) == 0) {
                column++;
                continue;
            }
            int start = column++;
            while (column < width && (mask & (1 << (width - column - 1))) != 0) column++;
            rect(graphics, x + start, y, column - start, 1, rgb, 255);
        }
    }

    private static boolean usesWarningGlyph(Status status) {
        return status == Status.WARNING || status == Status.ERROR;
    }

    private static DebugColor color(float[] rgb) {
        return new DebugColor(Math.round(rgb[0] * 255), Math.round(rgb[1] * 255), Math.round(rgb[2] * 255));
    }

    private static void rect(GraphicsManager graphics, int x, int y, int width, int height, float[] rgb, int alpha) {
        graphics.registerCommand(GLCommand.screenSpaceRect(graphics, 224, x, y, width, height,
                alpha << 24 | Math.round(rgb[0] * 255) << 16 | Math.round(rgb[1] * 255) << 8 | Math.round(rgb[2] * 255)));
    }
}
