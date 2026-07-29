package com.openggf.game.sonic3k.resources;

import com.openggf.data.Rom;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingSnapshot;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.tools.KosinskiReader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kKosModuleQueue {
    private static final byte[] ABC_KOSM = {
            0x00, 0x03,
            0x17, 0x00,
            'A', 'B', 'C',
            0x00, 0x00, 0x00
    };
    private static final byte[] EMPTY_KOSM = {0x00, 0x00};
    private static final byte[] TWO_MODULE_KOSM = {
            0x10, 0x01,
            0x17, 0x00, 'A', 'B', 'C', 0x00, 0x00, 0x00,
            0, 0, 0, 0, 0, 0, 0, 0,
            0x17, 0x00, 'D', 'E', 'F', 0x00, 0x00, 0x00
    };

    @TempDir
    Path tempDir;

    @Test
    void submitsCanonicalArchiveSpanAndPublishesAfterPostObjects() throws Exception {
        Path romPath = tempDir.resolve("fixture.gen");
        Files.write(romPath, ABC_KOSM);
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romPath.toString()));
            HardwareTimingService timing = new HardwareTimingService();
            S3kKosDecompressionQueue direct = new S3kKosDecompressionQueue(timing);
            S3kKosModuleQueue queue = new S3kKosModuleQueue(timing, direct);

            HardwareWorkHandle handle = queue.queue(rom, 0, 0x500);
            S3kKosModuleDescriptor descriptor = queue.descriptor(handle);

            assertEquals(ABC_KOSM.length, descriptor.compressedLength());
            assertEquals(3, descriptor.destinationLength());
            assertEquals(1, descriptor.moduleCount());
            assertEquals(0x500 * 32, descriptor.destinationAddress());
            assertFalse(queue.isReady(handle));

            for (int frame = 0; frame < 16 && !queue.isReady(handle); frame++) {
                queue.prepareQueuedModuleBeforeVSync();
                assertFalse(queue.isReady(handle),
                        "pre-VSync preparation must not publish readiness");
                queue.processModuleQueueAfterObjects();
            }

            assertTrue(queue.isReady(handle));
            assertFalse(queue.modulesLeft());
            assertArrayEquals(new byte[] {'A', 'B', 'C'}, queue.claim(handle));
        }
    }

    @Test
    void decodesTitleCardArchiveFromVerifiedRom() throws Exception {
        String configured = System.getProperty("s3k.rom.path");
        Assumptions.assumeTrue(configured != null && !configured.isBlank());
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(configured));
            HardwareTimingService timing = new HardwareTimingService();
            S3kKosDecompressionQueue direct = new S3kKosDecompressionQueue(timing);
            S3kKosModuleQueue queue = new S3kKosModuleQueue(timing, direct);
            HardwareWorkHandle handle = queue.queue(
                    rom,
                    Sonic3kConstants.ART_KOSM_TITLE_CARD_RED_ACT_ADDR,
                    Sonic3kConstants.VRAM_TITLE_CARD_BASE);
            S3kKosModuleDescriptor descriptor = queue.descriptor(handle);

            for (int frame = 0; frame < 512 && !queue.isReady(handle); frame++) {
                queue.prepareQueuedModuleBeforeVSync();
                queue.processModuleQueueAfterObjects();
            }

            assertTrue(queue.isReady(handle));
            byte[] archive = rom.readBytes(
                    descriptor.sourceAddress(),
                    descriptor.compressedLength());
            assertArrayEquals(
                    KosinskiReader.decompressModuled(archive, 0),
                    queue.claim(handle));
        }
    }

    @Test
    void aizIntroPlaneChildUsesRomKosDecompBufferIdentity() throws Exception {
        String configured = System.getProperty("s3k.rom.path");
        Assumptions.assumeTrue(configured != null && !configured.isBlank());
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(configured));
            HardwareTimingService timing = new HardwareTimingService();
            S3kKosDecompressionQueue direct =
                    new S3kKosDecompressionQueue(timing);
            S3kKosModuleQueue queue = new S3kKosModuleQueue(timing, direct);

            queue.queue(
                    rom,
                    Sonic3kConstants.ART_KOSM_AIZ_INTRO_PLANE_ADDR,
                    Sonic3kConstants.ARTTILE_AIZ_INTRO_PLANE);
            queue.processModuleQueueAfterObjects();

            var child = timing.capture().jobs().stream()
                    .filter(job -> job.kind()
                            == com.openggf.game.timing.HardwareWorkKind
                            .KOS_DECOMPRESSION_QUEUE)
                    .findFirst()
                    .orElseThrow();
            assertEquals(0x382626, child.romSourceAddress());
            assertEquals(1894, child.compressedLength());
            assertEquals(0xFFFFD000, child.destinationAddress());
            assertEquals(4096, child.destinationLength());
            assertEquals("kosinski", child.compressionVariant());
            assertEquals(1, child.moduleCount());
            assertEquals(
                    "sha256:c381a8f75b41d3e2d1e52fb90ae8a5c269b1daeb88dd198bd8fb3d07d3703a7b",
                    child.handle().submissionFingerprint());
        }
    }

    @Test
    void schemaOneLiveChildrenPrepareParentBeforeRecordedAdmission()
            throws Exception {
        String configured = System.getProperty("s3k.rom.path");
        Assumptions.assumeTrue(configured != null && !configured.isBlank());
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(configured));
            HardwareTimingService timing = new HardwareTimingService();
            var recorded = timing.beginRecordedAdmission();
            S3kKosDecompressionQueue direct = new S3kKosDecompressionQueue(timing);
            S3kKosModuleQueue queue = new S3kKosModuleQueue(timing, direct);
            HardwareWorkHandle handle = queue.queue(
                    rom,
                    Sonic3kConstants.ART_KOSM_HCZ2_SECONDARY_ADDR,
                    0x11B);

            assertEquals(5, queue.descriptor(handle).moduleCount(),
                    "the archive header pins five service modules");
            queue.processModuleQueueAfterObjects();
            for (int module = 1; module <= 5; module++) {
                S3kKosModuleSnapshot submitted = preparation(timing.capture());
                assertNotNull(submitted.activeChild());
                while (!direct.isReady(submitted.activeChild())) {
                    queue.prepareQueuedModuleBeforeVSync();
                }
                S3kKosModuleSnapshot beforeRetirement = preparation(timing.capture());
                assertEquals(submitted.activeChild(), beforeRetirement.activeChild());
                assertEquals(module - 1, beforeRetirement.completedModules());
                assertFalse(queue.isReady(handle),
                        "preparation cannot admit recorded readiness");

                queue.processModuleQueueAfterObjects();
                assertEquals(module, preparation(timing.capture()).completedModules());
                if (module < 5) {
                    queue.processModuleQueueAfterObjects();
                }
            }

            HardwareTimingSnapshot preparedAtFrameFive = timing.capture();
            assertNotNull(preparedAtFrameFive.jobs().getFirst().preparedPayload(),
                    "all five modules must be prepared well before frame 131");
            assertFalse(queue.isReady(handle),
                    "RECORDED mode holds prepared work until its trace edge");

            for (int frame = 6; frame <= 131; frame++) {
                queue.prepareQueuedModuleBeforeVSync();
                queue.processModuleQueueAfterObjects();
                assertFalse(queue.isReady(handle),
                        "service cadence cannot admit readiness before the trace edge");
            }
            recorded.admitRecordedCompletion(
                    HardwareServiceBoundary.POST_OBJECTS,
                    handle.kind(),
                    handle.ordinal(),
                    handle.submissionFingerprint());
            assertTrue(queue.isReady(handle));
        }
    }

    @Test
    void postRetiresOneChildBeforeFollowingPostSubmitsNextModule() throws Exception {
        Path romPath = tempDir.resolve("two-modules.gen");
        Files.write(romPath, TWO_MODULE_KOSM);
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romPath.toString()));
            HardwareTimingService timing = new HardwareTimingService();
            S3kKosDecompressionQueue direct = new S3kKosDecompressionQueue(timing);
            S3kKosModuleQueue queue = new S3kKosModuleQueue(timing, direct);
            HardwareWorkHandle parent = queue.queue(rom, 0, 0x500);

            queue.processModuleQueueAfterObjects();
            S3kKosModuleSnapshot submittedFirst = preparation(timing.capture());
            assertEquals(0, submittedFirst.completedModules());
            assertNotNull(submittedFirst.activeChild());
            assertEquals(1, direct.physicalQueueSize());

            while (!direct.isReady(submittedFirst.activeChild())) {
                queue.prepareQueuedModuleBeforeVSync();
            }
            queue.processModuleQueueAfterObjects();
            S3kKosModuleSnapshot retiredFirst = preparation(timing.capture());
            assertEquals(1, retiredFirst.completedModules());
            assertEquals(null, retiredFirst.activeChild());
            assertEquals(0, direct.physicalQueueSize());
            assertFalse(queue.isReady(parent));

            queue.processModuleQueueAfterObjects();
            S3kKosModuleSnapshot submittedSecond = preparation(timing.capture());
            assertNotNull(submittedSecond.activeChild());
            assertEquals(1, submittedSecond.completedModules());
            assertEquals(1, direct.physicalQueueSize());
        }
    }

    @Test
    void fullDirectFifoDefersModuleChildWithoutThrowing() throws Exception {
        Path romPath = tempDir.resolve("full-direct.gen");
        Files.write(romPath, ABC_KOSM);
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romPath.toString()));
            HardwareTimingService timing = new HardwareTimingService();
            S3kKosDecompressionQueue direct = new S3kKosDecompressionQueue(timing);
            S3kKosModuleQueue queue = new S3kKosModuleQueue(timing, direct);
            for (int index = 0; index < 4; index++) {
                direct.queueStandardKos(
                        rom, 2, S3kKosRamDestinations.blockTableOffset(index));
            }
            queue.queue(rom, 0, 0x500);

            queue.processModuleQueueAfterObjects();

            assertEquals(4, direct.physicalQueueSize());
            assertEquals(null, preparation(timing.capture()).activeChild());
        }
    }

    @Test
    void ordinaryTailDelaysChildClaimUntilCompleteDirectFifoIsEmpty()
            throws Exception {
        Path romPath = tempDir.resolve("ordinary-tail.gen");
        Files.write(romPath, ABC_KOSM);
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romPath.toString()));
            HardwareTimingService timing = new HardwareTimingService();
            S3kKosDecompressionQueue direct = new S3kKosDecompressionQueue(timing);
            S3kKosModuleQueue queue = new S3kKosModuleQueue(timing, direct);
            HardwareWorkHandle parent = queue.queue(rom, 0, 0x500);
            queue.processModuleQueueAfterObjects();
            HardwareWorkHandle child = preparation(timing.capture()).activeChild();
            HardwareWorkHandle ordinary = direct.queueStandardKos(
                    rom, 2, S3kKosRamDestinations.BLOCK_TABLE);

            while (!direct.isReady(child)) {
                queue.prepareQueuedModuleBeforeVSync();
            }
            queue.processModuleQueueAfterObjects();
            assertEquals(0, preparation(timing.capture()).completedModules());
            assertFalse(queue.isReady(parent));

            while (!direct.isReady(ordinary)) {
                queue.prepareQueuedModuleBeforeVSync();
            }
            queue.processModuleQueueAfterObjects();

            assertTrue(queue.isReady(parent));
            assertEquals(1, timing.capture().jobs().stream()
                    .filter(job -> job.kind()
                            == com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE)
                    .count());
            assertEquals(2, timing.capture().jobs().stream()
                    .filter(job -> job.kind()
                            == com.openggf.game.timing.HardwareWorkKind.KOS_DECOMPRESSION_QUEUE)
                    .count());
        }
    }

    @Test
    void zeroModuleParentCanOnlyPrepareAtPostObjects() throws Exception {
        Path romPath = tempDir.resolve("empty-kosm.gen");
        Files.write(romPath, EMPTY_KOSM);
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romPath.toString()));
            HardwareTimingService timing = new HardwareTimingService();
            S3kKosDecompressionQueue direct = new S3kKosDecompressionQueue(timing);
            S3kKosModuleQueue queue = new S3kKosModuleQueue(timing, direct);
            HardwareWorkHandle parent = queue.queue(rom, 0, 0x500);

            timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            direct.afterTimingService(HardwareServiceBoundary.PRE_MAIN_LOOP);
            assertNull(timing.capture().jobs().getFirst().preparedPayload());
            assertFalse(queue.isReady(parent));

            timing.service(HardwareServiceBoundary.VINT_SERVICE);
            assertNull(timing.capture().jobs().getFirst().preparedPayload());
            assertFalse(queue.isReady(parent));

            queue.processModuleQueueAfterObjects();
            assertTrue(queue.isReady(parent));
            assertArrayEquals(new byte[0], queue.claim(parent));
        }
    }

    @Test
    void preparedButNotYetAdmittedParentsDoNotOccupyPhysicalFifoCapacity()
            throws Exception {
        Path romPath = tempDir.resolve("recorded-empty-kosm.gen");
        Files.write(romPath, EMPTY_KOSM);
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romPath.toString()));
            HardwareTimingService timing = new HardwareTimingService();
            timing.beginRecordedAdmission();
            S3kKosDecompressionQueue direct =
                    new S3kKosDecompressionQueue(timing);
            S3kKosModuleQueue queue =
                    new S3kKosModuleQueue(timing, direct);

            for (int index = 0; index < 4; index++) {
                HardwareWorkHandle parent =
                        queue.queue(rom, 0, 0x500 + index);
                queue.processModuleQueueAfterObjects();
                assertFalse(queue.isReady(parent),
                        "recorded admission must retain prepared results");
                assertNotNull(timing.capture().jobs().get(index)
                                .preparedPayload(),
                        "the parent must have left the physical FIFO");
            }

            assertNotNull(queue.queue(rom, 0, 0x504));
        }
    }

    @Test
    void rejectsDirectOwnerFromAnotherTimingLedger() {
        HardwareTimingService parentTiming = new HardwareTimingService();
        HardwareTimingService directTiming = new HardwareTimingService();
        S3kKosDecompressionQueue direct =
                new S3kKosDecompressionQueue(directTiming);

        assertThrows(IllegalArgumentException.class,
                () -> new S3kKosModuleQueue(parentTiming, direct));
    }

    @Test
    void ordinaryHeadWorkDelaysChildAndParentUntilLaterPost() throws Exception {
        Path romPath = tempDir.resolve("ordinary-head.gen");
        Files.write(romPath, ABC_KOSM);
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romPath.toString()));
            HardwareTimingService timing = new HardwareTimingService();
            S3kKosDecompressionQueue direct = new S3kKosDecompressionQueue(timing);
            S3kKosModuleQueue queue = new S3kKosModuleQueue(timing, direct);
            for (int index = 0; index < 4; index++) {
                direct.queueStandardKos(
                        rom, 2, S3kKosRamDestinations.blockTableOffset(index));
            }
            HardwareWorkHandle parent = queue.queue(rom, 0, 0x500);

            queue.processModuleQueueAfterObjects();
            assertNull(preparation(timing.capture()).activeChild());

            while (direct.physicalQueueSize() == 4) {
                queue.prepareQueuedModuleBeforeVSync();
            }
            queue.processModuleQueueAfterObjects();
            HardwareWorkHandle child =
                    preparation(timing.capture()).activeChild();
            assertNotNull(child);
            assertEquals(4, direct.physicalQueueSize(),
                    "the module child must join behind three ordinary entries");

            while (!direct.isReady(child)) {
                queue.prepareQueuedModuleBeforeVSync();
                if (!direct.isReady(child)) {
                    queue.processModuleQueueAfterObjects();
                    assertEquals(0,
                            preparation(timing.capture()).completedModules());
                    assertFalse(queue.isReady(parent));
                }
            }
            assertFalse(direct.decompressionsPending());
            assertEquals(0, preparation(timing.capture()).completedModules());
            assertFalse(queue.isReady(parent),
                    "PRE retirement cannot advance the module parent");

            queue.processModuleQueueAfterObjects();
            assertTrue(queue.isReady(parent),
                    "the following POST owns the one parent transition");
        }
    }

    private static S3kKosModuleSnapshot preparation(HardwareTimingSnapshot snapshot) {
        return (S3kKosModuleSnapshot) snapshot.jobs().stream()
                .filter(job -> job.kind()
                        == com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE)
                .findFirst()
                .orElseThrow()
                .preparationSnapshot();
    }
}
