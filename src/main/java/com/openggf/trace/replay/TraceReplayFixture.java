package com.openggf.trace.replay;

import com.openggf.game.session.GameplayModeContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.timing.HardwareTimingReplayPort;
import com.openggf.trace.timing.HardwareTimingSchedule;

/**
 * Narrow view of a fixture capable of driving trace replay. Implemented
 * by {@code HeadlessTestFixture} in tests and by the live launcher's
 * internal adapter at runtime.
 */
public interface TraceReplayFixture {
    AbstractPlayableSprite sprite();

    GameplayModeContext gameplayMode();

    /** Registers the replay port and its stateless production-boundary observer. */
    void installHardwareTimingReplay(HardwareTimingReplayPort replayPort);

    /** Latches the trace row's physical raw frame before any row work or retry. */
    void beginTraceRow(int traceIndex, int rawFrame);

    /** Deactivates recorded edge authority while no trace row is represented. */
    void enterHardwareTimingGap();

    /** Verifies that the current segment consumed every recorded completion edge. */
    void verifyHardwareTimingSegmentEdges();

    /** Validates and installs the next structural segment's completion schedule. */
    void handoffHardwareTimingReplay(HardwareTimingSchedule nextSchedule);

    /** Final-run verification, admission closure, observer removal, and deregistration. */
    void closeHardwareTimingReplayRun();

    /**
     * Closes the current production dynamic-art comparison segment at a
     * structural replay boundary. This seam carries no expected trace value.
     */
    default void closeDynamicArtComparisonSegment() {
        gameplayMode().endDynamicArtComparisonSegment();
    }

    /** Run one gameplay tick using the next BK2 input. Returns the mask. */
    int stepFrameFromRecording();

    /** Advance BK2 without stepping gameplay (lag frame). Returns the mask. */
    int skipFrameFromRecording();

    /** Advance only the playable animation slice proven by a native mid-loop trace hook. */
    void advancePlayableAnimationsOnly();

    /** Hold the first CPU sidekick's next Animate dispatch while running the full tick. */
    void suppressFirstSidekickAnimationOnce();

    /** Consume one BK2 frame without stepping gameplay or timing counters. Returns the mask. */
    int consumeRecordingFrameInputOnly();

    /** Advance the BK2 cursor by N frames, no gameplay ticks. */
    void advanceRecordingCursor(int frameCount);

    /**
     * Returns the BK2 input mask at the given offset from the current cursor
     * without advancing the cursor or mutating gameplay state. Offset 0 is
     * the next frame {@link #stepFrameFromRecording} would consume; negative
     * offsets read prior BK2 frames (e.g. the last title-card frame at
     * offset -1). Returns -1 when no BK2 movie is loaded or the requested
     * frame is out of range.
     */
    default int peekRecordingInputAt(int offset) {
        return -1;
    }
}
