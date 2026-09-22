package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSszZoneRuntimeState {
    @Test
    void restoreAcrossBossRetirementAndFlyByReturnsTheirGameplayGates() {
        var state = new SszZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE);
        state.setBossFlag(true);
        state.setEggRoboFlyByBits(0xA500);
        byte[] before = state.captureBytes();
        state.setBossFlag(false);
        state.setEggRoboFlyByBits(0xA508);
        byte[] after = state.captureBytes();

        state.restoreBytes(before);
        assertAll(
                () -> assertTrue(state.bossFlag(), "rewinding retirement must restore Boss_flag"),
                () -> assertEquals(0xA500, state.eggRoboFlyByBits(),
                        "the nibble-2 EggRobo must wait again before its partner's signal"));
        state.restoreBytes(after);
        assertFalse(state.bossFlag());
        assertEquals(0xA508, state.eggRoboFlyByBits());

        var recreated = new SszZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE);
        recreated.restoreBytes(before);
        assertTrue(recreated.bossFlag());
        assertEquals(0xA500, recreated.eggRoboFlyByBits());
    }
}
