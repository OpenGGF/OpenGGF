package com.openggf.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TestS3kKnucklesTerminalCaptureTool {
    @TempDir Path output;

    @Test void exactInputsAndSingleOffsetRemainExplicit() {
        var args = S3kKnucklesTerminalCaptureTool.Arguments.parse(new String[]{
                "--rom", "rom.gen", "--route", "cold.bk2", "--native-input", "native.bk2",
                "--native-init", "432709", "--offset", "619", "--out-dir", output.toString()});
        assertEquals(432709, args.nativeInit());
        assertEquals(619, args.offset());
        assertTrue(args.route().isAbsolute());
        assertNotEquals(args.route(), args.nativeInput());
        assertThrows(IllegalArgumentException.class, () -> S3kKnucklesTerminalCaptureTool.Arguments.parse(
                new String[]{"--offset", "619"}));
        assertThrows(IllegalArgumentException.class, () -> S3kKnucklesTerminalCaptureTool.Arguments.parse(
                new String[]{"--offset", "619", "--offset", "620"}));
    }

    @Test void preferenceScopePrecedesTheBodyAndRestoresOnFailure() {
        String previous = System.getProperty("user.dir");
        assertThrows(IllegalStateException.class, () -> S3kKnucklesTerminalCaptureTool.inPreferenceDirectory(output, () -> {
            assertEquals(output.resolve("configuration").toString(), System.getProperty("user.dir"));
            Files.writeString(Path.of(System.getProperty("user.dir")).resolve("config.yaml.example"), "private");
            throw new IllegalStateException("body failure");
        }));
        assertEquals(previous, System.getProperty("user.dir"));
        assertTrue(Files.exists(output.resolve("configuration/config.yaml.example")));
    }

    @Test void existingPreferenceEntryIsRejectedWithoutFollowingIt() throws Exception {
        Files.createDirectory(output.resolve("configuration"));
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> S3kKnucklesTerminalCaptureTool.inPreferenceDirectory(output, () -> fail("must not enter")));
    }
}
