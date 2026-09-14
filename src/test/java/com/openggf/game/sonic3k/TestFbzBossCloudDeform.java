package com.openggf.game.sonic3k;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.game.sonic3k.scroll.SwScrlFbz;
import com.openggf.level.scroll.M68KMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestFbzBossCloudDeform {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {14, 46})
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void continuousShakeReadsLevelClockAndPublishesThePriorSample(int levelFrame) {
        var fixture = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(4, 1).build();
        var events = ((Sonic3kLevelEventManager) com.openggf.game.GameServices.module()
                .getLevelEventProvider()).getFbzEvents();
        events.initializeAct2Runtime();
        events.setScreenShakeState(true, 2, 13);
        var level = com.openggf.game.GameServices.level();
        level.setFrameCounter(levelFrame);
        var camera = com.openggf.game.GameServices.camera();
        camera.setY((short) 0x300);

        events.update(1, 30); // Event-dispatch count is deliberately a different clock.

        assertEquals(levelFrame, events.getScreenShakePhase(),
                "loc_4F3FA reads Level_frame_counter & $3F");
        assertEquals(0, events.getScreenShakeOffset(), "ScreenShakeArray2[14] and [46] are zero");
        assertEquals(2, events.getScreenShakeLastOffset(), "ScreenEvent consumes the earlier sample");
        assertEquals(0x302, camera.getYCopy());
        assertEquals(0x300, camera.getY(), "shake changes the render copy, not the live camera word");
        var runtime = com.openggf.game.GameServices.zoneRuntimeRegistry().current();
        byte[] snapshot = runtime.captureBytes();
        events.setScreenShakeState(false, 7, 3);
        runtime.restoreBytes(snapshot);
        org.junit.jupiter.api.Assertions.assertTrue(events.isScreenShakeActive());
        assertEquals(levelFrame, events.getScreenShakePhase());
        assertEquals(0, events.getScreenShakeOffset());
        assertEquals(2, events.getScreenShakeLastOffset());
    }

    @Test
    void cloudAddressSlotsExistBeforeTheFirstDeformationPass() {
        SwScrlFbz handler = new SwScrlFbz(() -> null);
        handler.init(1, 0, 0);
        assertEquals(0, handler.cloudPositions().size());
        for (int addressSlot = 0; addressSlot < 10; addressSlot++) {
            var slot = handler.cloudPositionAtAddressSlot(addressSlot);
            assertEquals(0, slot.x());
            assertEquals(0, slot.y());
            assertSame(slot, handler.cloudPositionAtAddressSlot(addressSlot));
        }
    }

    @Test
    void bossCloudModeUsesEventOffsetsFastDriftAndRomCloudCoordinates() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        events.setBossBackgroundState(16, 0x20, 0x10);
        events.setOutdoorBobOffset(3);
        events.restoreScreenShakePipelineState(true, 7, 4, 0, 0x404);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_AND_TAILS, events);
        SwScrlFbz handler = new SwScrlFbz(() -> state);
        handler.init(1, 0, 0);
        int[] scroll = new int[224];

        handler.update(scroll, 0x2800, 0x0400, 0, 1);

        assertEquals(0x0114, handler.getVscrollFactorBG() & 0xFFFF);
        assertEquals(0x0404, handler.getVscrollFactorFG() & 0xFFFF);
        assertEquals(-0x01E0, M68KMath.unpackFG(scroll[0]));
        assertEquals(-0x2800, M68KMath.unpackBG(scroll[0]));
        assertEquals(new SwScrlFbz.CloudPosition(0x213, 0x0F7, 1), handler.cloudPositions().get(0));
        assertEquals(new SwScrlFbz.CloudPosition(0x1E2, 0x0D7, 1), handler.cloudPositions().get(9));
    }

    @Test
    void bossCloudOutputReusesItsPublicViewAndPositionObjectsAcrossFrames() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        events.setBossBackgroundState(16, 0x20, 0x10);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_AND_TAILS, events);
        SwScrlFbz handler = new SwScrlFbz(() -> state);
        handler.init(1, 0, 0);
        int[] scroll = new int[224];

        handler.update(scroll, 0x2800, 0x0400, 0, 1);
        var firstView = handler.cloudPositions();
        var firstPosition = firstView.get(0);

        handler.update(scroll, 0x2800, 0x0400, 1, 1);

        assertSame(firstView, handler.cloudPositions(), "the hot-path public view must be preallocated");
        assertSame(firstPosition, handler.cloudPositions().get(0), "cloud position slots must be updated in place");
        assertEquals(10, handler.cloudPositions().size());
    }
}
