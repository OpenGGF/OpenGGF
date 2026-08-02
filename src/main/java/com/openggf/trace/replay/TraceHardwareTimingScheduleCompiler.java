package com.openggf.trace.replay;

import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.timing.HardwareCompletionEdge;
import com.openggf.trace.timing.HardwareTimingSchedule;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.openggf.game.timing.HardwareServiceBoundary.POST_OBJECTS;

/**
 * Validates that recorded hardware edges have executable production boundaries
 * in the replay row schedule before any live session state is touched.
 */
final class TraceHardwareTimingScheduleCompiler {

    private TraceHardwareTimingScheduleCompiler() {
    }

    static HardwareTimingSchedule compileForInstall(TraceData trace) {
        Objects.requireNonNull(trace, "trace");
        HardwareTimingSchedule schedule = trace.hardwareTimingSchedule();
        if (schedule.schema() != 2 || schedule.edges().isEmpty()) {
            return schedule;
        }

        Map<Integer, Integer> traceIndexByRawFrame = new HashMap<>();
        for (int traceIndex = 0; traceIndex < trace.frameCount(); traceIndex++) {
            traceIndexByRawFrame.put(trace.getFrame(traceIndex).frame(), traceIndex);
        }
        for (HardwareCompletionEdge edge : schedule.edges()) {
            if (edge.boundary() != POST_OBJECTS) {
                continue;
            }
            Integer traceIndex = traceIndexByRawFrame.get(edge.rawFrame());
            if (traceIndex == null) {
                continue;
            }
            TraceReplayRowPolicy row =
                    TraceReplayRowPolicy.resolve(trace, traceIndex, traceIndex);
            if (row.phase() == TraceExecutionPhase.VBLANK_ONLY) {
                throw new IllegalStateException(
                        "unsupported-held-row-POST: raw_frame="
                                + edge.rawFrame()
                                + " has no scheduled object/POST phase"
                                + "; kind=" + edge.kind()
                                + ", ordinal=" + edge.ordinal());
            }
        }
        return schedule;
    }
}
