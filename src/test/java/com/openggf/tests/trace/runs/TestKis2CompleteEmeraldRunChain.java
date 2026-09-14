package com.openggf.tests.trace.runs;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/** Continuous KiS2 route through all seven emerald stages and Death Egg. */
@RequiresRom(SonicGame.SONIC_2)
class TestKis2CompleteEmeraldRunChain extends AbstractRunChainTest {
    @Test
    void ehz1ThroughAllEmeraldsToDeathEgg() throws Exception {
        String candidate = System.getProperty("openggf.trace.kis2.run.dir");
        Path run = candidate == null
                ? Path.of("src/test/resources/traces/kis2/runs/kis2-full-run-all-emeralds")
                : Path.of(candidate);
        assertChainReplay(run);
    }
}
