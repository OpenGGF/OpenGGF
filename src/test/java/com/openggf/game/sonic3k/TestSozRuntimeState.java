package com.openggf.game.sonic3k;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSozRuntimeState {
    @Test void minibossSignalsAndSandFractionSurviveRestore() {
        var state = new SozZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE);
        state.requestMinibossDoorClose();
        state.requestMinibossShake(0x20);
        state.requestMinibossPostResultsAlignmentComplete();
        state.events().sandPosition(0x3E0A000);
        byte[] snapshot = state.captureBytes();
        state.events().doorSignal(0);
        state.events().screenShakeFlag(0);
        state.events().sandPosition(0);
        state.consumeSandCorkBackgroundFlag();
        state.restoreBytes(snapshot);
        assertEquals(-1, state.events().doorSignal());
        assertEquals(0x20, state.events().screenShakeFlag());
        assertEquals(0x55, state.sandCorkBackgroundFlag());
        assertEquals(0x3E0A000, state.events().sandPosition());
    }

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
