package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AnPal_HPZ counter semantics (sonic3k.asm:3934-3951). */
class TestHpzZoneRuntimeStatePaletteCycle {

    private static List<Integer> writes(HpzZoneRuntimeState state, int ticks) {
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            offsets.add(state.tickPaletteCycle());
        }
        return offsets;
    }

    @Test
    void clearedCountersWriteOnTheFirstPassThenEveryEighthPass() {
        HpzZoneRuntimeState state = new HpzZoneRuntimeState(0x16, 1, PlayerCharacter.SONIC_AND_TAILS);

        List<Integer> offsets = writes(state, 17);

        // Palette_cycle_counter1 underflows from 0 on the first pass and reloads 7.
        assertEquals(0, offsets.get(0));
        for (int i = 1; i < 8; i++) {
            assertEquals(-1, offsets.get(i), "pass " + i);
        }
        assertEquals(4, offsets.get(8));
        assertEquals(8, offsets.get(16));
    }

    @Test
    void counter0WrapsAfterTenFramesOfAnPalPalHpz() {
        HpzZoneRuntimeState state = new HpzZoneRuntimeState(0x16, 1, PlayerCharacter.SONIC_AND_TAILS);
        List<Integer> written = new ArrayList<>();
        for (int offset : writes(state, 8 * 11)) {
            if (offset >= 0) {
                written.add(offset);
            }
        }
        assertEquals(List.of(0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 0), written);
    }

    @Test
    void suppressionSkipsWithoutAdvancingTheTimer() {
        HpzZoneRuntimeState state = new HpzZoneRuntimeState(0x16, 1, PlayerCharacter.SONIC_AND_TAILS);
        state.setPaletteCycleSuppressed(true);
        assertTrue(writes(state, 20).stream().allMatch(offset -> offset == -1));

        state.setPaletteCycleSuppressed(false);
        assertEquals(0, state.tickPaletteCycle(),
                "tst.b (Palette_cycle_counters+$00) returns before subq on counter1");
    }

    @Test
    void masterEmeraldDelayHoldsTheCycleFor8000Passes() {
        HpzZoneRuntimeState state = new HpzZoneRuntimeState(0x17, 1, PlayerCharacter.SONIC_AND_TAILS);
        state.setPaletteCycleCounter1(8000 - 1);

        List<Integer> offsets = writes(state, 8000);

        assertTrue(offsets.subList(0, 7999).stream().allMatch(offset -> offset == -1));
        assertEquals(0, offsets.get(7999));
    }

    @Test
    void captureRestoreResumesTheSameSequence() {
        HpzZoneRuntimeState state = new HpzZoneRuntimeState(0x16, 1, PlayerCharacter.KNUCKLES);
        writes(state, 21);
        state.markPaletteControlAllocated();
        state.requestForegroundCollapse();
        byte[] snapshot = state.captureBytes();
        List<Integer> expected = writes(state, 40);

        HpzZoneRuntimeState restored = new HpzZoneRuntimeState(0x16, 1, PlayerCharacter.KNUCKLES);
        restored.restoreBytes(snapshot);

        assertEquals(expected, writes(restored, 40));
        assertTrue(restored.paletteControlAllocated());
        assertTrue(restored.consumeForegroundCollapse());
        assertFalse(restored.consumeForegroundCollapse());
    }
}
