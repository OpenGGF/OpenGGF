package com.openggf.tests.trace.s2;

import com.openggf.tests.RomTestUtils;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.SpecialStageTraceData;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class S2SpecialStageFinishBoundaryMappingTest {

    @Test
    void lagObservedFinishConsumesExactCapturedCompletedPass() throws Exception {
        File romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null && Files.exists(Path.of("s2.gen")),
                "s2.gen ROM required for S2 special-stage finish mapping");
        Path dir = AbstractS2SpecialStageTraceReplayTest.TRACE_DIRECTORY;
        SpecialStageTraceData trace = SpecialStageTraceData.load(dir);
        S2SpecialStageReplayHarness harness =
                AbstractS2SpecialStageTraceReplayTest.bootHarness(trace, dir, romFile);

        DivergenceReport report =
                AbstractS2SpecialStageTraceReplayTest.compareReplay(trace, harness);

        assertFalse(report.hasErrors(), report.toAssertionSummary());
        assertTrue(report.getContextWindow(5181, 0).contains("rings_togo_bcd"),
                "the raw finish observation must retain its final rings-to-go comparison");
        assertEquals(1, java.util.Collections.frequency(
                harness.steppedPassSequencesForTest(), 2990));
    }

    @Test
    void finishingBeforeTerminalPassIsAnErrorAndStillConsumesTerminalExactlyOnce() throws Exception {
        File romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null && Files.exists(Path.of("s2.gen")),
                "s2.gen ROM required for S2 special-stage finish mapping");
        Path dir = AbstractS2SpecialStageTraceReplayTest.TRACE_DIRECTORY;
        SpecialStageTraceData trace = SpecialStageTraceData.load(dir);
        S2SpecialStageReplayHarness harness =
                AbstractS2SpecialStageTraceReplayTest.bootHarness(trace, dir, romFile);
        harness.forceFinishedAfterPassForTest(2989);

        DivergenceReport report =
                AbstractS2SpecialStageTraceReplayTest.compareReplay(trace, harness);

        assertTrue(report.hasErrors(), "finishing before terminal pass must not compare green");
        assertTrue(report.toAssertionSummary().contains("before-terminal-pass@5180"),
                report.toAssertionSummary());
        assertEquals(1, java.util.Collections.frequency(
                harness.steppedPassSequencesForTest(), 2990),
                "terminal pass must still be consumed through its exact BK2 identity");
    }
}
