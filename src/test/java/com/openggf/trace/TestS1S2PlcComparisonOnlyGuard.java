package com.openggf.trace;

import com.openggf.game.timing.HardwareWorkKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Keeps S1/S2 PLC readiness native: trace records may compare it, but never schedule it.
 */
class TestS1S2PlcComparisonOnlyGuard {
    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path TRACE_REPLAY_TESTS = Path.of("src", "test", "java", "com", "openggf", "tests", "trace");
    private static final List<String> PLC_SERVICES = List.of(
            "com.openggf.game.sonic1.resources.Sonic1PlcService",
            "com.openggf.game.sonic2.resources.Sonic2PlcService");
    private static final Pattern TRACE_IMPORT = Pattern.compile(
            "(?m)^import\\s+(?:static\\s+)?com\\.openggf\\.trace(?:\\.[\\w*]+)*\\s*;");
    private static final Pattern PLC_IMPORT = Pattern.compile(
            "(?m)^import\\s+(?:static\\s+)?com\\.openggf\\.game\\.sonic[12]\\.resources\\.Sonic[12]PlcService(?:\\.[\\w*]+)?\\s*;");
    private static final Pattern PLC_FQ_REFERENCE = Pattern.compile(
            "com\\.openggf\\.game\\.sonic[12]\\.resources\\.Sonic[12]PlcService");

    @Test
    void nativePlcServicesDoNotDependOnTracePackages() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String service : PLC_SERVICES) {
            Path source = MAIN.resolve(service.replace('.', '/') + ".java");
            if (TRACE_IMPORT.matcher(Files.readString(source)).find()) {
                violations.add(service + " imports com.openggf.trace");
            }
        }
        assertNoViolations("native PLC services must not depend on trace data", violations);
    }

    @Test
    void traceProductionSourcesDoNotDependOnNativePlcServices() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaFiles(MAIN.resolve("com/openggf/trace"))) {
            String text = Files.readString(source);
            String relative = MAIN.relativize(source).toString().replace('\\', '/');
            if (PLC_IMPORT.matcher(text).find() || PLC_FQ_REFERENCE.matcher(text).find()) {
                violations.add(relative + " depends on an S1/S2 PLC service");
            }
        }
        assertNoViolations("trace production sources must not control native PLC readiness", violations);
    }

    @Test
    void timingKindRegistryAdmitsOnlyKosinskiWork() {
        assertFalse(Arrays.stream(HardwareWorkKind.values())
                        .map(Enum::name)
                        .anyMatch(name -> name.contains("PLC")),
                "PLC readiness is native deterministic service, not timing-stream authority");
        var admittedKinds = Arrays.stream(HardwareWorkKind.values())
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!admittedKinds.equals(Set.of("KOS_MODULE_QUEUE", "KOS_DECOMPRESSION_QUEUE"))) {
            fail("hardware timing may admit only S3K Kosinski work, but was " + admittedKinds);
        }
    }

    @Test
    void replayAndBootstrapSourcesDoNotReferenceNativePlcServices() throws IOException {
        List<String> violations = new ArrayList<>();
        List<Path> sources = new ArrayList<>();
        sources.addAll(javaFiles(MAIN.resolve("com/openggf/trace/replay")));
        sources.addAll(javaFiles(TRACE_REPLAY_TESTS));
        for (Path source : sources) {
            String text = Files.readString(source);
            if (PLC_IMPORT.matcher(text).find() || PLC_FQ_REFERENCE.matcher(text).find()) {
                violations.add(source.toString().replace('\\', '/')
                        + " references a native S1/S2 PLC service from replay/bootstrap code");
            }
        }
        assertNoViolations("trace replay/bootstrap must not reference native S1/S2 PLC services", violations);
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static void assertNoViolations(String rule, List<String> violations) {
        if (!violations.isEmpty()) {
            fail(rule + ":\n  " + String.join("\n  ", violations));
        }
    }
}
