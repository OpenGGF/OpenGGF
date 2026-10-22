package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Palette;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnPal_AIZ1 boundaries taken from the ROM routine (sonic3k.asm:3237-3294) and its tables,
 * observed through the level palette only:
 * <ul>
 *   <li>The normal branch's counters+$02 cycle is gated by {@code tst.b
 *       (Palette_cycle_counters+$00)} (3217), not by {@code Level_started_flag}.</li>
 *   <li>It reads the carried counters+$02 before wrapping it (3219-3223). The fire branch
 *       wraps the same word at $3C (3248-3252), so a carried $30 reads the six ROM bytes
 *       after AnPal_PalAIZ1_2.</li>
 *   <li>The branch is chosen by AIZ1_palette_cycle_flag, which AIZ1_Resize (loc_1C46E,
 *       38913-38917) recomputes in DeformBgLayer after Animate_Palette.</li>
 * </ul>
 * Palette calls are driven through the production entry without the object pass, with the
 * camera placed on either side of $1000.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kAiz1AnPalBoundaries {
    private static final int RUN_CALLS_NORMAL = 8;  // reload 7
    private static final int RUN_CALLS_FIRE = 10;   // reload 9

    @Test
    void normalCycleIsGatedByTheCounterByteNotByLevelStarted() throws Exception {
        byte[] secondary = boot(0x1100);
        GameServices.camera().setLevelStarted(false);
        // Cleared bank: normal runs at calls 1, 9, 17 read counters+$02 = 0, 6, 12.
        for (int run = 0; run < 3; run++) {
            paletteCall();
            assertSecondary(secondary, run * 6, "normal run " + (run + 1) + " with Level_started_flag clear");
            for (int i = 1; i < RUN_CALLS_NORMAL; i++) paletteCall();
        }
    }

    @Test
    void carriedFireOffsetIsReadBeforeTheWrap() throws Exception {
        byte[] secondary = boot(0x0800);
        // Fire runs at calls 1, 11, ..., 71 advance counters+$02 by 6, wrapping at $3C: $30 after 8.
        for (int call = 1; call <= 8 * RUN_CALLS_FIRE - 9; call++) paletteCall();
        GameServices.camera().setX((short) 0x1100);
        // Call 72 still runs the fire branch (decided after call 71); counter1 8 -> 0 at call 80,
        // and call 81 is the first normal run, reading the carried $30.
        for (int call = 72; call <= 80; call++) paletteCall();
        paletteCall();
        assertSecondary(secondary, 0x30, "the first normal run reads carried counters+$02 $30");
    }

    /** Returns AnPal_PalAIZ1_2 plus the ROM bytes after it, as read through the ROM pipeline. */
    private static byte[] boot(int cameraX) throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).withSkippedZoneIntro()
                .startPosition((short) 0x1EA6, (short) 0x490).startPositionIsCentre().build();
        GameServices.level().consumePendingInitialProcessSpritesPass();
        GameServices.camera().setX((short) cameraX);
        for (int i = 0; i < 40 && animator().paletteFadeTimerRunning(); i++) paletteCall();
        assertFalse(animator().paletteFadeTimerRunning());
        return RomByteReader.fromRom(GameServices.rom().getRom())
                .slice(Sonic3kConstants.ANPAL_AIZ1_2_ADDR, 0x36 + 6);
    }

    private static void paletteCall() {
        var level = GameServices.level();
        level.setFrameCounter(level.getFrameCounter() + 1);
        var registry = GameServices.paletteOwnershipRegistryOrNull();
        if (registry != null) registry.beginFrame();
        animator().update();
    }

    /** Normal_palette_line_4+$18..$1C: engine line 3 colours 12-14. */
    private static void assertSecondary(byte[] secondary, int offset, String message) {
        for (int i = 0; i < 3; i++) {
            int word = ((secondary[offset + 2 * i] & 0xFF) << 8) | (secondary[offset + 2 * i + 1] & 0xFF);
            Palette.Color expected = new Palette.Color();
            expected.fromSegaFormat(word);
            Palette.Color actual = GameServices.level().getCurrentLevel().getPalette(3).getColor(12 + i);
            assertArrayEquals(new int[] {expected.r & 0xFF, expected.g & 0xFF, expected.b & 0xFF},
                    new int[] {actual.r & 0xFF, actual.g & 0xFF, actual.b & 0xFF},
                    message + " colour " + (12 + i) + " (offset $" + Integer.toHexString(offset) + ")");
        }
    }

    private static Sonic3kLevelAnimationManager animator() {
        return (Sonic3kLevelAnimationManager) GameServices.level().getAnimatedPatternManager();
    }
}
