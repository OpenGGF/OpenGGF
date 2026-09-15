package com.openggf.game.sonic3k;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSozRuntimeState {
    @Test void sandCorkSignalsRemainIndependentAcrossConsumptionAndRestore() {
        var state = new SozZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);
        state.publishPushableRockSlot(7);
        state.requestSandCorkRelease(true);
        state.requestSandCorkRelease(false);
        byte[] snapshot = state.captureBytes();
        assertEquals(0xFFFF, state.consumeSandCorkForegroundFlag());
        assertEquals(0, state.consumeSandCorkForegroundFlag());
        assertEquals(0xFFFF, state.sandCorkBackgroundFlag());
        state.consumeSandCorkBackgroundFlag();
        state.restoreBytes(snapshot);
        assertEquals(7, state.pushableRockSlot());
        assertEquals(0xFFFF, state.consumeSandCorkForegroundFlag());
        assertEquals(0xFFFF, state.consumeSandCorkBackgroundFlag());
    }
}
