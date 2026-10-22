package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.sonic1.objects.Sonic1StaircaseObjectInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Staircase (_incObj/5B SLZ Staircase.asm:6-11) deletes each native SST at its
 * own position in ExecuteObjects. Borrowing a child's execution slot must not
 * defer the parent's release or release its higher children prematurely.
 */
class TestBorrowedExecutionSlotRetirement {
    private static final ObjectSpawn LOWER = spawn(0x80, 0x7E, 0);
    private static final ObjectSpawn STABLE = spawn(0x90, 0x7E, 1);
    private static final ObjectSpawn STAIRCASE = spawn(0x180, 0x5B, 0);
    private static final ObjectSpawn OBSERVER = spawn(0x190, 0x7E, 2);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ownerReleasesAtItsOwnSlotWhileChildrenWaitForTheirSlots(boolean counterBased) {
        Fixture fixture = new Fixture(counterBased);
        ObjectManager manager = fixture.manager;
        manager.update(0, null, List.of(), 1, false);

        Sonic1StaircaseObjectInstance originalOwner = fixture.owner();
        assertEquals(34, originalOwner.getSlotIndex());
        assertEquals(38, originalOwner.getExecutionSlotIndex());
        assertArrayEquals(new int[]{36, 37, 38}, reservedChildren(manager));

        // Leave a genuine lower hole by deleting an earlier object in the walk.
        fixture.lower().setDestroyed(true);
        manager.update(0, null, List.of(), 2, false);
        assertNull(manager.getActiveObjectForRewind(LOWER));
        assertEquals(6, manager.getAllocatedSlotCount());
        fixture.observer().armed = true;

        // Force real recreation, including the staircase's borrowed execution
        // slot and its manager-owned child reservations, before repeating the pass.
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        ObjectManagerSnapshot before = manager.rewindSnapshottable().capture();

        fixture.camera.x = 0x400;
        manager.update(0x400, null, List.of(), 3, false);
        assertRetiredPass(fixture);
        ObjectManagerSnapshot after = manager.rewindSnapshottable().capture();

        manager.rewindSnapshottable().restore(before);
        assertNotSame(originalOwner, fixture.owner(), "restore must recreate the placed owner");
        assertEquals(38, fixture.owner().getExecutionSlotIndex());
        assertArrayEquals(new int[]{36, 37, 38}, reservedChildren(manager));
        assertArrayEquals(before.usedSlotsBits(),
                manager.rewindSnapshottable().capture().usedSlotsBits());
        assertTrue(fixture.observer().armed, "the allocation decision must also rewind");
        assertEquals(0, manager.activeObjectsOfType(AllocationMarker.class).stream()
                .filter(marker -> marker.getSpawn().subtype() >= 10).count());

        manager.update(0x400, null, List.of(), 3, false);
        assertRetiredPass(fixture);
        assertArrayEquals(after.usedSlotsBits(),
                manager.rewindSnapshottable().capture().usedSlotsBits(),
                "restore and forward replay must reproduce the allocation map");
    }

    private static void assertRetiredPass(Fixture fixture) {
        ObjectManager manager = fixture.manager;
        AllocationObserver observer = fixture.observer();
        assertNull(manager.getActiveObjectForRewind(STAIRCASE));
        assertEquals(32, observer.lowerSlot,
                "FindFreeObj must consume the pre-existing lower hole first");
        assertEquals(34, observer.ownerSlot,
                "the next free slot must be the parent that just retired at 34");
        assertEquals(39, observer.forwardSlot,
                "children 36..38 remain occupied until the walk reaches each child");
        assertEquals(0, marker(manager, 32).updates,
                "a replacement behind the cursor must wait until the next pass");
        assertEquals(0, marker(manager, 34).updates);
        assertEquals(1, marker(manager, 39).updates,
                "a newly allocated forward slot executes in this same pass");
        assertEquals(36, marker(manager, 39).firstReleasedChildSlot,
                "the forward observer must see child release during the walk, before finally cleanup");
        assertEquals(5, manager.getAllocatedSlotCount(),
                "no parent or folded-child allocator bit may leak");

        // By the end of the pass each deferred child release has run. Check all
        // three slots, then give these test-only reservations straight back.
        for (int expected : new int[]{36, 37, 38}) {
            assertEquals(expected, manager.allocateDynamicSlot());
        }
        for (int slot : new int[]{36, 37, 38}) {
            manager.releaseDynamicSlot(slot);
        }
    }

    private static AllocationMarker marker(ObjectManager manager, int slot) {
        return manager.activeObjectsOfType(AllocationMarker.class).stream()
                .filter(candidate -> candidate.getSlotIndex() == slot)
                .findFirst().orElseThrow();
    }

    private static int[] reservedChildren(ObjectManager manager) {
        return manager.rewindSnapshottable().capture().childSpawns().stream()
                .filter(entry -> STAIRCASE.equals(entry.parentSpawn()))
                .findFirst().orElseThrow().reservedSlots();
    }

    private static ObjectSpawn spawn(int x, int id, int subtype) {
        return new ObjectSpawn(x, 0x60, id, subtype, 0, false, 0);
    }

    private static final class Fixture {
        final MutableCamera camera = new MutableCamera();
        final ObjectManager manager;

        Fixture(boolean counterBased) {
            ObjectManager[] holder = new ObjectManager[1];
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
            };
            ObjectRegistry registry = new ObjectRegistry() {
                @Override public ObjectInstance create(ObjectSpawn spawn) {
                    if (spawn.objectId() == 0x5B) {
                        return new Sonic1StaircaseObjectInstance(spawn);
                    }
                    return spawn.subtype() == 2
                            ? new AllocationObserver(spawn) : new AllocationMarker(spawn);
                }
                @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
                @Override public String getPrimaryName(int id) { return "retirement-probe"; }
            };
            manager = new ObjectManager(List.of(LOWER, STABLE, STAIRCASE, OBSERVER),
                    registry, 0, null, null, GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            if (counterBased) {
                manager.enableCounterBasedRespawn();
            }
            manager.reset(0);
        }

        Sonic1StaircaseObjectInstance owner() {
            return (Sonic1StaircaseObjectInstance) manager.getActiveObjectForRewind(STAIRCASE);
        }

        AllocationMarker lower() {
            return (AllocationMarker) manager.getActiveObjectForRewind(LOWER);
        }

        AllocationObserver observer() {
            return (AllocationObserver) manager.getActiveObjectForRewind(OBSERVER);
        }
    }

    private static final class MutableCamera extends Camera {
        short x;
        @Override public short getX() { return x; }
        @Override public short getY() { return 0; }
        @Override public short getWidth() { return 320; }
        @Override public short getHeight() { return 224; }
        @Override public boolean isVerticalWrapEnabled() { return false; }
    }

    public static class AllocationMarker extends AbstractObjectInstance implements SpawnRewindRecreatable {
        int updates;
        boolean probeRetiredChildSlots;
        int firstReleasedChildSlot = -1;

        public AllocationMarker(ObjectSpawn spawn) {
            super(spawn, "retirement-marker");
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            updates++;
            if (probeRetiredChildSlots) {
                // This marker executes at 39, after native child slots 36..38.
                // A finally-only drain would still hold all three here and
                // incorrectly give this probe slot 40 instead of slot 36.
                ObjectManager manager = services().objectManager();
                firstReleasedChildSlot = manager.allocateDynamicSlot();
                manager.releaseDynamicSlot(firstReleasedChildSlot);
            }
        }
        @Override public boolean isPersistent() { return true; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    public static final class AllocationObserver extends AllocationMarker {
        boolean armed;
        int lowerSlot = -1;
        int ownerSlot = -1;
        int forwardSlot = -1;

        public AllocationObserver(ObjectSpawn spawn) {
            super(spawn);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            super.update(vIntRunCount, player);
            if (!armed) {
                return;
            }
            armed = false;
            ObjectManager manager = services().objectManager();
            lowerSlot = manager.allocateDynamicSlot();
            manager.addDynamicObjectAtSlot(new AllocationMarker(spawn(0x480, 0x7E, 10)), lowerSlot);
            ownerSlot = manager.allocateDynamicSlot();
            manager.addDynamicObjectAtSlot(new AllocationMarker(spawn(0x480, 0x7E, 11)), ownerSlot);
            forwardSlot = manager.allocateSlotAfter(getSlotIndex());
            AllocationMarker forward = new AllocationMarker(spawn(0x480, 0x7E, 12));
            forward.probeRetiredChildSlots = true;
            manager.addDynamicObjectAtSlot(forward, forwardSlot);
        }
    }
}
