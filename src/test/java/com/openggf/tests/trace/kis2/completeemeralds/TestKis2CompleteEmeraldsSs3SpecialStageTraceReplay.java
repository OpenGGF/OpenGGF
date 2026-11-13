package com.openggf.tests.trace.kis2.completeemeralds;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.s2.AbstractS2SpecialStageTraceReplayTest;
import java.nio.file.Path;

/** Strict independent replay of the recorded solo Knuckles special-stage segment. */
@RequiresRom(SonicGame.SONIC_2)
public class TestKis2CompleteEmeraldsSs3SpecialStageTraceReplay extends AbstractS2SpecialStageTraceReplayTest {
    @Override protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/kis2/runs/kis2-full-run-all-emeralds/ss_3");
    }
}
