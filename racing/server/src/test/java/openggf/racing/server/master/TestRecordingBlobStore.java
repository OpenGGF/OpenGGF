package openggf.racing.server.master;

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
    private static final String OTHER_HASH = "b".repeat(64);

    @Test
    void accountsForExistingWritesOverwritesAndRetentionWithoutRescanning(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve(HASH), new byte[]{1, 2, 3});
        RecordingBlobStore store = new RecordingBlobStore(dir);
        assertEquals(3, store.totalBytes());

        assertTrue(store.putIfWithinLimit(OTHER_HASH, new byte[]{4, 5}, 5));
        assertEquals(5, store.totalBytes());
        store.put(HASH, new byte[]{6});
        assertEquals(3, store.totalBytes());
        assertTrue(store.putIfWithinLimit(HASH, new byte[]{6}, 3),
                "an identical content-addressed upload only refreshes retention");
        assertEquals(3, store.totalBytes());

        long cutoff = System.currentTimeMillis() - 1_000;
        Files.setLastModifiedTime(dir.resolve(OTHER_HASH), FileTime.fromMillis(cutoff - 1));
        assertEquals(1, store.deleteOlderThan(cutoff));
        assertEquals(1, store.totalBytes());
    }

    @Test
    void validatedUploadRepairsCorruptExistingBlob(@TempDir Path dir) throws Exception {
        byte[] corrupt = {9, 9};
        byte[] validatedUpload = {1, 2};
        Files.write(dir.resolve(HASH), corrupt);
        RecordingBlobStore store = new RecordingBlobStore(dir);

        assertTrue(store.putIfWithinLimit(HASH, validatedUpload, corrupt.length));

        assertArrayEquals(validatedUpload, store.get(HASH).orElseThrow());
        assertEquals(validatedUpload.length, store.totalBytes());
    }

    @Test
    void repairReconcilesExternallyChangedBlobSize(@TempDir Path dir) throws Exception {
        byte[] validatedUpload = {1, 2};
        Files.write(dir.resolve(HASH), validatedUpload);
        RecordingBlobStore store = new RecordingBlobStore(dir);
        Files.write(dir.resolve(HASH), new byte[]{9, 9, 9, 9});

        assertTrue(store.putIfWithinLimit(HASH, validatedUpload, validatedUpload.length));

        assertArrayEquals(validatedUpload, store.get(HASH).orElseThrow());
        assertEquals(validatedUpload.length, store.totalBytes());
    }

    @Test
    void rejectsNewBlobWhenCachedQuotaWouldBeExceeded(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve(HASH), new byte[]{1, 2, 3});
        RecordingBlobStore store = new RecordingBlobStore(dir);

        assertTrue(!store.putIfWithinLimit(OTHER_HASH, new byte[]{4}, 3));
        assertEquals(3, store.totalBytes());
        assertTrue(Files.notExists(dir.resolve(OTHER_HASH)));
    }

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

    @Test
    void startupRemovesInterruptedPublicationArtifactsFromQuota(@TempDir Path dir) throws Exception {
        Path staging = dir.resolve(".recording-interrupted.tmp");
        Files.write(staging, new byte[]{1, 2, 3});

        RecordingBlobStore store = new RecordingBlobStore(dir);

        assertEquals(0, store.totalBytes());
        assertTrue(Files.notExists(staging));
    }
}
