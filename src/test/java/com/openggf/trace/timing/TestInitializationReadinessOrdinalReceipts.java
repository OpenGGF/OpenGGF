package com.openggf.trace.timing;

import com.openggf.game.timing.RecordedOrdinalSpan;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static com.openggf.game.timing.HardwareServiceBoundary.POST_OBJECTS;
import static com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE;
import static org.junit.jupiter.api.Assertions.*;

class TestInitializationReadinessOrdinalReceipts {
    private static final String A = "sha256:" + "a".repeat(64), B = "sha256:" + "b".repeat(64), C = "sha256:" + "c".repeat(64);

    @Test void dedicatedReadinessSplitsClaimedPrefixFromUnconsumedSuffix() {
        var receipts = Map.of(KOS_MODULE_QUEUE, new RecordedOrdinalSpan(5, 7, List.of(A, B, C)));
        var edge = new HardwareCompletionEdge(100, POST_OBJECTS, KOS_MODULE_QUEUE, 6, B);
        var split = HardwareTimingInterstitialSpans.splitInitializationReadiness(receipts, new HardwareTimingSchedule(List.of(edge)));
        assertEquals(new RecordedOrdinalSpan(5, 5, List.of(A)), split.prefix().get(KOS_MODULE_QUEUE));
        assertEquals(new RecordedOrdinalSpan(7, 7, List.of(C)), split.remainder().get(KOS_MODULE_QUEUE));
        assertEquals(3, receipts.get(KOS_MODULE_QUEUE).submissionFingerprints().size());
    }

    @Test void rejectsWrongCartridgeJobAndOrdinalHolesRatherThanAdvancingPastThem() {
        var receipts = Map.of(KOS_MODULE_QUEUE, new RecordedOrdinalSpan(5, 7, List.of(A, B, C)));
        assertThrows(IllegalArgumentException.class, () -> HardwareTimingInterstitialSpans.splitInitializationReadiness(receipts,
                new HardwareTimingSchedule(List.of(new HardwareCompletionEdge(100, POST_OBJECTS, KOS_MODULE_QUEUE, 6, A)))));
        assertThrows(IllegalArgumentException.class, () -> HardwareTimingInterstitialSpans.splitInitializationReadiness(receipts,
                new HardwareTimingSchedule(List.of(new HardwareCompletionEdge(100, POST_OBJECTS, KOS_MODULE_QUEUE, 5, A),
                        new HardwareCompletionEdge(101, POST_OBJECTS, KOS_MODULE_QUEUE, 7, C)))));
        assertThrows(IllegalArgumentException.class, () -> HardwareTimingInterstitialSpans.splitInitializationReadiness(
                Map.of(KOS_MODULE_QUEUE, new RecordedOrdinalSpan(5, 7)),
                new HardwareTimingSchedule(List.of(new HardwareCompletionEdge(100, POST_OBJECTS, KOS_MODULE_QUEUE, 6, B)))));
    }
}
