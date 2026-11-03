package com.openggf.tests.trace.runs;

import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Main-loop admission over a recorded interior tail. The Gumball segment of the
 * Mega run ends with the return's terrain wait, the LoadLevelLoadBlock2 lag
 * frames and the masked j_LevelSetup rows; the tail is expressible as lag
 * admission only from the first row the main loop never ran on.
 */
class TestUnadmittedRecordedTail {
    private static final Path GUMBALL = Path.of("src", "test", "resources",
            "traces", "s3k", "runs", "s3-knux-multibonus-ss", "gumball");

    @Test
    void lagAndMaskedRowsFormTheExpressibleTail() throws Exception {
        TraceData trace = TraceData.load(GUMBALL);
        int firstLagRow = firstLagRowOfTail(trace);
        List<AbstractRunChainTest.RecordedTailRow> rows =
                AbstractRunChainTest.unadmittedRecordedTail(trace, firstLagRow);
        assertEquals(trace.frameCount() - firstLagRow, rows.size());
        long vints = rows.stream()
                .filter(AbstractRunChainTest.RecordedTailRow::vintServiced).count();
        long masked = rows.size() - vints;
        assertEquals(trace.getFrame(trace.frameCount() - 1).vblankCounter()
                        - trace.getFrame(firstLagRow - 1).vblankCounter(),
                vints, "each lag row services exactly the V-int it recorded");
        assertEquals(true, masked > 0, "the masked j_LevelSetup rows service none");
    }

    @Test
    void aRowWaitingInALoopMakesTheTailInexpressible() throws Exception {
        TraceData trace = TraceData.load(GUMBALL);
        int firstLagRow = firstLagRowOfTail(trace);
        assertNull(AbstractRunChainTest.unadmittedRecordedTail(trace, firstLagRow - 1),
                "the final loc_7870 row waited in Wait_VSync");
        assertNull(AbstractRunChainTest.unadmittedRecordedTail(trace, 0));
        assertNull(AbstractRunChainTest.unadmittedRecordedTail(trace, trace.frameCount()));
    }

    /** First row of the trailing run of rows on which no Wait_VSync returned. */
    private static int firstLagRowOfTail(TraceData trace) {
        int row = trace.frameCount() - 1;
        while (row > 1 && AbstractRunChainTest.unadmittedRecordedTail(trace, row - 1) != null) {
            row--;
        }
        return row;
    }
}
