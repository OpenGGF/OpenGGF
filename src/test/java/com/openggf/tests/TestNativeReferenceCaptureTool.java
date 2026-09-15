package com.openggf.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Checks the shared capture host without a display, emulator or ROM. */
class TestNativeReferenceCaptureTool {
    @Test
    void hostPreservesInputsAndRejectsFailedExports(@TempDir Path output) throws Exception {
        Path log = output.resolve("capture-host-tests.txt");
        Process process = new ProcessBuilder(System.getenv().getOrDefault("PYTHON_BIN", "python3"),
                "tools/bizhawk/test_native_capture.py")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Native capture host contract timed out");
        }
        assertEquals(0, process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
    }
}
