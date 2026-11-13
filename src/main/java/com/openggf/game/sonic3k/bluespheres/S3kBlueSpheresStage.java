package com.openggf.game.sonic3k.bluespheres;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageDataLoader;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageRomOffsets;

import java.io.IOException;
import java.util.Arrays;

/**
 * One generated Blue Spheres stage: the 32x32 layout plus its start
 * parameters, in the same {@code $408}-byte shape as a stored S3 layout so
 * the special stage grid loads it unchanged.
 *
 * <p>ROM: loc_86C8 (sonic3k.asm:10952-11028). {@code SSLayoutData2_Kos}
 * decompresses to {@code $80} ring counts, {@code $80} difficulty bytes and
 * 128 pieces of 16x16 cells. The four seed bytes each choose a piece for one
 * quadrant; {@code word_87BA} gives each quadrant's read step, start and row
 * step, mirroring the right and bottom quadrants.
 *
 * @param layout          {@code $400} grid bytes followed by angle, x, y and rings-left words
 * @param seed            the four piece indices ({@code Blue_spheres_current_stage})
 * @param ringsLeft       {@code Special_stage_rings_left}: the pieces' ring counts summed
 * @param difficultyWord  {@code Blue_spheres_difficulty} after the word adds: high byte is
 *                        the shown difficulty, low byte the target
 */
public record S3kBlueSpheresStage(byte[] layout, int[] seed, int ringsLeft, int difficultyWord) {
    /** {@code move.w #$4000,(Special_stage_angle).w}. */
    public static final int START_ANGLE = 0x4000;
    /** {@code move.w #$1000,(Special_stage_X_pos).w}. */
    public static final int START_X = 0x1000;
    /** {@code move.w #$300,(Special_stage_Y_pos).w}. */
    public static final int START_Y = 0x300;

    /** ROM address of {@code word_87BA}: four quadrants of (step, start, row step, offset). */
    static final long QUADRANT_TABLE = 0x87BA;
    private static final int GRID_SIZE = 0x400;
    private static final int PIECE_SIZE = 0x100;
    private static final int PIECE_BASE = 0x100;

    public S3kBlueSpheresStage {
        layout = layout.clone();
        seed = seed.clone();
    }

    @Override
    public byte[] layout() {
        return layout.clone();
    }

    @Override
    public int[] seed() {
        return seed.clone();
    }

    /** The difficulty the results screen counts up to ({@code Blue_spheres_target_difficulty}). */
    public int targetDifficulty() {
        return difficultyWord & 0xFF;
    }

    /** The difficulty the results screen starts from ({@code Blue_spheres_difficulty}). */
    public int startDifficulty() {
        return (difficultyWord >>> 8) & 0xFF;
    }

    /**
     * Stage palette bank: {@code (seed[2] & 7) << 4} indexes {@code SS_Pal_Map_Ptrs},
     * and bit 3 selects the {@code +$130} S&amp;K copy (loc_8798).
     */
    public int paletteIndex() {
        return seed[2] & 7;
    }

    public boolean skPalette() {
        return (seed[2] & 8) != 0;
    }

    /** loc_9C08 reads {@code Blue_spheres_current_stage+2} for the emerald colours. */
    public int emeraldPaletteIndex() {
        return seed[2] & 7;
    }

    /** Builds the stage for {@code seed} from the ROM's piece set and quadrant table. */
    public static S3kBlueSpheresStage build(Rom rom, Sonic3kSpecialStageDataLoader loader, int[] seed)
            throws IOException {
        byte[] pieces = loader.getCompressedLayoutSet(1);
        byte[] table = rom.readBytes(QUADRANT_TABLE, 4 * 8);
        return build(pieces, table, seed);
    }

    static S3kBlueSpheresStage build(byte[] pieces, byte[] quadrantTable, int[] seed) {
        if (seed.length != 4) throw new IllegalArgumentException("Blue Spheres seed has four pieces");
        byte[] grid = new byte[GRID_SIZE];
        int rings = 0;
        int difficulty = 0;
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            int piece = seed[quadrant] & 0x7F;
            rings = (rings + (pieces[piece] & 0xFF)) & 0xFFFF;
            difficulty = (difficulty + (pieces[0x80 + piece] & 0xFF)) & 0xFFFF;
            int base = PIECE_BASE + piece * PIECE_SIZE;
            int step = word(quadrantTable, quadrant * 8);
            int start = word(quadrantTable, quadrant * 8 + 2);
            int rowStep = word(quadrantTable, quadrant * 8 + 4);
            int out = word(quadrantTable, quadrant * 8 + 6);
            for (int row = 0; row < 16; row++) {
                int read = start;
                for (int col = 0; col < 16; col++) {
                    grid[out++] = pieces[base + (read & 0xFFFF)];
                    read = (short) (read + step);
                }
                out += 16;
                start = (short) (start + rowStep);
            }
        }
        byte[] layout = Arrays.copyOf(grid, Sonic3kSpecialStageRomOffsets.LAYOUT_STAGE_SIZE);
        putWord(layout, GRID_SIZE, START_ANGLE);
        putWord(layout, GRID_SIZE + 2, START_X);
        putWord(layout, GRID_SIZE + 4, START_Y);
        putWord(layout, GRID_SIZE + 6, rings);
        return new S3kBlueSpheresStage(layout, seed, rings, difficulty);
    }

    private static int word(byte[] data, int offset) {
        return (short) (((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF));
    }

    private static void putWord(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }
}
