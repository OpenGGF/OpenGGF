package com.openggf.level.objects;

import com.openggf.level.Pattern;

/**
 * The ring hunger dial: a 16x16 clock face (2x2 native tiles) drawn in the game's own HUD
 * inks. The face is a wedge that starts full at twelve o'clock and empties clockwise, like a
 * clock hand sweeping; the emptied part is transparent inside the outline ring.
 *
 * <p>Two sets are built. The ordinary set uses the RINGS label's most frequent ink (yellow in
 * all three games) on the HUD text palette line, so it reads as part of the native HUD. The
 * warning set is drawn on the HUD warning line (the line S2's RINGS warning switches to): its
 * face is that line's reddest palette entry, the red of the player's own palette. Each set's
 * ring is two-tone: an inner band in a light ink (the HUD digits' own fill on the text line,
 * the line's lightest entry on the warning line) inside an outer edge in the line's darkest
 * entry, so the empty dial shown while rings drain reads over dark and light scenery alike. Sonic 3 &amp; Knuckles'
 * native ring warning blinks the label off instead of turning it red, so only the warning
 * line's colours, not the label art, can give every game the same red.
 *
 * <p>Each set has seventeen frames for 0..16 sixteenths remaining; frame {@code n} occupies
 * patterns {@code n*4 .. n*4 + 3} of its set in top-left, top-right, bottom-left, bottom-right
 * order. The pixels are host-drawn presentation geometry, not ROM asset bytes; only the
 * palette indices come from the ROM art and palette.
 */
public final class HudHungerDial {
    public static final int FRAMES = 17;
    public static final int TILES_PER_FRAME = 4;
    public static final int SET_SIZE = FRAMES * TILES_PER_FRAME;
    public static final int SIZE = 16;

    private HudHungerDial() { }

    /** Non-transparent indices of {@code tiles}, most frequent first (ties: lower index). */
    static int[] inksByFrequency(Pattern... tiles) {
        int[] counts = new int[16];
        for (Pattern tile : tiles) {
            if (tile == null) continue;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) counts[tile.getPixel(x, y) & 15]++;
        }
        return java.util.stream.IntStream.range(1, 16).filter(i -> counts[i] > 0).boxed()
                .sorted((a, b) -> counts[b] != counts[a] ? counts[b] - counts[a] : a - b)
                .mapToInt(Integer::intValue).toArray();
    }

    /** The ten 8x16 decimal digits (twenty tiles) of the HUD digit art. */
    static Pattern[] decimalDigits(Pattern[] digits) {
        return digits == null ? new Pattern[0] : java.util.Arrays.copyOf(digits, Math.min(digits.length, 20));
    }

    /**
     * Ordinary face and outline: the label's dominant ink, outlined by the digits' second ink
     * (their shadow). Without label art the digits supply both. {@code null} without any ink.
     */
    static int[] ordinaryInks(Pattern[] labelTiles, Pattern[] digits) {
        int[] digitInks = inksByFrequency(decimalDigits(digits));
        int[] labelInks = labelTiles == null ? new int[0] : inksByFrequency(labelTiles);
        int face = labelInks.length > 0 ? labelInks[0] : digitInks.length > 0 ? digitInks[0] : -1;
        if (face < 0) return null;
        // Prefer the digits' second ink (the shadow under their dominant fill), then any other.
        int edge = face;
        for (int i = digitInks.length > 1 ? 1 : 0; i < digitInks.length; i++)
            if (digitInks[i] != face) { edge = digitInks[i]; break; }
        if (edge == face) for (int ink : digitInks) if (ink != face) { edge = ink; break; }
        return new int[] { face, edge };
    }

    /** Warning face (reddest) and outline (darkest) of one palette line; {@code null} without a palette. */
    static int[] warningInks(com.openggf.level.Palette line) {
        if (!complete(line)) return null;
        int red = -1, bestRed = Integer.MIN_VALUE;
        for (int i = 1; i < 16; i++) {
            var c = line.colors[i];
            int redness = 2 * (c.r & 255) - (c.g & 255) - (c.b & 255);
            if (redness > bestRed) { bestRed = redness; red = i; }
        }
        return new int[] { red, outline(line, red) };
    }

    /** The darkest opaque entry of {@code line} other than {@code face}, or {@code fallback}. */
    static int darkest(com.openggf.level.Palette line, int face, int fallback) {
        return complete(line) ? outline(line, face) : fallback;
    }

    private static int outline(com.openggf.level.Palette line, int face) {
        int dark = face, bestDark = Integer.MAX_VALUE;
        for (int i = 1; i < 16; i++) {
            if (i == face) continue;
            var c = line.colors[i];
            int luma = 3 * (c.r & 255) + 6 * (c.g & 255) + (c.b & 255);
            if (luma < bestDark) { bestDark = luma; dark = i; }
        }
        return dark;
    }

    private static boolean complete(com.openggf.level.Palette line) {
        return line != null && line.colors != null && line.colors.length >= 16;
    }

    /** Lightest opaque entry of {@code line} other than {@code face}, or {@code fallback}. */
    static int lightest(com.openggf.level.Palette line, int face, int fallback) {
        if (!complete(line)) return fallback;
        int light = fallback, best = Integer.MIN_VALUE;
        for (int i = 1; i < 16; i++) {
            if (i == face) continue;
            var c = line.colors[i];
            int luma = 3 * (c.r & 255) + 6 * (c.g & 255) + (c.b & 255);
            if (luma > best) { best = luma; light = i; }
        }
        return light;
    }

    /** The digits' dominant fill, the light ink of the ordinary ring; {@code fallback} if it is the face. */
    static int digitFill(Pattern[] digits, int face, int fallback) {
        int[] inks = inksByFrequency(decimalDigits(digits));
        return inks.length > 0 && inks[0] != face ? inks[0] : fallback;
    }

    /** One set with a single-colour ring (the light band takes the edge ink). */
    public static Pattern[] build(int face, int edge) { return build(face, edge, edge); }

    /** One set of seventeen frames: {@code face} wedge, {@code light} inner ring band, {@code edge} outer edge. */
    public static Pattern[] build(int face, int light, int edge) {
        Pattern[] result = new Pattern[SET_SIZE];
        for (int frame = 0; frame < FRAMES; frame++) {
            for (int tile = 0; tile < TILES_PER_FRAME; tile++) result[frame * 4 + tile] = new Pattern();
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int value = pixel(x, y, frame, face, light, edge);
                    if (value == 0) continue;
                    int tile = (y / 8) * 2 + x / 8;
                    result[frame * 4 + tile].setPixel(x % 8, y % 8, (byte) value);
                }
            }
        }
        return result;
    }

    /** One dial pixel for {@code remaining} sixteenths; 0 is transparent. */
    static int pixel(int x, int y, int remaining, int face, int light, int edge) {
        double dx = x + 0.5 - 8, dy = y + 0.5 - 8;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance > 7.6) return 0;
        if (distance > 6.6) return edge;          // dark outer edge, about 1 px
        if (distance > 5.2) return light;         // light ring band, about 1-2 px
        if (distance < 1.2) return edge;          // the hand's pivot
        // Clockwise from twelve o'clock, 0 inclusive .. 1 exclusive.
        double turn = Math.atan2(dx, -dy) / (2 * Math.PI);
        if (turn < 0) turn += 1;
        double emptied = 1 - remaining / 16.0;
        return turn >= emptied ? face : 0;
    }
}
