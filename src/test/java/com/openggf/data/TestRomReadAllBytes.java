package com.openggf.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRomReadAllBytes {

    @TempDir
    Path tempDir;

    @Test
    void readAllBytes_doesNotMoveSharedChannelPosition() throws Exception {
        Path romPath = tempDir.resolve("tiny.gen");
        byte[] bytes = new byte[] {0x10, 0x20, 0x30, 0x40, 0x50};
        Files.write(romPath, bytes);

        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romPath.toString()));
            rom.getFileChannel().position(2);

            byte[] actual = rom.readAllBytes();

            assertArrayEquals(bytes, actual);
            assertEquals(2, rom.getFileChannel().position(),
                    "whole-ROM buffering must not disturb positional readers using the same channel");
        }
    }
}
