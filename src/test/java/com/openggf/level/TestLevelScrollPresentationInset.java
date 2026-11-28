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

    /** A quarter-speed background whose frozen phase can be resampled at another camera X. */
    private static LevelScrollPresentation quarterSpeedRaster(int cameraX) throws ReflectiveOperationException {
        int[] words = new int[224];
        java.util.Arrays.fill(words, ((short) -cameraX << 16) | ((short) -(cameraX >> 2) & 0xFFFF));
        var registers = new LevelScrollPresentation.Registers(cameraX, 0, cameraX, (short) 0, (short) 0,
                Integer.MIN_VALUE, 512, false, false, false, false, (short) 0, false, (short) 0,
                new ArenaMaskState(0, 320, 0));
        var raster = new LevelScrollPresentation(registers, words, null, null, null, null);
        var field = LevelScrollPresentation.class.getDeclaredField("phase");
        field.setAccessible(true);
        field.set(raster, (com.openggf.level.scroll.ScrollProjectionPhase) view -> {
            int[] sampled = new int[view.height()];
            java.util.Arrays.fill(sampled, ((short) -view.cameraX() << 16) | ((short) -(view.cameraX() >> 2) & 0xFFFF));
            return new com.openggf.level.scroll.ScrollProjection(sampled, null, null, null, (short) 0, (short) 0,
                    Integer.MIN_VALUE, 512, 0, 0, com.openggf.level.scroll.BgTilemapUpdateMode.STATIC_WINDOW);
        });
        return raster;
    }

    @Test
    void aPictureHeldAtTheLevelStartHoldsItsBackgroundToo() throws ReflectiveOperationException {
        // 400-pixel picture, centred inset 40. Camera.getPresentationInset holds the picture
        // at minX 0 while the native camera moves 0..40, so the inset equals the camera X.
        int[] held = null;
        for (int cameraX = 0; cameraX <= 40; cameraX++) {
            var picture = quarterSpeedRaster(cameraX).insetForPresentation(cameraX, 40, 320, 400,
                    new ArenaMaskState(0, 400, 0));
            int[] words = picture.horizontal();
            assertEquals(0, (short) (words[0] >>> 16), "FG stays at the level start, camera " + cameraX);
            assertEquals(0, picture.registers().cameraX());
            if (held == null) held = words;
            assertArrayEquals(held, words, "BG must not scroll behind a held FG, camera " + cameraX);
        }
        // Once centred, the background resumes at its own rate from the same place.
        var centred = quarterSpeedRaster(44).insetForPresentation(40, 40, 320, 400, new ArenaMaskState(0, 400, 0));
        assertEquals(-4, (short) (centred.horizontal()[0] >>> 16));
        assertEquals((short) (-(44 >> 2) + 40), (short) centred.horizontal()[0]);
        assertEquals((short) (-(40 >> 2) + 40), (short) held[0]);
    }

    @Test
    void withoutAPhaseTheNativeWordsAreShiftedAsBefore() {
        var plain = nativeRaster().insetForPresentation(20, 40, 320, 400, new ArenaMaskState(0, 400, 0));
        assertArrayEquals(nativeRaster().insetForPresentation(20, 400, new ArenaMaskState(0, 400, 0)).horizontal(),
                plain.horizontal());
    }
}
