package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.objects.S3kDezGravityPuzzleObjectInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real allocator/lifetime witnesses for Obj_DEZGravityPuzzle's single display child. */
class TestDezGravityPuzzleAllocation {
    @BeforeEach void graphics() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void cleanup() { GraphicsManager.getInstance().resetState(); }

    @Test void displayChildUsesTheFirstFreeSlotAfterItsParentOnce() {
        var f = new Fixture();
        assertFalse(f.manager.occupiedDynamicSlotIdsWithReservations().containsKey(11));
        f.manager.update(0, null, List.of(), 0, false);
        assertEquals(ObjectManager.SLOT_STATE_RESERVED_CHILD,
                f.manager.occupiedDynamicSlotIdsWithReservations().get(11),
                "Obj_DEZGravityPuzzle allocates one Sprite_OnScreen_Test child after parent 9, skipping occupied 10");
        assertFalse(f.manager.occupiedDynamicSlotIdsWithReservations().containsKey(4),
                "AllocateObjectAfterCurrent must ignore the lower hole");
        var occupancy = f.manager.occupiedDynamicSlotIdsWithReservations();
        f.manager.update(0, null, List.of(), 1, false);
        assertEquals(occupancy, f.manager.occupiedDynamicSlotIdsWithReservations(),
                "initialization is a one-time allocation, not a retry on every bob");
    }

    @Test void destructiveRewindRestoresTheChildAndDoesNotAllocateItAgain() {
        var f = new Fixture();
        var before = f.manager.rewindSnapshottable().capture();
        f.manager.update(0, null, List.of(), 0, false);
        var after = f.manager.rewindSnapshottable().capture();
        var expected = f.manager.occupiedDynamicSlotIdsWithReservations();
        assertEquals(ObjectManager.SLOT_STATE_RESERVED_CHILD, expected.get(11));
        var original = f.manager.getActiveObjectForRewind(f.puzzleSpawn);
        f.manager.setRewindInPlaceRestoreEnabledForTest(false);
        f.manager.rewindSnapshottable().restore(before);
        assertNotSame(original, f.manager.getActiveObjectForRewind(f.puzzleSpawn));
        assertFalse(f.manager.occupiedDynamicSlotIdsWithReservations().containsKey(11));
        f.manager.update(0, null, List.of(), 0, false);
        assertEquals(expected, f.manager.occupiedDynamicSlotIdsWithReservations());
        f.manager.rewindSnapshottable().restore(after);
        f.manager.update(0, null, List.of(), 1, false);
        assertEquals(expected, f.manager.occupiedDynamicSlotIdsWithReservations(),
                "restoring initialized state must not reserve a second child");
    }

    @Test void ordinaryOffscreenUnloadReleasesOnlyThisParentsChild() {
        var f = new Fixture();
        f.manager.update(0, null, List.of(), 0, false);
        assertEquals(ObjectManager.SLOT_STATE_RESERVED_CHILD,
                f.manager.occupiedDynamicSlotIdsWithReservations().get(11));
        f.manager.update(0x1000, null, List.of(), 1, false);
        assertNull(f.manager.getActiveObjectForRewind(f.puzzleSpawn));
        assertFalse(f.manager.occupiedDynamicSlotIdsWithReservations().containsKey(11));
        assertEquals(11, f.manager.allocateSlotAfter(9),
                "unrelated live blocker 10 keeps its own slot");
    }

    private static final class Fixture {
        final ObjectSpawn puzzleSpawn = new ObjectSpawn(0x100, 0x100, 0x61, 0, 0, false, 0x100, 5);
        final ObjectManager manager;
        Fixture() {
            var camera = mock(Camera.class);
            when(camera.getWidth()).thenReturn((short) 320);
            when(camera.getHeight()).thenReturn((short) 224);
            var registry = new ObjectRegistry() {
                public ObjectInstance create(ObjectSpawn spawn) {
                    return spawn.objectId() == 0x61 ? new S3kDezGravityPuzzleObjectInstance(spawn) : new Probe(spawn);
                }
                public void reportCoverage(List<ObjectSpawn> spawns) { }
                public String getPrimaryName(int id) { return "allocation-probe"; }
                public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
            };
            ObjectManager[] holder = new ObjectManager[1];
            var services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
            };
            var spawns = new ArrayList<ObjectSpawn>();
            for (int i = 0; i < 5; i++) spawns.add(new ObjectSpawn(0x100, 0x100, 0xFF, 0, 0, false, 0x100, i));
            spawns.add(puzzleSpawn);
            spawns.add(new ObjectSpawn(0x100, 0x100, 0xFF, 0, 0, false, 0x100, 6));
            manager = new ObjectManager(spawns, registry, 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            manager.reset(0);
            var puzzle = (S3kDezGravityPuzzleObjectInstance) manager.getActiveObjectForRewind(puzzleSpawn);
            assertNotNull(puzzle);
            assertEquals(9, puzzle.getSlotIndex());
            assertNotNull(manager.getActiveObjectForRewind(spawns.getLast()),
                    "slot 10 has a real rewind-owned blocker, not an unowned reservation");
            ((AbstractObjectInstance) manager.getActiveObjectForRewind(spawns.getFirst())).setDestroyed(true);
        }
    }

    public static final class Probe extends AbstractObjectInstance implements SpawnRewindRecreatable {
        public Probe(ObjectSpawn spawn) { super(spawn, "allocation-probe"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public boolean isPersistent() { return true; }
    }
}
