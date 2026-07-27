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
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kKosModuleQueue {
    private static final byte[] ABC_KOSM = {
            0x00, 0x03,
            0x17, 0x00,
            'A', 'B', 'C',
            0x00, 0x00, 0x00
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
            S3kKosModuleQueue queue = new S3kKosModuleQueue(timing);

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
            S3kKosModuleQueue queue = new S3kKosModuleQueue(timing);
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
    void eachPreInvocationPreparesOneCompleteModuleBeforeRecordedAdmission()
            throws Exception {
        String configured = System.getProperty("s3k.rom.path");
        Assumptions.assumeTrue(configured != null && !configured.isBlank());
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(configured));
            HardwareTimingService timing = new HardwareTimingService();
            var recorded = timing.beginRecordedAdmission();
            S3kKosModuleQueue queue = new S3kKosModuleQueue(timing);
            HardwareWorkHandle handle = queue.queue(
                    rom,
                    Sonic3kConstants.ART_KOSM_HCZ2_SECONDARY_ADDR,
                    0x11B);

            assertEquals(5, queue.descriptor(handle).moduleCount(),
                    "the archive header pins five service modules");
            timing.service(HardwareServiceBoundary.POST_OBJECTS);
            for (int module = 1; module <= 5; module++) {
                timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
                S3kKosModuleSnapshot beforeRetirement = preparation(timing.capture());
                assertNotNull(beforeRetirement.activeDecoder());
                assertTrue(beforeRetirement.activeDecoder().complete(),
                        "one PRE invocation must completely prepare module " + module);
                assertEquals(module - 1, beforeRetirement.completedModules());
                assertFalse(queue.isReady(handle),
                        "preparation cannot admit recorded readiness");

                timing.service(HardwareServiceBoundary.POST_OBJECTS);
                assertEquals(module, preparation(timing.capture()).completedModules());
            }

            HardwareTimingSnapshot preparedAtFrameFive = timing.capture();
            assertNotNull(preparedAtFrameFive.jobs().getFirst().preparedPayload(),
                    "all five modules must be prepared well before frame 131");
            assertFalse(queue.isReady(handle),
                    "RECORDED mode holds prepared work until its trace edge");

            for (int frame = 6; frame <= 131; frame++) {
                timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
                timing.service(HardwareServiceBoundary.POST_OBJECTS);
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

    private static S3kKosModuleSnapshot preparation(HardwareTimingSnapshot snapshot) {
        return (S3kKosModuleSnapshot) snapshot.jobs().getFirst().preparationSnapshot();
    }
}
