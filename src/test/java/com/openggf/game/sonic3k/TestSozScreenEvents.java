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
    @Test void nativeRoomAndPostBossModesSelectTheirBackgroundSourceWindows() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(8, 1).build();
        var state = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var provider = GameServices.level().getZoneFeatureProvider();
        for (int routine : new int[]{0,4,8,0xC,0x10,0x20}) {
            state.events().backgroundRoutine(routine);
            assertFalse(provider.bgWrapsHorizontally(), "native fixed source zero routine="+routine);
        }
        for (int routine : new int[]{0x14,0x18,0x1C,0x24,0x28,0x2C,0x30,0x34}) {
            state.events().backgroundRoutine(routine);
            assertTrue(provider.bgWrapsHorizontally());
            assertTrue(provider.useLinearBackgroundLayoutOverflow(8));
            GameServices.level().recomputeParallaxOnlyForCurrentFrame();
            if (routine >= 0x2C) assertEquals(0x200,GameServices.parallax().getBgCameraX());
        }
    }

    @Test void bossArenaQueuesRomResourcesAndRecreatesItsEightWallSolids() throws Exception {
        var fixture = HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8, 1)
                .startPosition((short) 0x5100, (short) 0x650).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        fixture.camera().setY((short) 0x600);
        fixture.camera().setFrozen(true);
        var state = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        fixture.stepFrame(false, false, false, false, false);
        assertEquals(0x24, state.events().backgroundRoutine());
        assertEquals(8, GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(com.openggf.game.sonic3k.objects.SozBossWallObjectInstance.class::isInstance).count());
        var walls=GameServices.level().getObjectManager().activeObjectsOfType(
                com.openggf.game.sonic3k.objects.SozBossWallObjectInstance.class);
        walls.sort(java.util.Comparator.comparingInt(com.openggf.level.objects.AbstractObjectInstance::getSlotIndex));
        for(int index=0;index<8;index++)assertEquals(7-index,walls.get(index).getSpawn().subtype(),"native descending row allocation");
        assertTrue(state.events().artJobOrdinal() >= 0);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        int serviceFrames = 0;
        // The placed final boss also submits ROM art; wait for the real queue
        // completion instead of assuming an otherwise-empty 20-frame schedule.
        while ((state.events().artJobOrdinal() >= 0 || state.events().blockJobOrdinal() >= 0)
                && serviceFrames < 240) {
            fixture.stepFrame(false, false, false, false, false);
            serviceFrames++;
        }
        assertEquals(-1, state.events().artJobOrdinal());
        assertEquals(-1, state.events().blockJobOrdinal());
        byte[] art = new com.openggf.level.resources.ResourceLoader(GameServices.rom().getRom())
                .loadSingle(com.openggf.level.resources.LoadOp.kosinskiMBase(0x1B0860));
        var tile = GameServices.level().getCurrentLevel().getPattern(0x315);
        for (int pixel = 0; pixel < 64; pixel++) {
            int packed = art[pixel / 2] & 255;
            assertEquals((pixel % 2 == 0 ? packed >> 4 : packed) & 15,
                    tile.getPixel(pixel % 8, pixel / 8));
        }
        var after = registry.capture();
        registry.restore(before);
        for (int frame = 0; frame < serviceFrames; frame++) fixture.stepFrame(false, false, false, false, false);
        var replay = registry.capture();
        for (String key : after.entries().keySet()) {
            var differences = com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key, after.get(key), replay.get(key));
            assertTrue(differences.isEmpty(), differences.toString());
        }
    }

    @Test void foregroundCorkCopiesStoredRomLayoutAndRewindsThroughProductionLoop() {
        var fixture = HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8, 1)
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
        var fixture = HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8, 1).build();
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
        var fixture = HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8, 1).build();
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
        assertEquals(29, state.events().redrawRemaining());
        for (int i = 0; i < 14; i++) events.update(1, 5 + i);
        assertEquals(0x1C, state.events().backgroundRoutine());
        events.update(1, 19);
        assertEquals(0x20, state.events().backgroundRoutine());
    }

    @Test void act1ApproachUsesPlayerXThenCameraGate() {
        var fixture = HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8, 0).build();
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
