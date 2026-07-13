package com.openggf.game.sonic3k.events;

import com.openggf.game.rewind.snapshot.LevelEventSnapshot;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzEventRewindRoundTrip {
    @Test
    void managerSidecarDoesNotCaptureAuthoritativeFbzHandlerFieldsTwice() {
        Sonic3kLevelEventManager manager = new Sonic3kLevelEventManager();
        manager.initLevel(Sonic3kZoneIds.ZONE_FBZ, 1);
        Sonic3kFBZEvents events = manager.getFbzEvents();
        FbzZoneRuntimeState runtime = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE, events);
        events.setMagneticState(Sonic3kFBZEvents.MagneticPolarity.INACTIVE, 33);
        byte[] runtimeBytes = runtime.captureBytes();
        LevelEventSnapshot managerSnapshot = manager.capture();

        events.setMagneticState(Sonic3kFBZEvents.MagneticPolarity.ACTIVE, 2);
        manager.restore(managerSnapshot);
        assertEquals(Sonic3kFBZEvents.MagneticPolarity.ACTIVE, events.getMagneticPolarity(),
                "level-event sidecar must not restore FBZ authoritative runtime fields");

        runtime.restoreBytes(runtimeBytes);
        manager.reconcileAfterRewindRestore();
        assertEquals(Sonic3kFBZEvents.MagneticPolarity.INACTIVE, events.getMagneticPolarity());
        assertArrayEquals(runtimeBytes, runtime.captureBytes());
    }
}
