package com.openggf.trace;

import java.util.List;
import java.util.Map;

/**
 * Test-only factories for building {@link TraceData} / {@link TraceMetadata}
 * values without disk I/O. Kept in {@code src/test/} so production code
 * never depends on these helpers. Lives in the same Java package as the
 * types it constructs so it can call their package-private constructors.
 */
public final class TraceFixtures {

    private TraceFixtures() {
    }

    /** In-memory TraceData for unit tests. */
    public static TraceData trace(TraceMetadata metadata, List<TraceFrame> frames) {
        return new TraceData(metadata, List.copyOf(frames), Map.of());
    }

    /** In-memory TraceData for unit tests with explicit aux events. */
    public static TraceData trace(TraceMetadata metadata, List<TraceFrame> frames,
                                  Map<Integer, List<TraceEvent>> eventsByFrame) {
        return new TraceData(metadata, List.copyOf(frames), Map.copyOf(eventsByFrame));
    }

    /** Minimal metadata stub for unit tests. */
    public static TraceMetadata metadata(String gameId, int zoneId, int act) {
        return metadata(gameId, zoneId, act, null);
    }

    /** Minimal metadata stub with an explicit frame-0 RNG seed. */
    public static TraceMetadata metadataWithRngSeed(String gameId, int zoneId, int act, String rngSeedHex) {
        return metadata(gameId, zoneId, act, rngSeedHex);
    }

    private static TraceMetadata metadata(String gameId, int zoneId, int act, String rngSeedHex) {
        return new TraceMetadata(
                gameId,
                "TEST",
                zoneId,
                act,
                0,
                0,
                "0x0000",
                "0x0000",
                null,
                null,
                3,
                null,
                null,
                null,
                null,
                null /* aux_schema_extras */,
                null,
                null,
                null,
                null,
                null,
                null,
                "sonic",
                List.of(),
                0,
                rngSeedHex,
                null,
                null,
                null,
                null);
    }
}
