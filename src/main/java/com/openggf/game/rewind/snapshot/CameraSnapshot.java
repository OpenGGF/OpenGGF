package com.openggf.game.rewind.snapshot;

import java.util.Objects;

/**
 * Immutable capture of Camera state for rewind snapshots.
 * Captures all mutable gameplay-relevant fields including position, boundaries,
 * scroll delays, freeze state, and wrap tracking. Target sprite references are
 * rebindable via SpriteManager lookups on restore.
 */
@com.openggf.game.ModApi
public record CameraSnapshot(
        short x,
        short y,
        short xCopy,
        short yCopy,
        short minX,
        short minY,
        short maxX,
        short maxY,
        short shakeOffsetX,
        short shakeOffsetY,
        short minXTarget,
        short minYTarget,
        short maxXTarget,
        short maxYTarget,
        short maxXBeforeBoundaryEasing,
        boolean maxYChanging,
        int horizScrollDelayFrames,
        boolean frozen,
        boolean deferHorizontalBoundaryClampOnce,
        boolean deferMaxYWriteUntilAfterUpdate,
        short deferredMaxYValue,
        boolean levelStarted,
        boolean verticalWrapEnabled,
        int verticalWrapRange,
        int verticalWrapMask,
        boolean lastFrameWrapped,
        short wrapDeltaY,
        short yPosBias,
        short fastScrollCap,
        boolean customMaxXBoundaryEasingClaimed) {
    /** Binary-compatible constructor for the Mod API 2.4 snapshot shape. */
    public CameraSnapshot(short x, short y, short minX, short minY, short maxX, short maxY,
            short shakeOffsetX, short shakeOffsetY, short minXTarget, short minYTarget,
            short maxXTarget, short maxYTarget, short maxXBeforeBoundaryEasing,
            boolean maxYChanging, int horizScrollDelayFrames, boolean frozen,
            boolean deferHorizontalBoundaryClampOnce, boolean deferMaxYWriteUntilAfterUpdate,
            short deferredMaxYValue, boolean levelStarted, boolean verticalWrapEnabled,
            int verticalWrapRange, int verticalWrapMask, boolean lastFrameWrapped,
            short wrapDeltaY, short yPosBias, short fastScrollCap) {
        this(x, y, x, y, minX, minY, maxX, maxY, shakeOffsetX, shakeOffsetY,
                minXTarget, minYTarget, maxXTarget, maxYTarget, maxXBeforeBoundaryEasing,
                maxYChanging, horizScrollDelayFrames, frozen, deferHorizontalBoundaryClampOnce,
                deferMaxYWriteUntilAfterUpdate, deferredMaxYValue, levelStarted,
                verticalWrapEnabled, verticalWrapRange, verticalWrapMask, lastFrameWrapped,
                wrapDeltaY, yPosBias, fastScrollCap, false);
    }
}
