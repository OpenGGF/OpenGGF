package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.level.CarriedTitlePublicationTiming;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.AbstractResultsScreen;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** loc_2DD06 retains the results SST; Obj_TitleCardWait consumes its children's preceding $34 writes. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kRetainedTitleMovementLatch {
    @AfterEach void clear() {
        GameServices.configuration().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test
    void carriedParentConsumesHigherSlotMovementBeforeResetAndReplaysWholeWorldTwice() throws Exception {
        var fixture = fixture();
        var level = GameServices.level();
        var manager = level.getObjectManager();
        // Declared component boundary: results children already retired. Leave
        // a real occupied hole, as AllocateObjectAfterCurrent can encounter.
        for (int slot = 40; slot <= 45; slot++) {
            var old = NativeSstSlotAccess.currentOwner(manager, slot);
            if (old != null) manager.removeDynamicObject(old);
        }
        var blocker = manager.createDynamicObjectAtSlot(() -> new S3kTitleCardOwnerSlotObjectInstance(
                new com.openggf.level.objects.ObjectSpawn(0, 0, 0, 0, 0, false, 0)), 42);
        assertNotNull(blocker);
        var root = manager.createDynamicObjectAtSlot(() -> new S3kResultsScreenObjectInstance(true), 40);
        assertNotNull(root);
        var request = SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(1, 1).showInLevelTitleCard(false)
                .resetLevelGamestateAtInLevelTitleCardDisplay(true)
                .inLevelTitleCardResetAdditionalDispatches(com.openggf.game.TitleCardResetGates.NATIVE_WAIT_GATE)
                .lockPlayerControlForInLevelTitleCard(true)
                .inLevelTitleCardExitAdditionalDispatches(0).build();
        root.onCarriedAcrossSeamlessTransition(0, 0, CarriedTitlePublicationTiming.from(request));
        var state = AbstractResultsScreen.class.getDeclaredField("state");
        state.setAccessible(true);
        state.setInt(root, 4);
        root.onExitReady();
        assertEquals(S3kResultsScreenObjectInstance.CarriedTitlePhase.TITLE_CARD_INIT,
                root.carriedTitlePhase(), "loc_2DD06 replaces the retained SST's code pointer and returns");
        level.getLevelGamestate().setRings(17);
        fixture.sprite().setRingCount(17);
        fixture.stepIdleFrames(1);
        assertSame(root, NativeSstSlotAccess.currentOwner(manager, 40),
                "Obj_TitleCardInit cannot delete the parent whose $34 the children will write");
        assertTrue(children().isEmpty(), "Init submits archives; Create is a later dispatch");
        assertTrue(GameServices.module().getTitleCardProvider().shouldLockPlayerControlForInLevelOverlay(),
                "the carried request keeps its existing control-lock ownership");
        int queuePolls = 0;
        while (children().isEmpty() && queuePolls++ < 256) fixture.stepIdleFrames(1);
        assertEquals(List.of(41, 43, 44, 45), children().stream()
                .map(AbstractObjectInstance::getSlotIndex).toList());
        assertSame(blocker, NativeSstSlotAccess.currentOwner(manager, 42));
        assertEquals(List.of(0x250, 0x2EC, 0x334, 0xE0), children().stream()
                .map(AbstractObjectInstance::getX).toList(),
                "higher children execute their first movement on the Create scan");
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot birth = registry.capture();
        fixture.stepIdleFrames(27); // $344 -> $184 in 28 native $10 moves, including Create.
        assertEquals(0x184, children().get(2).getX());
        assertEquals(17, level.getLevelGamestate().getRings());
        fixture.stepIdleFrames(1);
        assertEquals(17, level.getLevelGamestate().getRings(),
                "the lower parent clears the final movement latch and returns");
        CompositeSnapshot cleared = registry.capture();
        fixture.stepIdleFrames(1);
        assertEquals(0, level.getLevelGamestate().getRings(),
                "only the next stationary parent poll executes loc_2D810");
        CompositeSnapshot reset = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(birth);
            assertWorld(birth, registry.capture());
            fixture.stepIdleFrames(28);
            assertWorld(cleared, registry.capture());
            fixture.stepIdleFrames(1);
            assertWorld(reset, registry.capture());
            registry.restore(cleared);
            assertWorld(cleared, registry.capture());
            fixture.stepIdleFrames(1);
            assertWorld(reset, registry.capture());
        }
    }

    private static HeadlessTestFixture fixture() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(1, 1).build();
        var art = (com.openggf.game.sonic3k.Sonic3kObjectArtProvider) GameServices.module().getObjectArtProvider();
        int ticks = 0;
        while ((!art.capture().runtimeArtAdmissionConsumed()
                || S3kRuntimeArtCoordinator.current().moduleQueue().hasPendingPhysicalModules())
                && ticks++ < 256) fixture.stepIdleFrames(1);
        assertTrue(art.capture().runtimeArtAdmissionConsumed());
        GameServices.level().loadZoneAndActAtFreshTitleCardBoundary(1, 1);
        assertTrue(GameServices.level().skipPendingInitialTitleCardPresentation());
        var manager = GameServices.level().getObjectManager();
        ticks = 0;
        while ((!manager.activeObjectsOfType(S3kFreshTitleCardOwnerObjectInstance.class).isEmpty()
                || S3kRuntimeArtCoordinator.current().moduleQueue().hasPendingPhysicalModules())
                && ticks++ < 256) fixture.stepIdleFrames(1);
        assertTrue(manager.activeObjectsOfType(S3kFreshTitleCardOwnerObjectInstance.class).isEmpty());
        assertFalse(S3kRuntimeArtCoordinator.current().moduleQueue().hasPendingPhysicalModules());
        return fixture;
    }

    private static List<S3kInLevelTitleCardElementObjectInstance> children() {
        return GameServices.level().getObjectManager()
                .activeObjectsOfType(S3kInLevelTitleCardElementObjectInstance.class).stream()
                .filter(child -> !child.isDestroyed())
                .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
    }

    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }
}
