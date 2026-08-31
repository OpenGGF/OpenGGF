package com.openggf.tools.audio.completerun;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestCompleteRunAudioAuthorityGuard {
    private static final Path MAIN_ROOT =
            Path.of("src/main/java/com/openggf");
    private static final Path COMPLETE_RUN_MAIN_ROOT =
            MAIN_ROOT.resolve("tools/audio/completerun");
    private static final List<Path> COMPLETE_RUN_ROOTS = List.of(
            Path.of("src/main/java/com/openggf/tools/audio/completerun"),
            Path.of("src/test/java/com/openggf/tools/audio/completerun"));
    private static final String TOOLING_PACKAGE =
            "com.openggf.tools.audio.completerun";
    private static final Path BK2_INPUT_CURSOR = completeRunSource(
            "Bk2InputCursor.java");
    private static final List<Path> REQUIRED_AUTHENTICATED_SOURCES = List.of(
            BK2_INPUT_CURSOR);
    private static final List<Path> TASK_5_AUTHENTICATED_STAGE = List.of(
            completeRunSource("ProductionBk2AudioRunner.java"),
            completeRunSource("CompleteRunAudioObserverLease.java"));
    private static final List<Path> TASK_6_AUTHENTICATED_STAGE = List.of(
            completeRunSource("CompleteRunAudioCaptureReducer.java"),
            completeRunSource("s2/S2CompleteRunOpenGgfProducer.java"),
            completeRunSource("s3k/S3kCompleteRunOpenGgfProducer.java"));
    private static final Set<Path> PERMITTED_REFERENCE_SOURCES = Set.of(
            completeRunSource("TraceChaserAudioProcess.java"),
            completeRunSource("s2/S2CompleteRunReferenceProducer.java"),
            completeRunSource("s2/S2CompleteRunReferenceProjector.java"),
            completeRunSource("s2/S2CompleteRunReferenceRawAdapter.java"),
            completeRunSource("s3k/S3kCompleteRunReferencePreflight.java"),
            completeRunSource("s3k/S3kCompleteRunReferenceProducer.java"),
            completeRunSource("s3k/S3kCompleteRunReferenceProjector.java"),
            completeRunSource("s3k/S3kCompleteRunReferenceRawAdapter.java"));
    private static final Set<Path> ESTABLISHED_NON_AUTHENTICATED_SOURCES = Set.of(
            completeRunSource("CompleteRunAudioCaptureStore.java"),
            completeRunSource("CompleteRunAudioComparator.java"),
            completeRunSource("CompleteRunAudioJson.java"),
            completeRunSource("CompleteRunAudioProducer.java"),
            completeRunSource("CompleteRunAudioProducerRegistry.java"),
            completeRunSource("CompleteRunAudioProfile.java"),
            completeRunSource("CompleteRunAudioProfiles.java"),
            completeRunSource("CompleteRunAudioRecordSink.java"),
            completeRunSource("CompleteRunAudioReport.java"),
            completeRunSource("CompleteRunAudioTool.java"),
            completeRunSource("CompleteRunAudioTrace.java"),
            completeRunSource("s1/S1CompleteRunAudioProfile.java"),
            completeRunSource("s1/S1CompleteRunStateNormalizer.java"),
            completeRunSource("s2/S2CompleteRunAssetCatalog.java"),
            completeRunSource("s2/S2CompleteRunAudioProfile.java"),
            completeRunSource("s2/S2CompleteRunStateDecoder.java"),
            completeRunSource("s2/S2CompleteRunStateNormalizer.java"),
            completeRunSource("s2/S2NativeSoundResolver.java"),
            completeRunSource("s3k/S3kCompleteRunAssetCatalog.java"),
            completeRunSource("s3k/S3kCompleteRunAudioProfile.java"),
            completeRunSource("s3k/S3kCompleteRunStateDecoder.java"),
            completeRunSource("s3k/S3kCompleteRunStateNormalizer.java"),
            completeRunSource("s3k/S3kNativeSoundResolver.java"));
    private static final List<Path> SHARED_DIAGNOSTIC_BOUNDARIES = List.of(
            Path.of("src/main/java/com/openggf/audio/AudioAdmissionObserver.java"),
            Path.of("src/main/java/com/openggf/audio/AudioDiagnosticObserverException.java"),
            Path.of("src/main/java/com/openggf/audio/driver/SmpsDriverServiceObserver.java"),
            Path.of("src/main/java/com/openggf/audio/driver/SmpsRequestAdmissionPolicy.java"));
    private static final Pattern GAME_NAME_CHECK = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:sonic(?:[_\\s-]*[123](?:k)?)|"
                    + "s[_-]?[123](?:k)?)(?![a-z0-9])");
    private static final Set<String> PERMITTED_BK2_INPUT_TYPES = Set.of(
            "Bk2FrameInput", "Bk2Movie", "Bk2MovieLoader",
            "RecordedInputSnapshots");
    private static final Pattern DEBUG_PLAYBACK_TYPE = Pattern.compile(
            "\\bcom\\.openggf\\.debug\\.playback\\.([A-Za-z0-9_*]+)");
    private static final List<ForbiddenAuthority> AUTHORITY_CATEGORIES = List.of(
            forbidden("forbidden-package",
                    "(?<![A-Za-z0-9_])(?:(?:[a-z_][a-z0-9_]*\\.)*trace\\."
                            + "(?:[A-Za-z_*][A-Za-z0-9_*]*)|"
                            + "com\\.openggf\\.(?:game\\.(?:timing|recording)|"
                            + "tools\\.audio\\.parity)(?:\\.|\\b))"),
            forbidden("trace-authority",
                    "\\bTrace(?![A-Za-z0-9_]*Playback)"
                            + "(?!RunManifest\\b|Manifest\\b)[A-Za-z0-9_]+\\b"),
            forbidden("playback-authority",
                    "\\b(?:Playback[A-Za-z0-9_]*(?:Controller|Manager|"
                            + "Coordinator|Driver|Session|Owner)|"
                            + "[A-Z][A-Za-z0-9_]*Playback(?:Controller|Manager|"
                            + "Coordinator|Driver|Session|Owner))\\b"),
            forbidden("frame-input-authority",
                    "\\b(?:com\\.openggf\\.tools\\.)?RecordingFrameDriver\\b"),
            forbidden("timing-authority",
                    "\\b(?:HardwareTiming|HardwareCompletion|RecordedCompletion)"
                            + "[A-Za-z0-9_]*\\b"),
            forbidden("oracle-authority",
                    "(?i)\\b[a-z0-9_]*(?:oracle|audio_?parity)[a-z0-9_]*\\b"),
            forbidden("reference-authority",
                    "(?:(?i:\\b(?:reference|expected|sidecar)[a-z0-9_]*\\b)|"
                            + "\\b[A-Za-z0-9_]*(?:Reference|Expected|Sidecar)"
                            + "[A-Za-z0-9_]*\\b|"
                            + "(?i:\\b(?:[a-z0-9]+_)+(?:reference|expected|sidecar)"
                            + "(?:_[a-z0-9]+)*\\b))"));
    private static final List<ForbiddenAuthority> FORBIDDEN_ENGINE_OPERATIONS = List.of(
            forbidden("fixture-bootstrap",
                    "\\b(?:HeadlessGameBoot|HeadlessTestFixture|"
                            + "UserRecordingSmokeHarness)\\b"),
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
        requirePresent(REQUIRED_AUTHENTICATED_SOURCES, violations);
        requirePresent(List.copyOf(PERMITTED_REFERENCE_SOURCES), violations);
        requireCompleteStage(TASK_5_AUTHENTICATED_STAGE, violations);
        requireCompleteStage(TASK_6_AUTHENTICATED_STAGE, violations);
        inspectDiscoveredEngineSources(
                COMPLETE_RUN_MAIN_ROOT, PERMITTED_REFERENCE_SOURCES,
                violations);

        assertEquals(List.of(), violations,
                "authenticated OpenGGF capture sources may consume normal ROM/BK2/run"
                        + " identity, never trace, reference, timing, or direct-load state");
    }

    @Test
    void plannedAuthenticatedStageCannotSilentlySkipMissingCollaborator()
            throws IOException {
        Path present = temporaryDirectory.resolve(
                "ProductionBk2AudioRunner.java");
        Path missing = temporaryDirectory.resolve(
                "CompleteRunAudioObserverLease.java");
        Files.writeString(present, "final class ProductionBk2AudioRunner { }\n");
        List<String> violations = new ArrayList<>();

        requireCompleteStage(List.of(present, missing), violations);

        assertEquals(List.of(missing + ":missing-planned-source"), violations);
    }

    @Test
    void newlyAddedCompleteRunHelperIsDiscoveredWithoutInventoryEdit()
            throws IOException {
        Path helper = temporaryDirectory.resolve("ProductionAudioHelper.java");
        Files.writeString(helper, """
                final class ProductionAudioHelper {
                    TraceRunFrameDriver forbidden;
                }
                """);
        List<String> violations = new ArrayList<>();

        inspectDiscoveredEngineSources(
                temporaryDirectory, Set.of(), violations);

        assertEquals(List.of(helper + ":trace-authority"), violations);
    }

    @Test
    void newlyAddedNestedSharedHelperMustRemainGameNeutral()
            throws IOException {
        Path sharedRoot = temporaryDirectory.resolve("common");
        Files.createDirectories(sharedRoot);
        Path helper = sharedRoot.resolve("ProductionAudioHelper.java");
        Files.writeString(helper, """
                final class ProductionAudioHelper {
                    int S2;
                }
                """);
        List<String> violations = new ArrayList<>();

        inspectDiscoveredEngineSources(
                temporaryDirectory, Set.of(), violations);

        assertEquals(List.of(helper + ":game-specific-authority"), violations);
    }

    @Test
    void sharedAuthenticatedSourcesRemainGameNeutral() {
        List<String> violations = new ArrayList<>();
        List<Path> sharedSources = new ArrayList<>(
                REQUIRED_AUTHENTICATED_SOURCES);
        sharedSources.addAll(TASK_5_AUTHENTICATED_STAGE);
        sharedSources.add(TASK_6_AUTHENTICATED_STAGE.getFirst());
        for (Path source : sharedSources) {
            if (Files.isRegularFile(source)) {
                inspectGameNeutrality(source, violations);
            }
        }

        assertEquals(List.of(), violations,
                "the cursor, runner, lease, and reducer are shared owners;"
                        + " typed game-specific producers own per-game behavior");
    }

    @Test
    void sharedAuthenticatedNeutralityIgnoresProseButRejectsGameCode()
            throws IOException {
        Path source = temporaryDirectory.resolve("ProductionBk2AudioRunner.java");
        Files.writeString(source, """
                final class ProductionBk2AudioRunner {
                    // S2 is permitted in explanatory prose.
                    String diagnostic = "S3K";
                    int S2;
                }
                """);
        List<String> violations = new ArrayList<>();

        inspectGameNeutrality(source, violations);

        assertEquals(List.of(source + ":game-specific-authority"), violations);
    }

    @Test
    void sharedDiagnosticBoundariesRemainGameNeutral() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path boundary : SHARED_DIAGNOSTIC_BOUNDARIES) {
            inspectGameNeutrality(boundary, violations);
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
                producer + ":trace-authority",
                producer + ":reference-authority"), violations);
    }

    @Test
    void producerGuardCoversEachForbiddenAuthorityCategory()
            throws IOException {
        Path producer = temporaryDirectory.resolve(
                "S2CompleteRunOpenGgfProducer.java");
        Files.writeString(producer, """
                final class S2CompleteRunOpenGgfProducer {
                    TraceRunFrameDriver traceRuntime;
                    TraceRunPlaybackCoordinator playbackOwner;
                    S2OracleRawStream rawStream;
                    AudioParityJsonl rawReader;
                    TraceHardwareTimingBoundaryObserver timingObserver;
                    HardwareCompletionEdge timingEdge;
                    RecordedCompletionAuthority timingAuthority;
                    void capture(CompleteRunAudioProducer.Request request) {
                        loadLevel();
                        consume(request::referenceHome);
                    }
                }
                """);

        List<String> violations = new ArrayList<>();
        inspectProducerAuthority(producer, violations);

        assertEquals(List.of(
                producer + ":trace-authority",
                producer + ":playback-authority",
                producer + ":timing-authority",
                producer + ":oracle-authority",
                producer + ":reference-authority",
                producer + ":direct-startup-mutation"), violations);
    }

    @Test
    void categoryClosureRejectsEquivalentAndFutureAuthorityOwners()
            throws IOException {
        List<AuthorityMutation> mutations = List.of(
                new AuthorityMutation(
                        "TraceRunReplayWalker owner;", "trace-authority"),
                new AuthorityMutation(
                        "TraceRunSpecialStageRowDriver owner;", "trace-authority"),
                new AuthorityMutation(
                        "PlaybackTimelineController owner;", "playback-authority"),
                new AuthorityMutation(
                        "RecordingFrameDriver owner;", "frame-input-authority"),
                new AuthorityMutation(
                        "com.openggf.tools.RecordingFrameDriver owner;",
                        "frame-input-authority"),
                new AuthorityMutation(
                        "TraceHardwareTimingScheduleCompiler owner;",
                        "trace-authority"),
                new AuthorityMutation(
                        "HardwareTimingService owner;", "timing-authority"),
                new AuthorityMutation(
                        "HardwareTimingSnapshot owner;", "timing-authority"),
                new AuthorityMutation(
                        "S2OracleDriverState owner;", "oracle-authority"),
                new AuthorityMutation(
                        "S2AudioOracleTool owner;", "oracle-authority"),
                new AuthorityMutation(
                        "AudioParityComparator owner;", "oracle-authority"),
                new AuthorityMutation(
                        "S2CompleteRunReferenceProjector owner;",
                        "reference-authority"),
                new AuthorityMutation(
                        "S3kCompleteRunReferenceProducer owner;",
                        "reference-authority"),
                new AuthorityMutation(
                        "java.nio.file.Path EXPECTED_AUDIO;",
                        "reference-authority"),
                new AuthorityMutation(
                        "java.nio.file.Path SidecarPath;",
                        "reference-authority"),
                new AuthorityMutation(
                        "java.nio.file.Path OracleCapture;",
                        "oracle-authority"),
                new AuthorityMutation(
                        "void run(java.nio.file.Path referenceCapture) { }",
                        "reference-authority"),
                new AuthorityMutation(
                        "com.openggf.trace.future.AnyFutureAuthority owner;",
                        "forbidden-package"));

        for (int index = 0; index < mutations.size(); index++) {
            AuthorityMutation mutation = mutations.get(index);
            Path producer = temporaryDirectory.resolve(
                    "ProductionAudioHelper" + index + ".java");
            Files.writeString(producer,
                    "final class ProductionAudioHelper" + index + " { "
                            + mutation.source() + " }\n");
            List<String> violations = new ArrayList<>();

            inspectProducerAuthority(producer, violations);

            assertEquals(List.of(producer + ":" + mutation.expectedLabel()),
                    violations, mutation.source());
        }
    }

    @Test
    void tracePackageClosureRejectsNestedSegmentsAndWildcards()
            throws IOException {
        Path producer = temporaryDirectory.resolve("ProductionAudioHelper.java");
        Files.writeString(producer, """
                import com.openggf.game.sonic2.trace.Sonic2TornadoRidePrelude;
                final class ProductionAudioHelper { }
                """);
        List<String> violations = new ArrayList<>();
        inspectProducerAuthority(producer, violations);
        assertEquals(List.of(producer + ":forbidden-package"), violations);

        Files.writeString(producer, """
                import com.openggf.game.sonic2.trace.*;
                final class ProductionAudioHelper { }
                """);
        violations.clear();
        inspectProducerAuthority(producer, violations);
        assertEquals(List.of(producer + ":forbidden-package"), violations);

        Files.writeString(producer, """
                import trace.future.*;
                final class ProductionAudioHelper { }
                """);
        violations.clear();
        inspectProducerAuthority(producer, violations);
        assertEquals(List.of(producer + ":forbidden-package"), violations);
    }

    @Test
    void ordinaryAudioPlaybackTerminologyIsNotAuthority() throws IOException {
        Path producer = temporaryDirectory.resolve("ProductionAudioHelper.java");
        Files.writeString(producer, """
                final class ProductionAudioHelper {
                    SmpsSfxPlaybackPolicy policy;
                    void stopPlayback() { }
                }
                """);
        List<String> violations = new ArrayList<>();

        inspectProducerAuthority(producer, violations);

        assertEquals(List.of(), violations);
    }

    @Test
    void unexpectedIdentifiersAreNotReferenceAuthority() throws IOException {
        Path producer = temporaryDirectory.resolve("ProductionAudioHelper.java");
        Files.writeString(producer, """
                final class ProductionAudioHelper {
                    boolean unexpectedMode;
                    RuntimeException unexpectedFailure;
                }
                """);
        List<String> violations = new ArrayList<>();

        inspectProducerAuthority(producer, violations);

        assertEquals(List.of(), violations);
    }

    @Test
    void playbackPackageAllowsOnlyBk2InputTypes() throws IOException {
        Path producer = temporaryDirectory.resolve("ProductionAudioHelper.java");
        Files.writeString(producer, """
                import com.openggf.debug.playback.Bk2FrameInput;
                import com.openggf.debug.playback.Bk2Movie;
                import com.openggf.debug.playback.Bk2MovieLoader;
                import com.openggf.debug.playback.RecordedInputSnapshots;
                final class ProductionAudioHelper { }
                """);
        List<String> violations = new ArrayList<>();
        inspectProducerAuthority(producer, violations);
        assertEquals(List.of(), violations);

        Files.writeString(producer, """
                import com.openggf.debug.playback.PlaybackDebugManager;
                final class ProductionAudioHelper { }
                """);
        inspectProducerAuthority(producer, violations);
        assertEquals(List.of(producer + ":playback-authority"), violations);

        violations.clear();
        Files.writeString(producer, """
                import com.openggf.debug.playback.*;
                final class ProductionAudioHelper { }
                """);
        inspectProducerAuthority(producer, violations);
        assertEquals(List.of(producer + ":playback-authority"), violations);
    }

    @Test
    void sharedNeutralityRecognizesProjectStyleGameIdentifiers()
            throws IOException {
        Path source = temporaryDirectory.resolve("ProductionBk2AudioRunner.java");
        Files.writeString(source, """
                final class ProductionBk2AudioRunner {
                    int SONIC_2;
                    int SONIC_3K;
                    int SONIC_2_ROM;
                }
                """);
        List<String> violations = new ArrayList<>();

        inspectGameNeutrality(source, violations);

        assertEquals(List.of(source + ":game-specific-authority"), violations);
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
    void producerGuardDoesNotEndTextBlockAtEscapedTripleQuote()
            throws IOException {
        Path producer = temporaryDirectory.resolve(
                "S2CompleteRunOpenGgfProducer.java");
        String tripleQuote = "\"\"\"";
        Files.writeString(producer,
                "final class S2CompleteRunOpenGgfProducer {\n"
                        + "  String diagnostic = " + tripleQuote + "\n"
                        + "    escaped delimiter " + "\\" + tripleQuote + "\n"
                        + "    TraceRunFrameDriver request::referenceHome\n"
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
            inspectForbiddenEngineAuthorities(path, source, true, violations);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Path completeRunSource(String relativePath) {
        return COMPLETE_RUN_MAIN_ROOT.resolve(relativePath);
    }

    private static void requirePresent(
            List<Path> requiredSources, List<String> violations) {
        for (Path source : requiredSources) {
            if (!Files.isRegularFile(source)) {
                violations.add(source + ":missing-required-source");
            }
        }
    }

    private static void requireCompleteStage(
            List<Path> stageSources, List<String> violations) {
        boolean stageStarted = stageSources.stream()
                .anyMatch(Files::isRegularFile);
        if (!stageStarted) {
            return;
        }
        for (Path source : stageSources) {
            if (!Files.isRegularFile(source)) {
                violations.add(source + ":missing-planned-source");
            }
        }
    }

    private static void inspectDiscoveredEngineSources(
            Path root,
            Set<Path> permittedReferenceSources,
            List<String> violations) throws IOException {
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !permittedReferenceSources.contains(path))
                    .forEach(path -> inspectDiscoveredEngineSource(
                            root, path, violations));
        }
    }

    private static void inspectDiscoveredEngineSource(
            Path root, Path path, List<String> violations) {
        try {
            String source = stripCommentsAndLiterals(Files.readString(path));
            boolean establishedFramework =
                    ESTABLISHED_NON_AUTHENTICATED_SOURCES.contains(path);
            inspectForbiddenEngineAuthorities(
                    path, source, !establishedFramework, violations);
            if (!establishedFramework) {
                if (!isTypedGameSource(root, path)) {
                    inspectGameNeutrality(path, source, violations);
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static boolean isTypedGameSource(Path root, Path path) {
        Path relative = root.relativize(path);
        if (relative.getNameCount() < 2) {
            return false;
        }
        String owner = relative.getName(0).toString();
        return Set.of("s1", "s2", "s3k").contains(owner);
    }

    private static void inspectForbiddenEngineAuthorities(
            Path path,
            String source,
            boolean authenticatedSource,
            List<String> violations) {
        for (ForbiddenAuthority authority : AUTHORITY_CATEGORIES) {
            if (authority.label().equals("reference-authority")
                    && !authenticatedSource) {
                continue;
            }
            if (authority.pattern().matcher(source).find()) {
                violations.add(path + ":" + authority.label());
            }
        }
        var playbackTypes = DEBUG_PLAYBACK_TYPE.matcher(source);
        while (playbackTypes.find()) {
            if (!PERMITTED_BK2_INPUT_TYPES.contains(playbackTypes.group(1))) {
                String violation = path + ":playback-authority";
                if (!violations.contains(violation)) {
                    violations.add(violation);
                }
            }
        }
        for (ForbiddenAuthority authority : FORBIDDEN_ENGINE_OPERATIONS) {
            if (authority.pattern().matcher(source).find()) {
                violations.add(path + ":" + authority.label());
            }
        }
    }

    private static void inspectGameNeutrality(
            Path path, List<String> violations) {
        try {
            String source = stripCommentsAndLiterals(Files.readString(path));
            inspectGameNeutrality(path, source, violations);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void inspectGameNeutrality(
            Path path, String source, List<String> violations) {
        if (GAME_NAME_CHECK.matcher(source).find()) {
            violations.add(path + ":game-specific-authority");
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
                if (current == '\\' && index + 1 < source.length()) {
                    stripped.append("  ");
                    index += 2;
                } else if (tripleQuote) {
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

    private record AuthorityMutation(String source, String expectedLabel) { }

    private enum LexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TEXT_BLOCK
    }
}
