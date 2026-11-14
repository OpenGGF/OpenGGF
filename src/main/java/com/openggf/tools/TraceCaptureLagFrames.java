package com.openggf.tools;

import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * The original's lag frames in a filmed frame-window clip, for
 * {@code TraceCaptureTool --skip-lag-frames}.
 *
 * <p>A recording is of the original game on its own hardware, which sometimes
 * could not finish a game frame before the next V-blank. On such a lag frame
 * the display went on while gameplay stood still. The replay follows the
 * recording row for row, so a frame-window clip films the same picture twice
 * there, and a viewer reads that as the engine stuttering.
 *
 * <p>This is presentation of a capture only. It decides which driven rows are
 * filmed and reads recorder counters to do so; it drives nothing, writes no
 * engine state and is not consulted by replay comparison.
 */
final class TraceCaptureLagFrames {

    /**
     * The longest hold of the gameplay counter that still counts as lag. In the
     * committed recordings a level loop that fell behind did so for one row, or
     * rarely two. Every longer hold there is five rows or more, and is the game
     * running outside its level loop (a title card, a fade, a load), which is
     * not lag.
     */
    static final int LONGEST_LAG_HOLD = 4;

    /** Lag frames stay in the clip: the capture is what it was before the option. */
    static final TraceCaptureLagFrames KEEP = new TraceCaptureLagFrames(false);

    /** Leaves the original's lag frames out of the clip. */
    static TraceCaptureLagFrames skipLagFrames() {
        return new TraceCaptureLagFrames(true);
    }

    private final boolean skip;
    private final List<Integer> frameRows = new ArrayList<>();
    private final List<Integer> skippedRows = new ArrayList<>();

    private TraceCaptureLagFrames(boolean skip) {
        this.skip = skip;
    }

    /**
     * True when the row at {@code index} is a lag frame of the original, judged
     * from the recording's own counters and never from pictures:
     * <ul>
     *   <li>the replay ran no gameplay frame for the row, so the engine has
     *       nothing new of play to show on it;</li>
     *   <li>{@code vblank_counter} advanced over the row before, so the
     *       original displayed a frame;</li>
     *   <li>{@code gameplay_frame_counter} held, so its level loop did not
     *       complete one; and</li>
     *   <li>the hold is at most {@link #LONGEST_LAG_HOLD} rows long.</li>
     * </ul>
     * {@code lag_counter} is not read: only Sonic 3&amp;K records a real one,
     * and it also counts inside loads, where the gameplay counter is not the
     * clock of what is on screen.
     */
    static boolean isLagRow(TraceExecutionPhase phase, IntFunction<TraceFrame> rows,
            int rowCount, int index) {
        if (phase == TraceExecutionPhase.FULL_LEVEL_FRAME
                || phase == TraceExecutionPhase.FULL_LEVEL_FRAME_WITH_SIDEKICK_ANIMATION_HELD) {
            return false;
        }
        int hold = holdRows(rows, rowCount, index);
        return hold >= 1 && hold <= LONGEST_LAG_HOLD;
    }

    /**
     * Length of the run of held rows containing {@code index}: consecutive rows
     * on which the gameplay counter stood while the V-blank counter advanced.
     * Zero when the row is not held; counting stops once the run is known to be
     * longer than {@link #LONGEST_LAG_HOLD}.
     */
    static int holdRows(IntFunction<TraceFrame> rows, int rowCount, int index) {
        if (!held(rows, rowCount, index)) {
            return 0;
        }
        int length = 1;
        for (int row = index - 1; length <= LONGEST_LAG_HOLD && held(rows, rowCount, row); row--) {
            length++;
        }
        for (int row = index + 1; length <= LONGEST_LAG_HOLD && held(rows, rowCount, row); row++) {
            length++;
        }
        return length;
    }

    private static boolean held(IntFunction<TraceFrame> rows, int rowCount, int index) {
        if (index < 1 || index >= rowCount) {
            return false;
        }
        TraceFrame previous = rows.apply(index - 1);
        TraceFrame current = rows.apply(index);
        return previous.vblankCounter() >= 0 && current.vblankCounter() >= 0
                && current.vblankCounter() != previous.vblankCounter()
                && previous.gameplayFrameCounter() >= 0
                && current.gameplayFrameCounter() == previous.gameplayFrameCounter();
    }

    boolean skipping() {
        return skip;
    }

    /** Whether this capture leaves the trace row at {@code index} out. */
    boolean skips(TraceExecutionPhase phase, TraceData trace, int index) {
        return skip && isLagRow(phase, trace::getFrame, trace.frameCount(), index);
    }

    /** Notes a lag row inside the clip window that was left out. */
    void skipped(int traceRow) {
        if (skip) {
            skippedRows.add(traceRow);
        }
    }

    /** Notes the trace row filmed as the next output frame. */
    void filmed(int traceRow) {
        if (skip) {
            frameRows.add(traceRow);
        }
    }

    /** Trace row of each output frame, in output order. */
    List<Integer> frameRows() {
        return List.copyOf(frameRows);
    }

    /** Lag rows inside the clip window that were left out, in trace order. */
    List<Integer> skippedRows() {
        return List.copyOf(skippedRows);
    }

    /**
     * The output-frame to trace-row mapping as runs of consecutive frames, for
     * example {@code 0-138 -> 4300-4438, 139-142 -> 4440-4443}.
     */
    String describeFrameRows() {
        StringBuilder text = new StringBuilder();
        int runStart = 0;
        for (int frame = 1; frame <= frameRows.size(); frame++) {
            if (frame < frameRows.size()
                    && frameRows.get(frame) == frameRows.get(frame - 1) + 1) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(range(runStart, frame - 1)).append(" -> ")
                    .append(range(frameRows.get(runStart), frameRows.get(frame - 1)));
            runStart = frame;
        }
        return text.toString();
    }

    private static String range(int first, int last) {
        return first == last ? Integer.toString(first) : first + "-" + last;
    }
}
