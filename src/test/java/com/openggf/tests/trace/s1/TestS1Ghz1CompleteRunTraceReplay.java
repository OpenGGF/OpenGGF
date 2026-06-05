package com.openggf.tests.trace.s1;
import com.openggf.trace.*;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;

import java.nio.file.Path;

/**
 * Trace replay test for Sonic 1 Green Hill Zone Act 1, sliced from the
 * continuous complete-run TAS BK2 ({@code s1-complete-run.bk2}) at BK2 offset
 * 788 (the very first gameplay act, but still through the multi-act recorder
 * pipeline).
 *
 * <p>Requires a Sonic 1 ROM and the BK2 recording in the trace directory.
 * Skipped when the ROM is unavailable or when the trace directory has no .bk2.
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestS1Ghz1CompleteRunTraceReplay extends AbstractTraceReplayTest {

    @Override
    protected SonicGame game() { return SonicGame.SONIC_1; }

    @Override
    protected int zone() { return 0; }

    @Override
    protected int act() { return 0; }

    @Override
    protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s1/ghz1_completerun");
    }
}
