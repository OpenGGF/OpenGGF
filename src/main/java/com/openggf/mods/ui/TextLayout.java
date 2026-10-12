package com.openggf.mods.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pixel-measured text layout, independent of a font or a mod's styling/markup. */
@com.openggf.game.ModApi
public final class TextLayout {
    private TextLayout() { }
    @com.openggf.game.ModApi
    @FunctionalInterface public interface Metrics { int width(String text); }
    @com.openggf.game.ModApi
    public enum Alignment { LEFT, CENTER, RIGHT }

    public static int alignedX(int x, int areaWidth, int textWidth, Alignment alignment) {
        Objects.requireNonNull(alignment, "alignment");
        return x + switch (alignment) {
            case LEFT -> 0;
            case CENTER -> (areaWidth - textWidth) / 2;
            case RIGHT -> areaWidth - textWidth;
        };
    }

    /** Word wrapping; explicit blank lines survive and a word wider than the area remains intact. */
    public static List<String> wrap(String text, int maxWidth, Metrics metrics) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(metrics, "metrics");
        if (maxWidth < 1) throw new IllegalArgumentException("Positive text area required");
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            String line = "";
            for (String word : paragraph.split(" +")) {
                if (word.isEmpty()) continue;
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && metrics.width(candidate) > maxWidth) {
                    lines.add(line);
                    line = word;
                } else line = candidate;
            }
            lines.add(line);
        }
        return List.copyOf(lines);
    }

    public static String ellipsis(String text, int maxWidth, Metrics metrics) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(metrics, "metrics");
        if (maxWidth <= 0) return "";
        if (metrics.width(text) <= maxWidth) return text;
        String suffix = "...";
        while (!suffix.isEmpty() && metrics.width(suffix) > maxWidth) suffix = suffix.substring(1);
        int end = text.length();
        while (end > 0 && metrics.width(text.substring(0, end) + suffix) > maxWidth) end--;
        return text.substring(0, end) + suffix;
    }
}
