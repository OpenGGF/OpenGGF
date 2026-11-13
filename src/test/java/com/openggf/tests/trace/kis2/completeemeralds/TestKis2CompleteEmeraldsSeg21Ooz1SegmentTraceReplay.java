package com.openggf.tests.trace.kis2.completeemeralds;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;

import java.nio.file.Path;

/**
 * Strict independent replay of manifest segment 27, {@code seg21_ooz1},
 * from {@code kis2-full-run-all-emeralds}: movie offset 154845, 12332 recorded rows.
 *
 * <p>Uses the shared entry bootstrap and its one-time carried run-state import.
 * Every row accepted by the sibling replay driver remains compared. A red names
 * the observed frontier; a green is conditional on segment-entry state and does
 * not certify the continuous chain or its round trips.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestKis2CompleteEmeraldsSeg21Ooz1SegmentTraceReplay extends AbstractTraceReplayTest {
    @Override protected SonicGame game() { return SonicGame.SONIC_2; }
    @Override protected int zone() { return 6; }
    @Override protected int act() { return 0; }
    @Override protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/kis2/runs/kis2-full-run-all-emeralds/seg21_ooz1");
    }
}
