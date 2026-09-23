package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.level.objects.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezFinalEntryObjects {
    private HeadlessTestFixture boot() {
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(23, 0).build();
        fixture.sprite().setDebugMode(true);
        return fixture;
    }
    private DezFinalBossZoneRuntimeState state() { return (DezFinalBossZoneRuntimeState) GameServices.zoneRuntimeState(); }
    private DezFinalEntrySprite cover() {
        var parent = new DezFinalArenaFloor(new ObjectSpawn(0x3C0, 0xF8, 0, 1, 0, false, 0));
        var manager = GameServices.level().getObjectManager(); manager.addDynamicObject(parent);
        var child = DezFinalEntrySprite.cover(parent); manager.addDynamicObject(child); return child;
    }
    @Test void runnerMovesAndAnimatesOnInitializationAndSignalsAt3D0() {
        boot(); var runner = DezFinalEntrySprite.robotnik(); GameServices.level().getObjectManager().addDynamicObject(runner);
        runner.update(0, null);
        assertEquals(0x76, runner.getX()); assertEquals(0xC0, runner.getY()); assertEquals(1, runner.frameForTest());
        for (int i = 1; i < 143; i++) runner.update(i, null);
        assertEquals(0x3CA, runner.getX()); assertFalse(runner.isDestroyed()); assertEquals(0, state().bossSignals());
        runner.update(143, null);
        assertEquals(0x3D0, runner.getX()); assertTrue(runner.isDestroyed()); assertEquals(1, state().bossSignals());
    }
    @Test void coverWaitsForRunnerThenMovesThirtyOneTimesBeforePublishing() {
        boot(); var cover = cover(); cover.update(0, null);
        assertEquals(0x3B0, cover.getX()); assertEquals(0xBC, cover.getY()); assertEquals(5, cover.frameForTest());
        for (int i = 0; i < 100; i++) cover.update(i, null);
        assertEquals(0x3B0, cover.getX()); assertEquals(0, state().eventsFg5());
        state().bossSignals(1); cover.update(100, null);
        assertEquals(0x3B0, cover.getX());
        for (int i = 0; i < 31; i++) cover.update(101 + i, null);
        assertEquals(0x3A0, cover.getX()); assertEquals(0x80, cover.fractionForTest());
        assertFalse(cover.isDestroyed()); assertEquals(1, state().bossSignals());
        state().eventsFg5(0x12); cover.update(132, null);
        assertTrue(cover.isDestroyed()); assertEquals(3, state().bossSignals()); assertEquals(0xFF12, state().eventsFg5());
    }
    @Test void recreatedCoverRetainsItsParentFractionAndSignalTiming() {
        var fixture = boot(); var cover = cover(); cover.update(0, null);
        state().bossSignals(1); cover.update(1, null); cover.update(2, null);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved = registry.capture();
        cover.setDestroyed(true); fixture.stepIdleFrames(1); registry.restore(saved);
        var restored = GameServices.level().getObjectManager().activeObjectsOfType(DezFinalEntrySprite.class).getFirst();
        assertNotSame(cover, restored); assertEquals(0x3AF, restored.getX()); assertEquals(0x80, restored.fractionForTest());
        for (int i = 0; i < 30; i++) restored.update(i, null);
        assertFalse(restored.isDestroyed()); assertEquals(1, state().bossSignals());
        restored.update(30, null); assertTrue(restored.isDestroyed()); assertEquals(3, state().bossSignals());
    }
    @Test void runnerPublicationReachesTheLaterCoverSlotInTheSamePass() {
        boot(); var cover = cover(); var runner = DezFinalEntrySprite.robotnik();
        GameServices.level().getObjectManager().addDynamicObject(runner);
        // Exercise the native allocation dispatch order explicitly: runner then cover.
        for (int pass = 0; pass < 175; pass++) {
            if (!runner.isDestroyed()) runner.update(pass, null);
            cover.update(pass, null);
            assertEquals(0, state().bossSignals() & 2);
        }
        assertEquals(1, state().bossSignals());
        assertEquals(0x3A0, cover.getX()); assertEquals(0x80, cover.fractionForTest());
        cover.update(175, null);
        assertEquals(3, state().bossSignals()); assertEquals(0xFF00, state().eventsFg5());
    }
    @Test void restoringBothUninitializedCoverAndParentRebuildsTheManagedLink() {
        var fixture = boot(); var cover = cover();
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved = registry.capture();
        var manager = GameServices.level().getObjectManager();
        cover.setDestroyed(true);
        manager.activeObjectsOfType(DezFinalArenaFloor.class).forEach(o -> o.setDestroyed(true));
        fixture.stepIdleFrames(1); registry.restore(saved);
        var restored = manager.activeObjectsOfType(DezFinalEntrySprite.class).getFirst();
        assertNotSame(cover, restored);
        restored.update(0, null);
        assertEquals(0x3B0, restored.getX()); assertEquals(0xBC, restored.getY());
    }
    @Test void cameraWorkerPublishesOnlyAtTheNativeThreshold() {
        boot(); var worker = DezFinalArenaSignal.camera(); GameServices.level().getObjectManager().addDynamicObject(worker);
        var camera = GameServices.camera(); camera.setX((short) 0x51F); worker.update(0, null);
        assertEquals(0x51F, camera.getMinX()); assertEquals(0, state().bossSignals()); assertFalse(worker.isDestroyed());
        camera.setX((short) 0x520); worker.update(1, null);
        assertEquals(0x520, camera.getMinX()); assertEquals(0x5C0, camera.getMaxX());
        assertEquals(4, state().bossSignals()); assertEquals(0x2C0, state().windowBase()); assertTrue(worker.isDestroyed());
    }
    @Test void quakePreservesFlagLowByteUsesVintNibbleAndDeletesWhenCleared() {
        boot(); var services = mock(ObjectServices.class); when(services.zoneRuntimeState()).thenReturn(state());
        var worker = DezFinalArenaSignal.quake(); worker.setServices(services);
        state().screenShake().writeFlag(0x14);
        worker.update(15, null); assertEquals((short) 0xFF14, state().screenShake().flag());
        verify(services, never()).playSfx(anyInt()); worker.update(16, null);
        verify(services).playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.RUMBLE_2.id);
        state().screenShake().clear(); worker.update(32, null); assertTrue(worker.isDestroyed());
        verify(services, times(1)).playSfx(anyInt());
    }
    @Test void entrySheetsUseTheFinalActTileDestinations() {
        boot(); var entries = com.openggf.game.sonic3k.Sonic3kPlcArtRegistry.getPlan(23, 0).levelArt();
        var runner = entries.stream().filter(e -> e.key().equals(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.DEZ_ROBOTNIK_RUN)).findFirst().orElseThrow();
        assertEquals(0x58C, runner.artTileBase()); assertEquals(0, runner.palette());
        var misc = entries.stream().filter(e -> e.key().equals(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.DEZ_FINAL_BOSS_MISC)).findFirst().orElseThrow();
        assertEquals(0x38F, misc.artTileBase()); assertEquals(0x187888, misc.mappingAddr());
    }
}
