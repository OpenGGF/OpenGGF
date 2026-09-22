package com.openggf.game.sonic3k.objects;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@RequiresRom(SonicGame.SONIC_3K)
class TestLrzAutoscrollGraphHeadless {
    @AfterEach
    void reset() {
        com.openggf.configuration.SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }
    private int field(Object object, String name) throws Exception {
        var f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(object);
    }
    @ParameterizedTest
    @ValueSource(ints = {320, 352, 400, 528, 800})
    void freshEntryCompletesFlashReleasesControlsAndStartsTheMissileSequence(int width) throws Exception {
        var config = com.openggf.configuration.SonicConfigurationService.getInstance();
        var aspect = java.util.Arrays.stream(com.openggf.configuration.WidescreenAspect.values())
                             .filter(a -> a.pixelWidth() == width)
                             .findFirst()
                             .orElseThrow();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(22, 0).withFreshLevelStartLifecycle().build();
        assertEquals(width, GameServices.camera().getWidth());
        var manager = GameServices.level().getObjectManager();
        var state = S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow().bossAct();
        boolean locked = false, sawFade = false, rewound = false;
        for (int frame = 0; frame < 220 && !state.flashComplete(); frame++) {
            fixture.stepFrame(false, false, false, false, false);
            locked |= fixture.sprite().isControlLocked();
            sawFade |= !manager.activeObjectsOfType(LrzAutoscrollPaletteFade.class).isEmpty();
            if (sawFade && !rewound) {
                roundTrip(fixture);
                rewound = true;
            }
            assertFalse(fixture.sprite().getDead(), "entry died at " + frame);
        }
        assertTrue(locked && sawFade && rewound);
        assertTrue(state.flashComplete());
        assertEquals(1, state.paletteMode());
        fixture.stepFrame(false, false, false, false, false);
        assertFalse(fixture.sprite().isControlLocked());
        assertEquals(8,
                manager.activeObjectsOfType(LrzAutoscrollObjectInstance.class)
                        .stream()
                        .filter(o -> uncheckedCode(o) == 0x792F0)
                        .count());
        for (int frame = 0; frame < 500 && !state.missilesReleased(); frame++)
            fixture.stepFrame(false, false, false, false, false);
        assertTrue(state.missilesReleased());
        roundTrip(fixture);
    }
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void flashAllocationIsOneFirstFreeAttemptAndDoesNotUnlockOnFailure(int capacity) throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(22, 0).withFreshLevelStartLifecycle().build();
        var manager = GameServices.level().getObjectManager();
        for (int frame = 0; frame < 150 && manager.activeObjectsOfType(LrzAutoscrollObjectInstance.class).isEmpty();
                frame++)
            fixture.stepFrame(false, false, false, false, false);
        var root = manager.activeObjectsOfType(LrzAutoscrollObjectInstance.class).getFirst();
        assertEquals(0x78F82, field(root, "code"));
        manager.reserveAllButNFreeSlots(capacity);
        for (int i = 0; i < 60; i++) root.update(200 + i, fixture.sprite());
        assertEquals(0x78FAE, field(root, "code"));
        var children =
                manager.activeObjectsOfType(LrzAutoscrollObjectInstance.class).stream().filter(o -> o != root).toList();
        assertEquals(capacity, children.size());
        children.forEach(manager::removeDynamicObject);
        root.update(260, fixture.sprite());
        assertEquals(1, manager.activeObjectsOfType(LrzAutoscrollObjectInstance.class).size());
        assertTrue(fixture.sprite().isControlLocked());
    }
    @ParameterizedTest
    @ValueSource(ints = {0, 3, 8})
    void missileTableKeepsItsForwardAllocatedPrefixAndDoesNotHealRemovedChildren(int capacity) throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(22, 0).withFreshLevelStartLifecycle().build();
        var manager = GameServices.level().getObjectManager();
        for (int frame = 0; frame < 150 && manager.activeObjectsOfType(LrzAutoscrollObjectInstance.class).isEmpty();
                frame++)
            fixture.stepFrame(false, false, false, false, false);
        var root = manager.activeObjectsOfType(LrzAutoscrollObjectInstance.class).getFirst();
        for (int i = 0; i < 60; i++) root.update(200 + i, fixture.sprite());
        manager.activeObjectsOfType(LrzAutoscrollObjectInstance.class)
                .stream()
                .filter(o -> o != root)
                .toList()
                .forEach(manager::removeDynamicObject);
        // Explicit callback-boundary setup; full fresh-entry publication is tested separately.
        S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow().bossAct().publishFlashComplete();
        manager.reserveAllButNFreeSlots(capacity);
        root.update(260, fixture.sprite());
        var children =
                manager.activeObjectsOfType(LrzAutoscrollObjectInstance.class).stream().filter(o -> o != root).toList();
        assertEquals(capacity, children.size());
        for (int i = 0; i < capacity; i++) {
            assertEquals(i * 2, children.get(i).getSpawn().subtype());
            assertTrue(children.get(i).getSlotIndex() > root.getSlotIndex());
            manager.removeDynamicObject(children.get(i));
        }
        root.update(261, fixture.sprite());
        assertEquals(1, manager.activeObjectsOfType(LrzAutoscrollObjectInstance.class).size());
    }
    @Test
    void bonusReturnInitializesTheCheckpointArenaBeforeTheCameraSnap() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(22, 0).withFreshLevelStartLifecycle().build();
        var level = GameServices.level();
        var saved = new com.openggf.game.BonusStageState(
                0x1600, 0x1600, 47, 0, 0, 0, 12, 0, 0x9C0, 0x368, 0x920, 0x308, (byte) 0x0C, (byte) 0x0D, 0x430, 100);
        var coordinator = new com.openggf.game.BonusStageTransitionCoordinator();
        level.setBonusStageReturnCheckpointIndex(0);
        try {
            coordinator.prepareReturnLoad(level, saved);
            level.loadZoneAndAct(22, 0);
        } finally {
            level.clearBonusStageReturn();
        }
        coordinator.restoreReturnState(level, GameServices.camera(), null,
                GameServices.sprites().getMainPlayable(), GameServices.module().getLevelEventProvider(), saved, 1,
                64, null, null, null);
        var state = S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow().bossAct();
        assertEquals(0x920, GameServices.camera().getX());
        assertEquals(0x920, GameServices.camera().getMinX());
        assertEquals(0x920, GameServices.camera().getMaxX());
        assertEquals(0x2F0, GameServices.camera().getY());
        assertTrue(state.initialized());
        // Once the scoped return flag is gone, a manual load must be fresh again.
        level.loadZoneAndAct(22, 0);
        assertEquals(0, GameServices.camera().getMaxX());
    }
    private int uncheckedCode(Object object) {
        try {
            return field(object, "code");
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
    private void roundTrip(HeadlessTestFixture fixture) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        fixture.stepFrame(false, false, false, false, false);
        var after = registry.capture();
        var manager = GameServices.level().getObjectManager();
        manager.getActiveObjects()
                .stream()
                .filter(o -> o instanceof LrzAutoscrollObjectInstance || o instanceof LrzAutoscrollPaletteFade)
                .toList()
                .forEach(manager::removeDynamicObject);
        registry.restore(before);
        for (String key : before.entries().keySet())
            assertTrue(RewindSnapshotDiff.diffKey(key, before.get(key), registry.capture().get(key)).isEmpty(), key);
        fixture.stepFrame(false, false, false, false, false);
        var replay = registry.capture();
        for (String key : after.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, after.get(key), replay.get(key));
            assertTrue(diff.isEmpty(), () -> key + ": " + diff);
        }
    }
}
