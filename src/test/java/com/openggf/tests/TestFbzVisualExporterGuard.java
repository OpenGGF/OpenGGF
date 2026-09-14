package com.openggf.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises the native reference exporter's evidence boundary with Lua API stubs. */
class TestFbzVisualExporterGuard {
    @Test
    void nativeExporterObservesDomainsAndRejectsUnverifiedDisplay(@TempDir Path output)
            throws Exception {
        String lua = System.getenv().getOrDefault("LUA_BIN", "lua");
        Path log = output.resolve("lua-result.txt");
        Process process = new ProcessBuilder(lua,
                "src/test/resources/bizhawk/fbz_visual_exporter_contract_test.lua",
                "tools/bizhawk/capture_fbz_visual_references.lua", output.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("FBZ exporter Lua contract timed out");
        }
        assertEquals(0, process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
    }

    @Test
    void framebufferProbeRejectsBlankAndBorderOnlyImages(@TempDir Path output)
            throws Exception {
        String python = System.getenv().getOrDefault("PYTHON_BIN", "python3");
        Path log = output.resolve("python-result.txt");
        Process process = new ProcessBuilder(python,
                "src/test/resources/bizhawk/fbz_framebuffer_probe_test.py")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("FBZ framebuffer probe contract timed out");
        }
        assertEquals(0, process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
    }

}
