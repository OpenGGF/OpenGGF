package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestNativeObjectQueries {
    @Test void removalIsVisibleInsideItsNotificationBeforeTheManagerPassTail() {
        var services = new StubObjectServices().withIsolatedObjectManager();
        var manager = services.objectManager();
        var old = new Occupant(1, 1) {
            @Override protected void onRemovedFromObjectManager() {
                assertNull(NativeObjectQueries.liveAtSlot(manager, 40, Occupant.class));
                assertNull(NativeObjectQueries.firstLive(manager, Occupant.class));
            }
        };
        manager.addDynamicObjectAtSlot(old, 40);
        assertSame(old, NativeObjectQueries.liveAtSlot(manager, 40, Occupant.class));
        manager.removeDynamicObject(old);
    }

    @Test void midPassChangesUseOwnedSlotsEvenWithStaleExecutionAliases() throws Exception {
        var services = new StubObjectServices().withIsolatedObjectManager();
        var manager = services.objectManager();
        var owner = new Occupant(1, 1);
        manager.addDynamicObjectAtSlot(owner, 40);
        var execField = ObjectManager.class.getDeclaredField("execOrder"); execField.setAccessible(true);
        var exec = (ObjectInstance[]) execField.get(manager);
        exec[15] = owner; // A consolidated execution alias is never the owned SST.
        var updating = ObjectManager.class.getDeclaredField("updating"); updating.setAccessible(true);
        var cursor = ObjectManager.class.getDeclaredField("currentExecSlot"); cursor.setAccessible(true);
        updating.setBoolean(manager, true); cursor.setInt(manager, 10);
        assertSame(owner, NativeObjectQueries.liveAtSlot(manager, 40, Occupant.class));
        assertNull(NativeObjectQueries.liveAtSlot(manager, 15, Occupant.class));
        var child = new Occupant(2, 2);
        manager.addDynamicObjectAtSlot(child, 41);
        assertSame(child, NativeObjectQueries.liveAtSlot(manager, 41, Occupant.class));
        manager.removeDynamicObject(child);
        var replacement = new Occupant(3, 3);
        manager.addDynamicObjectAtSlot(replacement, 41);
        assertSame(replacement, NativeObjectQueries.liveAtSlot(manager, 41, Occupant.class));
        owner.setSlotIndex(42);
        assertSame(owner, NativeObjectQueries.liveAtSlot(manager, 42, Occupant.class));
        assertNull(NativeObjectQueries.liveAtSlot(manager, 40, Occupant.class));
        assertSame(owner, exec[15], "the stale execution alias must not affect dereferencing");
        updating.setBoolean(manager, false); cursor.setInt(manager, -1);
    }

    @Test void uncheckedOverwriteInvalidatesIndexWithoutRequiringOldOwnerDestruction() {
        var services = new StubObjectServices().withIsolatedObjectManager();
        var manager = services.objectManager();
        int last = manager.getLastDynamicSlotExclusive() - 1;
        var old = new Occupant(1, 1);
        manager.addDynamicObjectAtSlot(old, last);
        manager.reserveAllButNFreeSlots(0);
        assertSame(old, NativeObjectQueries.liveAtSlot(manager, last, Occupant.class));
        var replacement = NativeSstSlotAccess.createUnchecked(manager, residue -> new Occupant(2, 2));
        assertFalse(old.isDestroyed(), "a partial code overwrite does not run DeleteObject");
        assertSame(replacement, NativeObjectQueries.liveAtSlot(manager, last, Occupant.class));
        assertSame(replacement, NativeObjectQueries.firstLive(manager, Occupant.class));
    }

    @Test void lowestLiveSelectionTracksNewEarlierSlotsDeletionAndSlotChanges() {
        var services = new StubObjectServices().withIsolatedObjectManager();
        var manager = services.objectManager();
        var later = new Occupant(1, 1);
        manager.addDynamicObjectAtSlot(later, 40);
        assertSame(later, NativeObjectQueries.firstLive(manager, Occupant.class));
        var earlier = new Occupant(2, 2);
        manager.addDynamicObjectAtSlot(earlier, 20);
        assertSame(earlier, NativeObjectQueries.firstLive(manager, Occupant.class));
        earlier.setDestroyed(true); // Still in the population until the deletion pass.
        assertSame(later, NativeObjectQueries.firstLive(manager, Occupant.class));
        assertNull(NativeObjectQueries.liveAtSlot(manager, 20, Occupant.class));
        earlier.setDestroyed(false);
        later.setSlotIndex(10);
        assertSame(later, NativeObjectQueries.firstLive(manager, Occupant.class));
        assertNull(NativeObjectQueries.liveAtSlot(manager, 40, Occupant.class));
        assertSame(later, NativeObjectQueries.liveAtSlot(manager, 10, Occupant.class));
    }

    @Test void slotDereferenceFollowsTypedReplacementAndRestoredGeneration() {
        var services = new StubObjectServices().withIsolatedObjectManager();
        var manager = services.objectManager();
        var original = new Occupant(1, 1);
        manager.addDynamicObjectAtSlot(original, 20);
        assertSame(original, NativeObjectQueries.liveAtSlot(manager, 20, Occupant.class));
        var registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        var snapshot = registry.capture();
        manager.removeDynamicObject(original);
        var otherType = new OtherOccupant();
        manager.addDynamicObjectAtSlot(otherType, 20);
        assertNull(NativeObjectQueries.liveAtSlot(manager, 20, Occupant.class));
        assertSame(otherType, NativeObjectQueries.liveAtSlot(manager, 20, OtherOccupant.class));
        manager.removeDynamicObject(otherType);
        var replacement = new Occupant(3, 3);
        manager.addDynamicObjectAtSlot(replacement, 20);
        assertSame(replacement, NativeObjectQueries.liveAtSlot(manager, 20, Occupant.class));
        registry.restore(snapshot);
        var restored = NativeObjectQueries.liveAtSlot(manager, 20, Occupant.class);
        assertNotNull(restored);
        assertNotSame(replacement, restored);
        assertEquals(1, restored.getX());
        assertSame(restored, NativeObjectQueries.firstLive(manager, Occupant.class));
    }

    @Test void repeatedQueriesVisitOnlyTypeCandidatesOrSlotOccupants() {
        var services = new StubObjectServices().withIsolatedObjectManager();
        var manager = services.objectManager();
        var target = new Occupant(1, 1);
        manager.addDynamicObjectAtSlot(target, 40);
        var noise = new OtherOccupant();
        manager.addDynamicObjectAtSlot(noise, 30);
        assertSame(target, NativeObjectQueries.firstLive(manager, Occupant.class));
        assertSame(target, NativeObjectQueries.liveAtSlot(manager, 40, Occupant.class));
        noise.slotReads = 0;
        for (int i = 0; i < 23; i++) {
            assertSame(target, NativeObjectQueries.firstLive(manager, Occupant.class));
            assertSame(target, NativeObjectQueries.liveAtSlot(manager, 40, Occupant.class));
        }
        assertEquals(0, noise.slotReads, "unchanged population is indexed once rather than rescanned per child");
    }

    @Test void typeOnlyQueriesAvoidUnrelatedSlotCallbacksDuringMembershipChurn() throws Exception {
        var manager = new StubObjectServices().withIsolatedObjectManager().objectManager();
        var target = new Occupant(1, 1);
        manager.addDynamicObjectAtSlot(target, 40);
        var noise = new OtherOccupant();
        manager.addDynamicObjectAtSlot(noise, 30);
        var dirty = ObjectManager.class.getDeclaredField("activeObjectsCacheDirty");
        dirty.setAccessible(true);
        for (int i = 0; i < 19; i++) {
            var transientNoise = new OtherOccupant();
            manager.addDynamicObjectAtSlot(transientNoise, 31);
            noise.slotReads = transientNoise.slotReads = 0;
            assertSame(target, NativeObjectQueries.firstLive(manager, Occupant.class));
            assertEquals(0, noise.slotReads);
            assertEquals(0, transientNoise.slotReads);
            assertTrue(dirty.getBoolean(manager), "native lookup must not service the general caches");
            manager.removeDynamicObject(transientNoise);
            noise.slotReads = 0;
            assertSame(target, NativeObjectQueries.firstLive(manager, Occupant.class));
            assertEquals(0, noise.slotReads);
        }
    }

    @Test void slotStorageIsReusedAcrossBoundedMembershipChurn() throws Exception {
        var manager = new StubObjectServices().withIsolatedObjectManager().objectManager();
        var target = new Occupant(1, 1);
        manager.addDynamicObjectAtSlot(target, 40);
        var other = new OtherOccupant();
        manager.addDynamicObjectAtSlot(other, 30);
        assertSame(target, NativeObjectQueries.atSlot(manager, 40, Occupant.class));
        var index = manager.nativeObjectIndex();
        var storage = NativeObjectIndex.class.getDeclaredField("slots");
        storage.setAccessible(true);
        Object preparedStorage = storage.get(index);
        for (int i = 0; i < 19; i++) {
            manager.removeDynamicObject(other);
            other = new OtherOccupant();
            manager.addDynamicObjectAtSlot(other, 30);
            assertSame(target, NativeObjectQueries.atSlot(manager, 40, Occupant.class));
            assertSame(other, NativeObjectQueries.liveAtSlot(manager, 30, OtherOccupant.class));
            assertSame(preparedStorage, storage.get(index), "same-sized rebuilds reuse primitive storage");
        }
    }

    @Test void resetAndSpawnResyncReleaseRetainedOwnersWithoutAnotherQuery() throws Exception {
        var manager = new StubObjectServices().withIsolatedObjectManager().objectManager();
        for (boolean resync : new boolean[]{false, true}) {
            var target = new Occupant(1, 1);
            manager.addDynamicObjectAtSlot(target, 40);
            assertSame(target, NativeObjectQueries.firstLive(manager, Occupant.class));
            assertSame(target, NativeObjectQueries.atSlot(manager, 40, Occupant.class));
            var index = manager.nativeObjectIndex();
            if (resync) manager.resyncSpawnList(java.util.List.of());
            else manager.reset(0);
            for (String name : new String[]{"population", "typed"}) {
                var field = NativeObjectIndex.class.getDeclaredField(name);
                field.setAccessible(true);
                assertTrue(((java.util.List<?>) field.get(index)).isEmpty(), name + " releases owners");
            }
            for (String name : new String[]{"type", "callbacks"}) {
                var field = NativeObjectIndex.class.getDeclaredField(name);
                field.setAccessible(true);
                assertNull(field.get(index), name + " is released without another query");
            }
        }
    }

    @Test void duplicateOwnedSlotsKeepStableSourceOrderAndLiveFiltering() {
        var index = new NativeObjectIndex();
        var first = new Occupant(1, 1);
        var second = new Occupant(2, 2);
        first.setSlotIndex(40);
        second.setSlotIndex(40);
        index.prepare(java.util.List.of(first), java.util.List.of(second), new ObjectCallbackRouter(null));
        assertSame(first, index.firstLive(Occupant.class));
        assertSame(first, index.atSlot(40, Occupant.class));
        first.setDestroyed(true);
        assertSame(first, index.atSlot(40, Occupant.class));
        assertSame(second, index.liveAtSlot(40, Occupant.class));
        assertSame(second, index.firstLive(Occupant.class));
    }

    @Test void slotPreparationUsesSnapshotWhenRoutedSlotCallbackRetiresAnotherObject() {
        var manager = new StubObjectServices().withIsolatedObjectManager().objectManager();
        var retired = new OtherOccupant();
        var target = new Occupant(1, 1) {
            boolean retire;
            @Override public int getSlotIndex() {
                if (retire) {
                    retire = false;
                    manager.removeDynamicObject(retired);
                }
                return super.getSlotIndex();
            }
            @Override public void update(int vIntRunCount, PlayableEntity player) { retire = true; }
        };
        manager.addDynamicObjectAtSlot(target, 40);
        manager.addDynamicObjectAtSlot(retired, 30);
        target.update(0, null);
        assertSame(target, NativeObjectQueries.atSlot(manager, 40, Occupant.class));
        assertNull(NativeObjectQueries.atSlot(manager, 30, OtherOccupant.class),
                "callback retirement invalidates the next query without mutating its snapshot walk");
    }

    public static class Occupant extends AbstractObjectInstance implements SpawnCoordinateRewindRecreatable, NativeSstStateSource {
        public Occupant(int x, int y) { super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "occupant"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public boolean isPersistent() { return true; }
        @Override public NativeSstState nativeSstState() { return NativeSstState.CLEARED; }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
    }
    static final class OtherOccupant extends AbstractObjectInstance {
        int slotReads;
        OtherOccupant() { super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "other"); }
        @Override public int getSlotIndex() { slotReads++; return super.getSlotIndex(); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
    }
}
