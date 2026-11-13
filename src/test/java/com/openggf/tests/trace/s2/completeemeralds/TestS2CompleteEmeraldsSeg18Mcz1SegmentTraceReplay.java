package com.openggf.tests.trace.s2.completeemeralds;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;

import java.nio.file.Path;

/**
 * Strict independent replay of manifest segment 24, {@code seg18_mcz1},
 * from {@code s2-sonic-tails-complete-emeralds}: movie offset 155265, 6213 recorded rows.
 *
 * <p>Uses the shared entry bootstrap and its one-time carried run-state import.
 * Every row accepted by the sibling replay driver remains compared. A red names
 * the observed frontier; a green is conditional on segment-entry state and does
 * not certify the continuous chain or its round trips.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestS2CompleteEmeraldsSeg18Mcz1SegmentTraceReplay extends AbstractTraceReplayTest {
    @Override protected SonicGame game() { return SonicGame.SONIC_2; }
    @Override protected int zone() { return 5; }
    @Override protected int act() { return 0; }
    @Override protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s2/runs/s2-sonic-tails-complete-emeralds/seg18_mcz1");
    }
}
