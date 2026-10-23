package com.openggf.net.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A player's chosen look: a base colour (their name colour and the colour presets build a
 * character's body ramp from) and the entries of the character's 16-colour palette line that
 * differ from the ROM's stock palette. Colours are Mega Drive words {@code 0x0BGR} with even
 * nibbles, so every value is one the console could show.
 *
 * <p>The wire form is canonical: empty for {@link #STOCK}; otherwise the base colour as three
 * hex digits ({@code BGR}), then one {@code .IBGR} group per entry in ascending index order,
 * e.g. {@code "0E6.20E6.30C4.40A2"}. {@link #decode} accepts only that form, so equal looks
 * always travel as equal strings.
 *
 * @param baseColour Mega Drive colour word, or {@link #NO_BASE_COLOUR} for the stock look
 * @param palette    overridden palette entries, strictly ascending by index
 */
@com.openggf.game.ModApi
public record PlayerAppearance(int baseColour, List<Entry> palette) {
    public static final int NO_BASE_COLOUR = -1;
    public static final PlayerAppearance STOCK = new PlayerAppearance(NO_BASE_COLOUR, List.of());
    /** Longest canonical wire form: base colour plus all 16 entries. */
    public static final int MAX_ENCODED_LENGTH = 3 + 16 * 5;

    /** One overridden entry of the character's palette line. */
    @com.openggf.game.ModApi
    public record Entry(int index, int colour) {
        public Entry {
            if (index < 0 || index > 15) throw new IllegalArgumentException("palette index must be 0..15");
            requireColour(colour);
        }
    }

    public PlayerAppearance {
        palette = List.copyOf(Objects.requireNonNull(palette, "palette"));
        if (baseColour != NO_BASE_COLOUR) requireColour(baseColour);
        for (int i = 1; i < palette.size(); i++) {
            if (palette.get(i).index() <= palette.get(i - 1).index()) {
                throw new IllegalArgumentException("palette entries must be strictly ascending by index");
            }
        }
        if (baseColour == NO_BASE_COLOUR && !palette.isEmpty()) {
            throw new IllegalArgumentException("a custom palette needs a base colour");
        }
    }

    public boolean isStock() {
        return baseColour == NO_BASE_COLOUR;
    }

    /** The colour of entry {@code index}: the override when present, else {@code stockColour}. */
    public int colourAt(int index, int stockColour) {
        for (Entry entry : palette) {
            if (entry.index() == index) return entry.colour();
        }
        return stockColour;
    }

    public String encode() {
        if (isStock()) return "";
        StringBuilder out = new StringBuilder(MAX_ENCODED_LENGTH).append(hex3(baseColour));
        for (Entry entry : palette) {
            out.append('.').append(Character.forDigit(entry.index(), 16)).append(hex3(entry.colour()));
        }
        return out.toString().toUpperCase(java.util.Locale.ROOT);
    }

    /** Parses the canonical wire form; anything else is rejected. */
    public static PlayerAppearance decode(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.isEmpty()) return STOCK;
        if (encoded.length() > MAX_ENCODED_LENGTH) throw new IllegalArgumentException("appearance too long");
        String[] parts = encoded.split("\\.", -1);
        int base = parseColour(parts[0]);
        List<Entry> entries = new ArrayList<>(parts.length - 1);
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.length() != 4) throw new IllegalArgumentException("bad palette entry");
            entries.add(new Entry(hexDigit(part.charAt(0)), parseColour(part.substring(1))));
        }
        PlayerAppearance appearance = new PlayerAppearance(base, entries);
        if (!appearance.encode().equals(encoded)) throw new IllegalArgumentException("appearance is not canonical");
        return appearance;
    }

    /** {@code 0xRRGGBB} for a Mega Drive colour word, using the engine's 3-bit expansion. */
    public static int rgb(int colour) {
        requireColour(colour);
        int r = ((colour >> 1) & 7) * 255 + 3;
        int g = ((colour >> 5) & 7) * 255 + 3;
        int b = ((colour >> 9) & 7) * 255 + 3;
        return (r / 7) << 16 | (g / 7) << 8 | (b / 7);
    }

    /** Nearest Mega Drive colour word for {@code 0xRRGGBB}. */
    public static int fromRgb(int rgb) {
        int r = Math.round(((rgb >> 16) & 0xFF) * 7 / 255f);
        int g = Math.round(((rgb >> 8) & 0xFF) * 7 / 255f);
        int b = Math.round((rgb & 0xFF) * 7 / 255f);
        return b << 9 | g << 5 | r << 1;
    }

    /**
     * {@code 0xFFRRGGBB} for drawing this player's name on a dark plate: the base colour,
     * mixed toward white until it is light enough to read; stock looks use white.
     */
    public int nameArgb() {
        if (isStock()) return 0xFFF5F8FF;
        int rgb = rgb(baseColour);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        for (int step = 0; step < 8 && (r * 299 + g * 587 + b * 114) / 1000 < 150; step++) {
            r += (255 - r) / 3 + 1;
            g += (255 - g) / 3 + 1;
            b += (255 - b) / 3 + 1;
        }
        return 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    private static void requireColour(int colour) {
        if ((colour & ~0x0EEE) != 0) throw new IllegalArgumentException("not a Mega Drive colour: " + colour);
    }

    private static int parseColour(String text) {
        if (text.length() != 3) throw new IllegalArgumentException("bad colour");
        int colour = hexDigit(text.charAt(0)) << 8 | hexDigit(text.charAt(1)) << 4 | hexDigit(text.charAt(2));
        requireColour(colour);
        return colour;
    }

    private static int hexDigit(char c) {
        int digit = Character.digit(c, 16);
        if (digit < 0 || Character.isLowerCase(c)) throw new IllegalArgumentException("bad hex digit");
        return digit;
    }

    private static String hex3(int colour) {
        return String.format(java.util.Locale.ROOT, "%03X", colour);
    }
}
