package com.openggf.game;

import com.openggf.graphics.PixelFont;

import java.util.ArrayList;
import java.util.List;

/**
 * Label/value reading lines for details and diagnostic pages. Values wrap by measured
 * {@link MenuChrome#compactWidth} at spaces or path punctuation, never at a fixed
 * character count. Every drawable character survives: joining a field's lines in order
 * reproduces its value, less explicit newlines, which only end a line.
 */
public final class MenuFieldLines {
    public record Field(String label, String value, float[] color) {
        public Field(String label, String value) { this(label, value, MenuChrome.TEXT); }
    }

    /** One drawn row; {@code label} is null on a value's continuation rows. */
    public record Line(String label, String text, float[] color) { }

    private static final String BREAK_AFTER = " /\\_-.,:;";
    private static final int GAP = 8;

    private MenuFieldLines() { }

    /** Label column width for {@code fields}, including the gap before values. */
    public static int labelWidth(List<Field> fields) {
        int width = 0;
        for (Field field : fields) if (field.label() != null) width = Math.max(width, MenuChrome.compactWidth(field.label()));
        return width == 0 ? 0 : width + GAP;
    }

    public static List<Line> layout(List<Field> fields, int labelWidth, int valueWidth) {
        List<Line> lines = new ArrayList<>();
        for (Field field : fields) {
            List<String> parts = wrap(field.value(), valueWidth);
            for (int i = 0; i < parts.size(); i++)
                lines.add(new Line(i == 0 ? field.label() : null, parts.get(i), field.color()));
        }
        return lines;
    }

    /** Draws {@code count} lines from {@code first}; values start {@code labelWidth} after {@code x}. */
    public static void draw(PixelFont font, List<Line> lines, int first, int count, int x, int y, int pitch, int labelWidth) {
        for (int i = first; i < Math.min(lines.size(), first + count); i++, y += pitch) {
            Line line = lines.get(i);
            if (line.label() != null) MenuChrome.compact(font, line.label(), x, y, MenuChrome.MUTED);
            else if (i == first) {
                // A page or scroll position that starts mid-value repeats its label, dimmed.
                for (int j = i - 1; j >= 0; j--) {
                    if (lines.get(j).label() == null) continue;
                    MenuChrome.compact(font, lines.get(j).label(), x, y, MenuChrome.DIM);
                    break;
                }
            }
            MenuChrome.compact(font, line.text(), x + labelWidth, y, line.color());
        }
    }

    /** Splits {@code text} into lines no wider than {@code width}; trailing spaces stay on their line. */
    public static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < (text == null ? 0 : text.length());) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            line.appendCodePoint(c);
            if (c == ' ' || c == '\n') {
                if (c == '\n') { line.setLength(line.length() - 1); lines.add(line.toString()); line.setLength(0); }
                continue;
            }
            while (line.codePointCount(0, line.length()) > 1
                    && MenuChrome.compactWidth(line.toString().stripTrailing()) > width) {
                int cut = 0;
                for (int j = line.length() - 1; j > 0; j--) {
                    if (BREAK_AFTER.indexOf(line.charAt(j - 1)) >= 0) { cut = j; break; }
                }
                if (cut == 0) cut = line.offsetByCodePoints(line.length(), -1);
                lines.add(line.substring(0, cut));
                line.delete(0, cut);
            }
        }
        if (line.length() > 0 || lines.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
