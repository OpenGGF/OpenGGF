package com.openggf.tools.audio.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class TestS1Ghz1OpenGgfAudioTimelineCapture {
    private static final String RUN_PROPERTY = "s1.audio.timeline.run.path";
    private static final String OUTPUT_PROPERTY = "s1.audio.timeline.output";

    @Test
    void captureRequestedOutput() throws Exception {
        // Break caught: the local producer accepts an incomplete request or needs a reference timeline.
        String run = System.getProperty(RUN_PROPERTY);
        String output = System.getProperty(OUTPUT_PROPERTY);
        Assumptions.assumeTrue(run != null || output != null,
                "no local OpenGGF timeline capture was requested");
        Path[] paths = requestedPaths(run, output);
        S1Ghz1OpenGgfAudioTimelineCapture.capture(paths[0], paths[1]);
        assertTrue(Files.isRegularFile(paths[1]));
        try (S1GameplayAudioTimelineJsonl.Reader reader = S1GameplayAudioTimelineJsonl.read(paths[1])) {
            assertEquals(S1GameplayAudioTimeline.OPENGGF_CAPTURE, reader.metadata().capture());
            int frames = 0;
            while (reader.hasNext()) {
                if (reader.next() instanceof S1GameplayAudioTimeline.Frame) {
                    frames++;
                }
            }
            assertEquals(4115, frames);
        }
    }

    @Test
    void rejectsPartialPropertyPair() {
        assertThrows(IllegalArgumentException.class, () -> requestedPaths("run", null));
        assertThrows(IllegalArgumentException.class, () -> requestedPaths(null, "output"));
    }

    private static Path[] requestedPaths(String run, String output) {
        if (run == null || output == null) {
            throw new IllegalArgumentException(RUN_PROPERTY + " and " + OUTPUT_PROPERTY
                    + " must be provided together");
        }
        return new Path[] {Path.of(run), Path.of(output)};
    }
}
