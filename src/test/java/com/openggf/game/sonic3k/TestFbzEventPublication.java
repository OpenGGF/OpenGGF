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
        assertNotEquals(point, runtime.cloudPositionAtAddressSlot(0), "real gameplay must move the cloud before restore");
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

    @Test void clearingOnlyTheFlagRetainsTheOffsetForTheCurrentScreenEvent() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(4, 1).build();
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getFbzEvents();
        events.setScreenShakeState(true, 3, 3);
        events.setScreenShakeActive(false);
        assertEquals(3, events.getScreenShakeOffset());
        fixture.camera().setY((short) 0x400);
        events.update(1, 0);
        assertEquals(0x403, events.scrollPublication().foregroundY());
        assertEquals(3, events.getScreenShakeLastOffset());
        assertEquals(0, events.getScreenShakeOffset());
    }

    @Test void timedShakeRestoresItsCountdownAndSubsequentSamples() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(4, 1).build();
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getFbzEvents();
        var field = events.getClass().getDeclaredField("screenShake");
        field.setAccessible(true);
        var shake = (com.openggf.game.sonic3k.runtime.S3kScreenShake) field.get(events);
        shake.writeFlag(14); // component injection: FBZ currently has no timed writer
        shake.setup(1, false);
        var runtime = GameServices.zoneRuntimeRegistry().current();
        byte[] before = runtime.captureBytes();
        // Three full words precede the fixed-size suffix (metadata + publication).
        for (int offset : new int[] {before.length - 156, before.length - 152, before.length - 148}) {
            byte[] bad = before.clone();
            java.nio.ByteBuffer.wrap(bad).putInt(offset, 0x10000);
            assertThrows(IllegalArgumentException.class, () -> runtime.restoreBytes(bad));
            assertArrayEquals(before, runtime.captureBytes(), "invalid shake word rejection is atomic");
        }
        int[] samples = new int[15];
        for (int i = 0; i < samples.length; i++) { shake.setup(i + 2, false); samples[i] = shake.offset(); }
        runtime.restoreBytes(before);
        assertEquals(13, shake.flag(), "rewind must retain timed mode, not convert it to continuous");
        for (int i = 0; i < samples.length; i++) {
            shake.setup(i + 2, false);
            assertEquals(samples[i], shake.offset(), "timed suffix sample " + i);
        }
    }
}
