package com.openggf.level.resources;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure queue/DMA tests: intentionally always-on and ROM-independent. */
class TestKosinskiModuleQueueDmaJournal {
    @Test void unboundHeadlessQueueCompletesBookkeepingWithoutInventingPhysicalReconciliation() {
        byte[] payload={1,2,3,4};
        KosinskiModuleQueue.ArchiveState active=new KosinskiModuleQueue.ArchiveState(
                0x100,0x102,6,4,1,1,2,0x110,true);
        KosinskiModuleQueue queue=new KosinskiModuleQueue();
        queue.restore(new KosinskiModuleQueue.Snapshot(List.of(active),
                KosinskiModuleQueue.Phase.DECOMPRESSION_IN_PROGRESS,payload,List.of(),List.of()));

        queue.processNativeFrame();

        assertTrue(queue.isIdle(),"an unbound headless queue has no physical sink to wait for");
        assertNull(queue.capture().pendingModuleData());
        assertTrue(queue.capture().appliedWrites().isEmpty());
        assertTrue(queue.applyImmediateDma(0x20,new byte[] {9,8}),
                "unbound immediate DMA is successful bookkeeping, not transient sink loss");
        queue.restore(queue.capture());
        queue.processNativeFrame();
        assertTrue(queue.isIdle(),"logical restore must not create an unflushable physical reconcile");
    }

    @Test void snapshotsUseDeterministicByteValueEqualityInsteadOfArrayIdentity() {
        KosinskiModuleQueue.ArchiveState active=new KosinskiModuleQueue.ArchiveState(
                0x100,0x102,6,4,1,1,2,0x110,true);
        KosinskiModuleQueue.Snapshot first=new KosinskiModuleQueue.Snapshot(List.of(active),
                KosinskiModuleQueue.Phase.DECOMPRESSION_IN_PROGRESS,new byte[] {1,2,3,4},
                List.of(new KosinskiModuleQueue.DmaWriteState(6,new byte[] {9,8})),
                List.of(new KosinskiModuleQueue.DmaWriteState(6,new byte[] {1,2})));
        KosinskiModuleQueue.Snapshot equalCopy=new KosinskiModuleQueue.Snapshot(first.archives(),
                first.phase(),first.pendingModuleData(),first.baselines(),first.appliedWrites());

        assertEquals(first,equalCopy);
        assertEquals(first.hashCode(),equalCopy.hashCode());
    }

    @Test void rewindJournalIsByteAccurateAcrossEveryOverlapShapeAndAliases() {
        for (boolean reverse : new boolean[] {false, true}) {
            byte[] memory = sequence(12);
            byte[] pristine = memory.clone();
            KosinskiModuleQueue queue = new KosinskiModuleQueue();
            MutableTarget target = new MutableTarget(memory);
            queue.bindDmaTarget(target);
            KosinskiModuleQueue.Snapshot before = queue.capture();
            byte[] a = {1, 2, 3, 4}, b = {9, 8, 7, 6};
            if (reverse) { queue.applyImmediateDma(2, b); queue.applyImmediateDma(0, a); }
            else { queue.applyImmediateDma(0, a); queue.applyImmediateDma(2, b); }
            byte[] expected = memory.clone();
            KosinskiModuleQueue.Snapshot overlap = queue.capture();
            a[0] = 99;b[0] = 99;
            queue.applyImmediateDma(0, new byte[] {5, 5});
            queue.applyImmediateDma(0, new byte[] {6, 6, 6, 6, 6, 6});
            KosinskiModuleQueue.Snapshot longer = queue.capture();
            byte[] expectedLonger = memory.clone();
            queue.restore(overlap);assertArrayEquals(expected, memory);
            queue.restore(before);assertArrayEquals(pristine, memory);
            queue.restore(longer);assertArrayEquals(expectedLonger, memory);
            queue.restore(overlap);assertArrayEquals(expected, memory);
            byte[] leaked = overlap.appliedWrites().getFirst().data();leaked[0] ^= 0x7F;
            queue.restore(overlap);assertArrayEquals(expected, memory);
        }
    }

    @Test void completedDmaRetainsPayloadAndNativeQueueStateAcrossLevelGapThenAppliesToNewLevel() {
        byte[] payload = {1, 2, 3, 4};
        KosinskiModuleQueue.ArchiveState active = new KosinskiModuleQueue.ArchiveState(
                0x100, 0x102, 6, 4, 1, 1, 2, 0x110, true);
        KosinskiModuleQueue queue = new KosinskiModuleQueue();
        MutableTarget target = new MutableTarget(null);
        queue.bindDmaTarget(target);
        queue.restore(new KosinskiModuleQueue.Snapshot(List.of(active),
                KosinskiModuleQueue.Phase.DECOMPRESSION_IN_PROGRESS, payload, List.of(), List.of()));

        queue.processNativeFrame();
        assertEquals(KosinskiModuleQueue.Phase.DECOMPRESSION_IN_PROGRESS, queue.phase());
        assertEquals(1, queue.modulesLeft());
        assertArrayEquals(payload, queue.capture().pendingModuleData());
        byte[] newLevel = sequence(16);target.memory = newLevel;
        queue.processNativeFrame();
        assertArrayEquals(payload, Arrays.copyOfRange(newLevel, 6, 10));
        assertTrue(queue.isIdle());
        assertEquals(1, target.applyCount);
    }

    @Test void rewindRestoreDefersPhysicalReconciliationUntilTargetReturnsAndAppliesOnce() {
        byte[] memory = sequence(12);
        KosinskiModuleQueue queue = new KosinskiModuleQueue();
        MutableTarget target = new MutableTarget(memory);queue.bindDmaTarget(target);
        queue.applyImmediateDma(2, new byte[] {1, 1, 1, 1});
        KosinskiModuleQueue.Snapshot first = queue.capture();
        queue.applyImmediateDma(2, new byte[] {2, 2, 2, 2});
        target.memory = null;int appliesBefore = target.applyCount;
        queue.restore(first);
        assertEquals(appliesBefore, target.applyCount);
        target.memory = memory;
        queue.processNativeFrame();
        assertArrayEquals(new byte[] {1,1,1,1}, Arrays.copyOfRange(memory,2,6));
        assertEquals(appliesBefore + 2, target.applyCount,
                "reconciliation writes baseline then captured image exactly once");
        queue.processNativeFrame();assertEquals(appliesBefore + 2, target.applyCount);
    }

    @Test void availableRestoreOfThePendingSnapshotCoalescesToOneBaselineAndImagePair() {
        byte[] memory = sequence(12), pristine = memory.clone();
        KosinskiModuleQueue queue = new KosinskiModuleQueue();
        MutableTarget target = new MutableTarget(memory);queue.bindDmaTarget(target);
        queue.applyImmediateDma(2, new byte[] {1,1,1,1});
        KosinskiModuleQueue.Snapshot snapshotA = queue.capture();
        queue.applyImmediateDma(2, new byte[] {2,2,2,2});

        target.memory = null;
        queue.restore(snapshotA);
        target.memory = memory;target.writes.clear();
        queue.restore(snapshotA);

        assertEquals(2,target.writes.size(),"pending A must not flush before requested A is restored");
        assertArrayEquals(Arrays.copyOfRange(pristine,2,6),target.writes.get(0).data());
        assertArrayEquals(new byte[] {1,1,1,1},target.writes.get(1).data());
        assertArrayEquals(new byte[] {1,1,1,1},Arrays.copyOfRange(memory,2,6));
        queue.processNativeFrame();
        assertEquals(2,target.writes.size(),"native processing must see reconciliation as already complete");

        queue.applyImmediateDma(2,new byte[] {3,3,3,3});
        target.writes.clear();queue.restore(snapshotA);
        assertEquals(2,target.writes.size());
        assertArrayEquals(new byte[] {1,1,1,1},Arrays.copyOfRange(memory,2,6));
    }

    @Test void availableRestoreOfDifferentSnapshotSupersedesPendingImageWithoutPublishingIt() {
        byte[] memory = sequence(12), pristine = memory.clone();
        KosinskiModuleQueue queue = new KosinskiModuleQueue();
        MutableTarget target = new MutableTarget(memory);queue.bindDmaTarget(target);
        queue.applyImmediateDma(2,new byte[] {1,1,1,1});
        KosinskiModuleQueue.Snapshot snapshotA=queue.capture();
        queue.applyImmediateDma(2,new byte[] {2,2,2,2});
        KosinskiModuleQueue.Snapshot snapshotB=queue.capture();
        queue.applyImmediateDma(2,new byte[] {3,3,3,3});

        target.memory=null;queue.restore(snapshotA);
        target.memory=memory;target.writes.clear();queue.restore(snapshotB);

        assertEquals(2,target.writes.size(),"pending A must be superseded, not physically published");
        assertArrayEquals(Arrays.copyOfRange(pristine,2,6),target.writes.get(0).data());
        assertArrayEquals(new byte[] {2,2,2,2},target.writes.get(1).data());
        assertTrue(target.writes.stream().noneMatch(write->Arrays.equals(
                write.data(),new byte[] {1,1,1,1})),"stale pending A image must never reach the target");
        assertArrayEquals(new byte[] {2,2,2,2},Arrays.copyOfRange(memory,2,6));
        assertWriteStatesEqual(snapshotB.baselines(),queue.capture().baselines());
        assertWriteStatesEqual(snapshotB.appliedWrites(),queue.capture().appliedWrites());
        queue.processNativeFrame();assertEquals(2,target.writes.size());

        queue.applyImmediateDma(2,new byte[] {4,4,4,4});
        target.writes.clear();queue.restore(snapshotA);
        assertEquals(2,target.writes.size());
        assertArrayEquals(new byte[] {1,1,1,1},Arrays.copyOfRange(memory,2,6));
    }

    @Test void snapshotKeepsLaterWritesFromOtherPatternOwners() {
        byte[] memory = sequence(16);
        var target = new MutableTarget(memory);
        var queue = new KosinskiModuleQueue();
        queue.bindDmaTarget(target);
        queue.applyImmediateDma(4, new byte[] {1, 2, 3, 4});
        // A later Nemesis PLC or animation owner replaces part of the same VRAM range.
        memory[5] = 9;
        memory[6] = 10;
        var expected = memory.clone();
        var snapshot = queue.capture();
        queue.applyImmediateDma(4, new byte[] {11, 12, 13, 14});
        queue.restore(snapshot);
        assertArrayEquals(expected, memory);
    }

    @Test void snapshotUsesReplacementLevelPatternMemoryWithoutReapplyingThePreviousActsArt() {
        var target = new MutableTarget(sequence(16));
        var queue = new KosinskiModuleQueue();
        queue.bindDmaTarget(target);
        queue.applyImmediateDma(4, new byte[] {1, 2, 3, 4});
        // A seamless target reload installs a new pattern owner behind the same capability.
        target.memory = new byte[16];
        Arrays.fill(target.memory, (byte) 7);
        var expected = target.memory.clone();
        var snapshot = queue.capture();
        queue.restore(snapshot);
        assertArrayEquals(expected, target.memory);
        assertEquals(snapshot, queue.capture());
    }

    @Test void captureDuringDeferredRestoreKeepsTheRequestedImageUntilItIsPublished() {
        byte[] memory = sequence(16);
        var target = new MutableTarget(memory);
        var queue = new KosinskiModuleQueue();
        queue.bindDmaTarget(target);
        queue.applyImmediateDma(4, new byte[] {1, 2, 3, 4});
        var requested = queue.capture();
        queue.applyImmediateDma(4, new byte[] {5, 6, 7, 8});
        target.memory = null;
        queue.restore(requested);
        assertEquals(requested, queue.capture());
        target.memory = memory;
        assertEquals(requested, queue.capture(), "physical memory still contains the later write");
        queue.processNativeFrame();
        assertArrayEquals(new byte[] {1, 2, 3, 4}, Arrays.copyOfRange(memory, 4, 8));
        assertEquals(requested, queue.capture());
    }

    private static byte[] sequence(int length) {
        byte[] result=new byte[length];for(int i=0;i<length;i++)result[i]=(byte)(0x40+i);return result;
    }

    private static void assertWriteStatesEqual(List<KosinskiModuleQueue.DmaWriteState> expected,
                                               List<KosinskiModuleQueue.DmaWriteState> actual) {
        assertEquals(expected.size(),actual.size());
        for(int i=0;i<expected.size();i++){
            assertEquals(expected.get(i).destinationVramBytes(),actual.get(i).destinationVramBytes());
            assertArrayEquals(expected.get(i).data(),actual.get(i).data());
        }
    }

    private static final class MutableTarget implements KosinskiModuleQueue.DmaTarget {
        byte[] memory;int applyCount;
        final List<KosinskiModuleQueue.DmaWriteState> writes=new java.util.ArrayList<>();
        MutableTarget(byte[] memory){this.memory=memory;}
        @Override public boolean isAvailable(){return memory!=null;}
        @Override public byte[] read(int destination,int length){return Arrays.copyOfRange(memory,destination,destination+length);}
        @Override public void apply(KosinskiModuleQueue.DmaChunk chunk){byte[] data=chunk.data();System.arraycopy(data,0,memory,chunk.destinationVramBytes(),data.length);applyCount++;writes.add(new KosinskiModuleQueue.DmaWriteState(chunk.destinationVramBytes(),data));}
    }
}
