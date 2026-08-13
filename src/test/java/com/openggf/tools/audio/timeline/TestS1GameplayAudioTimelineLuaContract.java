package com.openggf.tools.audio.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class TestS1GameplayAudioTimelineLuaContract {
    private static final Path LUA = Path.of("/usr/bin/lua");
    private static final Path CONTRACT = Path.of("tools", "bizhawk", "audio", "s1_gameplay_audio_timeline_contract.lua");
    private static final Path HARNESS = Path.of("src", "test", "resources", "bizhawk",
            "s1_gameplay_audio_timeline_contract_test.lua");

    @Test
    void pureLuaTimelineContractPreservesQueueAndContentionSemantics() throws Exception {
        // Break caught: the dependency-free ROM timeline contract loses request correlation or ownership semantics.
        Assumptions.assumeTrue(Files.isExecutable(LUA), "Lua is unavailable for the behavioral contract test");
        assertTrue(Files.isRegularFile(CONTRACT), "missing pure Lua gameplay-audio timeline contract");
        assertTrue(Files.isRegularFile(HARNESS), "missing pure Lua gameplay-audio timeline harness");

        Process process = new ProcessBuilder(LUA.toString(), HARNESS.toString(), CONTRACT.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), () -> "S1 gameplay-audio Lua contract failed:\n" + output);
        assertTrue(output.contains("S1_GAMEPLAY_AUDIO_TIMELINE_CONTRACT_OK"), output);
    }
}
