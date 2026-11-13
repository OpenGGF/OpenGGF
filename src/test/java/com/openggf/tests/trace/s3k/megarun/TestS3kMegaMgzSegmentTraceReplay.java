package com.openggf.tests.trace.s3k.megarun;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;
import org.junit.jupiter.api.Tag;

import java.nio.file.Path;

/**
 * Strict independent replay of manifest segment 20, {@code mgz},
 * from {@code s3-knux-multibonus-ss}: movie offset 84241, 8721 recorded rows.
 *
 * <p>Uses the shared entry bootstrap and its one-time carried run-state import.
 * Every row accepted by the sibling replay driver remains compared. A red names
 * the observed frontier; a green is conditional on segment-entry state and does
 * not certify the continuous chain or its round trips.
 */
@RequiresRom(SonicGame.SONIC_3K)
@Tag("trace-scope-r7")
public class TestS3kMegaMgzSegmentTraceReplay extends AbstractTraceReplayTest {
    @Override protected SonicGame game() { return SonicGame.SONIC_3K; }
    @Override protected int zone() { return 2; }
    @Override protected int act() { return 0; }
    @Override protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s3k/runs/s3-knux-multibonus-ss/mgz");
    }
}
