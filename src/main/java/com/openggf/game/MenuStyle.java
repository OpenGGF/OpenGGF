package com.openggf.game;

import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.PixelFont;

/** Shared host-menu presentation on the native pixel grid. */
public final class MenuStyle {
    public static final float COMPACT = 2f / 3f;
    private MenuStyle() { }

    /** Center a text cell on the native pixel grid; odd spare pixels go below. */
    public static int textY(int rowTop, int rowHeight, float scale) {
        return contentY(rowTop, rowHeight, MenuPixelFont.glyphLineHeight(scale));
    }

    public static int contentY(int rowTop, int rowHeight, int contentHeight) {
        return rowTop + (rowHeight - contentHeight) / 2;
    }

    /** A primary label and its focus share the same ten-pixel cell geometry. */
    public static void focusLabel(PixelFont font, int x, int textY, int width, int height) {
        focusContent(font, x, textY, width, height, MenuPixelFont.glyphLineHeight(1));
    }

    public static void focusContent(PixelFont font, int x, int textY, int width, int height,
                                    int contentHeight) {
        focus(font, x, textY - (height - contentHeight) / 2, width, height);
    }

    public static void page(PixelFont font, int width, String title, String subtitle) {
        fill(font, 0, 0, width, 224, .025f, .065f, .19f, 1);
        checkerboard(font, width);
        fill(font, 0, 0, width, 29, .045f, .14f, .34f, 1);
        fill(font, 0, 28, width, 1, 1, .78f, .23f, 1);
        label(font, title, 9, 7, width - 18, 1, 1, 1);
        if (subtitle != null && !subtitle.isBlank()) text(font, subtitle, 9, 34, width - 18, .7f, .8f, .95f);
    }

    /** Low-contrast Sonic-style checks, aligned to the logical pixel grid. */
    public static void checkerboard(PixelFont font, int width) {
        checkerboard(font, 0, width);
    }

    public static void checkerboard(PixelFont font, int startX, int width) {
        if (font instanceof MenuPixelFont menu) menu.drawCheckerboard(startX, width);
    }

    public static void footer(PixelFont font, int width, String first, String second) {
        fill(font, 0, 198, width, 26, .012f, .025f, .09f, 1);
        if (first != null) text(font, first, 9, 200, width - 18, .5f, .9f, 1);
        if (second != null) label(font, second, 9, 212, width - 18, .8f, .86f, 1);
    }

    public static void panel(PixelFont font, int x, int y, int width, int height) {
        fill(font, x, y, width, height, .05f, .13f, .3f, 1);
    }

    public static void focus(PixelFont font, int x, int y, int width, int height) {
        fill(font, x, y, width, height, .08f, .23f, .43f, 1);
        fill(font, x, y, width, 1, .4f, .88f, 1, 1);
        fill(font, x, y + height - 1, width, 1, .4f, .88f, 1, 1);
        fill(font, x, y, 1, height, .4f, .88f, 1, 1);
        fill(font, x + width - 1, y, 1, height, .4f, .88f, 1, 1);
    }

    public static void fill(PixelFont font, int x, int y, int width, int height,
                            float r, float g, float b, float a) {
        if (font instanceof MenuPixelFont menu) menu.fillRect(x, y, width, height, r, g, b, a);
    }

    public static void text(PixelFont font, String text, int x, int y, int maxWidth,
                            float r, float g, float b) {
        font.drawText(fit(text, maxWidth), x, y, COMPACT, r, g, b, 1);
    }

    /** Primary labels use the menu family's bold weight in fixed 9x10 cells. */
    public static void label(PixelFont font, String text, int x, int y, int maxWidth,
                            float r, float g, float b) {
        font.drawText(fitLabel(text, maxWidth), x, y, 1f, r, g, b, 1);
    }

    public static String fitLabel(String text, int maxWidth) {
        return fit(text, maxWidth, 1f);
    }

    public static String fit(String text, int maxWidth) {
        return fit(text, maxWidth, COMPACT);
    }

    /**
     * Clip proportional menu text to a pixel width: the longest code-point prefix
     * followed by "..." that fits, or as many dots as fit when not even the
     * ellipsis does. Text that already fits is returned unchanged.
     */
    public static String fit(String text, int maxWidth, float scale) {
        if (text == null) return "";
        if (MenuPixelFont.textWidth(text, scale) <= maxWidth) return text;
        int dot = MenuPixelFont.advance('.', scale);
        int ellipsis = 3 * dot;
        if (ellipsis > maxWidth) return ".".repeat(Math.max(0, maxWidth / dot));
        int end = 0, width = 0;
        while (end < text.length()) {
            int codePoint = text.codePointAt(end);
            if (codePoint == '\r' || codePoint == '\n') break;
            int next = width + MenuPixelFont.advance(codePoint, scale);
            if (next + ellipsis > maxWidth) break;
            width = next;
            end += Character.charCount(codePoint);
        }
        return text.substring(0, end) + "...";
    }

    /**
     * Width-based word wrap of proportional menu text. Lines break at the last space
     * that fits; an unbroken word splits at the last fitting code point. Explicit
     * line breaks start new paragraphs, including empty ones.
     */
    public static java.util.List<String> wrap(String text, int maxWidth, float scale) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (text == null) return result;
        for (String paragraph : text.split("\\R", -1)) {
            String remaining = paragraph;
            int first = result.size();
            while (!remaining.isEmpty() && MenuPixelFont.textWidth(remaining, scale) > maxWidth) {
                int fit = fittingPrefix(remaining, maxWidth, scale);
                int end = remaining.lastIndexOf(' ', fit);
                // Always make progress, even when one glyph is wider than the line.
                if (end <= 0) end = Math.max(fit, Character.charCount(remaining.codePointAt(0)));
                result.add(remaining.substring(0, end));
                remaining = remaining.substring(end).stripLeading();
            }
            // An empty paragraph is kept; an empty tail after a split piece is not.
            if (!remaining.isEmpty() || result.size() == first) result.add(remaining);
        }
        return java.util.List.copyOf(result);
    }

    /** Split text into consecutive pieces no wider than {@code maxWidth}, keeping every code point. */
    /**
     * Wraps a file path by measured width without dropping anything. A line breaks after its last
     * folder separator, so a folder or file name that fits moves whole to the next line; failing
     * that after its last space; and only a name wider than the line breaks where the width runs out.
     */
    public static java.util.List<String> wrapPath(String path, int maxWidth, float scale) {
        java.util.List<String> result = new java.util.ArrayList<>();
        String rest = path == null ? "" : path;
        while (!rest.isEmpty()) {
            if (MenuPixelFont.textWidth(rest, scale) <= maxWidth) { result.add(rest); break; }
            int fit = Math.max(fittingPrefix(rest, maxWidth, scale), Character.charCount(rest.codePointAt(0)));
            int separator = Math.max(rest.lastIndexOf('/', fit - 1), rest.lastIndexOf('\\', fit - 1));
            int space = rest.lastIndexOf(' ', fit - 1);
            int end = separator > 0 ? separator + 1 : space > 0 ? space + 1 : fit;
            result.add(rest.substring(0, end));
            rest = rest.substring(end);
        }
        return result;
    }

    public static java.util.List<String> chunks(String text, int maxWidth, float scale) {
        java.util.List<String> result = new java.util.ArrayList<>();
        String rest = text == null ? "" : text;
        while (!rest.isEmpty()) {
            int end = Math.max(fittingPrefix(rest, maxWidth, scale), Character.charCount(rest.codePointAt(0)));
            result.add(rest.substring(0, end));
            rest = rest.substring(end);
        }
        return result;
    }

    /**
     * A marquee window over text wider than {@code maxWidth}: after a short hold it
     * scrolls by whole code points, holds at the end, then restarts. {@code step}
     * is the caller's slow clock. Text that fits is returned unchanged.
     */
    public static String marquee(String text, int maxWidth, float scale, int step) {
        if (text == null) return "";
        if (MenuPixelFont.textWidth(text, scale) <= maxWidth) return text;
        int overflow = 0;
        for (int start = 0; start < text.length() && MenuPixelFont.textWidth(text.substring(start), scale) > maxWidth;
                start = text.offsetByCodePoints(start, 1)) overflow++;
        int offset = Math.clamp(Math.floorMod(step, overflow + 30) - 15, 0, overflow);
        String tail = text.substring(text.offsetByCodePoints(0, offset));
        return tail.substring(0, fittingPrefix(tail, maxWidth, scale));
    }

    /**
     * Keep both ends of text around "...", whole code points only. The end keeps at
     * least two code points and otherwise at most {@code 1/endShare} of the room;
     * the start fills the rest. Too narrow for that, it falls back to {@link #fit}.
     */
    public static String elideMiddle(String text, int maxWidth, float scale, int endShare) {
        if (text == null) return "";
        if (MenuPixelFont.textWidth(text, scale) <= maxWidth) return text;
        int room = maxWidth - MenuPixelFont.textWidth("...", scale);
        int end = text.length(), endWidth = 0, kept = 0;
        while (end > 0) {
            int codePoint = text.codePointBefore(end);
            int advance = MenuPixelFont.advance(codePoint, scale);
            if (kept >= 2 && endWidth + advance > room / endShare) break;
            endWidth += advance;
            end -= Character.charCount(codePoint);
            kept++;
        }
        int start = fittingPrefix(text, room - endWidth, scale);
        if (start == 0 || start >= end) return fit(text, maxWidth, scale);
        return text.substring(0, start) + "..." + text.substring(end);
    }

    /** "..." followed by the longest suffix that fits; always keeps the last code point. */
    public static String elideStart(String text, int maxWidth, float scale) {
        if (text == null) return "";
        if (MenuPixelFont.textWidth(text, scale) <= maxWidth) return text;
        int room = maxWidth - MenuPixelFont.textWidth("...", scale);
        int start = text.length(), width = 0;
        while (start > 0) {
            int codePoint = text.codePointBefore(start);
            width += MenuPixelFont.advance(codePoint, scale);
            if (width > room && start < text.length()) break;
            start -= Character.charCount(codePoint);
        }
        return "..." + text.substring(start);
    }

    /** Length in chars of the longest single-line code-point prefix whose width fits, possibly zero. */
    public static int fittingPrefix(String text, int maxWidth, float scale) {
        int end = 0, width = 0;
        while (end < text.length()) {
            int codePoint = text.codePointAt(end);
            if (codePoint == '\r' || codePoint == '\n') break;
            width += MenuPixelFont.advance(codePoint, scale);
            if (width > maxWidth) break;
            end += Character.charCount(codePoint);
        }
        return end;
    }
}
