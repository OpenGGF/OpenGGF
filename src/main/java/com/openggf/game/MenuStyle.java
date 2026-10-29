package com.openggf.game;

import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.TextFace;
import com.openggf.graphics.text.TextLanguage;

/**
 * Shared host-menu presentation on the native pixel grid. Widths and fits come from
 * {@link HostText} in the menu font's language (the active {@link HostTextContext} unless the
 * font is pinned), so they match what {@link MenuPixelFont} draws for every script.
 */
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
        font.drawText(fit(language(font), text, maxWidth), x, y, COMPACT, r, g, b, 1);
    }

    /** Primary labels use the menu family's bold weight in fixed 9x10 cells. */
    public static void label(PixelFont font, String text, int x, int y, int maxWidth,
                            float r, float g, float b) {
        font.drawText(fitLabel(language(font), text, maxWidth), x, y, 1f, r, g, b, 1);
    }

    /** Bold label clipped at a grapheme boundary with a trailing "...". */
    public static String fitLabel(String text, int maxWidth) {
        return fitLabel(HostTextContext.current(), text, maxWidth);
    }

    public static String fitLabel(TextLanguage language, String text, int maxWidth) {
        return HostText.shared().fit(text, maxWidth, TextFace.LABEL, language);
    }

    /** Compact text clipped at a grapheme boundary with a trailing "...". */
    public static String fit(String text, int maxWidth) {
        return fit(HostTextContext.current(), text, maxWidth);
    }

    public static String fit(TextLanguage language, String text, int maxWidth) {
        return HostText.shared().fit(text, maxWidth, TextFace.COMPACT, language);
    }

    /** The language a menu font measures and draws in. */
    public static TextLanguage language(PixelFont font) {
        TextLanguage pinned = font instanceof MenuPixelFont menu ? menu.language() : null;
        return pinned != null ? pinned : HostTextContext.current();
    }
}
