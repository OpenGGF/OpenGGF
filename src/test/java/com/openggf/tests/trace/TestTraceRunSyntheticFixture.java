package com.openggf.tests.trace;

import com.openggf.trace.TraceData;
import com.openggf.trace.TraceRunManifest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestTraceRunSyntheticFixture {

    private static final Path RUN_DIR =
        Path.of("src", "test", "resources", "traces", "synthetic", "run_aiz_gumball_3seg");

    @Test
    void syntheticRunLoadsValidatesAndSegmentsParse() throws Exception {
        TraceRunManifest run = TraceRunManifest.load(RUN_DIR.resolve("run_manifest.json"));
        run.validate(RUN_DIR);
        assertEquals(3, run.segments().size());
        // The bonus segment parses through the EXISTING level trace loader —
        // this is the spec's "bonus reuses CSV v7 level schema" contract.
        for (TraceRunManifest.Segment seg : run.segments()) {
            TraceData data = TraceData.load(RUN_DIR.resolve(seg.dir()));
            assertEquals(seg.traceFrameCount(), data.frameCount(),
                "segment " + seg.dir());
        }
        // Gap between segments (transition frames) is represented ONLY in the
        // manifest, never as CSV rows: each segment's row span must end before
        // the next segment's offset.
        for (int i = 0; i < run.segments().size() - 1; i++) {
            TraceRunManifest.Segment a = run.segments().get(i);
            TraceRunManifest.Segment b = run.segments().get(i + 1);
            assertTrue(a.bk2FrameOffset() + a.traceFrameCount() <= b.bk2FrameOffset(),
                "segment " + i + " rows overlap next segment's offset");
        }
    }
}
