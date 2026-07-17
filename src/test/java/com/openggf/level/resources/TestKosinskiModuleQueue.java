package com.openggf.level.resources;

import com.openggf.data.Rom;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestKosinskiModuleQueue {

    @Test
    void deferredEntryRetainsOnlyRawArchiveAddressAndDestination() throws Exception {
        Rom rom = com.openggf.tests.TestEnvironment.currentRom();
        KosinskiModuleQueue queue = new KosinskiModuleQueue();
        assertTrue(queue.enqueue(rom, 0x0D6A62, 0x520 * 0x20));
        assertTrue(queue.enqueue(rom, 0x0D6D84, 0x568 * 0x20));

        KosinskiModuleQueue.ArchiveState deferred = queue.queuedArchives().get(1);
        assertEquals(0x0D6D84, deferred.archiveAddress());
        assertEquals(-1, deferred.sourceAddress(),
                "an uninitialized FIFO entry has no active payload source");
        assertEquals(0x568 * 0x20, deferred.destinationVramBytes());
        assertEquals(0, deferred.uncompressedBytes());
        assertEquals(0, deferred.totalModules());
        assertEquals(0, deferred.modulesRemaining());
        assertEquals(0, deferred.lastModuleWords());
        assertEquals(-1, deferred.decompressionEndAddress());
        assertFalse(deferred.initialized());
    }

    @Test
    void invalidDeferredHeaderFailsOnlyWhenShiftedIntoSlotZero() throws Exception {
        Rom rom = com.openggf.tests.TestEnvironment.currentRom();
        KosinskiModuleQueue queue = new KosinskiModuleQueue();
        assertTrue(queue.enqueue(rom, 0x0D6A62, 0x520 * 0x20));
        assertDoesNotThrow(() -> assertTrue(queue.enqueue(rom, 0, 0x568 * 0x20)),
                "Queue_Kos_Module must not inspect a deferred archive header");

        assertDoesNotThrow(queue::processNativeFrame,
                "the prior archive's decompression start must not inspect the deferred header");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                queue::processNativeFrame,
                "the deferred header is validated only when DMA completion shifts it into slot zero");
        assertTrue(failure.getMessage().contains("$000000"));
    }

    @Test
    void rewindPreservesRawDeferredEntryAndItsFailureTiming() throws Exception {
        Rom rom = com.openggf.tests.TestEnvironment.currentRom();
        KosinskiModuleQueue queue = new KosinskiModuleQueue();
        assertTrue(queue.enqueue(rom, 0x0D6A62, 0x520 * 0x20));
        assertTrue(queue.enqueue(rom, 0, 0x568 * 0x20));
        KosinskiModuleQueue.Snapshot rawQueue = queue.capture();

        queue.processNativeFrame();
        assertThrows(IllegalStateException.class, queue::processNativeFrame);

        queue.restore(rawQueue);
        KosinskiModuleQueue.ArchiveState restored = queue.queuedArchives().get(1);
        assertEquals(rawQueue.archives().get(1), restored);
        assertFalse(restored.initialized());
        assertEquals(0, restored.totalModules());
        assertEquals(KosinskiModuleQueue.Phase.READY_TO_START, queue.phase());

        assertDoesNotThrow(queue::processNativeFrame,
                "restored raw metadata must remain deferred while the prior archive starts");
        assertThrows(IllegalStateException.class, queue::processNativeFrame,
                "restored invalid metadata must fail on the same shift boundary");
    }

    @Test
    void processesOneNativeStartOrDmaPhasePerFrameInFifoOrder() throws Exception {
        Rom rom = com.openggf.tests.TestEnvironment.currentRom();
            KosinskiModuleQueue queue = new KosinskiModuleQueue();
            assertTrue(queue.enqueue(rom, 0x0D6A62, 0x520 * 0x20));
            assertTrue(queue.enqueue(rom, 0x0D6D84, 0x568 * 0x20));

            assertEquals(2, queue.queuedArchiveCount());
            assertEquals(1, queue.modulesLeft());
            assertEquals(KosinskiModuleQueue.Phase.READY_TO_START, queue.phase());

            queue.processNativeFrame();
            assertEquals(0x81, queue.modulesLeftRaw());
            assertEquals(KosinskiModuleQueue.Phase.DECOMPRESSION_IN_PROGRESS, queue.phase());

            queue.processNativeFrame();
            assertEquals(1, queue.queuedArchiveCount());
            assertEquals(0x0D6D84 + 2, queue.activeSourceAddress(),
                    "shifting the FIFO must immediately consume the next archive header");
            assertEquals(1, queue.modulesLeft());
            assertEquals(KosinskiModuleQueue.Phase.READY_TO_START, queue.phase(),
                    "DMA completion initializes the next FIFO archive but does not start it in the same call");

            queue.processNativeFrame();
            queue.processNativeFrame();
            assertTrue(queue.isIdle());
            assertEquals(0, queue.modulesLeftRaw());
    }

    @Test
    void snapshotRestoresQueueOrderAndInProgressPhase() throws Exception {
        Rom rom = com.openggf.tests.TestEnvironment.currentRom();
            KosinskiModuleQueue queue = new KosinskiModuleQueue();
            queue.enqueue(rom, 0x0D6A62, 0x520 * 0x20);
            queue.enqueue(rom, 0x0D6E46, 0x568 * 0x20);
            queue.processNativeFrame();
            KosinskiModuleQueue.Snapshot snapshot = queue.capture();

            queue.processNativeFrame();
            queue.processNativeFrame();
            assertNotEquals(snapshot, queue.capture());

            queue.restore(snapshot);
            assertEquals(2, queue.queuedArchiveCount());
            assertEquals(0x81, queue.modulesLeftRaw());
            assertEquals(KosinskiModuleQueue.Phase.DECOMPRESSION_IN_PROGRESS, queue.phase());
            assertEquals("kosinski-module-queue", queue.key());
    }

    @Test
    void advancesPayloadSourceRelativeToItsResidueAndDestinationByNativeDmaWords()
            throws Exception {
        Rom rom = com.openggf.tests.TestEnvironment.currentRom();
        KosinskiModuleQueue queue = new KosinskiModuleQueue();
        int destination = 0x4000;
        queue.enqueue(rom, 0x15BABE, destination); // $12A0 bytes: $1000 + $2A0

        assertEquals(0x15BABE + 2, queue.activeSourceAddress());
        assertEquals(2, queue.modulesLeft());
        queue.processNativeFrame();
        KosinskiModuleQueue.ArchiveState inProgress = queue.queuedArchives().getFirst();
        assertTrue(inProgress.decompressionEndAddress() > inProgress.sourceAddress());
        int expectedNextSource = KosinskiModuleQueue.alignToModuleResidue(
                inProgress.sourceAddress(), inProgress.decompressionEndAddress());

        queue.processNativeFrame();
        assertEquals(expectedNextSource, queue.activeSourceAddress());
        assertEquals(destination + 0x1000, queue.activeDestinationVramBytes());
        assertEquals(1, queue.modulesLeft());

        KosinskiModuleQueue.Snapshot betweenModules = queue.capture();
        queue.processNativeFrame();
        queue.processNativeFrame();
        assertTrue(queue.isIdle());
        queue.restore(betweenModules);
        assertEquals(expectedNextSource, queue.activeSourceAddress());
        assertEquals(destination + 0x1000, queue.activeDestinationVramBytes());
        assertEquals(KosinskiModuleQueue.Phase.READY_TO_START, queue.phase());
    }
}
