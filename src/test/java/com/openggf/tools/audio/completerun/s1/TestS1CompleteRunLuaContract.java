package com.openggf.tools.audio.completerun.s1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class TestS1CompleteRunLuaContract {
    private static final Path LUA = Path.of("/usr/bin/lua");
    private static final Path CONTRACT = Path.of("tools", "bizhawk", "audio",
            "s1_complete_run_audio_contract.lua");
    private static final Path HARNESS = Path.of("tools", "bizhawk", "audio",
            "s1_complete_run_audio_contract_test.lua");

    @Test
    void pureContractCoversCompleteRunQueuePriorityLifecycleAndDacSemantics() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(LUA), "Lua is unavailable");
        assertTrue(Files.isRegularFile(CONTRACT), "missing complete-run S1 Lua contract");
        assertTrue(Files.isRegularFile(HARNESS), "missing complete-run S1 Lua harness");

        Process process = new ProcessBuilder(LUA.toString(), HARNESS.toString(), CONTRACT.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), () -> "S1 complete-run Lua contract failed:\n" + output);
        assertTrue(output.contains("S1_COMPLETE_RUN_AUDIO_CONTRACT_OK"), output);
    }
}
