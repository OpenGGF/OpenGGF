package com.openggf.game.rewind;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLbzScreenPublication {
    @AfterEach void reset() { TestEnvironment.resetAll(); }

    @Test void malformedComposedPayloadDoesNotMutateState() {
        var events = new com.openggf.game.sonic3k.events.Sonic3kLBZEvents();
        events.writeScreenShakeFlag(30);
        events.setupScreenShake(0, false); // timed index 29 reads into contiguous table 2
        events.publishScreenShakeForeground();
        byte[] before = events.captureLbzState();
        var restored = new com.openggf.game.sonic3k.events.Sonic3kLBZEvents();
        restored.restoreLbzState(before);
        assertArrayEquals(before, restored.captureLbzState());
        assertEquals(3, restored.screenShakeOffset());
        for (int at : new int[] {0, 4, 8, before.length - 20}) {
            byte[] bad = before.clone();
            java.nio.ByteBuffer.wrap(bad).putInt(at, 0x10000);
            assertFalse(com.openggf.game.sonic3k.events.Sonic3kLBZEvents.hasValidLbzState(bad));
            assertThrows(IllegalArgumentException.class, () -> events.restoreLbzState(bad));
            assertArrayEquals(before, events.captureLbzState());
        }
        byte[] truncated = java.util.Arrays.copyOf(before, before.length - 1);
        assertThrows(IllegalArgumentException.class, () -> events.restoreLbzState(truncated));
        assertArrayEquals(before, events.captureLbzState());
    }

    @Test void flagFromAnotherZoneDoesNotKeepDeathEggRumbleAlive() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1)
                .withSkippedZoneIntro().build();
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getLbzEvents();
        var state = com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentLbz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        // The ship's native release clears Screen_shake_flag; a retained S2/shared
        // boolean is not the ROM word tested by LBZ2BGE_DeathEgg.
        state.setLaunchActive(true);
        state.setDeathEggRumble(true);
        state.writeScreenShakeFlag(0);
        GameServices.gameState().setScreenShakeActive(true);
        fixture.runner().stepFrame(false, false, false, false, false);
        assertFalse(state.isDeathEggRumble());
        assertEquals(0, events.screenShakeFlag());
    }

    @Test void registryRebindRetainsTheEventOwnedPublication() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1)
                .withSkippedZoneIntro().build();
        var manager = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        var original = com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentLbz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        original.writeScreenShakeFlag(20);
        original.setLaunchYDelta(3);
        byte[] before = manager.getLbzEvents().captureLbzState();
        GameServices.zoneRuntimeRegistry().install(new com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState(1, original.playerCharacter()));
        manager.ensureZoneRuntimeStateInstalled();
        assertSame(original, GameServices.zoneRuntimeRegistry().current());
        assertArrayEquals(before, manager.getLbzEvents().captureLbzState());
        original.setLaunchRiderDelta(3);
        assertEquals(3, original.getLaunchRiderDelta());
        assertEquals(3, original.getLaunchRiderDelta(), "readers cannot consume the event publication");
    }

    @Test void rendererRebuildDoesNotConsumeTimedShake() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1)
                .withSkippedZoneIntro().build();
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getLbzEvents();
        events.startBigArmTimedShake(20);
        fixture.runner().stepFrame(false, false, false, false, false);
        fixture.runner().stepFrame(false, false, false, false, false);
        var handler = GameServices.parallax().getHandler(Sonic3kZoneIds.ZONE_LBZ);
        int shake = handler.getShakeOffsetY();
        assertNotEquals(0, shake, "real LBZ2 screen event published the timed shake");
        byte[] before = events.captureLbzState();
        int baseY = (events.foregroundCameraYCopy(0) - shake) & 0xFFFF;
        int fixed = ((short) (baseY - 0x5F0) << 16) >> 1;
        int step = fixed >> 3;
        int expectedBg = ((fixed - step - (step >> 2)) >> 16) + 0x2C0 + shake;
        assertEquals((short) expectedBg, handler.getVscrollFactorBG(),
                "LBZ2_Deform subtracts shake from the already-shaken FG copy");
        int[] words = new int[224];
        handler.update(words, fixture.camera().getX(), fixture.camera().getY(), GameServices.level().getFrameCounter(), 1);
        assertEquals(shake, handler.getShakeOffsetY(), "same-frame renderer rebuild must retain publication");
        assertArrayEquals(before, events.captureLbzState(), "rebuild advances neither shake nor cloud/table state");
    }
}
