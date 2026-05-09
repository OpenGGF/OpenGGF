package com.openggf.game.rewind;

/**
 * Strip cache between RewindController and KeyframeStore. Expands one
 * segment of {@code intervalFrames} on demand by stepping forward from
 * a keyframe and capturing per-frame snapshots. Subsequent backward
 * steps within the expanded segment are O(1) array lookups.
 *
 * <p>Keeps a small ring of expanded segments around the rewind cursor so
 * boundary scrubbing can reuse recently-expanded strips instead of replaying
 * both sides of the boundary every frame.
 */
public final class SegmentCache {

    /** Drives the engine forward one frame and returns a fresh snapshot. */
    @FunctionalInterface
    public interface Stepper {
        CompositeSnapshot stepAndCapture();
    }

    private static final int SEGMENT_RING_SIZE = 3;

    private final int intervalFrames;
    private final ExpandedSegment[] segments = new ExpandedSegment[SEGMENT_RING_SIZE];
    private long accessClock = 0;

    public SegmentCache(int intervalFrames) {
        if (intervalFrames <= 0) {
            throw new IllegalArgumentException(
                    "intervalFrames must be > 0, got " + intervalFrames);
        }
        this.intervalFrames = intervalFrames;
    }

    /** Drops any currently-cached segment. */
    public void invalidate() {
        for (int i = 0; i < segments.length; i++) {
            segments[i] = null;
        }
        accessClock = 0;
    }

    /**
     * Returns the snapshot at frame F, expanding segment [K, K+interval)
     * (where K = (F / interval) * interval) if necessary. If F lies in a
     * segment not currently in the small ring, the least-recently-used strip is
     * replaced and re-expanded from the new segment's keyframe (using {@code
     * restoreKeyframe} to bring the engine back to K, then {@code stepper} to
     * advance).
     */
    public CompositeSnapshot snapshotAt(
            int frame,
            CompositeSnapshot keyframeAt,   // base keyframe of segment containing F
            int keyframeFrame,
            Runnable restoreKeyframe,       // restores engine state from keyframeAt
            Stepper stepper) {
        if (frame < keyframeFrame) {
            throw new IllegalArgumentException(
                    "frame " + frame + " < keyframe " + keyframeFrame);
        }
        int offset = frame - keyframeFrame;
        ExpandedSegment segment = findSegment(keyframeFrame);
        if (segment != null && offset <= segment.validUpTo) {
            segment.lastAccess = nextAccess();
            return segment.strip[offset];
        }

        if (segment == null) {
            segment = segmentForExpansion();
            segment.baseFrame = keyframeFrame;
        }
        segment.strip[0] = keyframeAt;
        segment.validUpTo = 0;
        restoreKeyframe.run();
        for (int i = 1; i <= offset; i++) {
            segment.strip[i] = stepper.stepAndCapture();
            segment.validUpTo = i;
        }
        segment.lastAccess = nextAccess();
        return segment.strip[offset];
    }

    private ExpandedSegment findSegment(int baseFrame) {
        for (ExpandedSegment segment : segments) {
            if (segment != null && segment.baseFrame == baseFrame) {
                return segment;
            }
        }
        return null;
    }

    private ExpandedSegment segmentForExpansion() {
        int slot = -1;
        long oldestAccess = Long.MAX_VALUE;
        for (int i = 0; i < segments.length; i++) {
            ExpandedSegment segment = segments[i];
            if (segment == null) {
                slot = i;
                break;
            }
            if (segment.lastAccess < oldestAccess) {
                oldestAccess = segment.lastAccess;
                slot = i;
            }
        }
        ExpandedSegment segment = segments[slot];
        if (segment == null) {
            segment = new ExpandedSegment(intervalFrames);
            segments[slot] = segment;
        }
        return segment;
    }

    private long nextAccess() {
        return ++accessClock;
    }

    private static final class ExpandedSegment {
        private int baseFrame;
        private final CompositeSnapshot[] strip;
        private int validUpTo;
        private long lastAccess;

        private ExpandedSegment(int intervalFrames) {
            this.strip = new CompositeSnapshot[intervalFrames];
        }
    }
}
