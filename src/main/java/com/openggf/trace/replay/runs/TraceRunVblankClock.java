package com.openggf.trace.replay.runs;

import com.openggf.game.profiles.trace.TracePlaybackProfile;
import com.openggf.trace.TraceRunManifest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Carries the ROM-visible object VBlank clock across shortened run transitions.
 * Targets derive only from the production source clock and manifest/BK2 row
 * distances; no comparison frame value is read into gameplay state.
 */
public final class TraceRunVblankClock {
    private final TracePlaybackProfile profile;
    private final Map<Integer, SourceAnchor> levelSourceTails = new HashMap<>();

    public TraceRunVblankClock(TracePlaybackProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public void captureLevelSourceTail(
            int segmentIndex,
            TraceRunManifest.Segment source,
            int observedBk2Cursor,
            int observedVblank) {
        if (!profile.alignsInterLevelVblank()
                && !profile.alignUncomparedInteriorReturnVblank()) {
            return;
        }
        Objects.requireNonNull(source, "source");
        if (!"level".equals(source.kind())) {
            return;
        }
        levelSourceTails.put(segmentIndex, new SourceAnchor(
                source,
                TraceRunReplayWalker.sourceTailVblankAtBoundary(
                        source, observedBk2Cursor, observedVblank)));
    }

    public OptionalInt levelDestinationTarget(
            int sourceIndex,
            TraceRunManifest.Segment source,
            TraceRunManifest.Segment destination,
            int rowsConsumed) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (!profile.alignsInterLevelVblank()
                || !"level".equals(source.kind())
                || !"level".equals(destination.kind())) {
            return OptionalInt.empty();
        }
        SourceAnchor sourceAnchor = sourceAnchor(sourceIndex, source);
        if (sourceAnchor == null) {
            return OptionalInt.empty();
        }
        int ticks = TraceRunReplayWalker.interLevelVblankBudget(
                source, destination, rowsConsumed,
                profile.interLevelNonAdvancingMovieRows());
        return OptionalInt.of(Math.addExact(sourceAnchor.tailVblank(), ticks));
    }

    public OptionalInt uncomparedInteriorReturnTarget(
            int sourceIndex,
            TraceRunManifest.Segment source,
            TraceRunManifest.Segment destination) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (!profile.alignUncomparedInteriorReturnVblank()
                || !"level".equals(source.kind())
                || !"level".equals(destination.kind())) {
            return OptionalInt.empty();
        }
        SourceAnchor sourceAnchor = sourceAnchor(sourceIndex, source);
        if (sourceAnchor == null) {
            return OptionalInt.empty();
        }
        int ticks = TraceRunReplayWalker.uncomparedInteriorReturnVblankBudget(
                source, destination);
        return OptionalInt.of(Math.addExact(sourceAnchor.tailVblank(), ticks));
    }

    private SourceAnchor sourceAnchor(
            int sourceIndex, TraceRunManifest.Segment source) {
        SourceAnchor sourceAnchor = levelSourceTails.get(sourceIndex);
        if (sourceAnchor != null && !sourceAnchor.segment().equals(source)) {
            throw new IllegalArgumentException(
                    "source segment does not match captured index " + sourceIndex);
        }
        return sourceAnchor;
    }

    private record SourceAnchor(
            TraceRunManifest.Segment segment, int tailVblank) {
    }
}
