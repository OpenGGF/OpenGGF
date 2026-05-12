package com.openggf.tests;

import com.openggf.trace.ToleranceConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class TestTraceReplayInvariantGuard {
    private static final Path MAIN_ROOT = Path.of("src/main/java");
    private static final Path TEST_ROOT = Path.of("src/test/java");
    private static final String SELF_PATH =
            "src/test/java/com/openggf/tests/TestTraceReplayInvariantGuard.java";

    private static final Set<String> TRACE_REPLAY_BASE_ALLOWLIST = Set.of(
            "src/test/java/com/openggf/tests/trace/AbstractTraceReplayTest.java",
            "src/test/java/com/openggf/tests/trace/AbstractCreditsDemoTraceReplayTest.java"
    );

    private static final Set<String> FORBIDDEN_PARSER_DEPENDENCIES = Set.of(
            "com.openggf.game.GameServices",
            "com.openggf.level.objects.ObjectManager",
            "com.openggf.level.objects.AbstractObjectInstance",
            "com.openggf.level.objects.ObjectServices",
            "com.openggf.sprites.",
            "com.openggf.level.MutableLevel",
            "com.openggf.game.mutation.",
            "com.openggf.level.mutation."
    );

    private static final Set<String> PARSER_DEPENDENCY_BASELINE = Set.of(
            "src/main/java/com/openggf/trace/TraceCharacterState.java depends on com.openggf.game.GameServices",
            "src/main/java/com/openggf/trace/TraceCharacterState.java depends on com.openggf.level.objects.AbstractObjectInstance",
            "src/main/java/com/openggf/trace/TraceCharacterState.java depends on com.openggf.level.objects.ObjectManager",
            "src/main/java/com/openggf/trace/TraceCharacterState.java depends on com.openggf.sprites.",
            "src/main/java/com/openggf/trace/TraceEvent.java depends on com.openggf.sprites.",
            "src/main/java/com/openggf/trace/TraceObjectSnapshotBinder.java depends on com.openggf.level.objects.ObjectManager"
    );

    @Test
    void traceReplayCodeDoesNotWriteRecordedStateBackIntoEngine()
            throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : replaySources()) {
            List<String> lines = Files.readAllLines(source);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isForbiddenTraceHydration(source, line)) {
                    violations.add(source + ":" + (i + 1) + " - " + line.trim());
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("Trace replay must compare trace rows, not write them "
                    + "back into engine state:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void defaultTraceReplayToleranceIsStrict() {
        ToleranceConfig defaults = ToleranceConfig.DEFAULT;

        assertEquals(1, defaults.positionError(), "position delta of one must be an error");
        assertEquals(1, defaults.speedError(), "speed delta of one must be an error");
        assertEquals(1, defaults.angleError(), "angle delta of one must be an error");
        assertEquals(1, defaults.cameraError(), "camera delta of one must be an error");
        assertEquals(ToleranceConfig.RingCountMode.FORCE_ERROR, defaults.ringCountMode(),
                "ring count mismatch must default to error; opt into WARN_ONLY explicitly");
    }

    @Test
    void traceParserDataAndCatalogStayIndependentOfEngineRuntime()
            throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : traceParserDataAndCatalogSources()) {
            String text = Files.readString(source);
            for (String forbidden : FORBIDDEN_PARSER_DEPENDENCIES) {
                if (text.contains(forbidden)) {
                    violations.add(normalize(source) + " depends on " + forbidden);
                }
            }
        }

        List<String> unexpected = new ArrayList<>(violations);
        unexpected.removeAll(PARSER_DEPENDENCY_BASELINE);
        List<String> stale = new ArrayList<>(PARSER_DEPENDENCY_BASELINE);
        stale.removeAll(violations);
        if (!unexpected.isEmpty() || !stale.isEmpty()) {
            List<String> sections = new ArrayList<>();
            if (!unexpected.isEmpty()) {
                sections.add("New parser/data/catalog runtime dependencies:\n"
                        + String.join("\n", unexpected));
            }
            if (!stale.isEmpty()) {
                sections.add("Baseline dependencies no longer present:\n"
                        + String.join("\n", stale));
            }
            fail("Trace parser/data/catalog classes should stay read-only and engine-independent.\n"
                    + String.join("\n\n", sections));
        }
    }

    @Test
    void concreteTraceReplayTestsUseSharedReplayBaseClass() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources(Path.of("src/test/java/com/openggf/tests/trace"))) {
            String normalized = normalize(source);
            if (TRACE_REPLAY_BASE_ALLOWLIST.contains(normalized)) {
                continue;
            }
            String fileName = source.getFileName().toString();
            if (!fileName.startsWith("Test") || !fileName.endsWith("TraceReplay.java")) {
                continue;
            }
            String text = Files.readString(source);
            if (!text.contains("package com.openggf.tests.trace")) {
                continue;
            }
            if (!text.contains("extends AbstractTraceReplayTest")
                    && !text.contains("extends AbstractCreditsDemoTraceReplayTest")) {
                violations.add(normalized);
            }
        }

        if (!violations.isEmpty()) {
            fail("*TraceReplay tests under com.openggf.tests.trace.. must use the shared replay base:\n"
                    + String.join("\n", violations));
        }
    }

    private static List<Path> replaySources() throws IOException {
        Set<Path> sources = new HashSet<>();
        sources.addAll(javaSources(Path.of("src/main/java/com/openggf/trace")));
        sources.addAll(javaSources(Path.of("src/test/java/com/openggf/tests/trace")));
        sources.add(Path.of("src/main/java/com/openggf/TraceSessionLauncher.java"));
        sources.addAll(traceConsumersOutsideCurrentRoots());
        sources.add(Path.of("src/main/java/com/openggf/sprites/playable/SidekickCpuController.java"));
        sources.remove(Path.of(SELF_PATH));
        return sources.stream()
                .filter(Files::exists)
                .filter(path -> !isAllowedTraceSupportSource(path))
                .sorted(Comparator.comparing(TestTraceReplayInvariantGuard::normalize))
                .toList();
    }

    private static boolean isAllowedTraceSupportSource(Path source) {
        String normalized = normalize(source);
        return normalized.endsWith("src/main/java/com/openggf/sprites/ghost/GhostTraceRenderer.java")
                || normalized.endsWith("src/test/java/com/openggf/tests/HeadlessTestFixture.java");
    }

    /**
     * Catches setter calls whose argument expression reads from a trace
     * snapshot/frame field. The pattern below matches a leading dot then
     * a setter, then anywhere inside the parenthesised argument list one of
     * the well-known trace-side identifiers ({@code state}, {@code frame},
     * {@code snapshot}, {@code sn}, {@code fields.get}). This is a conservative
     * heuristic but it would have caught
     * {@code applyFrameZeroPlayerSnapshot}'s
     * {@code player.setControlLocked(controlLocked)} chain because each value
     * is parsed via {@code parseBoolean(fields.get(...))} earlier in the same
     * method, and the fields-derived locals are detected via the dedicated
     * {@code fields.get(} check below.
     */
    private static final Pattern SETTER_FROM_TRACE_FIELD = Pattern.compile(
            "\\.set[A-Z]\\w*\\([^)]*\\b(state|frame|snapshot|sn)\\.\\w+");

    private static boolean isForbiddenTraceHydration(Path source, String line) {
        if (isVisualTraceGhostHydration(source)) {
            return false;
        }
        String trimmed = line.trim();
        if (isAllowedTraceBootstrapLine(trimmed)) {
            return false;
        }
        return trimmed.contains("applyRecordedFirstSidekickState(")
                || line.contains("applyRecordedFrameState(")
                || line.contains("applySeededFirstSidekickState(")
                || line.contains("applySidekickFollowDelayOverride(")
                || line.contains("applyFrameZeroPlayerSnapshot(")
                || line.contains("applyCustomRadii(")
                || line.contains("hydrateFromRomCpuStatePerFrame(")
                || line.contains("hydrateRecordedHistory(")
                || line.contains("sidekickFollowDelayOverrideForTraceReplay(")
                || line.contains("setTraceReplayFollowDelayFrames(")
                || line.contains("traceReplayFollowDelayFrames")
                || line.contains("S3kElasticWindowController")
                || line.contains(".advanceRecordingCursor(")
                || line.contains(".isStrictComparisonEnabled()")
                || line.contains(".strictTraceIndex()")
                || line.contains(".driveTraceIndex()")
                || line.contains(".hydrateFromRomSnapshot(")
                || line.contains("hydrateFromRomSnapshot(")
                || setterHydratesPrimaryPlayerState(line)
                || setterHydratesCameraState(line)
                || setterHydratesRingState(line)
                || line.contains("setCentreX(state.")
                || line.contains("setCentreY(state.")
                || line.contains("setXSpeed(state.")
                || line.contains("setYSpeed(state.")
                || line.contains("setGSpeed(state.")
                || line.contains("setCentreX(frame.")
                || line.contains("setCentreY(frame.")
                || line.contains("setXSpeed(frame.")
                || line.contains("setYSpeed(frame.")
                || line.contains("setGSpeed(frame.")
                || line.contains("setCentreX((short) snapshot.xPos())")
                || line.contains("setCentreY((short) snapshot.yPos())")
                || line.contains("setXSpeed((short) snapshot.xVel())")
                || line.contains("setYSpeed((short) snapshot.yVel())")
                || line.contains("fields.get(\"")
                || line.contains("frameZero != null && frameZero.")
                || line.contains("recordedRings = frameZero")
                || line.contains("recordedCamera")
                || SETTER_FROM_TRACE_FIELD.matcher(line).find();
    }

    private static boolean isVisualTraceGhostHydration(Path source) {
        return normalize(source).equals("src/main/java/com/openggf/sprites/ghost/GhostTraceRenderer.java");
    }

    private static boolean isAllowedTraceBootstrapLine(String trimmed) {
        return trimmed.startsWith("//")
                || trimmed.startsWith("*")
                || trimmed.contains("public void advanceRecordingCursor(")
                || trimmed.contains("TraceReplayBootstrap.initialVblankCounterForTraceReplay(trace)")
                || trimmed.contains("TraceReplayBootstrap.alignFrameCountersForReplayStart(")
                || trimmed.contains("GameServices.spritesOrNull().setFrameCounter(")
                || trimmed.contains("GameServices.levelOrNull().setFrameCounter(")
                || trimmed.contains("sprite.setCentreX(meta.startX())")
                || trimmed.contains("sprite.setCentreY(meta.startY())");
    }

    private static boolean setterHydratesPrimaryPlayerState(String line) {
        return traceSetter(line, "setCentreX")
                || traceSetter(line, "setCentreY")
                || traceSetter(line, "setXSpeed")
                || traceSetter(line, "setYSpeed")
                || traceSetter(line, "setGSpeed")
                || traceSetter(line, "setXSubpixelRaw")
                || traceSetter(line, "setYSubpixelRaw")
                || traceSetter(line, "setAir")
                || traceSetter(line, "setRolling")
                || traceSetter(line, "setGroundMode")
                || traceSetter(line, "setAngle");
    }

    private static boolean setterHydratesCameraState(String line) {
        return traceSetter(line, "setX")
                || traceSetter(line, "setY")
                || traceSetter(line, "setPosition")
                || traceSetter(line, "setCameraX")
                || traceSetter(line, "setCameraY");
    }

    private static boolean setterHydratesRingState(String line) {
        return traceSetter(line, "setRingCount")
                || traceSetter(line, "setRings")
                || traceSetter(line, "setCollectedRing")
                || traceSetter(line, "setRing");
    }

    private static boolean traceSetter(String line, String method) {
        int call = line.indexOf("." + method + "(");
        if (call < 0) {
            return false;
        }
        int start = line.indexOf('(', call);
        int end = line.indexOf(')', start + 1);
        String args = end > start ? line.substring(start + 1, end) : line.substring(start + 1);
        return readsTraceState(args);
    }

    private static boolean readsTraceState(String expression) {
        return expression.contains("state.")
                || expression.contains("frame.")
                || expression.contains("snapshot.")
                || expression.contains("aux.")
                || expression.contains("fields.get(")
                || expression.contains("TraceFrame")
                || expression.contains("TraceEvent");
    }

    private static List<Path> traceConsumersOutsideCurrentRoots() throws IOException {
        List<Path> consumers = new ArrayList<>();
        for (Path root : List.of(MAIN_ROOT, TEST_ROOT)) {
            for (Path source : javaSources(root)) {
                String normalized = normalize(source);
                if (normalized.startsWith("src/main/java/com/openggf/trace/")
                        || normalized.startsWith("src/test/java/com/openggf/tests/trace/")
                        || SELF_PATH.equals(normalized)) {
                    continue;
                }
                String text = Files.readString(source);
                if (importsTraceReplayData(text)) {
                    consumers.add(source);
                }
            }
        }
        return consumers;
    }

    private static boolean importsTraceReplayData(String text) {
        return text.contains("import com.openggf.trace.*;")
                || text.contains("import com.openggf.trace.")
                || text.contains("com.openggf.trace.")
                || text.contains("import com.openggf.trace.TraceData;")
                || text.contains("import com.openggf.trace.TraceFrame;")
                || text.contains("import com.openggf.trace.TraceEvent;")
                || text.contains("import com.openggf.trace.TraceReplayBootstrap;");
    }

    private static List<Path> traceParserDataAndCatalogSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path source : javaSources(Path.of("src/main/java/com/openggf/trace"))) {
            String normalized = normalize(source);
            if (normalized.startsWith("src/main/java/com/openggf/trace/live/")
                    || normalized.startsWith("src/main/java/com/openggf/trace/replay/")
                    || normalized.equals("src/main/java/com/openggf/trace/TraceReplayBootstrap.java")) {
                continue;
            }
            sources.add(source);
        }
        return sources;
    }

    private static List<Path> javaSources(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(TestTraceReplayInvariantGuard::normalize))
                    .toList();
        }
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }
}
