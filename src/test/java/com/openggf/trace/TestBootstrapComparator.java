package com.openggf.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.openggf.level.objects.RomObjectSnapshot;

/**
 * Frame-0 bootstrap comparator. Verifies engine state at frame 0 already
 * matches the recorded frame-1 snapshots when native-prelude-mode is on.
 *
 * <p>Native-prelude mode is being added to {@link TraceMetadata} by worker T3.
 * Until that lands, the comparator routes through a package-private override
 * on {@link TraceBinder} so these synthetic tests can flip the flag without
 * depending on T3's record changes.
 */
class TestBootstrapComparator {

    @BeforeEach
    void enableNativePreludeForTest() {
        TraceBinder.setNativePreludeOverrideForTests(true);
    }

    @AfterEach
    void resetOverride() {
        TraceBinder.setNativePreludeOverrideForTests(null);
    }

    /**
     * When the trace metadata advertises native-prelude mode and every
     * recorded snapshot lines up with the engine projection, the comparator
     * must return no divergences.
     */
    @Test
    void synthetic_matching_snapshot_yields_empty_divergences() {
        TraceData trace = traceWithSnapshots(
                /* historyPos */ 0x34,
                /* xHistory  */ shorts(64, 0x0500),
                /* yHistory  */ shorts(64, 0x0300),
                /* inputHist */ shorts(64, 0x0000),
                /* statusHist*/ bytes(64, (byte) 0x00),
                cpu("tails", 1, 0, 2, (short) 0x0480, (short) 0x0320, 0x0000, false),
                List.of(objectSnapshot(0, 0x10, 0x0400, 0x0200, 0x02, 0x40)));

        EngineSnapshot snapshot = new EngineSnapshot(
                shorts(64, 0x0500),
                shorts(64, 0x0300),
                shorts(64, 0x0000),
                bytes(64, (byte) 0x00),
                12,
                new EngineSnapshot.SidekickCpuView(1, 0, 2, (short) 0x0480, (short) 0x0320, 0x0000, false),
                Map.of(0, new EngineSnapshot.ObjectSnapshot(0x10, 0x0400, 0x0200, 0x02, 0x40)));

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        List<BootstrapDivergence> divergences = binder.compareBootstrapFrame0(trace, snapshot);

        assertTrue(divergences.isEmpty(),
                () -> "Expected no bootstrap divergences but found: " + divergences);
    }

    /**
     * ROM {@code Sonic_Pos_Record_Index} is a byte offset into 4-byte Pos_table
     * records; the engine snapshot exposes the already-normalized 0-63 slot.
     */
    @Test
    void player_history_pos_compares_rom_byte_offset_as_engine_slot() {
        TraceData trace = traceWithSnapshots(
                /* historyPos */ 0x68,
                /* xHistory  */ shorts(64, 0x0500),
                /* yHistory  */ shorts(64, 0x0300),
                /* inputHist */ shorts(64, 0x0000),
                /* statusHist*/ bytes(64, (byte) 0x00),
                cpu("tails", 1, 0, 2, (short) 0x0480, (short) 0x0320, 0x0000, false),
                List.of());

        EngineSnapshot snapshot = new EngineSnapshot(
                shorts(64, 0x0500),
                shorts(64, 0x0300),
                shorts(64, 0x0000),
                bytes(64, (byte) 0x00),
                0x19,
                new EngineSnapshot.SidekickCpuView(1, 0, 2, (short) 0x0480, (short) 0x0320, 0x0000, false),
                Map.of());

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        List<BootstrapDivergence> divergences = binder.compareBootstrapFrame0(trace, snapshot);

        assertTrue(divergences.isEmpty(),
                () -> "Equivalent ROM byte offset and engine slot must not diverge: "
                        + divergences);
    }

    /**
     * Modifying a single recorded field must produce exactly one ERROR-severity
     * divergence with the matching field name, expected and actual rendered as
     * hex, and a non-blank context string.
     */
    @Test
    void single_field_mismatch_yields_one_divergence() {
        short[] xHistory = shorts(64, 0x0500);
        xHistory[12] = (short) 0x0501; // ROM trace has 0x0501 at idx 12

        TraceData trace = traceWithSnapshots(
                0x34,
                xHistory,
                shorts(64, 0x0300),
                shorts(64, 0x0000),
                bytes(64, (byte) 0x00),
                cpu("tails", 1, 0, 2, (short) 0x0480, (short) 0x0320, 0x0000, false),
                List.of());

        EngineSnapshot snapshot = new EngineSnapshot(
                shorts(64, 0x0500), // engine still has 0x0500 at idx 12
                shorts(64, 0x0300),
                shorts(64, 0x0000),
                bytes(64, (byte) 0x00),
                12,
                new EngineSnapshot.SidekickCpuView(1, 0, 2, (short) 0x0480, (short) 0x0320, 0x0000, false),
                Map.of());

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        List<BootstrapDivergence> divergences = binder.compareBootstrapFrame0(trace, snapshot);

        assertEquals(1, divergences.size(),
                () -> "Expected exactly one divergence but got: " + divergences);
        BootstrapDivergence only = divergences.get(0);
        assertEquals("player_history.x[12]", only.field(),
                "Field label should identify the array index that diverged");
        assertEquals(BootstrapDivergence.Severity.ERROR, only.severity(),
                "Mismatched recorded values must escalate to ERROR severity");
        assertEquals("0x0501", only.expected());
        assertEquals("0x0500", only.actual());
        assertFalse(only.context() == null || only.context().isBlank(),
                "Divergence context should carry a human-readable hint");
    }

    /**
     * If the trace omits the recorded player-history snapshot the comparator
     * should still complete, emitting a WARNING-level divergence that flags
     * the missing frame -1 event (not an ERROR).
     */
    @Test
    void missing_frame_minus_one_event_warns_not_errors() {
        // No player_history_snapshot, but cpu and object snapshots present so
        // the comparator can prove it skips only the missing schema.
        Map<Integer, List<TraceEvent>> events = new HashMap<>();
        events.put(-1, List.of(
                cpu("tails", 1, 0, 2, (short) 0x0480, (short) 0x0320, 0x0000, false),
                objectSnapshot(0, 0x10, 0x0400, 0x0200, 0x02, 0x40)));
        TraceData trace = TraceFixtures.trace(TraceFixtures.metadata("s2", 4, 0), List.of(), events);

        EngineSnapshot snapshot = new EngineSnapshot(
                shorts(64, 0x0500),
                shorts(64, 0x0300),
                shorts(64, 0x0000),
                bytes(64, (byte) 0x00),
                12,
                new EngineSnapshot.SidekickCpuView(1, 0, 2, (short) 0x0480, (short) 0x0320, 0x0000, false),
                Map.of(0, new EngineSnapshot.ObjectSnapshot(0x10, 0x0400, 0x0200, 0x02, 0x40)));

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        List<BootstrapDivergence> divergences = binder.compareBootstrapFrame0(trace, snapshot);

        // Must contain at least one WARNING about the missing player_history_snapshot
        // and NO ERROR-severity divergences (the rest of the snapshot matches).
        boolean missingWarning = divergences.stream().anyMatch(d ->
                d.severity() == BootstrapDivergence.Severity.WARNING
                && d.field() != null
                && d.field().startsWith("player_history"));
        boolean anyErrors = divergences.stream()
                .anyMatch(d -> d.severity() == BootstrapDivergence.Severity.ERROR);
        if (!missingWarning) {
            fail("Expected a WARNING-level divergence for the missing player_history_snapshot, got: "
                    + divergences);
        }
        assertFalse(anyErrors,
                () -> "Missing snapshot must not raise ERROR severity. Divergences: "
                        + divergences);
    }

    /**
     * When the metadata indicates legacy (non-native) prelude mode, the
     * comparator must return an empty list without examining any state.
     * Verify by feeding a snapshot that would otherwise mismatch.
     */
    @Test
    void legacy_mode_skips_comparator() {
        // Override turned off => legacy mode.
        TraceBinder.setNativePreludeOverrideForTests(false);

        // Intentional mismatches that would ERROR if examined.
        Map<Integer, List<TraceEvent>> events = new HashMap<>();
        events.put(-1, List.of(
                new TraceEvent.PlayerHistorySnapshot(-1,
                        0,
                        shorts(64, 0x9999),
                        shorts(64, 0x9999),
                        shorts(64, 0x9999),
                        bytes(64, (byte) 0x99))));
        TraceData trace = TraceFixtures.trace(TraceFixtures.metadata("s2", 4, 0), List.of(), events);

        EngineSnapshot snapshot = new EngineSnapshot(
                shorts(64, 0x0000),
                shorts(64, 0x0000),
                shorts(64, 0x0000),
                bytes(64, (byte) 0x00),
                0,
                null,
                Map.of());

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        List<BootstrapDivergence> divergences = binder.compareBootstrapFrame0(trace, snapshot);

        assertTrue(divergences.isEmpty(),
                () -> "Legacy traces must skip the comparator entirely; divergences: "
                        + divergences);
    }

    // ---- Test helpers ----

    /**
     * Build a TraceData with the three frame-1 events populated. Native prelude
     * mode is signalled to TraceBinder via the override toggled in {@link #enableNativePreludeForTest()}.
     */
    private static TraceData traceWithSnapshots(int historyPos,
                                                short[] xHistory,
                                                short[] yHistory,
                                                short[] inputHistory,
                                                byte[] statusHistory,
                                                TraceEvent.CpuStateSnapshot cpu,
                                                List<TraceEvent.ObjectStateSnapshot> objects) {
        List<TraceEvent> frameMinusOne = new ArrayList<>();
        frameMinusOne.add(new TraceEvent.PlayerHistorySnapshot(
                -1, historyPos, xHistory, yHistory, inputHistory, statusHistory));
        if (cpu != null) {
            frameMinusOne.add(cpu);
        }
        if (objects != null) {
            frameMinusOne.addAll(objects);
        }
        Map<Integer, List<TraceEvent>> events = new HashMap<>();
        events.put(-1, frameMinusOne);
        return TraceFixtures.trace(TraceFixtures.metadata("s2", 4, 0), List.of(), events);
    }

    private static TraceEvent.CpuStateSnapshot cpu(String character, int controlCounter,
                                                   int respawnCounter, int cpuRoutine,
                                                   short targetX, short targetY,
                                                   int interactId, boolean jumping) {
        return new TraceEvent.CpuStateSnapshot(-1, character, controlCounter, respawnCounter,
                cpuRoutine, targetX, targetY, interactId, jumping);
    }

    private static TraceEvent.ObjectStateSnapshot objectSnapshot(int slot, int objectType,
                                                                  int xPos, int yPos,
                                                                  int routine, int status) {
        Map<Integer, Integer> byteFields = new LinkedHashMap<>();
        Map<Integer, Integer> wordFields = new LinkedHashMap<>();
        wordFields.put(0x08, xPos & 0xFFFF); // x_pos
        wordFields.put(0x0C, yPos & 0xFFFF); // y_pos
        byteFields.put(0x22, status & 0xFF); // status
        byteFields.put(0x24, routine & 0xFF); // routine
        return new TraceEvent.ObjectStateSnapshot(-1, slot, objectType,
                new RomObjectSnapshot(byteFields, wordFields));
    }

    private static short[] shorts(int len, int fill) {
        short[] arr = new short[len];
        Arrays.fill(arr, (short) fill);
        return arr;
    }

    private static byte[] bytes(int len, byte fill) {
        byte[] arr = new byte[len];
        Arrays.fill(arr, fill);
        return arr;
    }
}
