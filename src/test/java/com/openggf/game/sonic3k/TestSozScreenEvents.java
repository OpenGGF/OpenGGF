package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.events.Sonic3kSOZEvents;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozScreenEvents {
    @Test void foregroundCorkCopiesStoredRomLayoutAndRewindsThroughProductionLoop() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(8, 1)
                .withFreshLevelStartLifecycle().build();
        fixture.stepFrame(false, false, false, false, false);
        var state = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var map = GameServices.level().getCurrentLevel().getMap();
        int[] source = new int[40];
        int index = 0;
        for (int x = 0; x < 4; x++) source[index++] = map.getValue(0, 0xAB + x, 7) & 255;
        for (int y = 10; y < 14; y++) for (int x = 0; x < 9; x++)
            source[index++] = map.getValue(0, 0xAB + x, y) & 255;
        state.requestSandCorkRelease(true);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        fixture.stepFrame(false, false, false, false, false);
        var after = registry.capture();
        map = GameServices.level().getCurrentLevel().getMap();
        index = 0;
        for (int x = 0; x < 4; x++) assertEquals(source[index++], map.getValue(0, 0x95 + x, 7) & 255);
        for (int y = 10; y < 14; y++) for (int x = 0; x < 9; x++)
            assertEquals(source[index++], map.getValue(0, 0x8C + x, y) & 255);
        registry.restore(before);
        fixture.stepFrame(false, false, false, false, false);
        var replay = registry.capture();
        for (String key : after.entries().keySet()) {
            var differences = com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key, after.get(key), replay.get(key));
            assertTrue(differences.isEmpty(), differences.toString());
        }
    }

    @Test void sandReleaseUsesFractionalSpecialEventMotionAndRewindCollisionOffset() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(8, 1).build();
        var state = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var events = new Sonic3kSOZEvents();
        NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), 0x2000);
        NativePositionOps.writeYPosResetSubpixel(fixture.sprite(), 0x200);
        events.update(1, 1);
        assertEquals(0x10, state.events().backgroundRoutine());
        state.requestSandCorkRelease(false);
        events.update(1, 2);
        assertEquals(0x14, state.events().backgroundRoutine());
        assertEquals(0x800000, state.events().sandPosition());
        assertFalse(state.backgroundPlaneCollisionStateOrNull().active());
        byte[] before = state.captureBytes();
        for (int i = 0; i < 8; i++) events.updateSpecialEvents(1);
        assertEquals(0x850000, state.events().sandPosition());
        var collision = state.backgroundPlaneCollisionStateOrNull();
        assertTrue(collision.active());
        assertEquals(0x1930, collision.cameraDiffX());
        assertEquals(-0x365, collision.cameraDiffY());
        byte[] after = state.captureBytes();
        state.restoreBytes(before);
        for (int i = 0; i < 8; i++) events.updateSpecialEvents(1);
        assertArrayEquals(after, state.captureBytes());
        state.requestSandCorkRelease(false);
        events.update(1, 3);
        assertEquals(0x18, state.events().backgroundRoutine());
        assertEquals(0x850000, state.events().sandPosition(), "second cork resumes existing sand position");
    }

    @Test void lowerRoomAndSandExitUseNativePositionGates() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(8, 1).build();
        var state = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var events = new Sonic3kSOZEvents();
        NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), 0x2000);
        NativePositionOps.writeYPosResetSubpixel(fixture.sprite(), 0x400);
        events.update(1, 1);
        state.requestSandCorkRelease(false);
        events.update(1, 2);
        assertEquals(0x18, state.events().backgroundRoutine());
        assertEquals(0x3E0, state.events().sandHeight());
        events.updateSpecialEvents(1);
        NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), 0x2A00);
        NativePositionOps.writeYPosResetSubpixel(fixture.sprite(), 0x181);
        events.update(1, 3);
        assertEquals(0x18, state.events().backgroundRoutine());
        NativePositionOps.writeYPosResetSubpixel(fixture.sprite(), 0x180);
        events.update(1, 4);
        assertEquals(0x1C, state.events().backgroundRoutine());
        assertFalse(state.backgroundPlaneCollisionStateOrNull().active());
        assertEquals(0, state.events().specialRoutine());
        for (int i = 0; i < 32; i++) events.update(1, 5 + i);
        assertEquals(0x20, state.events().backgroundRoutine());
    }

    @Test void act1ApproachUsesPlayerXThenCameraGate() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(8, 0).build();
        var events = new Sonic3kSOZEvents();
        NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), 0x3FFF);
        events.update(0, 0);
        assertEquals(0xB20, fixture.camera().getMaxY() & 0xFFFF);
        NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), 0x4000);
        fixture.camera().setY((short) 0x960);
        fixture.camera().setX((short) 0x4310);
        events.update(0, 1);
        assertEquals(0x960, fixture.camera().getMaxY() & 0xFFFF);
        assertEquals(0x960, fixture.camera().getMinY() & 0xFFFF);
        assertEquals(0x4180, fixture.camera().getMinX() & 0xFFFF);
    }
}
