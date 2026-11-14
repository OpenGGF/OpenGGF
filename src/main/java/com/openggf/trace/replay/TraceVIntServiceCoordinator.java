package com.openggf.trace.replay;

import com.openggf.game.GameModule;
import com.openggf.game.internal.UnservicedVBlankAdmission;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.timing.VIntServiceTimingReplayPort;
import com.openggf.trace.timing.VIntServiceTimingStreamLoader;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The replay's one reader of the lost-V-INT admission (hardware-timing contract, 2026-11-13 extension): it loads
 * the trace's {@code vint_service_timing.jsonl} into {@link VIntServiceTimingReplayPort}, checks every event
 * against the replay's existing execution phase ({@link TraceReplayBootstrap#phaseForReplay}: a lost V-INT can
 * only fall on a VBlank-only row) and the running game's {@link UnservicedVBlankAdmission}, and then tells the
 * row driver, row by row, whether that row's VBlank is serviced.
 *
 * <p>An unserviced row runs nothing: no V-INT work (counter, water, boredom or warp timer) and no main loop; the
 * driver only consumes its BK2 input row. A trace without the stream was recorded without V-INT entry observation
 * and services every row as before.
 */
public final class TraceVIntServiceCoordinator {
    private final VIntServiceTimingReplayPort port;

    private TraceVIntServiceCoordinator(VIntServiceTimingReplayPort port) {
        this.port = port;
    }

    /** Loads and validates the stream beside {@code trace}; fails loading on an inadmissible event. */
    public static TraceVIntServiceCoordinator open(Path traceDirectory, TraceData trace, GameModule host)
            throws IOException {
        return new TraceVIntServiceCoordinator(VIntServiceTimingReplayPort.install(
                VIntServiceTimingStreamLoader.load(traceDirectory, trace.metadata()),
                UnservicedVBlankAdmission.forHost(host),
                row -> row > 0 && row < trace.frameCount()
                        && TraceReplayBootstrap.phaseForReplay(trace, trace.getFrame(row - 1), trace.getFrame(row))
                        == TraceExecutionPhase.VBLANK_ONLY));
    }

    /** Whether the trace carries the stream (its recorder observed V-INT entry). */
    public boolean recorded() {
        return port.recorded();
    }

    /** Whether row {@code row}'s VBlank is serviced; ask each replayed row once, in order. */
    public boolean vBlankServiced(int row) {
        return port.vBlankServiced(row);
    }
}
