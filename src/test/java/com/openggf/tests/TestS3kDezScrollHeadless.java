package com.openggf.tests;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.scroll.Sonic3kScrollHandlerProvider;
import com.openggf.game.sonic3k.scroll.SwScrlS3kDez;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Sonic 3 &amp; Knuckles Death Egg acts 1 and 2 background scroll.
 *
 * <p>{@code DEZ1_BackgroundInit} and {@code DEZ2_BackgroundInit} (sonic3k.asm:118641 and
 * :118770) both do {@code clr.w (Camera_X_pos_BG_copy).w} / {@code clr.w (Camera_Y_pos_BG_copy).w}
 * and then run {@code PlainDeformation} (:103598), which reads those two words and never writes
 * them. Nothing else in the game writes them either — they are only ever set by a zone's own
 * deformation routine — so for the whole of both acts the background horizontal scroll word is 0
 * and {@code V_scroll_value_BG} (written from {@code Camera_Y_pos_BG_copy} at the end of
 * {@code ScreenEvents}, :102254) is 0. The Death Egg background does not move.
 *
 * <p>The foreground word is {@code PlainDeformation}'s {@code -Camera_X_pos_copy} on every one of
 * the 224 visible lines.
 */
class TestS3kDezScrollHeadless {

    /** Two camera positions per act: the act start and a point well into the level. */
    @ParameterizedTest
    @CsvSource({
            "0, 0x0040, 0x0900",
            "0, 0x2A80, 0x0300",
            "1, 0x0160, 0x0300",
            "1, 0x34C0, 0x0C00",
    })
    void plainDeformationHoldsTheBackgroundStillAndScrollsTheForegroundWithTheCamera(
            int actIndex, int cameraX, int cameraY) {
        SwScrlS3kDez handler = new SwScrlS3kDez();
        int[] horizScrollBuf = new int[224];

        handler.init(actIndex, cameraX, cameraY);
        handler.update(horizScrollBuf, cameraX, cameraY, 0, actIndex);

        short expectedForeground = (short) -cameraX;
        for (int line = 0; line < horizScrollBuf.length; line++) {
            assertEquals(expectedForeground, (short) (horizScrollBuf[line] >>> 16),
                    "foreground scroll word on line " + line);
            assertEquals((short) 0, (short) horizScrollBuf[line],
                    "background scroll word on line " + line + " must stay 0");
        }
        assertEquals((short) 0, handler.getVscrollFactorBG(),
                "Camera_Y_pos_BG_copy is cleared at init and never rewritten");
    }

    /** With a still background the BG-FG delta is the whole foreground offset on every line. */
    @Test
    void trackedScrollOffsetsAreTheForegroundOffsetItself() {
        SwScrlS3kDez handler = new SwScrlS3kDez();
        int[] horizScrollBuf = new int[224];

        handler.update(horizScrollBuf, 0x2A80, 0x0300, 0, 0);

        assertEquals(0x2A80, handler.getMinScrollOffset());
        assertEquals(0x2A80, handler.getMaxScrollOffset());
    }

    @RequiresRom(SonicGame.SONIC_3K)
    @Test
    void theProviderGivesDeathEggItsOwnHandlerRatherThanTheQuarterSpeedDefault() throws Exception {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        try (Rom rom = new Rom()) {
            org.junit.jupiter.api.Assertions.assertTrue(rom.open(romFile.getAbsolutePath()));
            Sonic3kScrollHandlerProvider provider = new Sonic3kScrollHandlerProvider();
            provider.load(rom);

            assertInstanceOf(SwScrlS3kDez.class, provider.getHandler(Sonic3kZoneIds.ZONE_DEZ));
        }
    }
}
