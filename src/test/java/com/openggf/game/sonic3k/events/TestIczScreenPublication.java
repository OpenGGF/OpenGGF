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

}
