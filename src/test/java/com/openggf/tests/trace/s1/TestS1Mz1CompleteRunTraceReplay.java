package com.openggf.tests.trace.s1;
import com.openggf.trace.*;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;

import java.nio.file.Path;

/**
 * Trace replay test for Sonic 1 Marble Zone Act 1, sliced from the continuous
 * complete-run TAS BK2 ({@code s1-complete-run.bk2}) at a large mid-run BK2
 * offset (20791) rather than from a dedicated per-act recording.
 *
 * <p>Exercises the mid-run bootstrap path: the engine bootstraps the level from
 * a fresh load while the BK2 cursor begins deep in a continuous playthrough.
 * Frame-0 state must match the dedicated {@code mz1_fullrun} trace despite the
 * different BK2 file and offset.
 *
 * <p>Requires a Sonic 1 ROM and the BK2 recording in the trace directory.
 * Skipped when the ROM is unavailable or when the trace directory has no .bk2.
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestS1Mz1CompleteRunTraceReplay extends AbstractTraceReplayTest {

    @Override
    protected SonicGame game() { return SonicGame.SONIC_1; }

    // Engine zone index 1 = Marble Zone (gameplay progression order: GHZ=0, MZ=1,
    // SYZ=2, LZ=3, ... per Sonic1ZoneRegistry). This is NOT the ROM v_zone value
    // (where Marble=2); the trace metadata's zone_id=2 is the ROM convention.
    // Loading zone()=2 here would wrongly load Spring Yard and put the Marble
    // spawn (0x30,0x266) in mid-air. Matches TestS1Mz1TraceReplay.
    @Override
    protected int zone() { return 1; }

    @Override
    protected int act() { return 0; }

    @Override
    protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s1/mz1_completerun");
    }
}
