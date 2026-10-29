package com.openggf.game;

import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.PixelFont;
import com.openggf.util.Graphemes;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.TextLanguage;
import com.openggf.graphics.text.TextFace;
import java.util.List;

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

    public static int footerTop() {
        int pitch = MenuChrome.compactLineHeight();
        return pitch <= 10 ? 198 : 224 - pitch * 2 - 4;
    }

    public static void footer(PixelFont font, int width, String first, String second) {
        footer(font, width, first, second, 0);
    }

    public static void footer(PixelFont font, int width, String first, String second, int ticks) {
        int top = footerTop();
        fill(font, 0, top, width, 224 - top, .012f, .025f, .09f, 1);
        boolean tall = MenuChrome.compactLineHeight() > 10;
        int firstY = top + 2 + (tall ? MenuChrome.compactMetrics().above() : 0);
        int secondY = tall ? firstY + MenuChrome.compactLineHeight() : 212;
        if (first != null) text(font, pageText(first, width - 18, COMPACT, ticks), 9, firstY, width - 18, .5f, .9f, 1);
        if (second != null) {
            float scale = tall ? COMPACT : 1;
            font.drawText(pageText(second, width - 18, scale, ticks), 9, secondY, scale, .8f, .86f, 1, 1);
        }
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
        font.drawText(fit(language(font), text, maxWidth), x, y, COMPACT, r, g, b, 1);
    }

    /** Primary labels use the menu family's bold weight in proportional 9x10 cells. */
    public static void label(PixelFont font, String text, int x, int y, int maxWidth,
                            float r, float g, float b) {
        font.drawText(fitLabel(language(font), text, maxWidth), x, y, 1f, r, g, b, 1);
    }

    public static String fitLabel(String text, int maxWidth) {
        return fitLabel(HostTextContext.current(), text, maxWidth);
    }

    public static String fit(String text, int maxWidth) {
        return fit(HostTextContext.current(), text, maxWidth);
    }

    /**
     * Clip proportional menu text to a pixel width: the longest grapheme prefix
     * followed by "..." that fits, or as many dots as fit when not even the
     * ellipsis does. Text that already fits is returned unchanged.
     */
    public static String fit(String text, int maxWidth, float scale) {
        return HostText.shared().fit(text, Math.floorDiv(maxWidth, MenuPixelFont.pixelScale(scale)),
                MenuPixelFont.face(scale), HostTextContext.current());
    }

    /**
     * Width-based word wrap of proportional menu text. Lines break at the last space
     * that fits; an unbroken word splits at the last fitting code point. Explicit
     * line breaks start new paragraphs, including empty ones.
     */
    public static java.util.List<String> wrap(String text, int maxWidth, float scale) {
        if (text == null) return java.util.List.of();
        return HostText.shared().wrap(text, Math.max(1, maxWidth / MenuPixelFont.pixelScale(scale)),
                MenuPixelFont.face(scale), HostTextContext.current());
    }

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
            int fit = Math.max(fittingPrefix(rest, maxWidth, scale), Graphemes.next(rest, 0));
            int separator = Math.max(rest.lastIndexOf('/', fit - 1), rest.lastIndexOf('\\', fit - 1));
            int space = rest.lastIndexOf(' ', fit - 1);
            int end = separator > 0 ? separator + 1 : space > 0 ? space + 1 : fit;
            result.add(rest.substring(0, end));
            rest = rest.substring(end);
        }
        return result;
    }

    /** Split text into consecutive pieces no wider than {@code maxWidth}, keeping every code point. */
    public static java.util.List<String> chunks(String text, int maxWidth, float scale) {
        java.util.List<String> result = new java.util.ArrayList<>();
        String rest = text == null ? "" : text;
        while (!rest.isEmpty()) {
            int end = Math.max(fittingPrefix(rest, maxWidth, scale), Graphemes.next(rest, 0));
            result.add(rest.substring(0, end));
            rest = rest.substring(end);
        }
        return result;
    }

    /**
     * A marquee window over text wider than {@code maxWidth}: after a short hold it
     * scrolls by whole graphemes, holds at the end, then restarts. {@code step}
     * is the caller's slow clock. Text that fits is returned unchanged.
     */
    public static String marquee(String text, int maxWidth, float scale, int step) {
        if (text == null) return "";
        if (MenuPixelFont.textWidth(text, scale) <= maxWidth) return text;
        int overflow = 0;
        for (int start = 0; start < text.length() && MenuPixelFont.textWidth(text.substring(start), scale) > maxWidth;
                start = Graphemes.next(text, start)) overflow++;
        int offset = Math.clamp(Math.floorMod(step, overflow + 30) - 15, 0, overflow);
        int start = 0;
        for (int i = 0; i < offset; i++) start = Graphemes.next(text, start);
        String tail = text.substring(start);
        return tail.substring(0, fittingPrefix(tail, maxWidth, scale));
    }

    /**
     * Keep both ends of text around "...", whole graphemes only. The end keeps at
     * least two graphemes and otherwise at most {@code 1/endShare} of the room;
     * the start fills the rest. Too narrow for that, it falls back to {@link #fit}.
     */
    public static String elideMiddle(String text, int maxWidth, float scale, int endShare) {
        if (text == null) return "";
        if (MenuPixelFont.textWidth(text, scale) <= maxWidth) return text;
        reportEllipsis(text, maxWidth, scale);
        int room = maxWidth - MenuPixelFont.textWidth("...", scale);
        int end = text.length(), endWidth = 0, kept = 0;
        while (end > 0) {
            int previous = Graphemes.previous(text, end);
            int nextWidth = MenuPixelFont.textWidth(text.substring(previous), scale);
            if (kept >= 2 && nextWidth > room / endShare) break;
            endWidth = nextWidth;
            end = previous;
            kept++;
        }
        int start = fittingPrefix(text, room - endWidth, scale);
        if (start == 0 || start >= end) return fit(text, maxWidth, scale);
        String result = text.substring(0, start) + "..." + text.substring(end);
        while (start > 0 && MenuPixelFont.textWidth(result, scale) > maxWidth) {
            start = Graphemes.previous(text, start);
            result = text.substring(0, start) + "..." + text.substring(end);
        }
        return start == 0 ? fit(text, maxWidth, scale) : result;
    }

    /** "..." followed by the longest suffix that fits; always keeps the last code point. */
    public static String elideStart(String text, int maxWidth, float scale) {
        if (text == null) return "";
        if (MenuPixelFont.textWidth(text, scale) <= maxWidth) return text;
        reportEllipsis(text, maxWidth, scale);
        int room = maxWidth - MenuPixelFont.textWidth("...", scale);
        int start = text.length(), width = 0;
        while (start > 0) {
            int previous = Graphemes.previous(text, start);
            width = MenuPixelFont.textWidth(text.substring(previous), scale);
            if (width > room && start < text.length()) break;
            start = previous;
        }
        String result = "..." + text.substring(start);
        while (start < text.length() && MenuPixelFont.textWidth(result, scale) > maxWidth) {
            int next = Graphemes.next(text, start);
            if (next == text.length()) break; // Retain the final grapheme as this helper promises.
            start = next;
            result = "..." + text.substring(start);
        }
        return result;
    }

    /** Length in chars of the longest single-line grapheme prefix whose width fits, possibly zero. */
    public static int fittingPrefix(String text, int maxWidth, float scale) {
        int end = 0;
        while (end < text.length()) {
            int next = Graphemes.next(text, end);
            String prefix = text.substring(0, next);
            if (prefix.indexOf('\r') >= 0 || prefix.indexOf('\n') >= 0
                    || MenuPixelFont.textWidth(prefix, scale) > maxWidth) break;
            end = next;
        }
        return end;
    }

    public static String fitLabel(TextLanguage language, String text, int maxWidth) {
        return HostText.shared().fit(text, maxWidth, MenuPixelFont.face(1), language);
    }

    public static String fit(TextLanguage language, String text, int maxWidth) {
        return HostText.shared().fit(text, maxWidth, MenuPixelFont.face(COMPACT), language);
    }

    public static void reportEllipsis(String text, int pixels, float scale) {
        HostText.shared().reportEllipsis(text, pixels / MenuPixelFont.pixelScale(scale),
                MenuPixelFont.face(scale), HostTextContext.current());
    }

    /** Complete measured fragments for small controls that cannot grow or wrap vertically. */
    public static String pageText(String text, int maxWidth, float scale, int ticks) {
        return pageText(HostTextContext.current(), text, maxWidth, scale, ticks);
    }

    public static String pageText(TextLanguage language, String text, int maxWidth, float scale, int ticks) {
        int width = maxWidth / MenuPixelFont.pixelScale(scale);
        if (text == null || width <= 0) return "";
        TextFace face = MenuPixelFont.face(scale);
        var host = HostText.shared();
        if (text.indexOf('\n') < 0 && text.indexOf('\r') < 0 && host.width(text, face, language) <= width) return text;
        List<String> pages = host.wrap(text, width, face, language);
        return pages.get(Math.floorMod(ticks / 90, pages.size()));
    }

    /** The language a menu font measures and draws in. */
    public static TextLanguage language(PixelFont font) {
        TextLanguage pinned = font instanceof MenuPixelFont menu ? menu.language() : null;
        return pinned != null ? pinned : HostTextContext.current();
    }

}
