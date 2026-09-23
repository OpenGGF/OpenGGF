package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.events.DezFinalScreenEvents;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezFinalScreenEntry {
    private HeadlessTestFixture boot() {
        com.openggf.game.session.SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder().withZoneAndAct(23, 0).build();
    }
    private DezFinalBossZoneRuntimeState state() {
        return (DezFinalBossZoneRuntimeState) GameServices.zoneRuntimeState();
    }
    @Test void firstProductionFrameAllocatesSupportsAndStartsForcedEntryOnlyOnce() {
        var fixture = boot();
        var manager = GameServices.level().getObjectManager();
        assertFalse(state().screenInitApplied());
        fixture.stepIdleFrames(1);
        assertTrue(state().screenInitApplied());
        assertEquals(2, manager.activeObjectsOfType(DezFinalArenaFloor.class).size());
        assertEquals(1, manager.activeObjectsOfType(DezFinalBossController.class).size());
        assertTrue(fixture.sprite().isObjectControlled());
        assertEquals(0x30, fixture.sprite().getCentreX());
        assertEquals(0x80, fixture.camera().getXCopy());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        fixture.stepIdleFrames(3);
        int x = fixture.sprite().getCentreX();
        registry.restore(saved);
        fixture.stepIdleFrames(3);
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(1, manager.activeObjectsOfType(DezFinalBossController.class).size());
        assertEquals(2, manager.activeObjectsOfType(DezFinalArenaFloor.class).size());
    }
    @Test void allocationPressureKeepsNativePrefixAndStillInitializesCamera() {
        for (int free = 0; free <= 3; free++) {
            var fixture = boot();
            var manager = GameServices.level().getObjectManager();
            manager.reserveAllButNFreeSlots(free);
            DezFinalScreenEvents.initializeObjectsAndCamera(manager, state());
            assertEquals(Math.min(2, free), manager.activeObjectsOfType(DezFinalArenaFloor.class).size());
            assertEquals(free == 3 ? 1 : 0, manager.activeObjectsOfType(DezFinalBossController.class).size());
            assertEquals(free == 3 ? 0x3C0 : 0, state().bossX());
            assertEquals(free == 3 ? 0xF8 : 0, state().bossY());
            assertEquals(0x80, fixture.camera().getXCopy());
            assertEquals(0x6C0, state().windowBase());
            assertEquals(0, state().uploadedLaserOffset());
            manager.releaseDynamicSlot(80);
            DezFinalScreenEvents.initializeObjectsAndCamera(manager, state());
            assertEquals(free == 3 ? 1 : 0, manager.activeObjectsOfType(DezFinalBossController.class).size());
        }
    }
}
