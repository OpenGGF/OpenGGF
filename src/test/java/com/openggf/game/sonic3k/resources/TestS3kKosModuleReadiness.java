package com.openggf.game.sonic3k.resources;

import com.openggf.data.Rom;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareTimingSnapshot;
import com.openggf.game.timing.HardwareWorkHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kKosModuleReadiness {
    private static final byte[] ABC_KOSM = {
            0x00, 0x03,
            0x17, 0x00,
            'A', 'B', 'C',
            0x00, 0x00, 0x00
    };

    @TempDir
    Path tempDir;

    @Test
    void rewindRestoresInFlightModuleDecoder() throws Exception {
        Path romPath = tempDir.resolve("fixture.gen");
        Files.write(romPath, ABC_KOSM);
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romPath.toString()));
            HardwareTimingService timing = new HardwareTimingService();
            S3kKosModuleQueue queue = new S3kKosModuleQueue(timing);
            HardwareWorkHandle handle = queue.queue(rom, 0, 0x500);

            timing.service(HardwareServiceBoundary.POST_OBJECTS);
            timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            HardwareTimingSnapshot snapshot = timing.capture();

            drain(timing, queue, handle);
            byte[] expected = queue.claim(handle);

            timing.restore(snapshot);
            S3kKosModuleQueue restoredQueue = new S3kKosModuleQueue(timing);
            assertFalse(restoredQueue.isReady(handle));
            drain(timing, restoredQueue, handle);

            assertArrayEquals(expected, restoredQueue.claim(handle));
        }
    }

    private static void drain(
            HardwareTimingService timing,
            S3kKosModuleQueue queue,
            HardwareWorkHandle handle) {
        for (int frame = 0; frame < 16 && !queue.isReady(handle); frame++) {
            timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            timing.service(HardwareServiceBoundary.POST_OBJECTS);
        }
        assertTrue(queue.isReady(handle));
    }
}
