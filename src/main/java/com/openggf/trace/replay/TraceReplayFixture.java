package com.openggf.trace.replay;

import com.openggf.game.session.GameplayModeContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Narrow view of a fixture capable of driving trace replay. Implemented
 * by {@code HeadlessTestFixture} in tests and by the live launcher's
 * internal adapter at runtime.
 */
public interface TraceReplayFixture {
    AbstractPlayableSprite sprite();

    GameplayModeContext gameplayMode();

    /** Run one gameplay tick using the next BK2 input. Returns the mask. */
    int stepFrameFromRecording();

    /** Advance BK2 without stepping gameplay (lag frame). Returns the mask. */
    int skipFrameFromRecording();

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
