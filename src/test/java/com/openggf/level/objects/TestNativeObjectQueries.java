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
        noise.slotReads = 0;
        for (int i = 0; i < 23; i++) {
            assertSame(target, NativeObjectQueries.firstLive(manager, Occupant.class));
            assertSame(target, NativeObjectQueries.liveAtSlot(manager, 40, Occupant.class));
        }
        assertEquals(0, noise.slotReads, "unchanged population is indexed once rather than rescanned per child");
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
