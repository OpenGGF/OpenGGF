package com.openggf.trace.timing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Source-level ratchet for the one-way hardware-timing replay input boundary. */
class TestHardwareTimingAuthorityGuard {
    private static final Path SRC_MAIN = Path.of("src", "main", "java");
    private static final Path SRC_TEST = Path.of("src", "test", "java", "com", "openggf", "trace", "timing");
    private static final String TIMING_PACKAGE_PREFIX = "com.openggf.trace.timing";
    private static final String HARDWARE_TIMING_SERVICE = "com.openggf.game.timing.HardwareTimingService";
    private static final String HARDWARE_TIMING_FILE = "hardware_timing.jsonl";
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");
    private static final Pattern HARDWARE_TIMING_SERVICE_ACCESS = Pattern.compile(
            "(?m)^import\\s+(?:static\\s+)?com\\.openggf\\.game\\.timing"
                    + "(?:\\.HardwareTimingService(?:\\.[\\w*]+)?|\\.\\*)\\s*;"
                    + "|\\bcom\\.openggf\\.game\\.timing\\.HardwareTimingService\\b");
    private static final Pattern TIMING_PARSER_ACCESS = Pattern.compile(
            "(?m)^import\\s+(?:static\\s+)?com\\.openggf\\.trace\\.timing(?:\\.[\\w*]+)*\\s*;"
                    + "|\\bcom\\.openggf\\.trace\\.timing\\.[A-Z][A-Za-z0-9_]*\\b");
    private static final Pattern HARDWARE_TIMING_FILE_LITERAL = Pattern.compile(
            "\\\"hardware_timing\\.jsonl\\\"");
    private static final Pattern HARDWARE_TIMING_FILE_CONCATENATION = Pattern.compile(
            "\\\"hardware_\\\"\\s*\\+\\s*\\\"timing\\.jsonl\\\"");
    private static final Pattern HARDWARE_TIMING_FILE_INDIRECTION = Pattern.compile(
            "\\b(?:HARDWARE_TIMING_FILE|HARDWARE_TIMING_FILENAME|TIMING_FILE_NAME)\\b");
    private static final Pattern AUXILIARY_PARSER_MARKER = Pattern.compile(
            "aux_state\\.jsonl|\\bloadAuxEvents\\s*\\(|\\bTraceData\\s*\\.\\s*loadAuxEvents\\s*\\(");
    private static final Pattern REFLECTIVE_GAMEPLAY_ACCESS = Pattern.compile(
            "\\b(?:java\\.lang\\.reflect|java\\.lang\\.invoke\\.MethodHandles|MethodHandles|"
                    + "getDeclaredMethod|getMethod|setAccessible|unreflect|findVirtual|findStatic|findSpecial|"
                    + "invokeExact|invokeWithArguments)\\b|\\.invoke\\s*\\(");
    private static final Pattern ROOT_GAMEPLAY_MUTATION = Pattern.compile(
            "\\bGameServices\\s*\\.|\\b(?:setGameMode|setGameplayMode|setLevel|setCentre[XY]|"
                    + "setRingCount|setForcedInputMask|startSession)\\s*\\(");
    private static final List<String> GAMEPLAY_OWNER_PACKAGE_PREFIXES = List.of(
            "com.openggf.game",
            "com.openggf.level",
            "com.openggf.sprites",
            "com.openggf.physics",
            "com.openggf.control",
            "com.openggf.camera");
    private static final List<String> AUXILIARY_PARSER_PATH_PREFIXES = List.of(
            "com/openggf/trace/",
            "com/openggf/game/sonic1/specialstage/",
            "com/openggf/game/sonic3k/specialstage/");
    private static final Set<String> ROOT_GAMEPLAY_OWNER_TYPES = Set.of(
            "com.openggf.Engine",
            "com.openggf.GameLoop",
            "com.openggf.TraceSessionLauncher",
            "com.openggf.LevelIterationAdmissionController");

    @Test
    void rejectsDirectWildcardStaticAndFullyQualifiedTimingServiceAccess() {
        assertDetected(scanForbiddenTimingServiceAccess("sample/TraceFrame.java", """
                import com.openggf.game.timing.HardwareTimingService;
                class TraceFrame {}
                """));
        assertDetected(scanForbiddenTimingServiceAccess("sample/TraceEvent.java", """
                import com.openggf.game.timing.*;
                class TraceEvent {}
                """));
        assertDetected(scanForbiddenTimingServiceAccess("sample/TraceBinder.java", """
                import static com.openggf.game.timing.HardwareTimingService.release;
                class TraceBinder {}
                """));
        assertDetected(scanForbiddenTimingServiceAccess("sample/TraceData.java", """
                class TraceData {
                    com.openggf.game.timing.HardwareTimingService timing;
                }
                """));
    }

    @Test
    void discoversAuxiliaryParsersFromOwnedPathsAndParserMarkers() {
        assertTrue(isComparisonOrAuxiliaryParser("com/openggf/trace/TraceData.java", """
                class TraceData { void parse() { loadAuxEvents(); } }
                """));
        assertTrue(isComparisonOrAuxiliaryParser(
                "com/openggf/game/sonic1/specialstage/Sonic1SpecialStageTraceData.java", """
                class Sonic1SpecialStageTraceData { void parse() { TraceData.loadAuxEvents(path); } }
                """));
        assertFalse(isComparisonOrAuxiliaryParser("com/openggf/game/sonic1/objects/Badnik.java", """
                class Badnik { void update() { loadAuxEvents(); } }
                """));
    }

    @Test
    void rejectsAuxiliaryParserThatUsesOnlyTheAuxFilenameLiteralToReachTimingService() {
        String relative = "com/openggf/trace/NewAuxiliaryParser.java";
        String source = """
                package com.openggf.trace;
                import com.openggf.game.timing.HardwareTimingService;
                class NewAuxiliaryParser {
                    void load(java.nio.file.Path directory) {
                        directory.resolve("aux_state.jsonl");
                    }
                }
                """;

        assertTrue(isComparisonOrAuxiliaryParser(relative, source));
        assertDetected(scanForbiddenTimingServiceAccess(relative, source));
    }

    @Test
    void rejectsDirectWildcardStaticAndFullyQualifiedParserAccessFromGameplayOwners() {
        assertDetected(scanGameplayTimingParserAccess("com.openggf.physics", "Physics.java", """
                import com.openggf.trace.timing.HardwareTimingSchedule;
                class Physics {}
                """));
        assertDetected(scanGameplayTimingParserAccess("com.openggf.control", "InputController.java", """
                import com.openggf.trace.timing.*;
                class InputController {}
                """));
        assertDetected(scanGameplayTimingParserAccess("com.openggf.camera", "Camera.java", """
                import static com.openggf.trace.timing.HardwareTimingSchedule.empty;
                class Camera {}
                """));
        assertDetected(scanGameplayTimingParserAccess("com.openggf", "GameLoop.java", """
                class GameLoop {
                    com.openggf.trace.timing.HardwareTimingSchedule schedule;
                }
                """));
        assertDetected(scanGameplayTimingParserAccess("com.openggf", "GameplayMutationRoot.java", """
                import com.openggf.trace.timing.HardwareTimingSchedule;
                class GameplayMutationRoot {
                    void update() { setGameMode(null); }
                }
                """));
    }

    @Test
    void rejectsDirectConcatenatedAndIndirectTimingFilenameConstructionOutsideTheLoader() {
        assertDetected(scanUnauthorizedTimingFilenameConstruction("com.openggf.trace", "TraceData.java", """
                class TraceData { String file = "hardware_timing.jsonl"; }
                """));
        assertDetected(scanUnauthorizedTimingFilenameConstruction("com.openggf.trace", "TraceData.java", """
                class TraceData { String file = "hardware_" + "timing.jsonl"; }
                """));
        assertDetected(scanUnauthorizedTimingFilenameConstruction("com.openggf.trace", "TraceData.java", """
                class TraceData { String file = HARDWARE_TIMING_FILE; }
                """));
        assertTrue(scanUnauthorizedTimingFilenameConstruction(TIMING_PACKAGE_PREFIX,
                "HardwareTimingStreamLoader.java", """
                class HardwareTimingStreamLoader {
                    String file = "hardware_" + "timing.jsonl";
                }
                """).isEmpty());
    }

    @Test
    void rejectsReflectiveAndMethodHandleTimingFixtureMutationAcrossTestRoots() {
        assertDetected(scanReflectiveTimingFixtureAccess("sample/TestTimingFixture.java", """
                import com.openggf.game.timing.HardwareTimingService;
                import java.lang.reflect.Method;
                class TestTimingFixture { void test(Method method) throws Exception { method.invoke(null); } }
                """));
        assertDetected(scanReflectiveTimingFixtureAccess("sample/TestTimingFixture.java", """
                class TestTimingFixture {
                    void test() throws Exception {
                        java.lang.reflect.Method method = null;
                        method.invoke(null);
                        Class.forName("com.openggf.game.timing.HardwareTimingService");
                    }
                }
                """));
        assertDetected(scanReflectiveTimingFixtureAccess("sample/TestTimingFixture.java", """
                import com.openggf.game.timing.HardwareTimingService;
                import java.lang.invoke.MethodHandles;
                class TestTimingFixture {
                    void test(MethodHandles.Lookup lookup) throws Throwable {
                        lookup.findVirtual(HardwareTimingService.class, "release", null).invoke(null);
                    }
                }
                """));
    }

    @Test
    void comparisonAndAuxiliaryParsersDoNotImportTheGameplayTimingService() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            String relative = relative(file);
            String source = Files.readString(file);
            if (!isComparisonOrAuxiliaryParser(relative, source)) {
                continue;
            }
            violations.addAll(scanForbiddenTimingServiceAccess(relative, source));
        }

        assertNoViolations("comparison and aux parsers must not control gameplay timing", violations);
    }

    @Test
    void onlyTheDedicatedTimingPackageMayParseTheHardwareTimingFile() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            violations.addAll(scanUnauthorizedTimingFilenameConstruction(
                    packageName(file), file.getFileName().toString(), Files.readString(file)));
        }

        assertNoViolations("only the timing parser package may parse the hardware timing stream", violations);
    }

    @Test
    void gameplayOwnersDoNotImportTimingParserTypes() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            String packageName = packageName(file);
            violations.addAll(scanGameplayTimingParserAccess(
                    packageName, file.getFileName().toString(), Files.readString(file)));
        }

        assertNoViolations("gameplay owners must receive timing readiness through the gameplay service", violations);
    }

    @Test
    void timingFixturesCannotInvokeGameplayMutationThroughReflection() throws IOException {
        if (!Files.isDirectory(SRC_TEST)) {
            return;
        }

        List<String> violations = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(SRC_TEST)) {
            for (Path file : stream.filter(path -> path.toString().endsWith(".java")).toList()) {
                violations.addAll(scanReflectiveTimingFixtureAccess(
                        SRC_TEST.relativize(file).toString().replace('\\', '/'), Files.readString(file)));
            }
        }

        assertNoViolations("timing fixtures must not mutate gameplay through reflection", violations);
    }

    private static List<Path> productionFiles() throws IOException {
        try (Stream<Path> stream = Files.walk(SRC_MAIN)) {
            return stream.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String relative(Path file) {
        return SRC_MAIN.relativize(file).toString().replace('\\', '/');
    }

    private static String packageName(Path file) throws IOException {
        var matcher = PACKAGE_DECLARATION.matcher(Files.readString(file));
        if (!matcher.find()) {
            throw new AssertionError("missing package declaration: " + relative(file));
        }
        return matcher.group(1);
    }

    private static boolean hasPackagePrefix(String packageName, List<String> prefixes) {
        return prefixes.stream().anyMatch(prefix -> packageName.equals(prefix) || packageName.startsWith(prefix + "."));
    }

    private static boolean hasPathPrefix(String relativePath, List<String> prefixes) {
        return prefixes.stream().anyMatch(relativePath::startsWith);
    }

    private static boolean isComparisonOrAuxiliaryParser(String relative, String source) {
        return relative.equals("com/openggf/trace/TraceFrame.java")
                || relative.equals("com/openggf/trace/TraceEvent.java")
                || relative.equals("com/openggf/trace/TraceBinder.java")
                || (hasPathPrefix(relative, AUXILIARY_PARSER_PATH_PREFIXES)
                && AUXILIARY_PARSER_MARKER.matcher(stripCommentsPreservingStrings(source)).find());
    }

    private static List<String> scanForbiddenTimingServiceAccess(String relative, String source) {
        return scanAccess(relative, stripCommentsAndStrings(source), HARDWARE_TIMING_SERVICE_ACCESS,
                " accesses " + HARDWARE_TIMING_SERVICE);
    }

    private static List<String> scanGameplayTimingParserAccess(String packageName, String fileName, String source) {
        String typeName = packageName + "." + fileName.replaceFirst("\\.java$", "");
        if (!hasPackagePrefix(packageName, GAMEPLAY_OWNER_PACKAGE_PREFIXES)
                && !ROOT_GAMEPLAY_OWNER_TYPES.contains(typeName)
                && !(packageName.equals("com.openggf")
                && ROOT_GAMEPLAY_MUTATION.matcher(stripCommentsAndStrings(source)).find())) {
            return List.of();
        }
        return scanAccess(typeName.replace('.', '/') + ".java", stripCommentsAndStrings(source), TIMING_PARSER_ACCESS,
                " accesses a " + TIMING_PACKAGE_PREFIX + " parser type");
    }

    private static List<String> scanUnauthorizedTimingFilenameConstruction(
            String packageName, String fileName, String source) {
        if (packageName.equals(TIMING_PACKAGE_PREFIX) && fileName.equals("HardwareTimingStreamLoader.java")) {
            return List.of();
        }
        if (HARDWARE_TIMING_FILE_LITERAL.matcher(source).find()
                || HARDWARE_TIMING_FILE_CONCATENATION.matcher(source).find()
                || HARDWARE_TIMING_FILE_INDIRECTION.matcher(stripCommentsAndStrings(source)).find()) {
            return List.of(packageName + "." + fileName + " constructs " + HARDWARE_TIMING_FILE
                    + " outside " + TIMING_PACKAGE_PREFIX + ".HardwareTimingStreamLoader");
        }
        return List.of();
    }

    private static List<String> scanReflectiveTimingFixtureAccess(String relative, String source) {
        if (!source.contains("HardwareTiming") && !source.contains("hardware_timing")) {
            return List.of();
        }
        if (REFLECTIVE_GAMEPLAY_ACCESS.matcher(stripCommentsAndStrings(source)).find()) {
            return List.of(relative + " reflectively accesses a hardware-timing gameplay boundary");
        }
        return List.of();
    }

    private static List<String> scanAccess(String relative, String source, Pattern access, String message) {
        return access.matcher(source).find() ? List.of(relative + message) : List.of();
    }

    private static void assertDetected(List<String> violations) {
        assertFalse(violations.isEmpty(), "source guard must reject this bypass form");
    }

    private static String stripCommentsAndStrings(String source) {
        StringBuilder stripped = new StringBuilder(source.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaping = false;

        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (inLineComment) {
                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                    stripped.append(current);
                } else {
                    stripped.append(' ');
                }
            } else if (inBlockComment) {
                if (current == '*' && next == '/') {
                    stripped.append("  ");
                    index++;
                    inBlockComment = false;
                } else {
                    stripped.append(current == '\n' || current == '\r' ? current : ' ');
                }
            } else if (inString || inChar) {
                char terminator = inString ? '"' : '\'';
                stripped.append(current == '\n' || current == '\r' || current == terminator ? current : ' ');
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == terminator) {
                    inString = false;
                    inChar = false;
                }
            } else if (current == '/' && next == '/') {
                stripped.append("  ");
                index++;
                inLineComment = true;
            } else if (current == '/' && next == '*') {
                stripped.append("  ");
                index++;
                inBlockComment = true;
            } else {
                if (current == '"') {
                    inString = true;
                } else if (current == '\'') {
                    inChar = true;
                }
                stripped.append(current);
            }
        }
        return stripped.toString();
    }

    private static String stripCommentsPreservingStrings(String source) {
        StringBuilder stripped = new StringBuilder(source.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaping = false;

        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (inLineComment) {
                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                    stripped.append(current);
                } else {
                    stripped.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    stripped.append("  ");
                    index++;
                    inBlockComment = false;
                } else {
                    stripped.append(current == '\n' || current == '\r' ? current : ' ');
                }
                continue;
            }
            if (inString || inChar) {
                char terminator = inString ? '"' : '\'';
                stripped.append(current);
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == terminator) {
                    inString = false;
                    inChar = false;
                }
                continue;
            }
            if (current == '/' && next == '/') {
                stripped.append("  ");
                index++;
                inLineComment = true;
            } else if (current == '/' && next == '*') {
                stripped.append("  ");
                index++;
                inBlockComment = true;
            } else {
                if (current == '"') {
                    inString = true;
                } else if (current == '\'') {
                    inChar = true;
                }
                stripped.append(current);
            }
        }
        return stripped.toString();
    }

    private static void assertNoViolations(String message, List<String> violations) {
        if (!violations.isEmpty()) {
            fail(message + ":\n  " + String.join("\n  ", violations));
        }
    }
}
