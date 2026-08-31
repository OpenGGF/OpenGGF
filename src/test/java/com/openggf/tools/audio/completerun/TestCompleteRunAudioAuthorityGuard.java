package com.openggf.tools.audio.completerun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestCompleteRunAudioAuthorityGuard {
    private static final Path MAIN_ROOT =
            Path.of("src/main/java/com/openggf");
    private static final List<Path> COMPLETE_RUN_ROOTS = List.of(
            Path.of("src/main/java/com/openggf/tools/audio/completerun"),
            Path.of("src/test/java/com/openggf/tools/audio/completerun"));
    private static final String TOOLING_PACKAGE =
            "com.openggf.tools.audio.completerun";
    private static final List<Path> AUTHENTICATED_OPEN_GGF_SOURCES = List.of(
            Path.of("src/main/java/com/openggf/tools/audio/completerun/Bk2InputCursor.java"),
            Path.of("src/main/java/com/openggf/tools/audio/completerun/ProductionBk2AudioRunner.java"),
            Path.of("src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioObserverLease.java"),
            Path.of("src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioCaptureReducer.java"),
            Path.of("src/main/java/com/openggf/tools/audio/completerun/s2/S2CompleteRunOpenGgfProducer.java"),
            Path.of("src/main/java/com/openggf/tools/audio/completerun/s3k/S3kCompleteRunOpenGgfProducer.java"));
    private static final List<Path> SHARED_DIAGNOSTIC_BOUNDARIES = List.of(
            Path.of("src/main/java/com/openggf/audio/AudioAdmissionObserver.java"),
            Path.of("src/main/java/com/openggf/audio/AudioDiagnosticObserverException.java"),
            Path.of("src/main/java/com/openggf/audio/driver/SmpsDriverServiceObserver.java"),
            Path.of("src/main/java/com/openggf/audio/driver/SmpsRequestAdmissionPolicy.java"));
    private static final Pattern GAME_NAME_CHECK = Pattern.compile(
            "(?i)\\b(sonic\\s*[123]|sonic3k|s1|s2|s3k)\\b");
    private static final Pattern REFERENCE_AUTHORITY_PARAMETER =
            Pattern.compile("(?i)\\b(reference|expected|oracle|sidecar)"
                    + "[a-z0-9_]*\\b");
    private static final List<ForbiddenAuthority> FORBIDDEN_ENGINE_AUTHORITIES = List.of(
            forbidden("trace-runtime",
                    "\\b(?:TraceSessionLauncher|TraceReplaySessionBootstrap|"
                            + "TraceReplayBootstrap|TraceReplayDrive|TraceReplayDriver|"
                            + "TraceReplayFixture|TraceData|TraceEntry|TraceFiles|"
                            + "TraceMetadata|TraceFrame|TraceEvent|TraceHistoryHydration|"
                            + "TracePayloadReader|TraceDataLoader|SpecialStageTraceData|"
                            + "Sonic1SpecialStageTraceData|Sonic3kSpecialStageTraceData|"
                            + "TraceInputSource)\\b"),
            forbidden("alternate-input-owner",
                    "\\b(?:PlaybackDebugManager|RecordingFrameDriver|"
                            + "TraceRunSpecialStageRows|SpecialStageRecordedPassPacing|"
                            + "UserRecordingSessionLauncher)\\b"),
            forbidden("fixture-bootstrap",
                    "\\b(?:HeadlessGameBoot|HeadlessTestFixture|"
                            + "UserRecordingSmokeHarness)\\b"),
            forbidden("reference-producer-process",
                    "\\bTraceChaserAudioProcess\\b"),
            forbidden("reference-home", "\\breferenceHome\\s*\\("),
            forbidden("reference-reader",
                    "\\b(?:S2OracleEngineCapture|S3kOpenGgfAudioCapture|"
                            + "[A-Za-z0-9_]*(?:ReferenceRawAdapter|ReferenceReader|"
                            + "ReferenceProjector|ReferenceProducer|AudioOracleComparator)|"
                            + "referenceTick|SPEED_UP_ROW)\\b"),
            forbidden("hardware-timing",
                    "\\b(?:HardwareTiming[A-Za-z0-9_]*|hardwareTiming|"
                            + "hardware_timing)\\b"),
            forbidden("direct-startup-mutation",
                    "\\b(?:loadLevel|loadCurrentLevel|loadDefaultStartingLevel|"
                            + "selectEntry|setGameMode)\\s*\\("),
            forbidden("clamping-bk2-access", "\\bgetFrame\\s*\\("),
            forbidden("manifest-gameplay-state",
                    "\\b(?:TraceRunManifest|TraceManifest|ManifestBootstrap|"
                            + "BootstrapDescriptor|PhysicsRow|PhysicsCsv|AuxiliaryRow|"
                            + "AuxState|DynamicArtDescriptor|LagState)\\b"),
            forbidden("fitted-audio-input",
                    "\\b(?:referenceAudio|referenceSound|mailboxBytes|queueVector|"
                            + "speedUpCoordinate)\\b"));

    @TempDir
    Path temporaryDirectory;

    @Test
    void productionOutsideToolsCannotImportCompleteRunCaptureAuthority()
            throws IOException {
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(MAIN_ROOT)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.startsWith(
                            MAIN_ROOT.resolve("tools")))
                    .forEach(path -> inspectForbiddenReference(
                            path, violations));
        }

        assertEquals(List.of(), violations,
                "production behavior must not depend on complete-run tooling");
    }

    @Test
    void openGgfProducersCannotAcceptReferenceReadersOrArtifacts()
            throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : COMPLETE_RUN_ROOTS) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var files = Files.walk(root)) {
                files.filter(path -> {
                            String name = path.getFileName().toString();
                            return name.endsWith(".java")
                                    && name.contains("OpenGgf");
                        })
                        .forEach(path -> inspectProducerAuthority(
                                path, violations));
            }
        }

        assertEquals(List.of(), violations,
                "OpenGGF producers may accept ROM/run/output identity, never"
                        + " reference capture authority");
    }

    @Test
    void authenticatedOpenGgfSourcesHaveNoTraceOrReferenceBehaviorAuthority()
            throws IOException {
        List<String> violations = new ArrayList<>();
        assertTrue(Files.isRegularFile(
                AUTHENTICATED_OPEN_GGF_SOURCES.getFirst()),
                "the inventory must include the live frame-zero cursor");
        for (Path source : AUTHENTICATED_OPEN_GGF_SOURCES) {
            if (Files.isRegularFile(source)) {
                inspectProducerAuthority(source, violations);
            }
        }

        assertEquals(List.of(), violations,
                "authenticated OpenGGF capture sources may consume normal ROM/BK2/run"
                        + " identity, never trace, reference, timing, or direct-load state");
    }

    @Test
    void sharedDiagnosticBoundariesRemainGameNeutral() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path boundary : SHARED_DIAGNOSTIC_BOUNDARIES) {
            String source = Files.readString(boundary);
            if (GAME_NAME_CHECK.matcher(source).find()) {
                violations.add(boundary.toString());
            }
        }
        assertEquals(List.of(), violations,
                "shared observer and request-policy contracts cannot contain"
                        + " game-name checks");
    }

    @Test
    void producerGuardReadsConstructorParametersNotComments()
            throws IOException {
        Path producer = temporaryDirectory.resolve(
                "CompleteRunOpenGgfCapture.java");
        Files.writeString(producer, """
                final class CompleteRunOpenGgfCapture {
                    // A reference capture is comparison-only.
                    CompleteRunOpenGgfCapture(java.nio.file.Path output) { }
                }
                """);
        List<String> violations = new ArrayList<>();
        inspectProducerAuthority(producer, violations);
        assertEquals(List.of(), violations);

        Files.writeString(producer, """
                final class CompleteRunOpenGgfCapture {
                    CompleteRunOpenGgfCapture(
                            java.nio.file.Path referenceCapturePath) { }
                }
                """);
        inspectProducerAuthority(producer, violations);
        assertEquals(1, violations.size());
    }

    @Test
    void producerGuardRejectsForbiddenEngineAuthoritiesBeyondConstructorNames()
            throws IOException {
        Path producer = temporaryDirectory.resolve(
                "S2CompleteRunOpenGgfProducer.java");
        Files.writeString(producer, """
                final class S2CompleteRunOpenGgfProducer {
                    S2CompleteRunOpenGgfProducer(java.nio.file.Path output) { }
                    void capture(CompleteRunAudioProducer.Request request) {
                        TraceData traceData = null;
                        request.referenceHome();
                    }
                }
                """);

        List<String> violations = new ArrayList<>();
        inspectProducerAuthority(producer, violations);

        assertEquals(List.of(
                producer + ":trace-runtime",
                producer + ":reference-home"), violations);
    }

    @Test
    void producerGuardIgnoresCommentsAndStringLiterals() throws IOException {
        Path producer = temporaryDirectory.resolve(
                "S2CompleteRunOpenGgfProducer.java");
        Files.writeString(producer, """
                final class S2CompleteRunOpenGgfProducer {
                    // TraceData and request.referenceHome() are forbidden examples.
                    /* TraceSessionLauncher and HardwareTimingInput too. */
                    String diagnostic = "S3kOpenGgfAudioCapture SPEED_UP_ROW";
                    char quote = '\"';
                    void capture(CompleteRunAudioProducer.Request request) {
                        use(request.runManifest());
                    }
                }
                """);

        List<String> violations = new ArrayList<>();
        inspectProducerAuthority(producer, violations);

        assertEquals(List.of(), violations);
    }

    @Test
    void producerGuardIgnoresJavaTextBlocks() throws IOException {
        Path producer = temporaryDirectory.resolve(
                "S2CompleteRunOpenGgfProducer.java");
        String tripleQuote = "\"\"\"";
        Files.writeString(producer,
                "final class S2CompleteRunOpenGgfProducer {\n"
                        + "  String diagnostic = " + tripleQuote + "\n"
                        + "    TraceData request.referenceHome() HardwareTimingInput\n"
                        + "    " + tripleQuote + ";\n"
                        + "}\n");

        List<String> violations = new ArrayList<>();
        inspectProducerAuthority(producer, violations);

        assertEquals(List.of(), violations);
    }

    @Test
    void producerGuardAllowsRunManifestIdentityButRejectsGameplayManifestTypes()
            throws IOException {
        Path producer = temporaryDirectory.resolve(
                "S2CompleteRunOpenGgfProducer.java");
        Files.writeString(producer, """
                final class S2CompleteRunOpenGgfProducer {
                    void capture(CompleteRunAudioProducer.Request request) {
                        verifyIdentity(request.runManifest());
                    }
                }
                """);
        List<String> violations = new ArrayList<>();
        inspectProducerAuthority(producer, violations);
        assertEquals(List.of(), violations);

        Files.writeString(producer, """
                final class S2CompleteRunOpenGgfProducer {
                    void capture(CompleteRunAudioProducer.Request request) {
                        TraceRunManifest manifest = parse(request.runManifest());
                    }
                }
                """);
        inspectProducerAuthority(producer, violations);
        assertEquals(List.of(producer + ":manifest-gameplay-state"),
                violations);
    }

    private static void inspectForbiddenReference(
            Path path, List<String> violations) {
        try {
            String source = Files.readString(path);
            if (source.contains(TOOLING_PACKAGE)) {
                violations.add(path.toString());
            }
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void inspectProducerAuthority(
            Path path, List<String> violations) {
        try {
            String source = stripCommentsAndLiterals(Files.readString(path));
            String name = path.getFileName().toString();
            String simpleName = name.substring(0,
                    name.length() - ".java".length());
            Pattern constructor = Pattern.compile(
                    "\\b" + Pattern.quote(simpleName)
                            + "\\s*\\(([^)]*)\\)",
                    Pattern.DOTALL);
            var constructors = constructor.matcher(source);
            while (constructors.find()) {
                var parameter = REFERENCE_AUTHORITY_PARAMETER.matcher(
                        constructors.group(1));
                if (parameter.find()) {
                    violations.add(path + ":"
                            + parameter.group().toLowerCase(Locale.ROOT));
                }
            }
            for (ForbiddenAuthority authority : FORBIDDEN_ENGINE_AUTHORITIES) {
                if (authority.pattern().matcher(source).find()) {
                    violations.add(path + ":" + authority.label());
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static ForbiddenAuthority forbidden(String label, String regex) {
        return new ForbiddenAuthority(label, Pattern.compile(regex));
    }

    private static String stripCommentsAndLiterals(String source) {
        StringBuilder stripped = new StringBuilder(source.length());
        LexicalState state = LexicalState.CODE;
        for (int index = 0; index < source.length();) {
            char current = source.charAt(index);
            char next = index + 1 < source.length()
                    ? source.charAt(index + 1)
                    : '\0';
            boolean tripleQuote = current == '"'
                    && next == '"'
                    && index + 2 < source.length()
                    && source.charAt(index + 2) == '"';

            if (state == LexicalState.CODE) {
                if (current == '/' && next == '/') {
                    stripped.append("  ");
                    index += 2;
                    state = LexicalState.LINE_COMMENT;
                } else if (current == '/' && next == '*') {
                    stripped.append("  ");
                    index += 2;
                    state = LexicalState.BLOCK_COMMENT;
                } else if (tripleQuote) {
                    stripped.append("   ");
                    index += 3;
                    state = LexicalState.TEXT_BLOCK;
                } else if (current == '"') {
                    stripped.append(' ');
                    index++;
                    state = LexicalState.STRING;
                } else if (current == '\'') {
                    stripped.append(' ');
                    index++;
                    state = LexicalState.CHARACTER;
                } else {
                    stripped.append(current);
                    index++;
                }
                continue;
            }

            if (state == LexicalState.LINE_COMMENT) {
                stripped.append(current == '\n' ? '\n' : ' ');
                index++;
                if (current == '\n') {
                    state = LexicalState.CODE;
                }
                continue;
            }

            if (state == LexicalState.BLOCK_COMMENT) {
                if (current == '*' && next == '/') {
                    stripped.append("  ");
                    index += 2;
                    state = LexicalState.CODE;
                } else {
                    stripped.append(current == '\n' ? '\n' : ' ');
                    index++;
                }
                continue;
            }

            if (state == LexicalState.TEXT_BLOCK) {
                if (tripleQuote) {
                    stripped.append("   ");
                    index += 3;
                    state = LexicalState.CODE;
                } else {
                    stripped.append(current == '\n' ? '\n' : ' ');
                    index++;
                }
                continue;
            }

            if (current == '\\' && index + 1 < source.length()) {
                stripped.append("  ");
                index += 2;
            } else if ((state == LexicalState.STRING && current == '"')
                    || (state == LexicalState.CHARACTER && current == '\'')) {
                stripped.append(' ');
                index++;
                state = LexicalState.CODE;
            } else {
                stripped.append(current == '\n' ? '\n' : ' ');
                index++;
            }
        }
        return stripped.toString();
    }

    private record ForbiddenAuthority(String label, Pattern pattern) { }

    private enum LexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TEXT_BLOCK
    }
}
