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

    @Test
    void act2ActiveLayoutAndBackgroundRedrawWordsRoundTripThroughRuntimeOwner() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        events.initializeAct2Screen(0x1800);
        events.initializeAct2Background(0x1800);
        events.setForegroundLayoutRegion(4);
        events.updateAct2BackgroundEvent(0xD80, 0xA40, false);
        Sonic3kFBZEvents.Act2TraversalState expected = events.captureAct2TraversalState();

        FbzZoneRuntimeState runtime = new FbzZoneRuntimeState(
                1, PlayerCharacter.SONIC_ALONE, events);
        byte[] snapshot = runtime.captureBytes();
        for (int i = 0; i < 8; i++) {
            events.updateAct2BackgroundEvent(0xD80, 0xA41, false);
        }
        assertNotEquals(expected, events.captureAct2TraversalState());

        runtime.restoreBytes(snapshot);
        assertEquals(expected, events.captureAct2TraversalState());
        assertArrayEquals(snapshot, runtime.captureBytes());
    }
}
