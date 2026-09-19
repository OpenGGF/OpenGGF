package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.scroll.ZoneScrollHandler;
import org.junit.jupiter.api.Test;

import static com.openggf.level.scroll.M68KMath.VISIBLE_LINES;
import static com.openggf.level.scroll.M68KMath.unpackBG;
import static com.openggf.level.scroll.M68KMath.unpackFG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sky Sanctuary act-1 background scroll parity with {@code SSZ1_BackgroundInit},
 * {@code SSZ1_BackgroundEvent}, {@code sub_579F0} and {@code sub_57A60}
 * (sonic3k.asm:116385-116708).
 *
 * <p>Every expectation below is derived from the ROM listing, not from the Java: the
 * {@code $28}/{@code $160}/{@code $180} framing offsets and the {@code $1800} latch from
 * {@code loc_57A12}/{@code loc_57A30}/{@code loc_57A4C}; the halved wrapped background Y and
 * the thirty-word fan from {@code sub_57A60}; the band heights from
 * {@code SSZ1_BGDeformArray}; the {@code $800}/{@code $F00} cloud band from the three copies
 * of the {@code Screen_Y_wrap_value} test.
 */
class TestS3kSszScrollBands {

    private static SszZoneRuntimeState act1State() {
        SszZoneRuntimeState state = new SszZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE);
        state.markScreenInitApplied();
        return state;
    }

    private static SszZoneRuntimeState act2State() {
        SszZoneRuntimeState state = new SszZoneRuntimeState(1, PlayerCharacter.KNUCKLES);
        state.markScreenInitApplied();
        return state;
    }

    @Test
    void actTwoColdFanUsesTheThreeCameraRatesAndFixedBackgroundOrigin() {
        SwScrlSsz handler = new SwScrlSsz();
        SszZoneRuntimeState state = act2State();
        int[] buffer = new int[VISIBLE_LINES];

        handler.composeActTwo(state, buffer, 0x800, 0x649, true);

        assertEquals(0x1000, state.cloudDrift(), "addi.l #$1000,(HScroll_table)");
        assertEquals(0x5E, state.backgroundCameraX(), "move.w #$5E,Camera_X_pos_BG_copy");
        assertEquals(0x331, state.backgroundCameraY(), "Camera_Y-$320+8");
        assertEquals((short) 0x20, handler.actTwoScrollWords().get(2), "Camera_X/64");
        assertEquals((short) 0x60, handler.actTwoScrollWords().get(5), "next Camera_X/32 step");
        assertEquals((short) -0x800, unpackFG(buffer[0]));
    }

    @Test
    void actTwoPublishesTwentyVsramColumnWords() {
        SwScrlSsz handler = new SwScrlSsz();
        SszZoneRuntimeState state = act2State();
        state.setEventsBgWord(0, 0x100);

        handler.composeActTwo(state, new int[VISIBLE_LINES], 0, 0x649, true);

        short[] columns = handler.getPerColumnVScrollBG();
        assertNotNull(columns);
        assertEquals(20, columns.length, "loc_5904A moveq #$14-1,d1");
        assertNotEquals(columns[0], columns[19], "the symmetric source wave advances by column");
    }

    @Test
    void providerRoutesSkySanctuaryToItsOwnHandler() throws Exception {
        Sonic3kScrollHandlerProvider provider = new Sonic3kScrollHandlerProvider();
        provider.load(new Rom());

        ZoneScrollHandler handler = provider.getHandler(Sonic3kZoneIds.ZONE_SSZ);

        assertNotNull(handler);
        assertTrue(handler instanceof SwScrlSsz,
                "Sky Sanctuary must not fall back to SwScrlS3kDefault");
    }

    /**
     * {@code loc_57A12}: {@code Camera_Y_pos_BG_copy = Camera_Y_pos_copy + $160 + _unkEE9C} and
     * {@code Camera_X_pos_BG_copy = Camera_X_pos_copy + $28}.
     */
    @Test
    void nearFramingAddsSub579F0Offsets() {
        SszZoneRuntimeState state = act1State();

        SwScrlSsz.plainParameters(state, 0x160, 0xC00);

        assertEquals(0x188, state.backgroundCameraX());
        assertEquals(0xD60, state.backgroundCameraY());
    }

    /** The same routine adds {@code _unkEE9C}, which the cloud oscillator object drives. */
    @Test
    void nearFramingFoldsInTheCloudOscillator() {
        SszZoneRuntimeState state = act1State();
        state.setCloudOscillator(-0x40);

        SwScrlSsz.plainParameters(state, 0x160, 0xC00);

        assertEquals(0xD20, state.backgroundCameraY());
    }

    /**
     * {@code sub_579F0}'s {@code Events_bg+$10} latch: crossing {@code Camera_X_pos $1800}
     * switches to {@code loc_57A4C}'s ({@code 0}, {@code $180}) framing and re-rounds
     * {@code Camera_X_pos_BG_rounded}; crossing back restores {@code loc_57A12}'s.
     */
    @Test
    void farFramingLatchesAtCameraX1800AndReRounds() {
        SszZoneRuntimeState state = act1State();

        SwScrlSsz.plainParameters(state, 0x1850, 0x500);
        assertEquals(0xFFFF, state.backgroundFarFraming());
        assertEquals(0x1850, state.backgroundCameraX());
        assertEquals(0x680, state.backgroundCameraY());
        assertEquals(0x1850, state.backgroundCameraXRounded());

        // Still far: the latch holds and the rounded word is not rewritten.
        SwScrlSsz.plainParameters(state, 0x1900, 0x500);
        assertEquals(0x1900, state.backgroundCameraX());
        assertEquals(0x1850, state.backgroundCameraXRounded());

        SwScrlSsz.plainParameters(state, 0x17FF, 0x500);
        assertEquals(0, state.backgroundFarFraming());
        assertEquals(0x1827, state.backgroundCameraX());
        assertEquals(0x660, state.backgroundCameraY());
        assertEquals(0x1820, state.backgroundCameraXRounded());
    }

    /**
     * {@code sub_57A60}: {@code ((Camera_Y_pos_copy + _unkEE9C/2) &amp; $FFF - $800) / 2 + $A0}.
     */
    @Test
    void cloudBackgroundYIsHalfTheWrappedBandOffset() {
        SwScrlSsz handler = new SwScrlSsz();
        SszZoneRuntimeState state = act1State();

        handler.cloudParameters(state, 0, 0x900);
        assertEquals(0x120, state.backgroundCameraY());

        SszZoneRuntimeState withOscillator = act1State();
        withOscillator.setCloudOscillator(0x40);
        handler.cloudParameters(withOscillator, 0, 0x900);
        assertEquals(0x130, withOscillator.backgroundCameraY());
    }

    /**
     * {@code sub_57A60}'s fan. With {@code Camera_X_pos_copy = $800} and a zero accumulator the
     * first band is {@code $800/64 = $20} and the step between bands is {@code $800/32 = $40};
     * the last three bands step by two steps, two steps plus a half, and again.
     */
    @Test
    void cloudScrollWordsFanOutFromTheTwoRates() {
        SwScrlSsz handler = new SwScrlSsz();
        SszZoneRuntimeState state = act1State();

        handler.cloudParameters(state, 0x800, 0x900);

        int[] expectedByIndex = new int[32];
        // move.w d0,(a1) / 6(a1) / $A(a1) / $14(a1)
        setExpected(expectedByIndex, 0x20, 0x00, 0x06, 0x0A, 0x14);
        setExpected(expectedByIndex, 0x60, 0x08, 0x0E);
        setExpected(expectedByIndex, 0xA0, 0x04, 0x0C, 0x12, 0x16, 0x3A);
        setExpected(expectedByIndex, 0xE0, 0x02, 0x10, 0x18, 0x38);
        setExpected(expectedByIndex, 0x120, 0x1A, 0x36);
        setExpected(expectedByIndex, 0x160, 0x1C, 0x34);
        setExpected(expectedByIndex, 0x1A0, 0x1E, 0x32);
        setExpected(expectedByIndex, 0x1E0, 0x20, 0x30);
        setExpected(expectedByIndex, 0x220, 0x22, 0x2E);
        setExpected(expectedByIndex, 0x2A0, 0x24, 0x2C);
        setExpected(expectedByIndex, 0x340, 0x26, 0x2A);
        setExpected(expectedByIndex, 0x3E0, 0x28);

        for (int index = 2; index < 32; index++) {
            assertEquals((short) expectedByIndex[index], handler.scrollWords().get(index),
                    "HScroll_table word " + index);
        }
        // HScroll_table+$000 is the drift longword, never a scroll word.
        assertEquals(0, handler.scrollWords().get(0));
        assertEquals(0, handler.scrollWords().get(1));
    }

    /**
     * {@code addi.l #$500,-4(a1)}: the fan reads the accumulator before advancing it, so the
     * top band's 16.16 value ({@code $200000} at {@code Camera_X $800}) carries into the next
     * pixel on the fifty-third call, the first whose accumulator reaches {@code $10000}
     * ({@code 51 * $500 = $FF00}, {@code 52 * $500 = $10400}).
     */
    @Test
    void cloudDriftAdvancesTheAccumulatorByFiveHundredPerFrame() {
        SwScrlSsz handler = new SwScrlSsz();
        SszZoneRuntimeState state = act1State();

        for (int frame = 0; frame < 52; frame++) {
            handler.cloudParameters(state, 0x800, 0x900);
            assertEquals((frame + 1) * 0x500, state.cloudDrift());
            assertEquals((short) 0x20, handler.scrollWords().get(2),
                    "top band before the accumulator carries, frame " + frame);
        }
        handler.cloudParameters(state, 0x800, 0x900);
        assertEquals((short) 0x21, handler.scrollWords().get(2));
    }

    /**
     * {@code SSZ1_BackgroundInit} and {@code SSZ1_BackgroundEvent} routines 0 and 8 all test the
     * wrapped camera Y against {@code $800} and {@code $F00}.
     */
    @Test
    void cloudBandIsTheWrappedEightHundredToFifteenHundredWindow() {
        assertFalse(SwScrlSsz.inCloudBand(0x7FF));
        assertTrue(SwScrlSsz.inCloudBand(0x800));
        assertTrue(SwScrlSsz.inCloudBand(0xEFF));
        assertFalse(SwScrlSsz.inCloudBand(0xF00));
        // Screen_Y_wrap_value $FFF: the act wraps, so Y $1900 is Y $900.
        assertTrue(SwScrlSsz.inCloudBand(0x1900));
    }

    /**
     * Routine 0 freezes {@code Camera_X/Y_pos_BG_copy} into {@code Events_bg+$0C}/{@code +$0E}
     * and keeps rendering them while the plane is redrawn, so the frame that starts the switch
     * is still plain; the deformation follows once {@code +$0C} is cleared.
     */
    @Test
    void enteringTheCloudBandKeepsThePlainFramingForOneFrame() {
        SwScrlSsz handler = new SwScrlSsz();
        SszZoneRuntimeState state = act1State();
        int[] buffer = new int[VISIBLE_LINES];

        assertFalse(handler.composeFrame(state, buffer, 0x400, 0x700));
        assertEquals(SwScrlSsz.BG_PLAIN, state.backgroundRoutine());
        int plainBgX = state.backgroundCameraX();

        assertFalse(handler.composeFrame(state, buffer, 0x400, 0x900),
                "the frame that starts Draw_PlaneVertBottomUp still renders PlainDeformation");
        assertEquals(SwScrlSsz.BG_ENTERING_CLOUDS, state.backgroundRoutine());
        assertEquals(plainBgX, state.backgroundCameraX(),
                "loc_5799A restores the saved copies while Events_bg+$0C is set");
        assertEquals(plainBgX, state.eventsBgWord(SwScrlSsz.EV_SAVED_BG_X));

        assertTrue(handler.composeFrame(state, buffer, 0x400, 0x900));
        assertEquals(SwScrlSsz.BG_CLOUDS, state.backgroundRoutine());
        assertEquals(0, state.eventsBgWord(SwScrlSsz.EV_SAVED_BG_X));
    }

    /** Routine 8 leaves through routine {@code $C}, and both of those frames render plain. */
    @Test
    void leavingTheCloudBandReturnsThroughRoutineC() {
        SwScrlSsz handler = new SwScrlSsz();
        SszZoneRuntimeState state = act1State();
        int[] buffer = new int[VISIBLE_LINES];

        handler.composeFrame(state, buffer, 0x400, 0x900);
        assertEquals(SwScrlSsz.BG_CLOUDS, state.backgroundRoutine());

        assertFalse(handler.composeFrame(state, buffer, 0x400, 0x700));
        assertEquals(SwScrlSsz.BG_LEAVING_CLOUDS, state.backgroundRoutine());
        assertFalse(handler.composeFrame(state, buffer, 0x400, 0x700));
        assertEquals(SwScrlSsz.BG_PLAIN, state.backgroundRoutine());
    }

    /**
     * {@code PlainDeformation} writes one background word for every line;
     * {@code ApplyDeformation} with {@code SSZ1_BGDeformArray} does not, because the background
     * Y sits inside the {@code $1D0}-tall first band only until the camera climbs.
     */
    @Test
    void plainModeIsFlatAndCloudModeIsBanded() {
        SwScrlSsz handler = new SwScrlSsz();
        SszZoneRuntimeState plain = act1State();
        int[] buffer = new int[VISIBLE_LINES];

        handler.composeFrame(plain, buffer, 0x400, 0x700);
        short first = unpackBG(buffer[0]);
        for (int line = 0; line < VISIBLE_LINES; line++) {
            assertEquals(first, unpackBG(buffer[line]), "plain line " + line);
            assertEquals((short) -0x400, unpackFG(buffer[line]), "foreground line " + line);
        }
        assertEquals((short) -(0x400 + 0x28), first);

        // Camera Y $E00 puts the cloud background Y at ($E00-$800)/2+$A0 = $3A0, which is past
        // the $1D0 first band, so the visible lines cross several SSZ1_BGDeformArray bands.
        SszZoneRuntimeState clouds = act1State();
        clouds.setBackgroundRoutine(SwScrlSsz.BG_CLOUDS);
        clouds.markBackgroundInitApplied();
        assertTrue(handler.composeFrame(clouds, buffer, 0x800, 0xE00));
        assertEquals(0x3A0, clouds.backgroundCameraY());

        // ApplyDeformation3 walks SSZ1_BGDeformArray from Camera_Y_pos_BG_copy $3A0: the
        // $1D0 band plus the next twenty-three heights consume $398, leaving 8 visible lines
        // of the twenty-fourth band ($10) at HScroll_table word 25, then one word per band.
        int[][] expectedBands = {
                {8, 0x220}, {8, 0x1E0}, {8, 0x1A0}, {8, 0x160},
                {32, 0x120}, {8, 0xE0}, {VISIBLE_LINES - 72, 0xA0}
        };
        int line = 0;
        for (int[] band : expectedBands) {
            for (int i = 0; i < band[0]; i++, line++) {
                assertEquals((short) -band[1], unpackBG(buffer[line]),
                        "cloud band background word on line " + line);
            }
        }
        assertEquals(VISIBLE_LINES, line);
        assertNotEquals(unpackBG(buffer[0]), unpackBG(buffer[VISIBLE_LINES - 1]));
    }

    private static void setExpected(int[] target, int value, int... byteOffsets) {
        for (int offset : byteOffsets) {
            target[SwScrlSsz.DEFORM_TABLE_START_INDEX + (offset >> 1)] = value;
        }
    }
}
