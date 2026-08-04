package com.openggf.trace.replay.runs;

import com.openggf.game.profiles.trace.TracePlaybackProfile;
import com.openggf.trace.TraceRunManifest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceRunVblankClock {

    @Test
    void levelDestinationsUseTheSameManifestMovieBudgetAsHeadlessChains() {
        TraceRunManifest.Segment ghz1 = level("ghz1", 788, 5_598, 1);
        TraceRunManifest.Segment ghz2 = level("ghz2", 6_622, 4_028, 2);
        TraceRunManifest.Segment ghz3 = level("ghz3", 10_885, 9_678, 3);
        TraceRunVblankClock clock = new TraceRunVblankClock(
                TracePlaybackProfile.SONIC_1);

        clock.captureLevelSourceTail(0, ghz1, 6_386, 0x17B7);
        assertEquals(0x17B7 + 230,
                clock.levelDestinationTarget(0, ghz1, ghz2, 0).orElseThrow());

        clock.captureLevelSourceTail(1, ghz2, 10_650, 0x2850);
        assertEquals(0x2850 + 229,
                clock.levelDestinationTarget(1, ghz2, ghz3, 0).orElseThrow());
    }

    @Test
    void disabledProfilesNeverRewriteTheProductionClock() {
        TraceRunManifest.Segment act1 = level("act1", 10, 20, 1);
        TraceRunManifest.Segment act2 = level("act2", 40, 20, 2);
        TraceRunVblankClock clock = new TraceRunVblankClock(
                TracePlaybackProfile.DISABLED);

        clock.captureLevelSourceTail(0, act1, 30, 1234);

        assertTrue(clock.levelDestinationTarget(0, act1, act2, 0).isEmpty());
    }

    @Test
    void specialStageReturnUsesThePreservedSourceLevelAnchor() {
        TraceRunManifest.Segment source = level("ghz1", 100, 10, 1);
        TraceRunManifest.Segment returned = level("ghz2", 140, 10, 2);
        TraceRunVblankClock clock = new TraceRunVblankClock(
                TracePlaybackProfile.SONIC_1);

        clock.captureLevelSourceTail(0, source, 110, 0x500);

        assertEquals(0x500 + 31,
                clock.uncomparedInteriorReturnTarget(
                        0, source, returned).orElseThrow());
    }

    private static TraceRunManifest.Segment level(
            String dir, int offset, int frames, int act) {
        return new TraceRunManifest.Segment(
                dir, "level", "complete_run", offset, frames,
                0, act, null, null);
    }
}
