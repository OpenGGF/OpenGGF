package com.openggf.tests.trace.kis2.completeemeralds;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;

import java.nio.file.Path;

/**
 * Strict independent replay of manifest segment 31, {@code seg25_mtz3},
 * from {@code kis2-full-run-all-emeralds}: movie offset 206299, 13804 recorded rows.
 *
 * <p>Uses the shared entry bootstrap and its one-time carried run-state import.
 * Every row accepted by the sibling replay driver remains compared. A red names
 * the observed frontier; a green is conditional on segment-entry state and does
 * not certify the continuous chain or its round trips.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestKis2CompleteEmeraldsSeg25Mtz3SegmentTraceReplay extends AbstractTraceReplayTest {
    @Override protected SonicGame game() { return SonicGame.SONIC_2; }
    @Override protected int zone() { return 7; }
    @Override protected int act() { return 2; }
    @Override protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/kis2/runs/kis2-full-run-all-emeralds/seg25_mtz3");
    }
}
