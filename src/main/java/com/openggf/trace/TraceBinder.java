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
    private static final int TRACE_INPUT_UP = 0x01;
    private static final int TRACE_INPUT_DOWN = 0x02;
    private static final int TRACE_INPUT_LEFT = 0x04;
    private static final int TRACE_INPUT_RIGHT = 0x08;
    private static final int TRACE_INPUT_JUMP = 0x10;

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
     * TraceBinder compares the diagnostic fields with stable engine parity data
     * and leaves the rest in the context window for cross-referencing.
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
        return compareFrame(expected, actualX, actualY, actualXSpeed, actualYSpeed,
            actualGSpeed, actualAngle, actualAir, actualRolling, actualGroundMode,
            romDiagOverride, engineDiag, secondaryCharacterLabel, actualSidekick, null, null, null);
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
            TraceCharacterState actualSidekick,
            TraceEvent.CpuState expectedSidekickCpu,
            EngineSidekickCpuState actualSidekickCpu) {
        return compareFrame(expected, actualX, actualY, actualXSpeed, actualYSpeed,
                actualGSpeed, actualAngle, actualAir, actualRolling, actualGroundMode,
                romDiagOverride, engineDiag, secondaryCharacterLabel, actualSidekick,
                expectedSidekickCpu, actualSidekickCpu, null);
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
            TraceCharacterState actualSidekick,
            TraceEvent.CpuState expectedSidekickCpu,
            EngineSidekickCpuState actualSidekickCpu,
            TraceEvent.TailsCpuNormalStep expectedSidekickNormalStep) {

        Map<String, FieldComparison> fields = new LinkedHashMap<>();

        // Position comparisons
        fields.put("x", compareNumeric("x", expected.x(), actualX,
            tolerances.positionWarn(), tolerances.positionError(), false));
        fields.put("y", compareNumeric("y", expected.y(), actualY,
            tolerances.positionWarn(), tolerances.positionError(), false));
        if (engineDiag != null && expected.hasExtendedData()
                && engineDiag.xSub() >= 0 && engineDiag.ySub() >= 0) {
            fields.put("x_sub", compareNumeric("x_sub",
                    expected.xSub() & 0xFFFF, engineDiag.xSub() & 0xFFFF,
                    0, 1, false));
            fields.put("y_sub", compareNumeric("y_sub",
                    expected.ySub() & 0xFFFF, engineDiag.ySub() & 0xFFFF,
                    0, 1, false));
        }

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

        if (engineDiag != null && expected.routine() >= 0 && engineDiag.routine() >= 0) {
            fields.put("routine", compareNumeric("routine",
                    expected.routine() & 0xFF, engineDiag.routine() & 0xFF,
                    0, 1, false));
        }
        if (engineDiag != null && expected.statusByte() >= 0 && engineDiag.statusByte() >= 0) {
            fields.put("status_byte", compareNumeric("status_byte",
                    expected.statusByte() & 0xFF, engineDiag.statusByte() & 0xFF,
                    0, 1, false));
        }

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

        String secondaryPrefix = normalizeCharacterPrefix(secondaryCharacterLabel);
        appendSidekickCpuComparisons(fields, secondaryPrefix,
                expectedSidekickCpu, actualSidekickCpu,
                expected.sidekick(), actualSidekick, expectedSidekickNormalStep);
        appendCharacterComparisons(fields,
            secondaryPrefix,
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
     * Merges read-only auxiliary object comparisons into an already-captured
     * frame. This is used to surface true object/RNG frontiers before later
     * player-state symptoms. It never feeds trace data back into the engine.
     */
    public void compareObjectNear(
            int frame,
            List<TraceEvent.ObjectNear> expectedObjects,
            List<EngineNearbyObject> actualObjects) {
        if (expectedObjects == null || expectedObjects.isEmpty()) {
            return;
        }
        FrameComparison existing = comparisonsByFrame.get(frame);
        if (existing == null) {
            return;
        }

        Map<String, FieldComparison> fields = new LinkedHashMap<>(existing.fields());
        List<EngineNearbyObject> actualList = actualObjects != null ? actualObjects : List.of();
        List<Integer> matchedActualSlots = new ArrayList<>();
        List<Integer> expectedTypes = new ArrayList<>();

        for (TraceEvent.ObjectNear expected : expectedObjects) {
            if (expected.character() != null && !expected.character().isBlank()
                    && !"sonic".equalsIgnoreCase(expected.character())) {
                continue;
            }
            int slot = expected.slot();
            int expectedType = parseHexByte(expected.objectType());
            if (!containsInt(expectedTypes, expectedType)) {
                expectedTypes.add(expectedType);
            }
            EngineNearbyObject actual = findSemanticObjectMatch(expected, expectedType, actualList, matchedActualSlots);
            if (actual != null) {
                matchedActualSlots.add(actual.slot());
            }
            String prefix = String.format("obj_s%02X_", slot & 0xFF);
            if (actual != null) {
                fields.put(prefix + "slot", compareObjectField(
                        prefix + "slot",
                        formatHexByte(slot & 0xFF),
                        formatHexByte(actual.slot() & 0xFF)));
            }
            fields.put(prefix + "type", compareObjectField(
                    prefix + "type",
                    formatHexByte(expectedType),
                    actual == null ? "missing" : formatHexByte(actual.objectId() & 0xFF)));
            if (actual != null && expectedType == (actual.objectId() & 0xFF)) {
                fields.put(prefix + "x", compareObjectField(
                        prefix + "x",
                        formatHex16(expected.x() & 0xFFFF),
                        formatHex16(actual.currentX() & 0xFFFF)));
                fields.put(prefix + "y", compareObjectField(
                        prefix + "y",
                        formatHex16(expected.y() & 0xFFFF),
                        formatHex16(actual.currentY() & 0xFFFF)));
            }
        }

        for (EngineNearbyObject actual : actualList) {
            if (containsInt(matchedActualSlots, actual.slot())
                    || !containsInt(expectedTypes, actual.objectId() & 0xFF)) {
                continue;
            }
            String prefix = String.format("obj_extra_s%02X_", actual.slot() & 0xFF);
            fields.put(prefix + "type", compareObjectField(
                    prefix + "type",
                    "absent",
                    formatHexByte(actual.objectId() & 0xFF)));
            fields.put(prefix + "x", compareObjectField(
                    prefix + "x",
                    "absent",
                    formatHex16(actual.currentX() & 0xFFFF)));
            fields.put(prefix + "y", compareObjectField(
                    prefix + "y",
                    "absent",
                    formatHex16(actual.currentY() & 0xFFFF)));
        }

        comparisonsByFrame.put(frame, new FrameComparison(
                existing.frame(), fields, existing.romDiagnostics(), existing.engineDiagnostics()));
    }

    private static EngineNearbyObject findSemanticObjectMatch(
            TraceEvent.ObjectNear expected,
            int expectedType,
            List<EngineNearbyObject> actualObjects,
            List<Integer> matchedActualSlots) {
        int expectedX = expected.x() & 0xFFFF;
        int expectedY = expected.y() & 0xFFFF;
        for (EngineNearbyObject actual : actualObjects) {
            if (containsInt(matchedActualSlots, actual.slot())) {
                continue;
            }
            if ((actual.objectId() & 0xFF) == expectedType
                    && (actual.currentX() & 0xFFFF) == expectedX
                    && (actual.currentY() & 0xFFFF) == expectedY) {
                return actual;
            }
        }
        return null;
    }

    private static boolean containsInt(List<Integer> values, int target) {
        for (int value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }

    private static FieldComparison compareObjectField(String name, String expected, String actual) {
        if (expected.equals(actual)) {
            return new FieldComparison(name, expected, actual, Severity.MATCH, 0);
        }
        return new FieldComparison(name, expected, actual, Severity.ERROR, 1);
    }

    private static int parseHexByte(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        String normalized = value.trim();
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        try {
            return Integer.parseInt(normalized, 16) & 0xFF;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String formatHexByte(int value) {
        return value < 0 ? "missing" : String.format("0x%02X", value & 0xFF);
    }

    private static String formatHex16(int value) {
        return String.format("0x%04X", value & 0xFFFF);
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

    private FieldComparison compareNumericEither(String name, int expected, int alternateExpected,
            int actual, int warn, int error, boolean signChangeIsError) {
        if (actual == expected || actual == alternateExpected) {
            return new FieldComparison(name, formatHex(actual), formatHex(actual),
                    Severity.MATCH, 0);
        }
        FieldComparison primary = compareNumeric(name, expected, actual, warn, error, signChangeIsError);
        if (!primary.isDivergent()) {
            return primary;
        }
        FieldComparison alternate = compareNumeric(name, alternateExpected, actual, warn, error, signChangeIsError);
        return alternate.severity().ordinal() < primary.severity().ordinal()
                ? alternate
                : primary;
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
        fields.put(prefix + "x_sub", compareNumeric(prefix + "x_sub",
                expected.xSub() & 0xFFFF, actual.xSub() & 0xFFFF,
                0, 1, false));
        fields.put(prefix + "y_sub", compareNumeric(prefix + "y_sub",
                expected.ySub() & 0xFFFF, actual.ySub() & 0xFFFF,
                0, 1, false));
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
        if (expected.routine() >= 0 && actual.routine() >= 0) {
            fields.put(prefix + "routine", compareNumeric(prefix + "routine",
                    expected.routine() & 0xFF, actual.routine() & 0xFF,
                    0, 1, false));
        }
        if (expected.statusByte() >= 0 && actual.statusByte() >= 0) {
            fields.put(prefix + "status_byte", compareNumeric(prefix + "status_byte",
                    expected.statusByte() & 0xFF, actual.statusByte() & 0xFF,
                    0, 1, false));
        }
    }

    private void appendSidekickCpuComparisons(Map<String, FieldComparison> fields, String prefix,
            TraceEvent.CpuState expected, EngineSidekickCpuState actual,
            TraceCharacterState expectedSidekick, TraceCharacterState actualSidekick,
            TraceEvent.TailsCpuNormalStep expectedNormalStep) {
        if (expected == null) {
            return;
        }
        boolean actualPresent = actual != null;
        fields.put(prefix + "cpu_present",
                compareFlag(prefix + "cpu_present", true, actualPresent));
        if (!actualPresent) {
            return;
        }

        fields.put(prefix + "cpu_routine", compareNumeric(prefix + "cpu_routine",
                expected.cpuRoutine(), actual.cpuRoutine(), 0, 1, false));
        fields.put(prefix + "cpu_control_counter", compareNumeric(prefix + "cpu_control_counter",
                expected.idleTimer(), actual.controlCounter(), 0, 1, false));
        fields.put(prefix + "cpu_respawn_counter", compareNumeric(prefix + "cpu_respawn_counter",
                expected.flightTimer(), actual.respawnCounter(), 0, 1, false));
        fields.put(prefix + "cpu_interact", compareNumeric(prefix + "cpu_interact",
                expected.interact() & 0xFF, actual.interact(), 0, 1, false));
        fields.put(prefix + "cpu_target_x", compareNumeric(prefix + "cpu_target_x",
                expected.targetX() & 0xFFFF, actual.targetX() & 0xFFFF, 0, 1, false));
        fields.put(prefix + "cpu_target_y", compareNumeric(prefix + "cpu_target_y",
                expected.targetY() & 0xFFFF, actual.targetY() & 0xFFFF, 0, 1, false));
        int expectedHeld = normalizeRomCtrl2LogicalByte(expected.ctrl2Held());
        int expectedHeldAlternate = expectedNormalStep != null
                ? normalizeRomCtrl2LogicalByte(expectedNormalStep.ctrl2Logical())
                : expectedHeld;
        int expectedPressed = normalizeRomCtrl2PressedByte(expected.ctrl2Pressed());
        int expectedPressedAlternate = expectedNormalStep != null
                ? normalizeRomCtrl2PressedByte(expectedNormalStep.ctrl2Logical())
                : expectedPressed;
        fields.put(prefix + "cpu_ctrl2_held", compareNumericEither(prefix + "cpu_ctrl2_held",
                expectedHeld, expectedHeldAlternate, actual.generatedHeld() & 0xFF, 0, 1, false));
        fields.put(prefix + "cpu_ctrl2_pressed", compareNumericEither(prefix + "cpu_ctrl2_pressed",
                expectedPressed, expectedPressedAlternate,
                normalizeEngineCtrl2PressedByte(actual.generatedPressed()), 0, 1, false));
        fields.put(prefix + "cpu_jumping", compareNumeric(prefix + "cpu_jumping",
                expected.autoJumpFlag() & 0xFF, actual.jumpingFlag() & 0xFF, 0, 1, false));
        if (expected.cpuRoutine() == 0x06 && actual.cpuRoutine() == 0x06
                && expected.delayedIndex() >= 0
                && actual.followHistorySlot() >= 0
                && sidekickObjectControlRoutineRan(expectedSidekick, actualSidekick)) {
            fields.put(prefix + "cpu_follow_ring", compareNumeric(prefix + "cpu_follow_ring",
                    romHistoryByteOffsetToSlot(expected.delayedIndex()),
                    actual.followHistorySlot(), 0, 1, false));
        }
    }

    private static boolean sidekickObjectControlRoutineRan(
            TraceCharacterState expectedSidekick, TraceCharacterState actualSidekick) {
        return expectedSidekick != null
                && actualSidekick != null
                && expectedSidekick.present()
                && actualSidekick.present()
                && expectedSidekick.routine() == 0x02
                && actualSidekick.routine() == 0x02;
    }

    /**
     * ROM Pos_table/Stat_table indices are byte offsets into 4-byte records.
     * Engine diagnostics expose the already-normalized 0-63 ring slot.
     */
    private static int romHistoryByteOffsetToSlot(int byteOffset) {
        return ((byteOffset & 0xFF) >>> 2) & 0x3F;
    }

    /**
     * {@code Sonic_Pos_Record_Index} points at the next free 4-byte record;
     * engine {@code historyPos} points at the latest written slot.
     */
    private static int romNextFreeHistoryByteOffsetToLatestSlot(int byteOffset) {
        return (romHistoryByteOffsetToSlot(byteOffset) + 0x3F) & 0x3F;
    }

    /**
     * The recorder emits raw ROM button bits for {@code Ctrl_2_Logical}. The
     * engine collapses A/B/C into its single abstract jump bit while preserving
     * directional bits.
     */
    private static int normalizeRomCtrl2LogicalByte(int raw) {
        int normalized = raw & (TRACE_INPUT_UP
                | TRACE_INPUT_DOWN
                | TRACE_INPUT_LEFT
                | TRACE_INPUT_RIGHT);
        if ((raw & 0x70) != 0) {
            normalized |= TRACE_INPUT_JUMP;
        }
        return normalized & 0xFF;
    }

    private static int normalizeRomCtrl2PressedByte(int raw) {
        return (raw & 0x70) != 0 ? TRACE_INPUT_JUMP : 0;
    }

    private static int normalizeEngineCtrl2PressedByte(int raw) {
        return raw & TRACE_INPUT_JUMP;
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

        int recordedHistorySlot =
                romNextFreeHistoryByteOffsetToLatestSlot(recorded.historyPos());
        if (recordedHistorySlot != snapshot.playerHistoryPos()) {
            out.add(new BootstrapDivergence(
                    "player_history.pos",
                    BootstrapDivergence.Severity.ERROR,
                    String.format("0x%04X (slot 0x%02X)",
                            recorded.historyPos() & 0xFFFF,
                            recordedHistorySlot & 0x3F),
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
            TraceCharacterState frameZeroTails = firstFrameSidekickState(trace);
            if (frameZeroTails != null && !frameZeroTails.present()) {
                return;
            }
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

    private static TraceCharacterState firstFrameSidekickState(TraceData trace) {
        if (trace == null) {
            return null;
        }
        try {
            TraceFrame frame = trace.getFrame(0);
            return frame.sidekick();
        } catch (IndexOutOfBoundsException ex) {
            return null;
        }
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


