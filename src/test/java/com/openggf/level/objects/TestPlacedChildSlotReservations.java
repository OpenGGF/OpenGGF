package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestPlacedChildSlotReservations {
    @BeforeEach void graphics() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void cleanup() { GraphicsManager.getInstance().resetState(); }

    @Test void fixedPrefixSurvivesInterleavedTailReuseAndAllocationFailure() {
        var f = new Fixture();
        int[] prefix = f.prefix(f.first, 6);
        int a = f.reserve(f.first, f.first.getSlotIndex(), 6);
        int b = f.reserve(f.first, a, 6);
        assertTrue(b > a);
        assertTrue(f.manager.releasePlacedChildSlot(f.first, a, 6));
        int c = f.reserve(f.first, b, 6);
        assertTrue(c > b, "tail bookkeeping reuse must not change native predecessor search");
        assertArrayEquals(prefix, Arrays.copyOf(f.slots(f.first), 6));
        assertEquals(8, f.slots(f.first).length, "reuse a freed bookkeeping cell instead of growing");
        f.manager.reserveAllButNFreeSlots(1);
        int last = f.reserve(f.first, f.first.getSlotIndex(), 6);
        assertTrue(last >= 0);
        int[] beforeFailure = f.slots(f.first);
        assertEquals(-1, f.reserve(f.first, last, 6));
        assertArrayEquals(beforeFailure, f.slots(f.first));
        assertArrayEquals(prefix, Arrays.copyOf(f.slots(f.first), 6));
    }

    @Test void destructiveRestorePreservesExactReservationsAndNextFreeSlot() {
        var f = new Fixture();
        f.prefix(f.first, 6);
        int a = f.reserve(f.first, f.first.getSlotIndex(), 6);
        int b = f.reserve(f.first, a, 6);
        var snapshot = f.snapshot();
        int expectedNext = f.reserve(f.first, b, 6);
        var oldParent = f.first;
        f.manager.setRewindInPlaceRestoreEnabledForTest(false);
        f.manager.rewindSnapshottable().restore(snapshot);
        f.first = (Probe) f.manager.getActiveObjectForRewind(f.firstSpawn);
        assertNotSame(oldParent, f.first);
        var restored = f.snapshot();
        assertArrayEquals(snapshot.usedSlotsBits(), restored.usedSlotsBits());
        assertArrayEquals(snapshot.childSpawns().getFirst().reservedSlots(), restored.childSpawns().getFirst().reservedSlots());
        assertFalse(f.manager.releasePlacedChildSlot(oldParent, a, 6));
        assertEquals(expectedNext, f.reserve(f.first, b, 6));
    }

    @Test void releaseIsOwnerMatchedAndCannotFreePrefixOrAnotherOwnersReusedSlot() {
        var f = new Fixture();
        f.prefix(f.first, 6); f.prefix(f.second, 2);
        int a = f.reserve(f.first, f.first.getSlotIndex(), 6);
        assertFalse(f.manager.releasePlacedChildSlot(f.second, a, 2));
        assertFalse(f.manager.releasePlacedChildSlot(f.first, f.slots(f.first)[0], 6));
        assertTrue(f.manager.releasePlacedChildSlot(f.first, a, 6));
        assertFalse(f.manager.releasePlacedChildSlot(f.first, a, 6));
        int reused = f.reserve(f.second, a - 1, 2);
        assertEquals(a, reused);
        assertFalse(f.manager.releasePlacedChildSlot(f.first, reused, 6));
        assertTrue(f.manager.releasePlacedChildSlot(f.second, reused, 2));
    }

    @Test void unmanagedDynamicAndSameSpawnAliasAreRejectedBeforeAllocation() {
        var f = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> f.reserve(f.first, f.first.getSlotIndex(), 6),
                "fixed prefix must already be reserved");
        f.prefix(f.first, 6);
        var dynamic = f.manager.createDynamicObject(() -> new Probe(new ObjectSpawn(0x140, 0x100, 1, 0, 0, false, 0)));
        int count = f.manager.getAllocatedSlotCount();
        assertThrows(IllegalArgumentException.class, () -> f.reserve(dynamic, dynamic.getSlotIndex(), 0));
        assertThrows(IllegalArgumentException.class, () -> f.reserve(new Probe(f.firstSpawn), 4, 6));
        var alias = f.manager.createDynamicObject(() -> new Probe(f.firstSpawn));
        int aliasedCount = f.manager.getAllocatedSlotCount();
        assertThrows(IllegalArgumentException.class, () -> f.reserve(f.first, f.first.getSlotIndex(), 6));
        assertEquals(aliasedCount, f.manager.getAllocatedSlotCount());
        assertFalse(f.manager.releasePlacedChildSlot(alias, f.slots(f.first)[0], 0));
        f.manager.removeDynamicObject(alias);
        assertEquals(count, f.manager.getAllocatedSlotCount());
        assertTrue(f.reserve(f.first, f.first.getSlotIndex(), 6) >= 0);
    }

    @Test void ordinaryParentRemovalReleasesOnlyItsReservations() {
        var f = new Fixture();
        f.prefix(f.first, 6); f.prefix(f.second, 2);
        int firstTail = f.reserve(f.first, f.first.getSlotIndex(), 6);
        int secondTail = f.reserve(f.second, f.second.getSlotIndex(), 2);
        int[] secondSlots = f.slots(f.second);
        var old = f.first;
        old.setDestroyed(true);
        f.manager.update(0, null, List.of(), 0, false);
        assertNull(f.manager.getActiveObjectForRewind(f.firstSpawn));
        assertFalse(f.manager.releasePlacedChildSlot(old, firstTail, 6));
        assertArrayEquals(secondSlots, f.slots(f.second));
        assertTrue(f.manager.releasePlacedChildSlot(f.second, secondTail, 2));
        assertEquals(firstTail, f.manager.allocateSlotAfter(firstTail - 1));
    }

    private static final class Fixture {
        final ObjectSpawn firstSpawn = new ObjectSpawn(0x100, 0x100, 1, 0, 0, false, 0, 1);
        final ObjectSpawn secondSpawn = new ObjectSpawn(0x120, 0x100, 1, 0, 0, false, 0, 2);
        final ObjectManager manager;
        Probe first, second;
        Fixture() {
            var camera = mock(Camera.class);
            when(camera.getWidth()).thenReturn((short) 320);
            when(camera.getHeight()).thenReturn((short) 224);
            var registry = new ObjectRegistry() {
                public ObjectInstance create(ObjectSpawn spawn) { return new Probe(spawn); }
                public void reportCoverage(List<ObjectSpawn> spawns) { }
                public String getPrimaryName(int id) { return "probe"; }
                public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
            };
            manager = new ObjectManager(List.of(firstSpawn, secondSpawn), registry, 0, null, null,
                    GraphicsManager.getInstance(), camera, new StubObjectServices());
            manager.reset(0);
            first = (Probe) manager.getActiveObjectForRewind(firstSpawn);
            second = (Probe) manager.getActiveObjectForRewind(secondSpawn);
            assertNotNull(first); assertNotNull(second);
        }
        int[] prefix(Probe owner, int count) { return manager.allocateChildSlotsAfter(owner.getSpawn(), count, owner.getSlotIndex()).clone(); }
        int reserve(Probe owner, int after, int prefix) { return NativeChildSlotReservations.reserveAfter(manager, owner, after, prefix); }
        ObjectManagerSnapshot snapshot() { return manager.rewindSnapshottable().capture(); }
        int[] slots(Probe owner) {
            return snapshot().childSpawns().stream().filter(e -> e.parentSpawn() == owner.getSpawn())
                    .findFirst().orElseThrow().reservedSlots().clone();
        }
    }

    public static final class Probe extends AbstractObjectInstance implements SpawnRewindRecreatable {
        public Probe(ObjectSpawn spawn) { super(spawn, "placed-reservation-probe"); }
        @Override public void update(int frame, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
