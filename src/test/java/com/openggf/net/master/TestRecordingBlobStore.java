package com.openggf.net.master;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestRecordingBlobStore {
    private static final String HASH = "a".repeat(64);

    @Test
    void reusedRecordingSurvivesRetentionForFreshJob(@TempDir Path dir) throws Exception {
        RecordingBlobStore store = new RecordingBlobStore(dir);
        byte[] recording = {1, 2, 3};
        store.put(HASH, recording);
        long cutoff = System.currentTimeMillis() - 3L * 24 * 3_600_000;
        Files.setLastModifiedTime(dir.resolve(HASH), FileTime.fromMillis(cutoff - 1_000));

        assertTrue(store.putIfWithinLimit(HASH, recording, recording.length));

        assertEquals(0, store.deleteOlderThan(cutoff));
        assertArrayEquals(recording, store.get(HASH).orElseThrow());
    }

    @Test
    void existingSymlinkIsNotAcceptedAsStoredRecording(@TempDir Path dir) throws Exception {
        assumeTrue(Files.getFileStore(dir).supportsFileAttributeView("posix"));
        Path external = dir.resolve("external");
        Files.write(external, new byte[]{1, 2, 3});
        Path storeDir = dir.resolve("recordings");
        RecordingBlobStore store = new RecordingBlobStore(storeDir);
        Files.createSymbolicLink(storeDir.resolve(HASH), external);

        assertThrows(IllegalStateException.class,
                () -> store.putIfWithinLimit(HASH, new byte[]{1, 2, 3}, 100));
    }
}
