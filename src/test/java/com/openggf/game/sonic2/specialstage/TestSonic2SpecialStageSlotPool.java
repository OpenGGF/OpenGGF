package com.openggf.game.sonic2.specialstage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSlotPool.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

/** Short ROM-free tests for native RAM ownership, independent of complete SS routes. */
class TestSonic2SpecialStageSlotPool {
    @Test
    void dynamicAllocationUsesNativeAddressesAndHasAnExactFullRamFailure() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        for (int slot = 0; slot < 8; slot++) {
            assertEquals(slot, pool.claimFixed(slot, HUD, 0x5E, 0, slot, null).slot());
        }
        for (int slot = 8; slot < 128; slot++) {
            Sonic2SpecialStageSlotPool.Handle handle = ring(pool);
            assertEquals(slot, handle.slot());
            assertEquals(0xB000 + slot * 0x40, handle.address());
        }
        Sonic2SpecialStageSlotPool.Snapshot full = pool.snapshot();
        assertEquals(128, pool.size());
        assertFalse(pool.hasFreeDynamicSlot());
        assertEquals(0xCFC0, pool.entryAt(127).address());
        assertNull(ring(pool));
        assertEquals(full.entries(), pool.entries());
        assertArrayEquals(full.generations(), pool.snapshot().generations());
        assertThrows(UnsupportedOperationException.class, () -> pool.entries().clear());
    }

    @Test
    void lowestHoleReuseAndAfterCurrentHaveDifferentSearchBoundaries() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageSlotPool.Handle low = ring(pool);
        Sonic2SpecialStageSlotPool.Handle current = ring(pool);
        Sonic2SpecialStageSlotPool.Handle high = ring(pool);
        assertTrue(pool.release(low));
        assertTrue(pool.release(high));

        Sonic2SpecialStageSlotPool.Handle shadow = pool.allocateAfterCurrent(current,
                OBJECT_SHADOW, 0x60, 5, 0, current);
        assertEquals(10, shadow.slot());
        assertEquals(8, ring(pool).slot());
        assertEquals(List.of(8, 9, 10), pool.entries().stream().map(e -> e.slot()).toList());
        assertEquals(current, pool.entry(shadow).parent());

        Sonic2SpecialStageSlotPool.Handle last = null;
        while (pool.hasFreeDynamicSlot()) {
            last = ring(pool);
        }
        assertEquals(127, last.slot());
        assertTrue(pool.release(current));
        assertNull(pool.allocateAfterCurrent(last, OBJECT_SHADOW, 0x60, 5, 0, last));
        assertEquals(9, ring(pool).slot());
    }

    @Test
    void generationsPreventReleasedOwnerWritesFromMutatingAReplacement() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageSlotPool.Handle old = ring(pool);
        assertTrue(pool.release(old));
        Sonic2SpecialStageSlotPool.Handle replacement = ring(pool);
        assertEquals(old.slot(), replacement.slot());
        assertNotEquals(old, replacement);
        assertFalse(pool.release(old));
        pool.setPriority(old, 7);
        pool.setRoutine(old, 6);
        pool.markForDeletion(old);
        assertNull(pool.entry(old));
        assertEquals(3, pool.entry(replacement).priorityBucket());
        assertEquals(0, pool.entry(replacement).routine());
        assertFalse(pool.entry(replacement).deletionPending());
        pool.clear();
        assertFalse(pool.isLive(replacement));
        assertNotEquals(replacement, ring(pool));
    }

    @Test
    void snapshotRestoresValuesAndIdenticalForwardAllocationWithoutJavaOwnerReferences() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageSlotPool.Handle owner = ring(pool);
        Sonic2SpecialStageSlotPool.Handle shadow = pool.allocateAfterCurrent(owner,
                OBJECT_SHADOW, 0x60, 5, 1, owner);
        pool.setRoutine(owner, 6);
        pool.setRoutine(shadow, 4);
        pool.markForDeletion(shadow);
        Sonic2SpecialStageSlotPool.Snapshot snapshot = pool.snapshot();
        snapshot.generations()[8] = 999;
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());

        pool.release(owner);
        Sonic2SpecialStageSlotPool.Handle forward = pool.allocate(MESSAGE_DIGIT, 0x5A, 1, 7, null);
        List<Sonic2SpecialStageSlotPool.Entry> forwardEntries = pool.entries();
        pool.clear();
        pool.restore(snapshot);
        assertTrue(pool.isLive(owner));
        assertFalse(pool.isLive(forward));
        assertEquals(6, pool.entry(owner).routine());
        assertTrue(pool.isPendingDeletion(shadow));
        assertEquals(owner, pool.entry(shadow).parent());
        pool.release(owner);
        assertEquals(forward, pool.allocate(MESSAGE_DIGIT, 0x5A, 1, 7, null));
        assertEquals(forwardEntries, pool.entries());
    }

    @Test
    void ringShadowIsAnAfterCurrentOwnerAndDeletionWaitsForItsOwnScanTurn() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageSlotPool.Handle lowerHole = ring(pool);
        Sonic2SpecialStageRing ring = attachedRing(pool, 20, 0x40);
        pool.release(lowerHole);
        ring.initializeNativeOwner();
        Sonic2SpecialStageSlotPool.Handle shadow = ring.getNativeShadowHandle();
        assertEquals(9, ring.getNativeSlot());
        assertEquals(10, shadow.slot());
        assertEquals(0xB240, ring.getNativeAddress());
        assertEquals(OBJECT_SHADOW, pool.entry(shadow).kind());
        assertEquals(0x60, pool.entry(shadow).objectId());
        assertEquals(4, pool.entry(shadow).routine());
        assertEquals(5, pool.entry(shadow).priorityBucket());
        assertEquals(0, pool.entry(shadow).component());
        assertTrue(ring.collect());
        ring.updateNativeTopology();
        assertNull(ring.getNativeShadowHandle());
        assertTrue(pool.isLive(shadow));
        assertTrue(pool.isPendingDeletion(shadow));
        assertEquals(8, ring(pool).slot());
        // The manager performs this release when its scan reaches the child.
        pool.release(shadow);
        assertEquals(10, ring(pool).slot());
    }

    @Test
    void negativeAngleAndFullPoolShadowFailureNeverCreateOrRetryAChild() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageRing negative = attachedRing(pool, 20, 0x80);
        negative.initializeNativeOwner();
        assertNull(negative.getNativeShadowHandle());
        Sonic2SpecialStageRing noCapacity = attachedRing(pool, 20, 0x40);
        Sonic2SpecialStageSlotPool.Handle last = null;
        while (pool.hasFreeDynamicSlot()) {
            last = ring(pool);
        }
        noCapacity.initializeNativeOwner();
        assertNull(noCapacity.getNativeShadowHandle());
        pool.release(last);
        noCapacity.initializeNativeOwner();
        assertNull(noCapacity.getNativeShadowHandle());
        assertEquals(119, pool.size());
    }

    @Test
    void objectRewindRetainsNativeOwnerShadowAndInitializationWithoutDuplicateAllocation() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageRing original = attachedRing(pool, 12, 0x20);
        original.initializeNativeOwner();
        original.collect();
        original.updateNativeTopology();
        original.update(0, false, 12, false);
        original.updateNativeTopology();
        Sonic2SpecialStageSnapshot.ObjectSnapshot objectSnapshot = original.captureRewindSnapshot();
        Sonic2SpecialStageSlotPool.Snapshot poolSnapshot = pool.snapshot();

        List<Sonic2SpecialStageSnapshot.ObjectSnapshot> forward = new ArrayList<>();
        for (int frame = 0; frame < 6; frame++) {
            original.update(0, false, 12, false);
            original.updateNativeTopology();
            forward.add(original.captureRewindSnapshot());
        }
        pool.restore(poolSnapshot);
        Sonic2SpecialStageRing restored = new Sonic2SpecialStageRing();
        restored.restoreRewindSnapshot(objectSnapshot);
        restored.bindNativePoolAfterRestore(pool);
        restored.initializeNativeOwner();
        assertEquals(poolSnapshot.entries(), pool.entries());
        assertEquals(original.getNativeHandle(), restored.getNativeHandle());
        for (int frame = 0; frame < 6; frame++) {
            restored.update(0, false, 12, false);
            restored.updateNativeTopology();
            assertEquals(forward.get(frame), restored.captureRewindSnapshot());
        }
    }

    @Test
    void ringAndBombEffectsLoopAndReleaseOnlyAtFullFixedPointDepthExhaustion() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageRing ring = attachedRing(pool, 20, 0x80);
        Sonic2SpecialStageBomb bomb = new Sonic2SpecialStageBomb();
        bomb.initialize(20, 0x80);
        bomb.attachNativeSlot(pool, pool.allocate(BOMB, 0x61, 3, 0, null));
        ring.initializeNativeOwner();
        bomb.initializeNativeOwner();
        ring.collect();
        bomb.explode();
        for (int frame = 0; frame < 12; frame++) {
            ring.update(0, false, 12, false);
            bomb.update(0, false, 12, false);
            ring.updateNativeTopology();
            bomb.updateNativeTopology();
        }
        assertFalse(ring.shouldRemove());
        assertFalse(bomb.shouldRemove());
        assertEquals(2, ring.getAnimFrame());
        assertEquals(0, bomb.getAnimFrame());
        assertEquals(6, pool.entry(ring.getNativeHandle()).routine());
        assertEquals(6, pool.entry(bomb.getNativeHandle()).routine());
        assertEquals(3, ring.getNativePriorityBucket());
        for (int frame = 0; frame < 20; frame++) {
            ring.update(0, false, 12, false);
            bomb.update(0, false, 12, false);
            ring.updateNativeTopology();
            bomb.updateNativeTopology();
        }
        assertTrue(ring.shouldRemove());
        assertTrue(bomb.shouldRemove());
        assertEquals(0, pool.size());
    }

    @Test
    void effectPriorityUsesDepthBeforeMovementAndPositiveFractionKeepsItsOwner() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageRing ring = attachedRing(pool, 5, 0x80);
        ring.initializeNativeOwner();
        ring.collect();
        ring.depthFixed = 0x48000;
        ring.update(0, false, 12, false);
        assertEquals(3, ring.getNativePriorityBucket());
        ring.update(0, false, 12, false);
        assertEquals(1, ring.getNativePriorityBucket());
        ring.depthFixed = 0x10000;
        ring.update(0, false, 12, false);
        ring.updateNativeTopology();
        assertEquals(0, ring.getDepth());
        assertFalse(ring.shouldRemove());
        assertTrue(pool.isLive(ring.getNativeHandle()));
        ring.update(0, false, 12, false);
        ring.updateNativeTopology();
        assertTrue(ring.shouldRemove());
        assertFalse(pool.isLive(ring.getNativeHandle()));
    }

    @Test
    void emeraldAllocatesItsShadowOnTheSixtiethInitializerTurnAndFallsThrough() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageEmerald emerald = new Sonic2SpecialStageEmerald();
        emerald.initialize(0, 0);
        emerald.attachNativeSlot(pool, pool.allocate(EMERALD, 0x59, 0, 0, null));
        for (int frame = 0; frame < 59; frame++) {
            emerald.initializeNativeOwner();
            emerald.update(0, false, 12, false);
            emerald.updateNativeTopology();
        }
        assertEquals(1, pool.size());
        assertNull(emerald.getNativeShadowHandle());
        assertEquals(0, pool.entry(emerald.getNativeHandle()).routine());
        assertEquals(54, emerald.getDepth());
        Sonic2SpecialStageSnapshot.ObjectSnapshot before = emerald.captureRewindSnapshot();
        Sonic2SpecialStageSlotPool.Snapshot poolBefore = pool.snapshot();

        emerald.update(0, false, 12, false);
        emerald.updateNativeTopology();
        assertEquals(53, emerald.getDepth());
        assertEquals(4, emerald.getNativePriorityBucket());
        assertEquals(9, emerald.getNativeShadowHandle().slot());
        assertEquals(0x59, pool.entry(emerald.getNativeShadowHandle()).objectId());
        assertEquals(2, pool.entry(emerald.getNativeHandle()).routine());
        List<Sonic2SpecialStageSlotPool.Entry> forwardEntries = pool.entries();
        Sonic2SpecialStageSnapshot.ObjectSnapshot forward = emerald.captureRewindSnapshot();

        pool.restore(poolBefore);
        Sonic2SpecialStageEmerald restored = new Sonic2SpecialStageEmerald();
        restored.restoreRewindSnapshot(before, null);
        restored.bindNativePoolAfterRestore(pool);
        restored.update(0, false, 12, false);
        restored.updateNativeTopology();
        assertEquals(forward, restored.captureRewindSnapshot());
        assertEquals(forwardEntries, pool.entries());
    }

    @Test
    void nativeShadowGeometryUsesWidenedEllipseAndSurvivesRewind() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageRing ring = attachedRing(pool, 12, 0x20);
        ring.initializeNativeOwner();
        assertEquals(28, ring.getNativeShadowMappingFrame());
        // Deliberately use the signed-byte path used by getEntry(). Native
        // ext.w yields -16 even if PerspectiveEntry normalized it twice.
        Sonic2PerspectiveData perspective = perspective(127, -16, 83, 61);
        ring.updateScreenPosition(perspective, 0, false);
        assertEquals(199, ring.getNativeShadowScreenX());
        assertEquals(37, ring.getNativeShadowScreenY());
        assertEquals(19, ring.getNativeShadowMappingFrame());
        assertTrue(ring.isNativeShadowXFlip());
        assertFalse(ring.isNativeShadowYFlip());
        assertTrue(ring.isNativeShadowVisible());
        Sonic2SpecialStageSnapshot.ObjectSnapshot snapshot = ring.captureRewindSnapshot();
        Sonic2SpecialStageRing restored = new Sonic2SpecialStageRing();
        restored.restoreRewindSnapshot(snapshot);
        restored.bindNativePoolAfterRestore(pool);
        assertEquals(199, restored.getNativeShadowScreenX());
        assertEquals(37, restored.getNativeShadowScreenY());
        assertEquals(19, restored.getNativeShadowMappingFrame());
        restored.updateScreenPosition(perspective, 0, true);
        assertEquals(201, restored.getNativeShadowScreenX());
        assertEquals(37, restored.getNativeShadowScreenY());
        restored.collect();
        restored.updateNativeTopology();
        assertFalse(restored.isNativeShadowVisible());
    }

    @Test
    void emeraldShadowUsesOriginalRadiiWhileBodyUsesThreeQuarters() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageEmerald emerald = new Sonic2SpecialStageEmerald();
        emerald.initialize(0, 0);
        emerald.attachNativeSlot(pool, pool.allocate(EMERALD, 0x59, 0, 0, null));
        for (int frame = 0; frame < 60; frame++) {
            emerald.update(0, false, 12, false);
        }
        emerald.updateScreenPosition(perspective(127, 88, 83, 61), 0, false);
        assertEquals(127, emerald.getScreenX());
        assertEquals(133, emerald.getScreenY());
        assertEquals(127, emerald.getNativeShadowScreenX());
        assertEquals(164, emerald.getNativeShadowScreenY());
        assertFalse(emerald.isNativeShadowXFlip());
        assertTrue(emerald.isNativeShadowVisible());
        assertEquals(27, emerald.getNativeShadowMappingFrame());
    }

    private static Sonic2PerspectiveData perspective(int x, int y, int xRadius, int yRadius) {
        return new Sonic2PerspectiveData() {
            @Override
            public PerspectiveEntry getEntry(int frame, int depth) {
                return new PerspectiveEntry(x, y, xRadius, yRadius, 0, 0);
            }
        };
    }

    private static Sonic2SpecialStageSlotPool.Handle ring(Sonic2SpecialStageSlotPool pool) {
        return pool.allocate(RING, 0x60, 3, 0, null);
    }

    private static Sonic2SpecialStageRing attachedRing(Sonic2SpecialStageSlotPool pool,
                                                       int depth, int angle) {
        Sonic2SpecialStageRing ring = new Sonic2SpecialStageRing();
        ring.initialize(depth, angle);
        ring.attachNativeSlot(pool, ring(pool));
        return ring;
    }
}
