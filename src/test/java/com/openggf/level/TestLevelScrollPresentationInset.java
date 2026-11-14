package com.openggf.level;

import com.openggf.graphics.ArenaMaskState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** NATIVE widescreen draw mode widens the ROM's own scroll generation by an inset. */
class TestLevelScrollPresentationInset {
    private static LevelScrollPresentation nativeRaster() {
        int[] words = new int[224];
        for (int row = 0; row < words.length; row++) {
            short fg = (short) -1000, bg = (short) (-500 - row); // per-line BG deformation
            words[row] = (fg << 16) | (bg & 0xFFFF);
        }
        short[] columns = new short[20];
        for (int i = 0; i < columns.length; i++) columns[i] = (short) (i * 2);
        var registers = new LevelScrollPresentation.Registers(1000, 300, 1000, (short) 300, (short) 40,
                Integer.MIN_VALUE, 512, false, false, false, false, (short) 0, false, (short) 0,
                new ArenaMaskState(0, 320, 0));
        return new LevelScrollPresentation(registers, words, null, columns, null, null);
    }

    @Test
    void hscrollWordsShiftSoTheMarginsShowTheSamePlanes() {
        var inset = nativeRaster().insetForPresentation(40, 400, new ArenaMaskState(0, 400, 0));
        int[] words = inset.horizontal();
        for (int row = 0; row < 224; row++) {
            assertEquals(-960, (short) (words[row] >>> 16), "FG row " + row);
            assertEquals(-460 - row, (short) words[row], "BG row " + row);
        }
        assertEquals(960, inset.registers().cameraX());
        assertEquals(960, inset.registers().cameraXWithShake());
        assertEquals(Integer.MIN_VALUE, inset.registers().backgroundX());
    }

    @Test
    void perColumnVScrollRepeatsItsEdgeColumnsIntoTheMargins() {
        var inset = nativeRaster().insetForPresentation(40, 400, new ArenaMaskState(0, 400, 0));
        // -40 starts 8 pixels into column -3; 26 columns cover 400 pixels from there.
        assertEquals(8, inset.columnPhaseX());
        short[] columns = inset.backgroundColumns();
        assertEquals(26, columns.length);
        short[] expected = new short[26];
        for (int i = 0; i < expected.length; i++) expected[i] = (short) (Math.clamp(i - 3, 0, 19) * 2);
        assertArrayEquals(expected, columns);
    }

    @Test
    void aZeroInsetOnlyWidensToTheRight() {
        var inset = nativeRaster().insetForPresentation(0, 400, new ArenaMaskState(0, 400, 0));
        assertEquals(1000, inset.registers().cameraX());
        assertEquals(0, inset.columnPhaseX());
        assertEquals(25, inset.backgroundColumns().length);
    }
}
