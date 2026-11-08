package openggf.timeattack.mp;

import java.util.List;

/** Pure progress-strip composition: the 40-column text strip, and the lap-aware pixel strip's positions. */
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

    /** A racer's share of the whole race, in thousandths ({@code CompetitionHudState.progressPermille}). */
    public static final int PERMILLE = 1000;

    /**
     * Where a racer at {@code permille} of the race sits on a strip {@code width} pixels wide; the
     * last two pixels are the marker's own width, as for level-x positions.
     */
    public static int permilleX(int permille, int width) {
        return (int) ((long) clamp(permille, 0, PERMILLE) * Math.max(0, width - 2) / PERMILLE);
    }

    /** The x of each lap line inside the strip: {@code lapCount - 1} evenly spaced ticks (none for acts). */
    public static List<Integer> lapTicks(int lapCount, int width) {
        List<Integer> ticks = new java.util.ArrayList<>();
        for (int lap = 1; lap < lapCount; lap++) ticks.add(permilleX(lap * PERMILLE / lapCount, width));
        return List.copyOf(ticks);
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
