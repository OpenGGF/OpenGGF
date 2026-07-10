package com.openggf.game.timeattack.mp;

import java.util.List;

/** Pure 40-column progress-strip composition. */
public final class MinimapLayout {
    public static final int COLUMNS = 40;

    public record Dot(int xPx, char glyph) {
    }

    private MinimapLayout() {
    }

    public static String compose(int levelWidthPx, List<Dot> dots) {
        char[] strip = new char[COLUMNS];
        java.util.Arrays.fill(strip, ' ');
        int width = Math.max(1, levelWidthPx);
        for (Dot dot : dots) {
            int column = clamp((int) ((long) dot.xPx() * COLUMNS / width),
                    0, COLUMNS - 1);
            if (precedence(dot.glyph()) >= precedence(strip[column])) {
                strip[column] = dot.glyph();
            }
        }
        return new String(strip);
    }

    public static char glyphForFarStatus(int status) {
        return switch (status) {
            case 2 -> '+';
            case 1 -> '.';
            default -> ' ';
        };
    }

    private static int precedence(char glyph) {
        return switch (glyph) {
            case '*' -> 3;
            case 'o' -> 2;
            case '+' -> 1;
            case '.' -> 0;
            default -> -1;
        };
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
