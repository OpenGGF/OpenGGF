package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.scroll.SwScrlFbz;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestFbzEventPublication {
    @AfterEach void close() { TestEnvironment.resetAll(); }

    @Test void extraProjectionCannotAdvanceOutdoorMotionOrChangeRewindPayload() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(4, 0)
                .startPosition((short) 0x100, (short) 0x100).startPositionIsCentre().build();
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getFbzEvents();
        assertTrue(events.isBackgroundOutdoor(), "real outdoor screen initialization");
        fixture.runner().stepFrame(false, false, false, false, false);
        var runtime = GameServices.zoneRuntimeRegistry().current();
        byte[] before = runtime.captureBytes();
        var handler = new SwScrlFbz();
        handler.init(0, fixture.camera().getX(), fixture.camera().getY());
        handler.update(new int[224], fixture.camera().getX(), fixture.camera().getY(), 0x1234, 0);
        assertArrayEquals(before, runtime.captureBytes(), "drawing with another clock cannot move gameplay");
    }

    @Test void cloudPublicationRestoresWithoutAnotherGameplayStep() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(4, 1)
                .startPosition((short) 0x2C80, (short) 0x400).startPositionIsCentre().build();
        fixture.runner().stepFrame(false, false, false, false, false);
        var runtime = (com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState)
                GameServices.zoneRuntimeRegistry().current();
        assertEquals(2, runtime.scrollPublication().mode());
        var point = runtime.cloudPositionAtAddressSlot(0);
        byte[] before = runtime.captureBytes();
        fixture.runner().stepFrame(false, false, false, false, false);
        runtime.restoreBytes(before);
        var handler = new SwScrlFbz();
        handler.init(1, 0, 0);
        handler.update(new int[224], 0, 0, 0x1234, 1);
        assertArrayEquals(before, runtime.captureBytes());
        assertEquals(point, runtime.cloudPositionAtAddressSlot(0));
        assertEquals(point, handler.cloudPositionAtAddressSlot(0));
        for (int offset : new int[] {before.length - 4, before.length - 116}) {
            byte[] bad = before.clone();
            java.nio.ByteBuffer.wrap(bad).putInt(offset, -1);
            assertThrows(IllegalArgumentException.class, () -> runtime.restoreBytes(bad));
            assertArrayEquals(before, runtime.captureBytes(), "atomic rejection of invalid publication");
        }
    }
}
