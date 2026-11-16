package com.openggf.game.sonic3k.events;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.scroll.SwScrlIcz;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestIczScreenPublication {
    @Test
    void rebuildingRendererRetainsIndoorHysteresisFromRealLevel() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 1).build();
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getIczEvents();
        var original = new SwScrlIcz();
        int[] expected = new int[224];
        fixture.camera().setX((short) 0x1900);
        fixture.camera().setY((short) 0x800);
        events.update(1, 100);
        original.update(expected, 0x1900, 0x800, 100, 1);
        fixture.camera().setY((short) 0x400);
        events.update(1, 101);
        original.update(expected, 0x1900, 0x400, 101, 1);
        byte[] saved = events.captureIczState();
        fixture.camera().setX((short) 0x2000);
        events.update(1, 102);
        events.restoreIczState(saved);
        var rebuilt = new SwScrlIcz();
        rebuilt.init(1, 0x1900, 0x400);
        int[] actual = new int[224];
        rebuilt.update(actual, 0x1900, 0x400, 101, 1);
        assertArrayEquals(saved, events.captureIczState(), "renderer rebuild cannot consume continuation");
        assertArrayEquals(expected, actual, "renderer rebuild must retain the indoor publication at the hysteresis bridge");
        assertEquals(original.getVscrollFactorBG(), rebuilt.getVscrollFactorBG());
    }
    @Test
    void timedShakeReadsIntoSecondTableAndRestoresItsNextSample() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 0).build();
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getIczEvents();
        events.triggerScreenShake(30);
        events.update(0, 100);
        byte[] saved = events.captureIczState();
        events.update(0, 101);
        assertEquals(3, events.getScreenShakeOffsetX(), "index 29 is ScreenShakeArray2[9]");
        byte[] next = events.captureIczState();
        events.restoreIczState(saved);
        events.update(0, 101);
        assertArrayEquals(next, events.captureIczState());
    }

    @Test
    void indoorRestartIgnoresResizeSnowTrigger() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 0).build();
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getIczEvents();
        fixture.camera().setX((short) 0x3940);
        fixture.camera().setY((short) 0x700);
        events.init(0); // restart background initialization at the retained camera
        events.initializeScrollAtLoad(0);
        fixture.sprite().setControlLocked(false);
        long piles = GameServices.level().getObjectManager().activeObjectsOfType(
                com.openggf.game.sonic3k.objects.IczBigSnowPileInstance.class).size();
        events.update(0, 1); // ICZ1_Resize sets Events_fg_5 at this camera
        assertEquals(16, events.getIcz1BackgroundRoutine());
        assertFalse(fixture.sprite().isControlLocked(), "indoor restart must not start the intro quake");
        assertEquals(piles, GameServices.level().getObjectManager().activeObjectsOfType(
                com.openggf.game.sonic3k.objects.IczBigSnowPileInstance.class).size());
        var state = java.nio.ByteBuffer.wrap(events.captureIczState());
        assertEquals(0, state.getInt(state.capacity() - 12), "no constant shake flag");
    }

    @Test
    void managerPublishesShakeUsingLevelClockRatherThanUpdateCount() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 0).build();
        var manager = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        assertEquals(0, manager.capture().frameCounter(), "fresh manager update counter");
        GameServices.level().setFrameCounter(17);
        manager.getIczEvents().triggerScreenShake(-1);
        manager.update();
        byte[] bytes = manager.getIczEvents().captureIczState();
        assertEquals(0, java.nio.ByteBuffer.wrap(bytes).getInt(bytes.length - 8),
                "ScreenShakeArray2[17] is 0, while manager count 1 samples 2");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "0x0FFF,0x057F,false", "0x0FFF,0x0580,true", "0x1000,0x0580,false",
            "0x1000,0x071F,false", "0x1000,0x0720,true", "0x3600,0x0800,false"})
    void startIndoorPredicateMatchesRom(int x, int y, boolean indoor) throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 1).build();
        fixture.camera().setX((short) x);
        fixture.camera().setY((short) y);
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getIczEvents();
        events.init(1);
        assertEquals(indoor, events.isIndoorPaletteCyclingActive());
    }

    @Test
    void retainedIndoorPredicateKeepsLeftSideBelowHeightThreshold() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 1).build();
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getIczEvents();
        fixture.camera().setX((short) 0x1800);
        fixture.camera().setY((short) 0x800);
        events.update(1, 1);
        fixture.camera().setX((short) 0x0FFF);
        fixture.camera().setY((short) 0x500);
        events.update(1, 2);
        assertTrue(events.isIndoorPaletteCyclingActive());
        var handler = new SwScrlIcz();
        handler.update(new int[224], 0x0FFF, 0x500, 2, 1);
        assertEquals((short) (((0x500 - 0x700) >> 2) + 0x118), handler.getVscrollFactorBG());
    }

    @Test
    void everyProducedSnowFallAndSettlePublicationValidates() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 0).build();
        fixture.camera().setX((short) 0x3800);
        fixture.camera().setY((short) 0x700);
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getIczEvents();
        events.init(0);
        events.initializeScrollAtLoad(0);
        for (int frame = 1; frame <= 72; frame++) {
            events.update(0, frame);
            assertTrue(Sonic3kICZEvents.hasValidIczState(events.captureIczState()), "fall/settle frame " + frame);
        }
        assertEquals(-302, events.getIcz1BigSnowOffset());
        assertEquals(4, events.getIcz1BackgroundRoutine(), "no refresh trigger is staged");
    }

}
