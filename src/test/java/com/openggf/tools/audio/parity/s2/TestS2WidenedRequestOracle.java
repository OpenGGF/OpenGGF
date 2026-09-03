package com.openggf.tools.audio.parity.s2;

import com.openggf.tests.SessionInvocationExtension;
import com.openggf.tests.trace.runs.S2RequestProjectionBk2TestBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Widened request-aware measurement: every committed window cut from the
 * complete run that the run-chain harness can replay is compared against the
 * engine's own request projection, from one capture that spans them all. The
 * committed payloads are comparison-only; nothing here hydrates engine state,
 * and nothing here fixes an engine divergence.
 */
class TestS2WidenedRequestOracle {

    /** The widest movie row the committed run chain replays in one capture. */
    private static final int CAPTURE_FIRST_ROW = 10_150;
    private static final int CAPTURE_EXCLUSIVE_END = 12_400;

    @TempDir
    Path temporaryDirectory;

    @Test
    @ExtendWith(SessionInvocationExtension.class)
    void everyReplayableWindowComparesAgainstTheEnginesOwnRequests()
            throws Exception {
        String romProperty = System.getProperty("sonic2.rom.path");
        String bk2Property = System.getProperty("s2.request.bk2.path");
        assumeTrue(romProperty != null && bk2Property != null,
                "explicit ROM and BK2 paths are required");

        S2RequestProjectionBk2TestBridge.Capture capture =
                S2RequestProjectionBk2TestBridge.capture(
                        Path.of(romProperty), Path.of(bk2Property),
                        CAPTURE_FIRST_ROW, CAPTURE_EXCLUSIVE_END);

        for (S2PublishedRequestWindows.Published published
                : S2PublishedRequestWindows.COMPLETE_RUN_WINDOWS) {
            if (published.window().exclusiveEnd() > CAPTURE_EXCLUSIVE_END) {
                continue;
            }
            S2RequestAwareCandidateComparator.Report report =
                    S2RequestAwareCandidateComparator.compareWindow(
                            published.expand(temporaryDirectory),
                            published.window(), capture.projector(),
                            capture.requestRows());
            System.out.println("MEASUREMENT_ONLY " + published.name() + " "
                    + report.describe());
            assertNotEquals(S2RequestAwareCandidateComparator.Kind.INVALID,
                    report.kind(), published.name() + ": " + report.describe());
        }
    }

    /**
     * A deliberately corrupted candidate must break the comparison. Without
     * this, a window that never actually compared would read exactly like a
     * window that agreed.
     */
    @Test
    void aCorruptedCandidateBreaksTheComparison() throws Exception {
        S2PublishedRequestWindows.Published published =
                S2PublishedRequestWindows.EHZ1_CONTINUATION;
        Path expanded = published.expand(temporaryDirectory);
        byte[] payload = java.nio.file.Files.readAllBytes(expanded);
        int index = new String(payload, java.nio.charset.StandardCharsets.UTF_8)
                .indexOf("\"first_row\":10900");
        assertNotEquals(-1, index, "the window bound must appear in the payload");
        Path corrupted = temporaryDirectory.resolve("corrupted.jsonl");
        java.nio.file.Files.writeString(corrupted,
                new String(payload, java.nio.charset.StandardCharsets.UTF_8)
                        .replaceFirst("\"first_row\":10900", "\"first_row\":10901"));

        S2RequestAwareCandidateComparator.Report report =
                S2RequestAwareCandidateComparator.compareWindow(corrupted,
                        published.window(),
                        new com.openggf.tools.audio.completerun.s2
                                .S2ProductionRequestProjector(),
                        java.util.List.of());

        assertEquals(S2RequestAwareCandidateComparator.Kind.INVALID,
                report.kind(), report.describe());
    }
}
