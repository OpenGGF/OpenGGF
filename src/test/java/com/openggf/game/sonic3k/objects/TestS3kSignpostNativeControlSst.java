package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Independent normal-allocation scene for Child6_EndSign; no assigned or trace-seeded SSTs. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSignpostNativeControlSst {
    @AfterEach
    void clear() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
    }

    @Test
    void detachedFlowAllocatesAfterTheRetainedControlAndReplaysTwice() throws Exception {
        exerciseAllocation(true, false);
    }

    @Test
    void unspecifiedControlKeepsTheCurrentFlowAllocationAndReplaysTwice() throws Exception {
        exerciseAllocation(false, false);
    }

    @Test
    void inPlaceControlKeepsItsOwnAllocationAndReplaysTwice() throws Exception {
        exerciseAllocation(true, true);
    }

    private void exerciseAllocation(boolean retainsNativeControl, boolean inPlace) throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        var manager = GameServices.level().getObjectManager();
        var retained = manager.createDynamicObject(() -> new DeclaredSst(0));
        var transientOwner = manager.createDynamicObject(() -> new DeclaredSst(1));
        var flow = manager.createDynamicObject(() -> {
            var owner = new S3kBossDefeatSignpostFlow(
                    fixture.sprite().getCentreX(), 0, S3kBossDefeatSignpostFlow.CleanupAction.NONE);
            return retainsNativeControl ? owner.withNativeControlSlot(retained.getSlotIndex()) : owner;
        });
        int retainedSlot = retained.getSlotIndex();
        int hole = transientOwner.getSlotIndex();
        int flowSlot = flow.getSlotIndex();
        assertTrue(retainedSlot < hole && hole < flowSlot, "normal allocation orders the declared owners");
        if (inPlace) flow.withNativeControlSlot(flowSlot);
        fixture.stepIdleFrames(100);
        assertNull(NativeSstSlotAccess.currentOwner(manager, hole), "the declared transient retires itself");
        assertTrue(manager.activeObjectsOfType(S3kSignpostInstance.class).isEmpty());

        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var occupied = manager.occupiedDynamicSlotIdsWithReservations();
        int firstAfterFlow = java.util.stream.IntStream.range(flowSlot + 1, manager.getObjectSlotCapacity())
                .filter(slot -> !occupied.containsKey(slot)).findFirst().orElseThrow();
        int expectedSlot = retainsNativeControl && !inPlace ? hole : firstAfterFlow;
        CompositeSnapshot before = registry.capture();
        fixture.stepIdleFrames(45); // Real WAIT_FADE -> SPAWN_SIGNPOST dispatch, then sign/stub init.
        var signpost = S3kSignpostInstance.activeSignpost(manager);
        assertNotNull(signpost, "the real flow must reach Child6_EndSign");
        int actualSlot = signpost.getSlotIndex();
        CompositeSnapshot after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            assertWorld(before, registry.capture());
            fixture.stepIdleFrames(45);
            assertWorld(after, registry.capture());
            assertEquals(actualSlot, S3kSignpostInstance.activeSignpost(manager).getSlotIndex());
        }
        System.out.printf("SIGNPOST_CONTROL retained=%d hole=%d flow=%d native=%s expected=%d actual=%d%n",
                retainedSlot, hole, flowSlot, retainsNativeControl, expectedSlot, actualSlot);
        assertEquals(expectedSlot, actualSlot,
                "CreateChild6_Simple calls AllocateObjectAfterCurrent from the retained native control SST");
    }

    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }

    private static final class DeclaredSst extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private DeclaredSst(int retire) {
            this(new ObjectSpawn(0, 0, 0, retire, 0, false, 0));
        }

        public DeclaredSst(ObjectSpawn spawn) {
            super(spawn, "DeclaredSignpostAllocationOwner");
            setRomWorldPositioned(false);
        }

        @Override public boolean isPersistent() { return true; }
        @Override public boolean usesCustomOutOfRangeCheck() { return true; }
        @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            if (getSpawn().subtype() != 0) setDestroyed(true);
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
