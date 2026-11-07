package com.openggf.game.sonic2;

import com.openggf.level.Pattern;

import java.util.Objects;

/**
 * Converts the S&amp;K {@code ArtNem_KnucklesLifeIcon} (sonic3k.asm:202118-202119)
 * to the palette-line-0 colour roles Knuckles in Sonic 2 draws its lives
 * counter with, for the tier-one stand-in and the S3K-donor Knuckles HUD.
 *
 * <p>The icon is 12 tiles: a 2x2 head (tiles 0-3) and a 4x2 "K.T.E" name
 * (tiles 4-11). The two halves were drawn for different palette lines in
 * S&amp;K: the head on line 0 and the name on line 1 ({@code Map_HUD_}
 * {@code dc.b $40,$D,$21,$E,0,$10}, Map - HUD.asm:18). KiS2's
 * {@code Map_hud_a} draws both pieces on line 0 (KiS2
 * mappings/sprite/hud_a.asm:16-17), so the head takes the caller's sprite
 * conversion ({@code ArtConvTable}) while the name needs its own: running the
 * name's line-1 indices through {@code ArtConvTable} sends its strokes (5) to
 * line-0 index 4, Knuckles' green, and its shadow (14) to grey index 9.
 *
 * <p>{@link #NAME_CONV_TABLE} is read off KiS2's own name glyphs in
 * {@code ArtNem_Sonic_life_counter} = "Knuckles lives counter.nem" (the KiS2
 * branch's BINCLUDE; chip address {@code Kis2Constants.ART_NEM_KNUCKLES_LIFE_COUNTER}),
 * which on the user-supplied dump are pixel-for-pixel the S&amp;K
 * glyphs with indices 5 → 14 ({@code $0AE} yellow-orange), 1 → 6
 * ({@code $EEE} white band), 14 and 15 → 1 ({@code $000} outline) on
 * {@code Pal_BGND} line 0. The S&amp;K name tiles use no other index; any other
 * index is rejected rather than guessed.
 */
public final class S3kKnucklesLifeIconConversion {

    /** Tiles in {@code ArtNem_KnucklesLifeIcon} (its Nemesis header). */
    public static final int TILE_COUNT = 12;
    /** The 2x2 head piece ({@code Map_hud_a} tile {@code $10A}). */
    public static final int HEAD_TILE_COUNT = 4;

    private static final int UNMAPPED = -1;

    /** S&amp;K HUD-name index (line 1) → KiS2 lives-name index (line 0). */
    static final int[] NAME_CONV_TABLE = {
        0x0, 0x6, UNMAPPED, UNMAPPED, UNMAPPED, 0xE, UNMAPPED, UNMAPPED,
        UNMAPPED, UNMAPPED, UNMAPPED, UNMAPPED, UNMAPPED, UNMAPPED, 0x1, 0x1
    };

    private S3kKnucklesLifeIconConversion() {
    }

    /**
     * Converts {@code tiles} in place: the head through {@code headTable}
     * (the S3K → S2 line-0 sprite conversion), the name through
     * {@link #NAME_CONV_TABLE}.
     *
     * @throws IllegalArgumentException if the tile count is not the icon's, or
     *                                  a name pixel uses an index KiS2's name glyphs do not
     */
    public static void convert(Pattern[] tiles, int[] headTable) {
        Objects.requireNonNull(tiles, "tiles");
        Objects.requireNonNull(headTable, "headTable");
        if (tiles.length != TILE_COUNT) {
            throw new IllegalArgumentException("Knuckles life icon has " + tiles.length
                    + " tiles, expected " + TILE_COUNT);
        }
        for (int i = 0; i < tiles.length; i++) {
            int[] table = i < HEAD_TILE_COUNT ? headTable : NAME_CONV_TABLE;
            Pattern tile = tiles[i];
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                    int index = tile.getPixel(x, y) & 0x0F;
                    int converted = table[index];
                    if (converted == UNMAPPED) {
                        throw new IllegalArgumentException("Knuckles life icon name tile " + i
                                + " uses index " + index + ", which has no lives-name colour role");
                    }
                    tile.setPixel(x, y, (byte) converted);
                }
            }
        }
    }
}
