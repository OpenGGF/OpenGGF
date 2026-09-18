package com.openggf.tools.audio.completerun;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestCompleteRunAudioFiles {
    @TempDir Path temporary;

    @Test
    void acceptsOnlyNormalizedCanonicalFilesAndDirectories() throws Exception {
        Path root = temporary.toRealPath();
        Path file = Files.writeString(root.resolve("input"), "abc");
        assertEquals(file, CompleteRunAudioFiles.file(file, "ROM"));
        assertEquals(root, CompleteRunAudioFiles.directory(root, "root"));
        assertRejected("ROM must be an absolute normalized path",
                () -> CompleteRunAudioFiles.file(Path.of("input"), "ROM"));
        assertRejected("ROM must be an absolute normalized path",
                () -> CompleteRunAudioFiles.file(root.resolve("child/../input"), "ROM"));
        assertRejected("ROM must be an ordinary non-symlink file",
                () -> CompleteRunAudioFiles.file(root, "ROM"));
        assertRejected("root must be an ordinary non-symlink directory",
                () -> CompleteRunAudioFiles.directory(file, "root"));
        assertRejected("ROM must be an ordinary non-symlink file",
                () -> CompleteRunAudioFiles.file(root.resolve("missing"), "ROM"));
        Path link = Files.createSymbolicLink(root.resolve("link"), file);
        assertRejected("ROM must be an ordinary non-symlink file",
                () -> CompleteRunAudioFiles.file(link, "ROM"));
        Path parentLink = Files.createSymbolicLink(root.resolve("alias"), root);
        assertRejected("ROM must be an ordinary non-symlink file",
                () -> CompleteRunAudioFiles.file(parentLink.resolve("input"), "ROM"));
        assertEquals("ROM", assertThrows(NullPointerException.class,
                () -> CompleteRunAudioFiles.file(null, "ROM")).getMessage());
    }

    @Test
    void digestsRetainHexIdentityCaseAndIoFailures() throws Exception {
        Path file = Files.writeString(temporary.resolve("input"), "abc");
        String sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assertDoesNotThrow(() -> CompleteRunAudioFiles.requireDigest(file, "SHA-256", sha256, "ROM"));
        assertDoesNotThrow(() -> CompleteRunAudioFiles.requireDigest(file, "SHA-1",
                "a9993e364706816aba3e25717850c26c9cd0d89d", "ROM"));
        assertRejected("ROM identity does not match the fixed profile",
                () -> CompleteRunAudioFiles.requireDigest(file, "SHA-256", sha256.toUpperCase(), "ROM"));
        Files.writeString(file, "abcd");
        assertRejected("ROM identity does not match the fixed profile",
                () -> CompleteRunAudioFiles.requireDigest(file, "SHA-256", sha256, "ROM"));
        Files.delete(file);
        assertThrows(IOException.class,
                () -> CompleteRunAudioFiles.requireDigest(file, "SHA-256", sha256, "ROM"));
    }

    private static void assertRejected(String message, org.junit.jupiter.api.function.Executable action) {
        assertEquals(message, assertThrows(IllegalArgumentException.class, action).getMessage());
    }
}
