package com.openggf.trace;

import com.openggf.level.objects.RomObjectSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Comparison engine: compares engine sprite state against expected trace state
 * for a single frame, applying configurable tolerance thresholds.
 */
public class TraceBinder {

    /**
     * Test-only override for the native-prelude-mode signal consumed by
     * {@link #compareBootstrapFrame0(TraceData, EngineSnapshot)}. When
     * non-{@code null}, the comparator uses this value instead of reading
     * {@code trace.metadata().nativePreludeMode()}.
     *
     * <p>Test-only hook for synthetic {@link TraceData} fixtures that omit
     * {@code lua_script_version} or otherwise can't satisfy the production
     * version-gate. Production callers leave the override {@code null} and
     * read the real metadata flag, which derives bootstrap eligibility from
     * {@code luaScriptVersion >= 9.2-s2}.
     */
    private static volatile Boolean NATIVE_PRELUDE_OVERRIDE_FOR_TESTS = null;

    private final ToleranceConfig tolerances;
    // Keyed by frame number so re-comparisons of the same frame (e.g., during
    // held-rewind segment-cache rebuilds in test mode) replace the previous
    // entry instead of accumulating duplicates. Memory bounded by trace length.
    private final TreeMap<Integer, FrameComparison> comparisonsByFrame = new TreeMap<>();
    private List<BootstrapDivergence> lastBootstrapDivergences = List.of();

    public TraceBinder(ToleranceConfig tolerances) {
        this.tolerances = tolerances;
    }

    /**
     * Package-private hook used only by tests in this package to flip the
     * native-prelude-mode signal for synthetic {@link TraceData} fixtures
     * that don't carry a realistic {@code luaScriptVersion}.
     *
     * @param value {@code true} to force native-prelude mode on,
     *              {@code false} to force it off, {@code null} to fall back
     *              to {@link TraceMetadata#nativePreludeMode()}.
     */
    static void setNativePreludeOverrideForTests(Boolean value) {
        NATIVE_PRELUDE_OVERRIDE_FOR_TESTS = value;
    }

    /**
     * Compare a single frame's expected trace values against actual engine values.
     * Accepts raw values extracted from the sprite to keep this class decoupled
     * from AbstractPlayableSprite.
     */
    public FrameComparison compareFrame(
            TraceFrame expected,
            short actualX, short actualY,
            short actualXSpeed, short actualYSpeed, short actualGSpeed,
            byte actualAngle, boolean actualAir, boolean actualRolling,
            int actualGroundMode) {
        return compareFrame(expected, actualX, actualY, actualXSpeed, actualYSpeed,
            actualGSpeed, actualAngle, actualAir, actualRolling, actualGroundMode, null);
    }

    /**
     * Compare a single frame with optional engine-side diagnostic context.
     * The diagnostics are display-only (not compared for pass/fail) but appear
     * in the context window alongside ROM trace diagnostics for cross-referencing.
     */
    public FrameComparison compareFrame(
            TraceFrame expected,
            short actualX, short actualY,
            short actualXSpeed, short actualYSpeed, short actualGSpeed,
            byte actualAngle, boolean actualAir, boolean actualRolling,
            int actualGroundMode,
            EngineDiagnostics engineDiag) {
        return compareFrame(expected, actualX, actualY, actualXSpeed, actualYSpeed,
            actualGSpeed, actualAngle, actualAir, actualRolling, actualGroundMode,
            null, engineDiag, null);
    }

    public FrameComparison compareFrame(
            TraceFrame expected,
            short actualX, short actualY,
            short actualXSpeed, short actualYSpeed, short actualGSpeed,
            byte actualAngle, boolean actualAir, boolean actualRolling,
            int actualGroundMode,
            String romDiagOverride,
            EngineDiagnostics engineDiag) {
        return compareFrame(expected, actualX, actualY, actualXSpeed, actualYSpeed,
            actualGSpeed, actualAngle, actualAir, actualRolling, actualGroundMode,
            romDiagOverride, engineDiag, null);
    }

    public FrameComparison compareFrame(
            TraceFrame expected,
            short actualX, short actualY,
            short actualXSpeed, short actualYSpeed, short actualGSpeed,
            byte actualAngle, boolean actualAir, boolean actualRolling,
            int actualGroundMode,
            String romDiagOverride,
            EngineDiagnostics engineDiag,
            TraceCharacterState actualSidekick) {
        return compareFrame(expected, actualX, actualY, actualXSpeed, actualYSpeed,
            actualGSpeed, actualAngle, actualAir, actualRolling, actualGroundMode,
            romDiagOverride, engineDiag, "sidekick", actualSidekick);
    }

    public FrameComparison compareFrame(
            TraceFrame expected,
            short actualX, short actualY,
            short actualXSpeed, short actualYSpeed, short actualGSpeed,
            byte actualAngle, boolean actualAir, boolean actualRolling,
            int actualGroundMode,
            String romDiagOverride,
            EngineDiagnostics engineDiag,
            String secondaryCharacterLabel,
            TraceCharacterState actualSidekick) {

        Map<String, FieldComparison> fields = new LinkedHashMap<>();

        // Position comparisons
        fields.put("x", compareNumeric("x", expected.x(), actualX,
            tolerances.positionWarn(), tolerances.positionError(), false));
        fields.put("y", compareNumeric("y", expected.y(), actualY,
            tolerances.positionWarn(), tolerances.positionError(), false));

        // Speed comparisons
        fields.put("x_speed", compareNumeric("x_speed", expected.xSpeed(), actualXSpeed,
            tolerances.speedWarn(), tolerances.speedError(), tolerances.speedSignChangeIsError()));
        fields.put("y_speed", compareNumeric("y_speed", expected.ySpeed(), actualYSpeed,
            tolerances.speedWarn(), tolerances.speedError(), tolerances.speedSignChangeIsError()));
        fields.put("g_speed", compareNumeric("g_speed", expected.gSpeed(), actualGSpeed,
            tolerances.speedWarn(), tolerances.speedError(), tolerances.speedSignChangeIsError()));

        // Angle comparison (circular: 0xFC and 0x04 are 8 apart, not 248)
        fields.put("angle", compareAngle("angle",
            expected.angle() & 0xFF, actualAngle & 0xFF,
            tolerances.angleWarn(), tolerances.angleError()));

        // Boolean/enum flags: any mismatch is ERROR
        fields.put("air", compareFlag("air", expected.air(), actualAir));
        fields.put("rolling", compareFlag("rolling", expected.rolling(), actualRolling));

        // Derive ground_mode from angle for BOTH sides. The ROM has no stored ground_mode
        // variable; the Lua script and engine both compute it from angle. During airborne
        // frames the engine doesn't call updateGroundMode(), so the stored value is stale.
        // Deriving from angle makes the comparison symmetric and correct.
        int expectedGroundMode = deriveGroundMode(expected.angle() & 0xFF);
        int derivedActualGroundMode = deriveGroundMode(actualAngle & 0xFF);
        fields.put("ground_mode", compareEnum("ground_mode",
            expectedGroundMode, derivedActualGroundMode));

        if (tolerances.ringCountMode() != ToleranceConfig.RingCountMode.DISABLED
                && expected.rings() >= 0 && engineDiag != null && engineDiag.rings() >= 0) {
            fields.put("rings", compareRingCount(expected.rings(), engineDiag.rings()));
        }

        // Camera coords: only compared when BOTH ROM trace and engine diagnostics
        // recorded them. Engine values are masked to 16 bits to align with ROM's
        // u16 Camera_X_pos / Camera_Y_pos representation across the sign boundary.
        if (engineDiag != null
                && expected.cameraX() >= 0 && expected.cameraY() >= 0
                && engineDiag.cameraX() >= 0 && engineDiag.cameraY() >= 0) {
            fields.put("camera_x", compareNumeric("camera_x",
                    expected.cameraX() & 0xFFFF, engineDiag.cameraX() & 0xFFFF,
                    tolerances.cameraWarn(), tolerances.cameraError(), false));
            fields.put("camera_y", compareNumeric("camera_y",
                    expected.cameraY() & 0xFFFF, engineDiag.cameraY() & 0xFFFF,
                    tolerances.cameraWarn(), tolerances.cameraError(), false));
        }

        appendCharacterComparisons(fields,
            normalizeCharacterPrefix(secondaryCharacterLabel),
            expected.sidekick(), actualSidekick);

        // Store diagnostic context (ROM trace + engine side) for the context window.
        // These are NOT compared for pass/fail — they're for human debugging only.
        String romDiag = romDiagOverride != null
                ? romDiagOverride
                : expected.hasExtendedData() ? expected.formatDiagnostics() : "";
        String engDiag = engineDiag != null ? engineDiag.format() : "";

        FrameComparison result = new FrameComparison(expected.frame(), fields, romDiag, engDiag);
        comparisonsByFrame.put(result.frame(), result);
        return result;
    }

    /**
     * Build a divergence report from all comparisons accumulated so far.
     * Includes any bootstrap (frame-0) divergences captured via
     * {@link #compareBootstrapFrame0(TraceData, EngineSnapshot)}.
     */
    public DivergenceReport buildReport() {
        return new DivergenceReport(
                new ArrayList<>(comparisonsByFrame.values()), null, lastBootstrapDivergences);
    }

    /**
     * Build a divergence report from all comparisons accumulated so far,
     * enriched with trace-side checkpoint and zone/act context. Includes any
     * bootstrap (frame-0) divergences captured via
     * {@link #compareBootstrapFrame0(TraceData, EngineSnapshot)}.
     */
    public DivergenceReport buildReport(TraceData trace) {
        return new DivergenceReport(
                new ArrayList<>(comparisonsByFrame.values()), trace, lastBootstrapDivergences);
    }

    /**
     * Validate that BK2-derived input matches trace-embedded input.
     * Returns true if they match, false on mismatch (alignment error).
     */
    public boolean validateInput(TraceFrame frame, int bk2Input) {
        return frame.input() == bk2Input;
    }

    private FieldComparison compareNumeric(String name, int expected, int actual,
            int warn, int error, boolean signChangeIsError) {
        int delta = Math.abs(expected - actual);

        // Check sign change (both nonzero, different signs)
        if (signChangeIsError && expected != 0 && actual != 0) {
            boolean expectedNeg = (expected < 0) || (expected > 0x7FFF);
            boolean actualNeg = (actual < 0) || (actual > 0x7FFF);
            if (expectedNeg != actualNeg) {
                return new FieldComparison(name,
                    formatHex(expected), formatHex(actual), Severity.ERROR, delta);
            }
        }

        Severity severity = tolerances.classify(delta, warn, error);
        return new FieldComparison(name,
            formatHex(expected), formatHex(actual), severity, delta);
    }

    private FieldComparison compareFlag(String name, boolean expected, boolean actual) {
        if (expected == actual) {
            return new FieldComparison(name,
                String.valueOf(expected ? 1 : 0), String.valueOf(actual ? 1 : 0),
                Severity.MATCH, 0);
        }
        return new FieldComparison(name,
            String.valueOf(expected ? 1 : 0), String.valueOf(actual ? 1 : 0),
            Severity.ERROR, 1);
    }

    private FieldComparison compareEnum(String name, int expected, int actual) {
        if (expected == actual) {
            return new FieldComparison(name,
                String.valueOf(expected), String.valueOf(actual), Severity.MATCH, 0);
        }
        return new FieldComparison(name,
            String.valueOf(expected), String.valueOf(actual),
            Severity.ERROR, Math.abs(expected - actual));
    }

    private FieldComparison compareRingCount(int expected, int actual) {
        if (expected == actual) {
            return new FieldComparison("rings",
                String.valueOf(expected), String.valueOf(actual), Severity.MATCH, 0);
        }
        Severity severity = tolerances.ringCountMode() == ToleranceConfig.RingCountMode.WARN_ONLY
            ? Severity.WARNING
            : Severity.ERROR;
        return new FieldComparison("rings",
            String.valueOf(expected), String.valueOf(actual),
            severity, Math.abs(expected - actual));
    }

    private FieldComparison compareAngle(String name, int expected, int actual,
            int warn, int error) {
        // Circular distance on a 256-unit ring
        int rawDelta = Math.abs(expected - actual);
        int delta = Math.min(rawDelta, 256 - rawDelta);

        Severity severity = tolerances.classify(delta, warn, error);
        return new FieldComparison(name,
            formatHex(expected), formatHex(actual), severity, delta);
    }

    /**
     * Derive ground mode from angle using ROM quadrant thresholds.
     * Floor wraps: 0xE0-0xFF and 0x00-0x1F are both mode 0.
     */
    static int deriveGroundMode(int angle) {
        if (angle <= 0x1F || angle >= 0xE0) return 0;  // floor
        if (angle <= 0x5F) return 1;                     // right wall
        if (angle <= 0x9F) return 2;                     // ceiling
        return 3;                                         // left wall
    }

    private void appendCharacterComparisons(Map<String, FieldComparison> fields, String prefix,
            TraceCharacterState expected, TraceCharacterState actual) {
        if (expected == null && actual == null) {
            return;
        }
        boolean expectedPresent = expected != null && expected.present();
        boolean actualPresent = actual != null && actual.present();
        fields.put(prefix + "present",
            compareFlag(prefix + "present", expectedPresent, actualPresent));
        if (!expectedPresent || !actualPresent) {
            return;
        }

        fields.put(prefix + "x", compareNumeric(prefix + "x", expected.x(), actual.x(),
            tolerances.positionWarn(), tolerances.positionError(), false));
        fields.put(prefix + "y", compareNumeric(prefix + "y", expected.y(), actual.y(),
            tolerances.positionWarn(), tolerances.positionError(), false));
        fields.put(prefix + "x_speed",
            compareNumeric(prefix + "x_speed", expected.xSpeed(), actual.xSpeed(),
                tolerances.speedWarn(), tolerances.speedError(),
                tolerances.speedSignChangeIsError()));
        fields.put(prefix + "y_speed",
            compareNumeric(prefix + "y_speed", expected.ySpeed(), actual.ySpeed(),
                tolerances.speedWarn(), tolerances.speedError(),
                tolerances.speedSignChangeIsError()));
        fields.put(prefix + "g_speed",
            compareNumeric(prefix + "g_speed", expected.gSpeed(), actual.gSpeed(),
                tolerances.speedWarn(), tolerances.speedError(),
                tolerances.speedSignChangeIsError()));
        fields.put(prefix + "angle", compareAngle(prefix + "angle",
            expected.angle() & 0xFF, actual.angle() & 0xFF,
            tolerances.angleWarn(), tolerances.angleError()));
        fields.put(prefix + "air", compareFlag(prefix + "air", expected.air(), actual.air()));
        fields.put(prefix + "rolling",
            compareFlag(prefix + "rolling", expected.rolling(), actual.rolling()));
        fields.put(prefix + "ground_mode", compareEnum(prefix + "ground_mode",
            deriveGroundMode(expected.angle() & 0xFF),
            deriveGroundMode(actual.angle() & 0xFF)));
    }

    private static String normalizeCharacterPrefix(String label) {
        if (label == null || label.isBlank()) {
            return "sidekick_";
        }
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < label.length(); i++) {
            char ch = Character.toLowerCase(label.charAt(i));
            normalized.append(Character.isLetterOrDigit(ch) ? ch : '_');
        }
        if (normalized.isEmpty()) {
            return "sidekick_";
        }
        normalized.append('_');
        return normalized.toString();
    }

    private static String formatHex(int value) {
        if (value < 0) {
            return String.format("-%04X", -value);
        }
        return String.format("0x%04X", value & 0xFFFF);
    }

    // ---- Frame-0 bootstrap comparator (ADR-3) -------------------------------

    /**
     * Cross-references the engine state captured at frame 0 against the ROM
     * frame -1 snapshots in {@code trace}. The bootstrap comparator is the
     * test-time invariant that engine state arrives at frame 0 via natural
     * simulation (running the title card / level init the same way the ROM
     * does), not via state hydration from the trace.
     *
     * <p>Returns an empty list when the trace metadata says the recording
     * was captured under legacy mode (no native title-card prelude). Legacy
     * traces continue to short-circuit the comparator entirely.
     *
     * <p>Coverage v1 (conservative):
     * <ul>
     *     <li>{@code player_history_snapshot} — pos + 4 ring buffers (64 entries each).</li>
     *     <li>{@code cpu_state_snapshot} for character "tails" — all named fields.</li>
     *     <li>{@code object_state_snapshot} per slot — objectType + x_pos + y_pos
     *         + routine + status only.</li>
     * </ul>
     * Per-slot SST bytes beyond the cardinal set are NOT compared in this
     * pass; they will be added as confidence builds.
     */
    public List<BootstrapDivergence> compareBootstrapFrame0(TraceData trace,
                                                            EngineSnapshot snapshot) {
        if (trace == null || snapshot == null) {
            return List.of();
        }
        if (!nativePreludeMode(trace)) {
            return List.of();
        }

        List<BootstrapDivergence> divergences = new ArrayList<>();
        comparePlayerHistory(trace, snapshot, divergences);
        compareTailsCpu(trace, snapshot, divergences);
        compareObjectSnapshots(trace, snapshot, divergences);

        divergences.sort(Comparator.comparingInt(
                (BootstrapDivergence d) -> d.severity() == BootstrapDivergence.Severity.ERROR
                        ? 0 : 1));
        lastBootstrapDivergences = List.copyOf(divergences);
        return divergences;
    }

    /**
     * Resolves the native-prelude-mode flag for {@code trace}. Tests can
     * override via {@link #setNativePreludeOverrideForTests(Boolean)};
     * production reads {@link TraceMetadata#nativePreludeMode()} (which
     * derives eligibility from {@code luaScriptVersion >= 9.2-s2}).
     */
    private static boolean nativePreludeMode(TraceData trace) {
        Boolean override = NATIVE_PRELUDE_OVERRIDE_FOR_TESTS;
        if (override != null) {
            return override;
        }
        if (trace == null || trace.metadata() == null) {
            return false;
        }
        return trace.metadata().nativePreludeMode();
    }

    private static void comparePlayerHistory(TraceData trace,
                                             EngineSnapshot snapshot,
                                             List<BootstrapDivergence> out) {
        TraceEvent.PlayerHistorySnapshot recorded = trace.preTracePlayerHistorySnapshot();
        if (recorded == null) {
            out.add(new BootstrapDivergence(
                    "player_history.snapshot",
                    BootstrapDivergence.Severity.WARNING,
                    "present", "missing",
                    "player_history_snapshot missing from trace at frame -1"));
            return;
        }

        if (recorded.historyPos() != snapshot.playerHistoryPos()) {
            out.add(new BootstrapDivergence(
                    "player_history.pos",
                    BootstrapDivergence.Severity.ERROR,
                    String.format("0x%04X", recorded.historyPos() & 0xFFFF),
                    String.format("0x%04X", snapshot.playerHistoryPos() & 0xFFFF),
                    "ROM and engine ring-buffer positions disagree"));
        }

        compareShortArray("player_history.x", recorded.xHistory(),
                snapshot.playerXHistory(), out);
        compareShortArray("player_history.y", recorded.yHistory(),
                snapshot.playerYHistory(), out);
        compareShortArray("player_history.input", recorded.inputHistory(),
                snapshot.playerInputHistory(), out);
        compareByteArray("player_history.status", recorded.statusHistory(),
                snapshot.playerStatusHistory(), out);
    }

    private static void compareShortArray(String fieldBase,
                                          short[] expected,
                                          short[] actual,
                                          List<BootstrapDivergence> out) {
        if (expected == null) {
            out.add(new BootstrapDivergence(
                    fieldBase, BootstrapDivergence.Severity.WARNING,
                    "present", "missing",
                    fieldBase + " absent from trace snapshot"));
            return;
        }
        int len = Math.min(expected.length, actual == null ? 0 : actual.length);
        for (int i = 0; i < len; i++) {
            short e = expected[i];
            short a = actual[i];
            if (e != a) {
                out.add(new BootstrapDivergence(
                        fieldBase + "[" + i + "]",
                        BootstrapDivergence.Severity.ERROR,
                        String.format("0x%04X", e & 0xFFFF),
                        String.format("0x%04X", a & 0xFFFF),
                        fieldBase + " ring-buffer entry " + i
                                + " differs from ROM snapshot"));
            }
        }
        if (expected.length != (actual == null ? 0 : actual.length)) {
            out.add(new BootstrapDivergence(
                    fieldBase + ".length",
                    BootstrapDivergence.Severity.WARNING,
                    Integer.toString(expected.length),
                    Integer.toString(actual == null ? 0 : actual.length),
                    fieldBase + " ring-buffer length differs from ROM snapshot"));
        }
    }

    private static void compareByteArray(String fieldBase,
                                         byte[] expected,
                                         byte[] actual,
                                         List<BootstrapDivergence> out) {
        if (expected == null) {
            out.add(new BootstrapDivergence(
                    fieldBase, BootstrapDivergence.Severity.WARNING,
                    "present", "missing",
                    fieldBase + " absent from trace snapshot"));
            return;
        }
        int len = Math.min(expected.length, actual == null ? 0 : actual.length);
        for (int i = 0; i < len; i++) {
            byte e = expected[i];
            byte a = actual[i];
            if (e != a) {
                out.add(new BootstrapDivergence(
                        fieldBase + "[" + i + "]",
                        BootstrapDivergence.Severity.ERROR,
                        String.format("0x%02X", e & 0xFF),
                        String.format("0x%02X", a & 0xFF),
                        fieldBase + " ring-buffer entry " + i
                                + " differs from ROM snapshot"));
            }
        }
        if (expected.length != (actual == null ? 0 : actual.length)) {
            out.add(new BootstrapDivergence(
                    fieldBase + ".length",
                    BootstrapDivergence.Severity.WARNING,
                    Integer.toString(expected.length),
                    Integer.toString(actual == null ? 0 : actual.length),
                    fieldBase + " ring-buffer length differs from ROM snapshot"));
        }
    }

    private static void compareTailsCpu(TraceData trace,
                                        EngineSnapshot snapshot,
                                        List<BootstrapDivergence> out) {
        TraceEvent.CpuStateSnapshot recorded = trace.preTraceCpuStateSnapshot("tails");
        EngineSnapshot.SidekickCpuView engine = snapshot.tailsCpu();
        if (recorded == null && engine == null) {
            return;
        }
        if (recorded == null) {
            out.add(new BootstrapDivergence(
                    "tails_cpu.snapshot",
                    BootstrapDivergence.Severity.WARNING,
                    "present", "missing",
                    "cpu_state_snapshot for 'tails' missing from trace"));
            return;
        }
        if (engine == null) {
            out.add(new BootstrapDivergence(
                    "tails_cpu.engine",
                    BootstrapDivergence.Severity.WARNING,
                    "present", "missing",
                    "engine has no sidekick CPU view; cannot compare against ROM"));
            return;
        }

        compareInt("tails_cpu.control_counter",
                recorded.controlCounter(), engine.controlCounter(), out);
        compareInt("tails_cpu.respawn_counter",
                recorded.respawnCounter(), engine.respawnCounter(), out);
        compareInt("tails_cpu.routine",
                recorded.cpuRoutine(), engine.cpuRoutine(), out);
        compareInt("tails_cpu.target_x",
                recorded.targetX() & 0xFFFF, engine.targetX() & 0xFFFF, out);
        compareInt("tails_cpu.target_y",
                recorded.targetY() & 0xFFFF, engine.targetY() & 0xFFFF, out);
        compareInt("tails_cpu.interact_id",
                recorded.interactId(), engine.interactId(), out);
        compareInt("tails_cpu.jumping",
                recorded.jumping() ? 1 : 0, engine.jumping() ? 1 : 0, out);
    }

    private static void compareInt(String field, int expected, int actual,
                                   List<BootstrapDivergence> out) {
        if (expected == actual) {
            return;
        }
        out.add(new BootstrapDivergence(
                field,
                BootstrapDivergence.Severity.ERROR,
                String.format("0x%04X", expected & 0xFFFF),
                String.format("0x%04X", actual & 0xFFFF),
                field + " differs from ROM snapshot"));
    }

    private static void compareObjectSnapshots(TraceData trace,
                                               EngineSnapshot snapshot,
                                               List<BootstrapDivergence> out) {
        List<TraceEvent.ObjectStateSnapshot> recordedSnapshots =
                trace.preTraceObjectSnapshots();
        if (recordedSnapshots == null || recordedSnapshots.isEmpty()) {
            // Object snapshots are optional — emit a WARNING only when the
            // engine has populated slots and the trace has none, so the
            // legitimate "no slots" case stays silent.
            if (!snapshot.slotStates().isEmpty()) {
                out.add(new BootstrapDivergence(
                        "object_snapshots",
                        BootstrapDivergence.Severity.WARNING,
                        "present", "missing",
                        "object_state_snapshot events missing from trace, but engine has "
                                + snapshot.slotStates().size() + " active slot(s)"));
            }
            return;
        }

        for (TraceEvent.ObjectStateSnapshot recorded : recordedSnapshots) {
            EngineSnapshot.ObjectSnapshot engine = snapshot.slotStates().get(recorded.slot());
            String slotLabel = "object_slot[" + recorded.slot() + "]";
            if (engine == null) {
                out.add(new BootstrapDivergence(
                        slotLabel,
                        BootstrapDivergence.Severity.WARNING,
                        "present", "missing",
                        "engine slot " + recorded.slot() + " empty but ROM recorded one"));
                continue;
            }
            RomObjectSnapshot fields = recorded.fields();
            compareInt(slotLabel + ".object_type",
                    recorded.objectType() & 0xFF, engine.objectType() & 0xFF, out);
            if (fields != null) {
                compareInt(slotLabel + ".x_pos",
                        fields.xPos() & 0xFFFF, engine.xPos() & 0xFFFF, out);
                compareInt(slotLabel + ".y_pos",
                        fields.yPos() & 0xFFFF, engine.yPos() & 0xFFFF, out);
                compareInt(slotLabel + ".routine",
                        fields.routine() & 0xFF, engine.routine() & 0xFF, out);
                compareInt(slotLabel + ".status",
                        fields.status() & 0xFF, engine.status() & 0xFF, out);
            }
        }
    }
}


