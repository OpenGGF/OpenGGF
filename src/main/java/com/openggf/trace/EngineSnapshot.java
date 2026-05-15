package com.openggf.trace;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable, defensive projection of engine state captured at frame 0 for
 * comparison against the recorded ROM frame-1 snapshots.
 *
 * <p>Fields here mirror the recorder schema:
 * <ul>
 *     <li>Player position-history rings (x, y, input, status) at 64 entries
 *         each, indexed by the engine's ring-buffer position.</li>
 *     <li>Sidekick CPU view ({@link SidekickCpuView}) - may be {@code null}
 *         when no sidekick is active.</li>
 *     <li>Per-slot object snapshots keyed by slot index ({@link ObjectSnapshot}).</li>
 * </ul>
 *
 * <p>All array references are defensively copied at construction time so the
 * snapshot never aliases live engine state. Callers may freely keep the
 * record across frames without worrying that subsequent engine writes will
 * mutate it.
 */
public record EngineSnapshot(
        short[] playerXHistory,
        short[] playerYHistory,
        short[] playerInputHistory,
        byte[] playerStatusHistory,
        int playerHistoryPos,
        SidekickCpuView tailsCpu,
        Map<Integer, ObjectSnapshot> slotStates) {

    /**
     * Compact constructor enforces defensive copies for the array members and
     * an unmodifiable map view for {@link #slotStates()}.
     */
    public EngineSnapshot {
        playerXHistory = copyOrEmpty(playerXHistory);
        playerYHistory = copyOrEmpty(playerYHistory);
        playerInputHistory = copyOrEmpty(playerInputHistory);
        playerStatusHistory = copyOrEmpty(playerStatusHistory);
        slotStates = slotStates == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(slotStates));
    }

    /**
     * Snapshot of the engine's sidekick CPU controller for cross-reference
     * with the recorded {@code cpu_state_snapshot} event.
     */
    public record SidekickCpuView(
            int controlCounter,
            int respawnCounter,
            int cpuRoutine,
            short targetX,
            short targetY,
            int interactId,
            boolean jumping) {
    }

    /**
     * Minimal per-slot object projection. v1 covers the cardinal SST fields
     * (objectType + x_pos + y_pos + routine + status). Broader SST coverage
     * is deliberately deferred to follow-up work once these five fields are
     * stable across the S2 zone trace set.
     */
    public record ObjectSnapshot(
            int objectType,
            int xPos,
            int yPos,
            int routine,
            int status) {
    }

    /**
     * Factory placeholder. The orchestrator (worker T7) will wire this up to
     * the actual {@code HeadlessTestFixture} / {@code AbstractPlayableSprite}
     * / {@code SidekickCpuController} / {@code ObjectManager} types when the
     * comparator is plumbed into {@code AbstractTraceReplayTest}.
     *
     * <p>The interim implementation accepts the prebuilt parts so the
     * comparator can be exercised in isolation by unit tests without dragging
     * in {@code HeadlessTestFixture}.
     */
    public static EngineSnapshot capture(short[] xHistory, short[] yHistory,
                                         short[] inputHistory, byte[] statusHistory,
                                         int historyPos,
                                         SidekickCpuView tailsCpu,
                                         Map<Integer, ObjectSnapshot> slotStates) {
        return new EngineSnapshot(xHistory, yHistory, inputHistory, statusHistory,
                historyPos, tailsCpu, slotStates);
    }

    private static short[] copyOrEmpty(short[] src) {
        if (src == null) {
            return new short[0];
        }
        short[] dst = new short[src.length];
        System.arraycopy(src, 0, dst, 0, src.length);
        return dst;
    }

    private static byte[] copyOrEmpty(byte[] src) {
        if (src == null) {
            return new byte[0];
        }
        byte[] dst = new byte[src.length];
        System.arraycopy(src, 0, dst, 0, src.length);
        return dst;
    }
}
