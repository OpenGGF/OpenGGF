package com.openggf.game.sonic3k.runtime;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native Process_Kos_Module_Queue waits for the actual direct FIFO, not a host frame. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kKosPatternPublication {
    @Test void residentArtCannotCompleteWhileItsNativeDirectChildHasNotBeenServiced() throws Exception {
        com.openggf.game.session.SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture.builder().withZoneAndAct(23, 0).build();
        var services = mock(ObjectServices.class);
        var timing = new HardwareTimingService();
        var coordinator = new S3kRuntimeArtCoordinator(timing);
        var physical = new KosinskiModuleQueue();
        when(services.rom()).thenReturn(TestEnvironment.objectServices().rom());
        when(services.currentLevel()).thenReturn(GameServices.level().getCurrentLevel());
        when(services.levelManager()).thenReturn(GameServices.level());
        when(services.kosinskiModuleQueue()).thenReturn(physical);
        when(services.runtimeArtCoordinator()).thenReturn(coordinator);
        when(services.hardwareTiming()).thenReturn(timing);
        new DezFinalArtState().queueModule(services, 0x17FCBA, 0x52E);

        // Native bit-7-clear branch submits this module to Queue_Kos. Do not
        // service Process_Kos_Queue: its count remains nonzero independently
        // of how many times the module owner is dispatched.
        coordinator.beforeTimingService(HardwareServiceBoundary.POST_OBJECTS);
        assertTrue(coordinator.directQueue().decompressionsPending());
        physical.processNativeFrame();
        var inFlight = physical.capture();
        for (int dispatch = 0; dispatch < 3; dispatch++) physical.processNativeFrame();

        assertEquals(KosinskiModuleQueue.Phase.DECOMPRESSION_IN_PROGRESS, physical.phase(),
                "Process_Kos_Module_Queue must retain bit7 while Kos_decomp_queue_count is nonzero");
        assertEquals(inFlight, physical.capture(),
                "an unserviced real child cannot write resident patterns or advance FIFO/source/destination");
    }
    @Test void completedPayloadWaitsForVintEvenIfTheSpriteClaimsItsParentFirst() throws Exception {
        Rig rig = new Rig();
        rig.queue(0x17FCBA, 0x52E);
        rig.drain();
        assertTrue(rig.physical.isIdle());
        assertEquals(0, rig.art.pendingCount());
        assertEquals(0, rig.writes, "Add_To_DMA_Queue is not a VRAM upload");
        rig.coordinator.publishPatternModuleDma();
        assertEquals(1, rig.writes);
        rig.expected(0x17FCBA, 0x52E);
        rig.coordinator.publishPatternModuleDma();
        assertEquals(1, rig.writes, "the next service cannot republish consumed DMA");
    }

    @Test void multipleModulesKeepNativeFifoFinalLengthAndSourceResidue() throws Exception {
        Rig rig = new Rig();
        rig.queue(0x15BABE, 0x100); // $12A0 bytes: one full module and a $2A0-byte tail.
        rig.queue(0x17FCBA, 0x52E);
        rig.drain();
        assertEquals(0, rig.writes);
        rig.coordinator.publishPatternModuleDma();
        assertEquals(java.util.List.of(0x1000, 0x2A0, 2176), rig.uploads.stream().map(c -> c.data().length).toList());
        assertEquals(java.util.List.of(0x2000, 0x3000, 0xA5C0), rig.uploads.stream().map(KosinskiModuleQueue.DmaChunk::destinationVramBytes).toList());
        rig.expected(0x15BABE, 0x100); rig.expected(0x17FCBA, 0x52E);
    }

    @Test void submittedQueuedAndPresentedWorldsRestoreInBothDirectionsWithoutFutureDma() throws Exception {
        Rig rig = new Rig();
        rig.queue(0x17FCBA, 0x52E);
        var submitted = rig.registry.capture();
        rig.tail();
        var started = rig.registry.capture();
        assertEquals(0x81, rig.physical.modulesLeftRaw());
        rig.drain();
        var queued = rig.registry.capture();
        rig.coordinator.publishPatternModuleDma();
        var presented = rig.registry.capture();
        byte[] image = rig.memory.clone();
        for (int cycle = 0; cycle < 2; cycle++) {
            rig.registry.restore(started); rig.same(started);
            assertArrayEquals(new byte[65536], rig.memory, "restore cannot publish the queued future payload");
            rig.drain(); rig.same(queued);
            rig.coordinator.publishPatternModuleDma(); rig.same(presented);
            assertArrayEquals(image, rig.memory);
            rig.registry.restore(submitted); rig.same(submitted);
            assertArrayEquals(new byte[65536], rig.memory);
            rig.registry.restore(presented); rig.same(presented);
            assertArrayEquals(image, rig.memory);
            rig.registry.restore(queued); rig.same(queued);
            assertArrayEquals(new byte[65536], rig.memory);
            rig.coordinator.publishPatternModuleDma(); rig.same(presented);
            assertArrayEquals(image, rig.memory);
        }
    }

    @Test void unavailablePatternOwnerRetainsThePendingVintPayloadAcrossRestore() throws Exception {
        Rig rig = new Rig();
        rig.queue(0x17FCBA, 0x52E); rig.drain();
        var queued = rig.registry.capture();
        rig.available = false;
        rig.coordinator.publishPatternModuleDma();
        rig.same(queued); assertEquals(0, rig.writes);
        rig.registry.restore(queued); rig.same(queued);
        rig.available = true;
        rig.coordinator.publishPatternModuleDma();
        rig.expected(0x17FCBA, 0x52E); assertEquals(1, rig.writes);
    }

    @Test void identicalUnpairedPredecessorCannotBorrowTheLaterJobsReadiness() throws Exception {
        Rig rig = new Rig();
        assertTrue(rig.physical.enqueue(rig.rom, 0x17FCBA, 0xA5C0));
        rig.queue(0x17FCBA, 0x52E);
        // The first entry deliberately exercises the public standalone queue
        // contract; the second has a real timed owner, still unserviced.
        rig.physical.processNativeFrame();
        assertEquals(KosinskiModuleQueue.Phase.DECOMPRESSION_IN_PROGRESS, rig.physical.phase(),
                "an identical later paired job cannot seize the earlier admitted entry");
        rig.physical.processNativeFrame();
        assertEquals(1, rig.writes);
        assertEquals(1, rig.physical.queuedArchiveCount());
        rig.physical.processNativeFrame();
        assertEquals(KosinskiModuleQueue.Phase.READY_TO_START, rig.physical.phase(),
                "the actual paired successor still waits for its own native Queue_Kos receipt");
        var before = rig.registry.capture();
        rig.drain(); var pending = rig.registry.capture();
        rig.coordinator.publishPatternModuleDma(); var presented = rig.registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rig.registry.restore(before); rig.same(before);
            rig.drain(); rig.same(pending);
            rig.coordinator.publishPatternModuleDma(); rig.same(presented);
            rig.registry.restore(presented); rig.same(presented);
        }
    }

    @Test void identicalPairedDescriptorsKeepTheirActualSlotsAndBothRestoreDirections() throws Exception {
        Rig rig = new Rig(); rig.queue(0x17FCBA, 0x52E); rig.queue(0x17FCBA, 0x52E);
        var before = rig.registry.capture();
        for (int i = 0; i < 10000 && rig.physical.queuedArchiveCount() == 2; i++) rig.tail();
        assertEquals(1, rig.physical.queuedArchiveCount());
        assertEquals(KosinskiModuleQueue.Phase.READY_TO_START, rig.physical.phase());
        var between = rig.registry.capture();
        for (int i = 0; i < 3; i++) rig.physical.processNativeFrame();
        rig.same(between); // The second identical parent has not submitted its child yet.
        rig.drain(); var queued = rig.registry.capture();
        rig.coordinator.publishPatternModuleDma(); var presented = rig.registry.capture();
        assertEquals(2, rig.writes);
        for (int cycle = 0; cycle < 2; cycle++) {
            rig.registry.restore(before); rig.same(before);
            rig.drain(); rig.same(queued);
            rig.coordinator.publishPatternModuleDma(); rig.same(presented);
            rig.registry.restore(between); rig.same(between);
            rig.registry.restore(presented); rig.same(presented);
            rig.registry.restore(between); rig.same(between);
            rig.drain(); rig.same(queued);
        }
    }

    @Test void fourAdmittedPhysicalJobsRetainTheirPrefixWhenTheFifthCannotFit() throws Exception {
        Rig rig = new Rig();
        for (int i = 0; i < 4; i++) rig.queue(0x17FCBA, 0x300 + i * 0x50);
        var full = rig.registry.capture();
        assertThrows(IllegalStateException.class, () -> rig.queue(0x17FCBA, 0x440));
        rig.same(full);
        rig.drain(); rig.coordinator.publishPatternModuleDma();
        assertEquals(4, rig.writes);
        for (int i = 0; i < 4; i++) rig.expected(0x17FCBA, 0x300 + i * 0x50);
    }

    @Test void globalProducerDoesNotLeaveAnUnpairedEntryWhenTheActualNativeFifoIsFull() throws Exception {
        Rig rig = new Rig();
        for (int i = 0; i < 4; i++) rig.coordinator.moduleQueue().queue(rig.rom, 0x17FCBA, 0x300 + i * 0x50);
        var full = rig.registry.capture();
        assertFalse(S3kRuntimeArtCoordinator.queueGlobalPatternModule(rig.services, 0x17FCBA, 0xA5C0),
                "the existing native FIFO owns admission even when no earlier job has a pattern journal");
        rig.same(full);
        assertTrue(rig.physical.isIdle());
    }

    @Test void globalProducerRetiresItsOwnClaimButPublishesOnlyAtNativeVint() throws Exception {
        Rig rig = new Rig();
        assertTrue(S3kRuntimeArtCoordinator.queueGlobalPatternModule(rig.services, 0x17FCBA, 0xA5C0));
        rig.bindRecordingSink();
        var submitted = rig.registry.capture();
        for (int i = 0; i < 10000 && !rig.physical.isIdle(); i++) rig.tail();
        assertTrue(rig.physical.isIdle());
        assertTrue(rig.timing.pendingHandles().isEmpty(), "global native jobs have their own captured claim owner");
        assertEquals(0, rig.writes);
        var queued = rig.registry.capture();
        rig.coordinator.publishPatternModuleDma(); var presented = rig.registry.capture();
        rig.expected(0x17FCBA, 0x52E);
        for (int cycle = 0; cycle < 2; cycle++) {
            rig.registry.restore(submitted); rig.same(submitted);
            for (int i = 0; i < 10000 && !rig.physical.isIdle(); i++) rig.tail();
            rig.same(queued); rig.coordinator.publishPatternModuleDma(); rig.same(presented);
            rig.registry.restore(presented); rig.same(presented);
            rig.registry.restore(queued); rig.same(queued);
        }
    }

    @Test void retainedEggRoboRequestPublishesExactNativeTailWithoutAnAutomaticClaim() throws Exception {
        Rig rig = new Rig();
        var handle = S3kRuntimeArtCoordinator.queueRetainedPatternModule(rig.services, 0x17B17E, 0xA000);
        assertNotNull(handle);
        rig.bindRecordingSink();
        assertEquals(1, rig.physical.modulesLeftRaw());
        assertEquals(0x4F0, rig.physical.capture().archives().getFirst().lastModuleWords());
        var submitted = rig.registry.capture();
        rig.tail();
        var started = rig.registry.capture();
        assertEquals(0x81, rig.physical.modulesLeftRaw());
        rig.drain();
        assertTrue(rig.timing.isReady(handle));
        assertEquals(java.util.List.of(handle), rig.timing.pendingHandles(), "only the retained owner may claim");
        rig.coordinator.moduleQueue().claim(handle);
        assertEquals(0, rig.writes);
        var queued = rig.registry.capture();
        assertEquals(0x4F0, rig.coordinator.pendingKosModuleDmaEntries().getFirst().words());
        rig.coordinator.publishPatternModuleDma();
        var presented = rig.registry.capture();
        assertEquals(1, rig.writes);
        assertEquals(2528, rig.uploads.getFirst().data().length);
        assertEquals(0xA000, rig.uploads.getFirst().destinationVramBytes());
        rig.expected(0x17B17E, 0x500);
        for (int cycle = 0; cycle < 2; cycle++) {
            rig.registry.restore(submitted); rig.same(submitted);
            rig.tail(); rig.same(started);
            rig.drain();
            rig.coordinator.moduleQueue().claim(handle); rig.same(queued);
            rig.coordinator.publishPatternModuleDma(); rig.same(presented);
            rig.registry.restore(presented); rig.same(presented);
            rig.registry.restore(queued); rig.same(queued);
            assertArrayEquals(new byte[65536], rig.memory);
            rig.coordinator.publishPatternModuleDma(); rig.same(presented);
        }
    }

    @Test void retainedRequestRejectionAndPreparationFailureLeaveNoUnpairedMutation() throws Exception {
        Rig rig = new Rig();
        var empty = rig.registry.capture();
        var malformed = assertThrows(java.io.IOException.class, () -> S3kRuntimeArtCoordinator.queueRetainedPatternModule(
                rig.services, -1, 0xA000));
        assertEquals("KosM source is outside ROM: 0xffffffff", malformed.getMessage());
        rig.same(empty);
        assertThrows(IllegalArgumentException.class, () -> S3kRuntimeArtCoordinator.queueRetainedPatternModule(
                rig.services, 0x17B17E, 0xA001));
        rig.same(empty);
        var foreign = new S3kRuntimeArtCoordinator(new HardwareTimingService());
        foreign.bindPatternModuleQueue(rig.physical);
        assertThrows(IllegalStateException.class, () -> S3kRuntimeArtCoordinator.queueRetainedPatternModule(
                rig.services, 0x17B17E, 0xA000));
        rig.same(empty);
    }

    @Test void retainedRequestRejectsEitherFullFifoWithoutConsumingAnOrdinal() throws Exception {
        Rig rig = new Rig();
        for (int i = 0; i < 4; i++) assertTrue(rig.physical.enqueue(rig.rom, 0x17B17E, 0xA000));
        var full = rig.registry.capture();
        assertNull(S3kRuntimeArtCoordinator.queueRetainedPatternModule(rig.services, 0x17B17E, 0xA000));
        rig.same(full);
        rig = new Rig();
        for (int i = 0; i < 4; i++) rig.coordinator.moduleQueue().queue(rig.rom, 0x17B17E, 0x500);
        full = rig.registry.capture();
        assertNull(S3kRuntimeArtCoordinator.queueRetainedPatternModule(rig.services, 0x17B17E, 0xA000));
        rig.same(full);
    }

    @Test void failedTimedSubmissionCannotLeaveAnUnpairedPhysicalEntry() throws Exception {
        Rig rig = new Rig();
        var state = rig.timing.capture();
        rig.timing.restore(new com.openggf.game.timing.HardwareTimingSnapshot(
                java.util.Map.of(com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE, Long.MAX_VALUE),
                state.jobs(), state.admissionPolicies(), state.recordedAdmissionActive(),
                state.hasSubmitted(), state.lastServicedBoundary(), state.unrepresentedSubmissions()));
        var before = rig.registry.capture();
        var error = assertThrows(IllegalStateException.class,
                () -> S3kRuntimeArtCoordinator.queueRetainedPatternModule(rig.services, 0x17B17E, 0xA000));
        assertEquals("hardware work ordinal exhausted for KOS_MODULE_QUEUE", error.getMessage());
        rig.same(before);
        assertTrue(rig.physical.isIdle());
        assertTrue(rig.timing.pendingHandles().isEmpty());
    }

    @Test void globalProducerPublishesIntoTheActualResidentOwnerThroughItsJournal() throws Exception {
        Rig rig = new Rig();
        var level = rig.services.currentLevel();
        byte[] expected = com.openggf.data.compression.KosinskiReader.decompressModuled(
                rig.rom.readBytes(0x17FCBA, 0x10000), 0);
        byte[] before = residentBytes(level, 0x52E, expected.length);
        assertFalse(java.util.Arrays.equals(before, expected), "the declared DEZ resident owner has not loaded this ending art");
        assertTrue(S3kRuntimeArtCoordinator.queueGlobalPatternModule(rig.services, 0x17FCBA, 0xA5C0));
        for (int i = 0; i < 10000 && !rig.physical.isIdle(); i++) rig.tail();
        assertTrue(rig.physical.isIdle());
        assertArrayEquals(before, residentBytes(level, 0x52E, expected.length));
        assertTrue(rig.physical.capture().appliedWrites().isEmpty());
        rig.coordinator.publishPatternModuleDma();
        assertArrayEquals(expected, residentBytes(level, 0x52E, expected.length));
        assertFalse(rig.physical.capture().appliedWrites().isEmpty(), "the VInt upload must enter the actual physical rewind journal");
    }
    private static byte[] residentBytes(com.openggf.level.Level level, int tileBase, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            var pattern = level.getPattern(tileBase + i / 32); int pixel = (i % 32) * 2;
            bytes[i] = (byte) (pattern.getPixel(pixel % 8, pixel / 8) << 4
                    | pattern.getPixel((pixel + 1) % 8, (pixel + 1) / 8));
        }
        return bytes;
    }

    private static final class Rig {
        final ObjectServices services = mock(ObjectServices.class);
        final HardwareTimingService timing = new HardwareTimingService();
        final S3kRuntimeArtCoordinator coordinator = new S3kRuntimeArtCoordinator(timing);
        final KosinskiModuleQueue physical = new KosinskiModuleQueue();
        final DezFinalArtState art = new DezFinalArtState();
        final com.openggf.game.rewind.RewindRegistry registry = new com.openggf.game.rewind.RewindRegistry();
        final byte[] memory = new byte[65536]; // Declared recording sink, not a native-ingress pixel oracle.
        final java.util.List<KosinskiModuleQueue.DmaChunk> uploads = new java.util.ArrayList<>();
        int writes; boolean available = true;
        final com.openggf.data.Rom rom;
        Rig() throws Exception {
            com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
            HeadlessTestFixture.builder().withZoneAndAct(23, 0).build();
            rom = TestEnvironment.objectServices().rom();
            when(services.rom()).thenReturn(rom);
            when(services.currentLevel()).thenReturn(GameServices.level().getCurrentLevel());
            when(services.levelManager()).thenReturn(GameServices.level());
            when(services.kosinskiModuleQueue()).thenReturn(physical);
            when(services.runtimeArtCoordinator()).thenReturn(coordinator);
            when(services.hardwareTiming()).thenReturn(timing);
            registry.register(timing); coordinator.registerRewindAdapters(registry); registry.register(physical);
            registry.register(new com.openggf.game.rewind.RewindSnapshottable<byte[]>() {
                @Override public String key() { return "native-producer-art"; }
                @Override public byte[] capture() { var b = java.nio.ByteBuffer.allocate(art.snapshotBytes()); art.captureTo(b); return b.array(); }
                @Override public void restore(byte[] state) { art.restoreFrom(java.nio.ByteBuffer.wrap(state)); }
            });
        }
        void queue(int source, int tile) {
            try { art.queueModule(services, source, tile); }
            finally { bindRecordingSink(); }
        }
        void bindRecordingSink() {
            physical.bindDmaTarget(new KosinskiModuleQueue.DmaTarget() {
                @Override public boolean isAvailable() { return available; }
                @Override public byte[] read(int destination, int length) { return java.util.Arrays.copyOfRange(memory, destination, destination + length); }
                @Override public void apply(KosinskiModuleQueue.DmaChunk chunk) {
                    System.arraycopy(chunk.data(), 0, memory, chunk.destinationVramBytes(), chunk.data().length);
                    uploads.add(chunk); writes++;
                }
            });
        }
        void tail() {
            com.openggf.game.HardwareBoundaryDispatch.serviceBoundary(HardwareServiceBoundary.POST_OBJECTS,
                    coordinator, timing, com.openggf.game.timing.HardwareTimingBoundaryObserver.NO_OP);
            com.openggf.game.HardwareBoundaryDispatch.serviceBoundary(HardwareServiceBoundary.PRE_MAIN_LOOP,
                    coordinator, timing, com.openggf.game.timing.HardwareTimingBoundaryObserver.NO_OP);
            // Native completion remains safe even when a sprite claims and
            // forgets the logical archive before the physical transfer consumes it.
            art.service(services); physical.processNativeFrame();
        }
        void drain() {
            for (int i = 0; i < 10000 && (!physical.isIdle() || art.pendingCount() != 0); i++) tail();
            assertTrue(physical.isIdle()); assertEquals(0, art.pendingCount());
        }
        void expected(int source, int tile) throws Exception {
            byte[] expected = com.openggf.data.compression.KosinskiReader.decompressModuled(rom.readBytes(source, 0x10000), 0);
            assertArrayEquals(expected, java.util.Arrays.copyOfRange(memory, tile * 32, tile * 32 + expected.length));
        }
        void same(com.openggf.game.rewind.CompositeSnapshot expected) {
            var actual = registry.capture(); assertEquals(expected.entries().keySet(), actual.entries().keySet());
            for (String key : expected.entries().keySet()) assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    key, expected.get(key), actual.get(key)).isEmpty(), key + ": " + com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }

}
