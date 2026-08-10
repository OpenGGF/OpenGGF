package com.openggf.tools.audio.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class TestS1AudioParityLuaContract {
    private static final Path LUA = Path.of("/usr/bin/lua");
    private static final Path CONTRACT = Path.of("tools", "bizhawk", "audio", "s1_audio_parity_contract.lua");
    private static final Path HARNESS = Path.of("src", "test", "resources", "bizhawk",
            "s1_audio_parity_contract_test.lua");
    private static final Path VECTOR = Path.of("src", "test", "resources", "audio", "parity", "s1",
            "normalization-contract-v1.json");

    @Test
    void pureLuaContractReproducesSharedHandDerivedGoldenVector() throws Exception {
        // Break caught: the actual dependency-free Lua contract no longer reproduces its cross-language vector.
        Assumptions.assumeTrue(Files.isExecutable(LUA),
                "Lua is unavailable for the behavioral contract test");
        assertTrue(Files.isRegularFile(CONTRACT), "missing pure Lua audio parity contract");
        assertTrue(Files.isRegularFile(HARNESS), "missing pure Lua audio parity harness");
        assertTrue(Files.isRegularFile(VECTOR), "missing cross-language normalization vector");

        Process process = new ProcessBuilder(LUA.toString(), HARNESS.toString(), CONTRACT.toString(),
                VECTOR.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), () -> "S1 audio Lua contract failed:\n" + output);
        assertTrue(output.contains("S1_AUDIO_PARITY_CONTRACT_OK"), output);
    }
}
