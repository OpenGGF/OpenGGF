package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code ShakeScreen_Setup} (sonic3k.asm:104188-104210) as the Hidden Palace
 * sanctuary owns it, plus the rewind round trip of the countdown.
 */
class TestHpzZoneRuntimeState {

    /** {@code ScreenShakeArray} indexed by the decremented flag: 8 -> entries 7..0. */
    private static final int[] TIMED_TAPER_FROM_EIGHT = {-2, 2, -2, 2, -1, 1, -1, 1};

    @Test
    void timedFlagCountsDownThroughScreenShakeArrayAndThenRests() {
        S3kScreenShake shake = new S3kScreenShake();
        shake.writeFlag(8);

        for (int i = 0; i < TIMED_TAPER_FROM_EIGHT.length; i++) {
            int previous = shake.offset();
            shake.setup(i, false);
            assertEquals(7 - i, shake.flag(), "subq.w #1 before the table read");
            assertEquals(TIMED_TAPER_FROM_EIGHT[i], shake.offset(), "ScreenShakeArray[" + (7 - i) + "]");
            assertEquals(previous, shake.lastOffset(), "Screen_shake_last_offset keeps the prior word");
        }

        shake.setup(8, false);
        assertEquals(0, shake.flag());
        assertEquals(0, shake.offset(), "a zero flag yields moveq #0,d1");
        assertEquals(1, shake.lastOffset());
    }

    @Test
    void negativeFlagSamplesScreenShakeArray2ByLevelFrameCounter() {
        S3kScreenShake shake = new S3kScreenShake();
        shake.writeFlag(-1);

        shake.setup(0, false);
        assertEquals(1, shake.offset(), "ScreenShakeArray2[0]");
        shake.setup(3, false);
        assertEquals(3, shake.offset(), "ScreenShakeArray2[3]");
        shake.setup(0x40 + 14, false);
        assertEquals(0, shake.offset(), "andi.w #$3F wraps the counter: entry 14 is 0");
        assertEquals(-1, shake.flag(), "the constant mode never decrements");
    }

    @Test
    void deadPlayerHoldsTheFlagAndPublishesZero() {
        S3kScreenShake shake = new S3kScreenShake();
        shake.writeFlag(8);
        shake.setup(0, false);
        assertEquals(7, shake.flag());

        shake.setup(1, true);
        assertEquals(7, shake.flag(), "cmpi.b #6,(Player_1+routine).w / bhs skips the countdown");
        assertEquals(0, shake.offset());
        assertEquals(-2, shake.lastOffset());
    }

    @Test
    void sanctuaryStateAppliesThePreviousSetupAndRoundTripsThroughCapture() {
        HpzZoneRuntimeState state = new HpzZoneRuntimeState(
                Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 1, PlayerCharacter.SONIC_ALONE);
        assertEquals(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, state.zoneIndex());
        assertEquals(1, state.actIndex());
        assertEquals("s3k", state.gameId());

        // Frame N: the crystal lands (Process_Sprites), then ScreenEvents run.
        state.screenShake().writeFlag(8);
        state.advanceScreenShake(0, false);
        assertEquals(0, state.appliedScreenShakeOffset(),
                "HPZS_ScreenEvent reads the word the previous frame's setup produced");
        assertEquals(-2, state.screenShake().offset(), "ShakeScreen_Setup prepared -2 for the next frame");

        state.advanceScreenShake(1, false);
        assertEquals(-2, state.appliedScreenShakeOffset());
        assertEquals(2, state.screenShake().offset());

        byte[] captured = state.captureBytes();
        HpzZoneRuntimeState restored = new HpzZoneRuntimeState(
                Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 1, PlayerCharacter.SONIC_ALONE);
        restored.restoreBytes(captured);
        assertEquals(state.appliedScreenShakeOffset(), restored.appliedScreenShakeOffset());
        assertEquals(state.screenShake().flag(), restored.screenShake().flag());
        assertEquals(state.screenShake().offset(), restored.screenShake().offset());
        assertEquals(state.screenShake().lastOffset(), restored.screenShake().lastOffset());
        assertArrayEquals(captured, restored.captureBytes());

        // Forward replay from the restored state matches the live timeline.
        state.advanceScreenShake(2, false);
        restored.advanceScreenShake(2, false);
        assertEquals(state.appliedScreenShakeOffset(), restored.appliedScreenShakeOffset());
        assertEquals(state.screenShake().offset(), restored.screenShake().offset());
    }
}
