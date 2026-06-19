package com.openggf.tests;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class TestBuildToolingGuard {

    private static final List<String> TRACE_REPLAY_DIAGNOSTIC_EXCLUDES = List.of(
            "**/Debug*.java",
            "**/*Debug*.java",
            "**/*Probe.java",
            "**/*Probe*.java");
    private static final List<Pattern> TRACE_BOOTSTRAP_POLICY_SIGNALS = List.of(
            Pattern.compile("\\b(?:meta|metadata)\\s*\\.\\s*(?:zoneId|act|traceProfile)\\s*\\("),
            Pattern.compile("\\bhasPerFrameSlotMachineState\\s*\\("),
            Pattern.compile("\\bfindCheckpointFrame\\s*\\("),
            Pattern.compile("\\bcheckpoint\\s*\\.\\s*name\\s*\\("),
            Pattern.compile("\\bcurrent\\s*\\.\\s*frame\\s*\\(\\s*\\)\\s*(?:[<>=!]=?|\\+|-)"));
    private static final Pattern TRACE_ROW_PLAYER_SETTER_HYDRATION = Pattern.compile(
            "\\.\\s*set(?:CentreX|CentreY|XSpeed|YSpeed|GSpeed|Angle|Air|Rolling|SubpixelRaw)\\s*\\("
                    + ".*\\b(?:current|previous|firstFrame|expected|traceFrame|frame)\\s*\\.\\s*"
                    + "(?:x|y|xSpeed|ySpeed|gSpeed|angle|air|rolling|xSub|ySub)\\s*\\(");
    private static final Set<String> ACCEPTED_TRACE_BOOTSTRAP_POLICY_SIGNALS = Set.of(
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - if (!meta.hasPerFrameSlotMachineState()) {",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - && \"level_gated_reset_aware\".equals(metadata.traceProfile())",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - if (current.frame() < firstLevelFrame) {",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - if (current.frame() == firstLevelFrame) {",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - if (current.frame() <= firstLevelFrame) {",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - if (previous != null || current.frame() != 0) {",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - int gameplayStartFrame = findCheckpointFrame(trace, \"gameplay_start\");",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - return gameplayStartFrame >= 0 && current.frame() <= gameplayStartFrame;",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - || !\"complete_run\".equals(metadata.traceProfile())",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - private static int findCheckpointFrame(TraceData trace, String checkpointName) {",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - && checkpointName.equals(checkpoint.name())) {");
    private static final Set<String> REVIEWED_S3K_STATIC_SESSION_STATE = Set.of(
            "src/main/java/com/openggf/game/sonic3k/events/S3kSeamlessMutationExecutor.java - private static volatile AizFireOverlayData cachedAizFireOverlay;",
            "src/main/java/com/openggf/game/sonic3k/events/Sonic3kAIZEvents.java - private static volatile PendingFireSequence pendingFireSequence;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterSkimHandler.java - private static boolean skimActiveP1;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterSkimHandler.java - private static boolean skimActiveP2;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterSkimHandler.java - private static int splashAnimFrameP1;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterSkimHandler.java - private static int splashAnimFrameP2;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterSkimHandler.java - private static int splashAnimTimerP1;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterSkimHandler.java - private static int splashAnimTimerP2;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterSkimHandler.java - private static int frameCounter;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterSkimHandler.java - private static PatternSpriteRenderer splashRenderer;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterSkimHandler.java - private static boolean artLoaded;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterSkimHandler.java - private static int actId;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterTunnelHandler.java - private static boolean windTunnelFlagP1;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterTunnelHandler.java - private static boolean windTunnelFlagP2;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterTunnelHandler.java - private static int activeTunnelInfluenceP1;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterTunnelHandler.java - private static int activeTunnelInfluenceP2;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterTunnelHandler.java - private static int exitAnimTimerP1;",
            "src/main/java/com/openggf/game/sonic3k/features/HCZWaterTunnelHandler.java - private static int exitAnimTimerP2;",
            "src/main/java/com/openggf/game/sonic3k/objects/Aiz2BossEndSequenceState.java - private static volatile boolean bridgeDropTriggered;",
            "src/main/java/com/openggf/game/sonic3k/objects/Aiz2BossEndSequenceState.java - private static volatile boolean buttonPressed;",
            "src/main/java/com/openggf/game/sonic3k/objects/Aiz2BossEndSequenceState.java - private static volatile boolean eggCapsuleReleased;",
            "src/main/java/com/openggf/game/sonic3k/objects/Aiz2BossEndSequenceState.java - private static volatile boolean cutsceneOverrideObjectsActive;",
            "src/main/java/com/openggf/game/sonic3k/objects/Aiz2BossEndSequenceState.java - private static volatile CutsceneKnucklesAiz2Instance activeKnuckles;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizCollapsingLogBridgeObjectInstance.java - private static volatile boolean drawBridgeBurnActive;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizHollowTreeObjectInstance.java - private static int eventsFg4;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static Pattern[] planePatterns;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static Pattern[] emeraldPatterns;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static Pattern[] introSpritesPatterns;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static Pattern[] knucklesPatterns;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static Pattern[] corkFloorPatterns;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static List<SpriteMappingFrame> planeMappings;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static List<SpriteMappingFrame> emeraldMappings;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static List<SpriteMappingFrame> waveMappings;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static List<SpriteMappingFrame> knucklesMappings;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static List<SpriteMappingFrame> corkFloorMappings;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static List<SpriteDplcFrame> knucklesDplcFrames;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static byte[] superSonicPaletteCycleData;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static byte[] cutsceneKnucklesPalette;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static byte[] emeraldPalette;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static ObjectSpriteSheet planeSheet;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static ObjectSpriteSheet emeraldSheet;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static ObjectSpriteSheet introSpritesSheet;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static ObjectSpriteSheet knucklesSheet;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static ObjectSpriteSheet corkFloorSheet;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static boolean loaded = false;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static ObjectServices activeServices;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static PatternSpriteRenderer planeRenderer;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static PatternSpriteRenderer emeraldRenderer;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static PatternSpriteRenderer introSpritesRenderer;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static PatternSpriteRenderer knucklesRenderer;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static PatternSpriteRenderer corkFloorRenderer;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java - private static boolean renderersCached;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizIntroTerrainSwap.java - private static OverlayData cachedOverlayData;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizPlaneIntroInstance.java - private static int introScrollOffset = 0;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizPlaneIntroInstance.java - private static boolean mainLevelPhaseActive = false;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizPlaneIntroInstance.java - private static boolean mainLevelTerrainSwapAttempted = false;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizPlaneIntroInstance.java - private static int decompressionCountdown = 0;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizPlaneIntroInstance.java - private static AizPlaneIntroInstance activeIntroInstance;",
            "src/main/java/com/openggf/game/sonic3k/objects/AizPlaneIntroInstance.java - private static boolean simulateDecompressionLoading = true;",
            "src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesCnz2AInstance.java - private static volatile CutsceneKnucklesCnz2AInstance activeInstance;",
            "src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesCnz2BInstance.java - private static volatile CutsceneKnucklesCnz2BInstance activeInstance;",
            "src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesHcz2Instance.java - private static volatile CutsceneKnucklesHcz2Instance activeInstance;",
            "src/main/java/com/openggf/game/sonic3k/objects/GumballMachineObjectInstance.java - private static volatile int debugBucketFilter = -1;",
            "src/main/java/com/openggf/game/sonic3k/objects/GumballMachineObjectInstance.java - private static volatile int debugSourceFilter = -1;",
            "src/main/java/com/openggf/game/sonic3k/objects/HCZWaterRushObjectInstance.java - private static int state;",
            "src/main/java/com/openggf/game/sonic3k/objects/HCZWaterRushObjectInstance.java - private static boolean active;",
            "src/main/java/com/openggf/game/sonic3k/objects/IczSnowboardArtLoader.java - private static PatternSpriteRenderer sonicRenderer;",
            "src/main/java/com/openggf/game/sonic3k/objects/IczSnowboardArtLoader.java - private static PatternSpriteRenderer snowboardRenderer;",
            "src/main/java/com/openggf/game/sonic3k/objects/IczSnowboardArtLoader.java - private static PatternSpriteRenderer dustRenderer;",
            "src/main/java/com/openggf/game/sonic3k/objects/IczSnowboardArtLoader.java - private static boolean loaded;");

    @Test
    void surefireShouldPreloadMockitoAsJavaAgent() throws Exception {
        String file = "pom.xml";
        Document pom = parsePom(file);
        List<String> violations = new ArrayList<>();

        if (property(pom, "mockito.version") == null) {
            violations.add(file + " does not define a reusable Mockito version property");
        }
        String mockitoAgentArgLine = property(pom, "mockito.agent.argLine");
        if (mockitoAgentArgLine == null) {
            violations.add(file + " does not define a reusable Mockito javaagent property");
        }
        String mockitoAgentPath = property(pom, "mockito.agent.path");
        if (mockitoAgentPath == null) {
            violations.add(file + " does not define a reusable quoted Mockito agent path property");
        }
        String cdsArgLine = property(pom, "test.cds.argLine");
        if (cdsArgLine == null) {
            violations.add(file + " does not define a reusable test JVM CDS toggle property");
        }
        String surefireArgLine = property(pom, "surefire.argLine");
        if (surefireArgLine == null) {
            violations.add(file + " does not define a reusable Surefire argLine property");
        }
        if (mockitoAgentArgLine != null
                && !mockitoAgentArgLine.contains("-javaagent:")
                && !mockitoAgentArgLine.contains("@{mockito.agent.path}")) {
            violations.add(file + " does not preload mockito-core as a Surefire javaagent");
        }
        if (mockitoAgentPath != null && !mockitoAgentPath.contains("mockito-core-${mockito.version}.jar")) {
            violations.add(file + " does not resolve the Mockito javaagent from the reusable versioned jar path");
        }
        if (mockitoAgentArgLine != null && !mockitoAgentArgLine.contains("${mockito.agent.path}")) {
            violations.add(file + " does not route the Mockito javaagent through the shared mockito.agent.path property");
        }
        if (mockitoAgentPath != null && !mockitoAgentPath.contains("\"")) {
            violations.add(file + " does not quote or escape the Mockito javaagent path for Maven repositories with spaces");
        }
        if (cdsArgLine != null && !"-Xshare:off".equals(cdsArgLine)) {
            violations.add(file + " does not disable CDS for test JVMs after adding the Mockito agent");
        }
        if (surefireArgLine != null && !surefireArgLine.contains("${test.cds.argLine}")) {
            violations.add(file + " does not thread the CDS toggle through Surefire argLine");
        }
        if (surefireArgLine != null && !surefireArgLine.contains("${mockito.agent.argLine}")) {
            violations.add(file + " does not thread the Mockito agent property through Surefire argLine");
        }
        if (!surefirePluginUsesSharedArgLine(pom)) {
            violations.add(file + " does not wire the Surefire plugin to the shared surefire.argLine property");
        }

        if (!violations.isEmpty()) {
            fail("Surefire should preload Mockito cleanly without runtime self-attach or CDS bootstrap warnings:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void traceReplayProfileShouldExcludeDiagnosticTraceProbes() throws Exception {
        String file = "pom.xml";
        Document pom = parsePom(file);
        Element profile = profileById(pom, "trace-replay");
        List<String> violations = new ArrayList<>();

        if (profile == null) {
            violations.add(file + " does not define the trace-replay profile");
        } else {
            List<String> excludes = textValues(profile, "exclude");
            for (String diagnosticExclude : TRACE_REPLAY_DIAGNOSTIC_EXCLUDES) {
                if (!excludes.contains(diagnosticExclude)) {
                    violations.add(file + " trace-replay profile does not exclude " + diagnosticExclude);
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("trace-replay should not select diagnostic Debug*/Probe tests by default:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void traceReplayProfileShouldNotUseBroadTraceIncludeWithoutDiagnosticExcludes() throws Exception {
        String file = "pom.xml";
        Document pom = parsePom(file);
        Element profile = profileById(pom, "trace-replay");
        List<String> violations = new ArrayList<>();

        if (profile == null) {
            violations.add(file + " does not define the trace-replay profile");
        } else {
            List<String> includes = textValues(profile, "include");
            List<String> excludes = textValues(profile, "exclude");
            boolean hasBroadTraceInclude = includes.contains("**/tests/trace/**/*.java");
            boolean hasAllDiagnosticExcludes = excludes.containsAll(TRACE_REPLAY_DIAGNOSTIC_EXCLUDES);
            if (hasBroadTraceInclude && !hasAllDiagnosticExcludes) {
                violations.add(file + " trace-replay profile uses the broad trace include without diagnostic excludes");
            }
        }

        if (!violations.isEmpty()) {
            fail("trace-replay broad includes must be paired with diagnostic test excludes:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void releaseWorkflowShouldRunBranchPolicyOnMasterPullRequests() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/release.yml"));
        List<String> violations = new ArrayList<>();

        if (!workflow.contains("Validate branch policy")) {
            violations.add(".github/workflows/release.yml does not define a branch policy validation step");
        }
        if (!workflow.contains(".githooks/validate-policy.sh ci-pr")) {
            violations.add(".github/workflows/release.yml does not run validate-policy.sh ci-pr for release PRs");
        }
        if (!workflow.contains("fetch-depth: 0")) {
            violations.add(".github/workflows/release.yml policy checkout must use fetch-depth: 0 for commit range validation");
        }
        if (!workflow.contains("Validate branch policy (push)")) {
            violations.add(".github/workflows/release.yml does not validate branch policy on direct master pushes");
        }
        if (!workflow.contains(".githooks/validate-policy.sh ci-push")) {
            violations.add(".github/workflows/release.yml does not run validate-policy.sh ci-push for direct master pushes");
        }

        if (!violations.isEmpty()) {
            fail("release PRs into master must not bypass branch policy validation:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void releaseWorkflowShouldRunTraceReplayPolicyProfile() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/release.yml"));
        List<String> violations = new ArrayList<>();

        if (!workflow.contains("-Ptrace-replay")) {
            violations.add(".github/workflows/release.yml does not run the trace-replay profile during release validation");
        }
        if (!workflow.contains("Run trace replay policy tests")) {
            violations.add(".github/workflows/release.yml should name the trace policy step explicitly");
        }

        if (!violations.isEmpty()) {
            fail("release validation must exercise the trace replay policy profile, not only the default suite:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void releaseWorkflowShouldRequireRomPathsForTraceReplay() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/release.yml"));
        List<String> violations = new ArrayList<>();

        if (!workflow.contains("Verify trace replay ROM paths")) {
            violations.add(".github/workflows/release.yml does not fail early when release trace ROM paths are missing");
        }
        if (!workflow.contains("SONIC1_ROM_PATH")) {
            violations.add(".github/workflows/release.yml does not expose a Sonic 1 ROM path for trace replay");
        }
        if (!workflow.contains("SONIC2_ROM_PATH")) {
            violations.add(".github/workflows/release.yml does not expose a Sonic 2 ROM path for trace replay");
        }
        if (!workflow.contains("S3K_ROM_PATH")) {
            violations.add(".github/workflows/release.yml does not expose an S3K ROM path for trace replay");
        }
        if (!workflow.contains("-Dsonic1.rom.path=\"${SONIC1_ROM_PATH}\"")) {
            violations.add(".github/workflows/release.yml does not pass the Sonic 1 ROM path into Maven");
        }
        if (!workflow.contains("-Dsonic2.rom.path=\"${SONIC2_ROM_PATH}\"")) {
            violations.add(".github/workflows/release.yml does not pass the Sonic 2 ROM path into Maven");
        }
        if (!workflow.contains("-Ds3k.rom.path=\"${S3K_ROM_PATH}\"")) {
            violations.add(".github/workflows/release.yml does not pass the S3K ROM path into Maven");
        }

        if (!violations.isEmpty()) {
            fail("release trace replay validation must require explicit ROM-backed inputs:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void releaseWorkflowShouldRunBroadTestsWithRomFixturesAfterPathVerification() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/release.yml"));
        List<String> violations = new ArrayList<>();

        if (!normalizeLineEndings(workflow).contains("test:\n    runs-on: [self-hosted, release-fixtures]")) {
            violations.add(".github/workflows/release.yml test job must run on the self-hosted release fixture runner");
        }
        int verifyIndex = workflow.indexOf("Verify trace replay ROM paths");
        int broadTestIndex = workflow.indexOf("Run tests");
        if (verifyIndex < 0 || broadTestIndex < 0 || verifyIndex > broadTestIndex) {
            violations.add(".github/workflows/release.yml must verify ROM paths before the broad test suite");
        }
        if (!workflow.contains("mvn -Dmse=off test -B")) {
            violations.add(".github/workflows/release.yml broad test run must disable Maven Silent Extension");
        }
        if (!workflow.contains("-Dsonic1.rom.path=\"${SONIC1_ROM_PATH}\"")) {
            violations.add(".github/workflows/release.yml broad test run must receive the Sonic 1 ROM path");
        }
        if (!workflow.contains("-Dsonic2.rom.path=\"${SONIC2_ROM_PATH}\"")) {
            violations.add(".github/workflows/release.yml broad test run must receive the Sonic 2 ROM path");
        }
        if (!workflow.contains("-Ds3k.rom.path=\"${S3K_ROM_PATH}\"")) {
            violations.add(".github/workflows/release.yml broad test run must receive the S3K ROM path");
        }

        if (!violations.isEmpty()) {
            fail("release validation must run default tests against the same verified ROM fixtures:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void releaseWorkflowShouldAssertTraceReplayCoverageWasNotSkipped() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/release.yml"));
        List<String> violations = new ArrayList<>();

        if (!workflow.contains("Assert trace replay coverage")) {
            violations.add(".github/workflows/release.yml does not assert trace replay coverage after running the profile");
        }
        if (!workflow.contains("target/surefire-reports")) {
            violations.add(".github/workflows/release.yml does not inspect trace replay surefire reports");
        }
        if (!workflow.contains("com.openggf.tests.trace*TraceReplay.txt")) {
            violations.add(".github/workflows/release.yml does not narrow release coverage to TraceReplay reports");
        }
        if (!workflow.contains("Trace replay profile produced no executed ROM-backed trace tests")) {
            violations.add(".github/workflows/release.yml does not fail when every ROM-backed trace test is absent/skipped");
        }
        if (!workflow.contains("Trace replay skipped tests are release-blocking")) {
            violations.add(".github/workflows/release.yml does not fail on unexpected skipped trace replay tests");
        }
        if (workflow.contains("allowed_skipped_reports")
                || workflow.contains("com.openggf.tests.trace.s3k.TestS3kAizTraceReplay.txt")) {
            violations.add(".github/workflows/release.yml still allowlists skipped S3K AIZ trace replay debt");
        }
        if (!workflow.contains("expected_trace_reports")) {
            violations.add(".github/workflows/release.yml does not derive expected trace reports from source tests");
        }
        if (!workflow.contains("expected_policy_reports")) {
            violations.add(".github/workflows/release.yml does not derive expected reports for the full trace-replay profile surface");
        }
        if (!workflow.contains("src/test/java/com/openggf/tests/trace")) {
            violations.add(".github/workflows/release.yml does not scan the source trace tree for expected reports");
        }
        if (!workflow.contains("source_root.rglob(\"Test*.java\")")) {
            violations.add(".github/workflows/release.yml does not scan every Test*.java selected by the trace-replay profile");
        }
        if (!workflow.contains("TRACE_REPLAY_DIAGNOSTIC_EXCLUDES")) {
            violations.add(".github/workflows/release.yml does not name the diagnostic trace exclusions used by the Maven profile");
        }
        for (String diagnosticExclude : TRACE_REPLAY_DIAGNOSTIC_EXCLUDES) {
            String pythonGlob = diagnosticExclude.replace("**/", "");
            if (!workflow.contains("\"" + pythonGlob + "\"")) {
                violations.add(".github/workflows/release.yml trace coverage assertion does not mirror Maven exclude "
                        + diagnosticExclude);
            }
        }
        if (!workflow.contains("if is_diagnostic_trace_source(source):")) {
            violations.add(".github/workflows/release.yml does not skip diagnostic trace sources before expecting reports");
        }
        if (!workflow.contains("expected_trace_reports.add")) {
            violations.add(".github/workflows/release.yml does not add expected reports from TraceReplay source classes");
        }
        if (!workflow.contains("expected_policy_reports.add")) {
            violations.add(".github/workflows/release.yml does not add expected reports from non-TraceReplay profile classes");
        }
        if (!workflow.contains("missing_expected")) {
            violations.add(".github/workflows/release.yml does not fail when expected trace reports are missing");
        }
        if (workflow.contains("allowed_missing_reports")
                || workflow.contains("TEST-com.openggf.tests.trace.s3k.TestS3kAizTraceReplay.xml")) {
            violations.add(".github/workflows/release.yml still allowlists missing S3K AIZ trace replay reports");
        }
        if (!workflow.contains("Missing expected trace replay reports")) {
            violations.add(".github/workflows/release.yml does not report missing expected trace replay reports");
        }
        if (!workflow.contains("Missing expected trace policy reports")) {
            violations.add(".github/workflows/release.yml does not report missing non-TraceReplay trace policy reports");
        }
        if (!workflow.contains("Expected trace replay report did not execute")) {
            violations.add(".github/workflows/release.yml does not fail when an expected trace report is skipped");
        }

        if (!violations.isEmpty()) {
            fail("release trace validation must prove ROM-backed trace tests actually executed:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void releaseWorkflowShouldFailTraceReplayWarnings() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/release.yml"));
        List<String> violations = new ArrayList<>();

        if (!workflow.contains("target/trace-reports")) {
            violations.add(".github/workflows/release.yml does not inspect trace replay divergence reports");
        }
        if (!workflow.contains("warning_count")) {
            violations.add(".github/workflows/release.yml does not check trace replay warning counts");
        }
        if (!workflow.contains("Trace replay warnings are release-blocking")) {
            violations.add(".github/workflows/release.yml does not fail release validation on trace warnings");
        }

        if (!violations.isEmpty()) {
            fail("release trace validation must not certify warning-only trace parity fields:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void releaseWorkflowShouldNotPublishStaticPrereleaseOnEveryMasterPush() throws Exception {
        String workflow = normalizeLineEndings(Files.readString(Path.of(".github/workflows/release.yml")));
        List<String> violations = new ArrayList<>();

        if (!workflow.contains("release:\n    needs: [build, universal-jar]\n    if: github.event_name == 'workflow_dispatch'")) {
            violations.add(".github/workflows/release.yml release job must be gated to manual workflow_dispatch");
        }
        if (workflow.contains("release:\n    needs: [build, universal-jar]\n    if: github.event_name == 'push'")) {
            violations.add(".github/workflows/release.yml still publishes releases automatically on every master push");
        }
        if (!workflow.contains("release:\n    needs: [build, universal-jar]\n    if: github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/master'")) {
            violations.add(".github/workflows/release.yml manual publishing must be restricted to refs/heads/master");
        }
        if (!workflow.contains("Check release tag does not already exist")) {
            violations.add(".github/workflows/release.yml does not fail before publishing an already-existing release tag");
        }
        if (!workflow.contains("git ls-remote --exit-code --tags origin \"refs/tags/v${VERSION}\"")) {
            violations.add(".github/workflows/release.yml does not check whether the version tag already exists on origin");
        }

        if (!violations.isEmpty()) {
            fail("release publishing must be deliberate while the pom version is a static prerelease tag:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void ciAndReleaseMavenCommandsShouldDisableSilentExtension() throws Exception {
        List<String> violations = new ArrayList<>();
        String ci = Files.readString(Path.of(".github/workflows/ci.yml"));
        String release = Files.readString(Path.of(".github/workflows/release.yml"));

        if (!ci.contains("mvn -Dmse=off test -B")) {
            violations.add(".github/workflows/ci.yml test command must pass -Dmse=off");
        }
        if (!release.contains("mvn -Dmse=off test -B")) {
            violations.add(".github/workflows/release.yml broad test command must pass -Dmse=off");
        }
        if (!release.contains("mvn -Dmse=off test -Ptrace-replay -B")) {
            violations.add(".github/workflows/release.yml trace replay command must pass -Dmse=off");
        }
        if (!release.contains("mvn -Dmse=off package -Pnative -DskipTests -B")) {
            violations.add(".github/workflows/release.yml native package command must pass -Dmse=off");
        }

        if (!violations.isEmpty()) {
            fail("CI and release Maven logs must be unsuppressed for release diagnostics:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void developCiShouldProtectDirectPushes() throws Exception {
        String ci = Files.readString(Path.of(".github/workflows/ci.yml"));
        String shellPolicy = Files.readString(Path.of(".githooks/validate-policy.sh"));
        String powershellPolicy = Files.readString(Path.of(".githooks/validate-policy.ps1"));
        List<String> violations = new ArrayList<>();

        if (!ci.contains("push:\n    branches:\n      - develop")) {
            violations.add(".github/workflows/ci.yml must run on direct pushes to develop");
        }
        if (!ci.contains(".githooks/validate-policy.sh ci-push")) {
            violations.add(".github/workflows/ci.yml must run validate-policy.sh ci-push for direct develop pushes");
        }
        if (!ci.contains("github.event_name == 'pull_request'")) {
            violations.add(".github/workflows/ci.yml policy job must keep pull-request branch policy validation");
        }
        if (!ci.contains("github.event_name == 'push'")) {
            violations.add(".github/workflows/ci.yml policy job must add push branch policy validation");
        }
        if (!ci.contains("develop-trace-replay:")) {
            violations.add(".github/workflows/ci.yml must include the nightly/manual develop trace replay job");
        }
        if (!ci.contains("ref: develop")) {
            violations.add(".github/workflows/ci.yml develop trace replay checkout must pin ref: develop so scheduled default-branch runs validate develop");
        }
        if (!shellPolicy.contains("validate_ci_commit_range \"$before_sha\" \"$after_sha\"")) {
            violations.add(".githooks/validate-policy.sh direct pushes must validate commit trailers over the pushed range");
        }
        if (!powershellPolicy.contains("Validate-CiCommitRange $BeforeSha $AfterSha")) {
            violations.add(".githooks/validate-policy.ps1 direct pushes must validate commit trailers over the pushed range");
        }

        if (!violations.isEmpty()) {
            fail("develop CI must protect both pull requests and direct pushes:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void nativeReleasePackagesShouldIncludeEditableConfigYaml() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/release.yml"));
        List<String> violations = new ArrayList<>();

        if (!workflow.contains("Copy-Item \"target/config.yaml\" \"dist/OpenGGF/\"")) {
            violations.add(".github/workflows/release.yml Windows package does not include target/config.yaml");
        }
        if (!workflow.contains("zip -r OpenGGF-macos.zip OpenGGF.app config.yaml")) {
            violations.add(".github/workflows/release.yml macOS package does not include exported config.yaml");
        }
        if (!workflow.contains("cp target/config.yaml dist/OpenGGF/")) {
            violations.add(".github/workflows/release.yml Linux package does not include target/config.yaml");
        }

        if (!violations.isEmpty()) {
            fail("native release packages must include editable config.yaml next to the executable/app:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void worktreePostCheckoutHookShouldLinkCurrentYamlConfig() throws Exception {
        String hook = Files.readString(Path.of(".githooks/post-checkout"));
        List<String> violations = new ArrayList<>();

        if (!hook.contains("link_file \"config.yaml\"")) {
            violations.add(".githooks/post-checkout does not link config.yaml into worktrees");
        }
        if (hook.contains("link_file \"config.json\"")) {
            violations.add(".githooks/post-checkout still links legacy config.json");
        }

        if (!violations.isEmpty()) {
            fail("worktree resource linking must follow the current YAML config file:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void releaseWorkflowShouldSmokeValidatePackagedArtifactsBeforeUpload() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/release.yml"));
        List<String> violations = new ArrayList<>();

        int smokeIndex = workflow.indexOf("Smoke validate packaged artifact");
        int uploadIndex = workflow.indexOf("Upload artifacts");
        if (smokeIndex < 0) {
            violations.add(".github/workflows/release.yml does not smoke validate assembled native archives");
        }
        if (smokeIndex < 0 || uploadIndex < 0 || smokeIndex > uploadIndex) {
            violations.add(".github/workflows/release.yml must smoke validate artifacts before upload");
        }
        if (!workflow.contains("target/OpenGGF-{version}-jar-with-dependencies.jar")) {
            violations.add(".github/workflows/release.yml does not inspect the packaged JVM jar");
        }
        if (!workflow.contains("META-INF/MANIFEST.MF") || !workflow.contains("Main-Class: com.openggf.Engine")) {
            violations.add(".github/workflows/release.yml does not validate manifest bootstrap metadata");
        }
        if (!workflow.contains("config.yaml")) {
            violations.add(".github/workflows/release.yml does not validate packaged config.yaml presence");
        }
        if (!workflow.contains("CFBundleShortVersionString") || !workflow.contains("CFBundleVersion")) {
            violations.add(".github/workflows/release.yml does not validate macOS bundle version metadata");
        }
        if (!workflow.contains("OpenGGF.exe") || !workflow.contains("OpenGGF.app/Contents/MacOS/OpenGGF")
                || !workflow.contains("OpenGGF/OpenGGF")) {
            violations.add(".github/workflows/release.yml does not validate platform launch entry points");
        }

        if (!violations.isEmpty()) {
            fail("release artifacts must be structurally smoke-validated before upload:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void releaseWorkflowShouldPublishUniversalJvmJar() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/release.yml"));
        String normalizedWorkflow = normalizeLineEndings(workflow);
        String pom = Files.readString(Path.of("pom.xml"));
        List<String> violations = new ArrayList<>();

        if (!pom.contains("<id>universal-jar</id>")) {
            violations.add("pom.xml does not define a universal-jar profile");
        }
        List<String> expectedClassifiers = List.of(
                "natives-linux",
                "natives-linux-arm32",
                "natives-linux-arm64",
                "natives-windows",
                "natives-windows-arm64",
                "natives-windows-x86",
                "natives-macos",
                "natives-macos-arm64");
        for (String classifier : expectedClassifiers) {
            if (!pom.contains("<classifier>" + classifier + "</classifier>")) {
                violations.add("pom.xml universal-jar profile does not include LWJGL " + classifier);
            }
        }
        if (!normalizedWorkflow.contains("universal-jar:\n    needs: test")) {
            violations.add(".github/workflows/release.yml does not build the universal JVM jar after release tests");
        }
        if (!workflow.contains("mvn -Dmse=off package -Puniversal-jar -DskipTests -B")) {
            violations.add(".github/workflows/release.yml does not build the universal JVM jar with the universal-jar profile");
        }
        if (!workflow.contains("OpenGGF-universal.jar")) {
            violations.add(".github/workflows/release.yml does not publish a stable OpenGGF-universal.jar artifact");
        }
        for (String classifier : expectedClassifiers) {
            if (!workflow.contains(classifier)) {
                violations.add(".github/workflows/release.yml does not validate the universal jar native classifier "
                        + classifier);
            }
        }
        if (!normalizedWorkflow.contains("release:\n    needs: [build, universal-jar]")) {
            violations.add(".github/workflows/release.yml release job does not wait for the universal jar artifact");
        }

        if (!violations.isEmpty()) {
            fail("release workflow must publish a smoke-validated universal JVM jar:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void macosBundleMetadataShouldMatchMavenVersion() throws Exception {
        String expectedVersion = property(parsePom("pom.xml"), "version");
        String plist = Files.readString(Path.of("src/packaging/Info.plist"));
        List<String> violations = new ArrayList<>();

        if (!plistValueEquals(plist, "CFBundleVersion", expectedVersion)) {
            violations.add("src/packaging/Info.plist CFBundleVersion must match pom.xml version " + expectedVersion);
        }
        if (!plistValueEquals(plist, "CFBundleShortVersionString", expectedVersion)) {
            violations.add("src/packaging/Info.plist CFBundleShortVersionString must match pom.xml version "
                    + expectedVersion);
        }

        if (!violations.isEmpty()) {
            fail("macOS release metadata must not drift from the Maven release version:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void sourceManifestShouldNotCarryLegacyJogampClasspath() throws Exception {
        String manifest = Files.readString(Path.of("src/main/java/META-INF/MANIFEST.MF"));
        List<String> violations = new ArrayList<>();

        if (!manifest.contains("Main-Class: com.openggf.Engine")) {
            violations.add("src/main/java/META-INF/MANIFEST.MF does not identify com.openggf.Engine as Main-Class");
        }
        if (manifest.contains("Class-Path:")) {
            violations.add("src/main/java/META-INF/MANIFEST.MF should not define a stale manual Class-Path");
        }
        for (String legacyDependency : List.of("jogl", "gluegen", "joal", "jocl")) {
            if (manifest.toLowerCase().contains(legacyDependency)) {
                violations.add("src/main/java/META-INF/MANIFEST.MF still references legacy " + legacyDependency
                        + " artifacts");
            }
        }

        if (!violations.isEmpty()) {
            fail("the checked-in manifest must not mislead packaging work with obsolete JOGL-era dependencies:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void nativeImageLwjglDiscoveryShouldOnlyTrustExecutableAdjacentLibraries() throws Exception {
        String engine = Files.readString(Path.of("src/main/java/com/openggf/Engine.java"));
        List<String> violations = new ArrayList<>();

        if (engine.contains("hasNativeLibs(cwd)")) {
            violations.add("Engine native-image LWJGL discovery trusts the process working directory");
        }
        if (engine.contains("target/native-libs")) {
            violations.add("Engine native-image LWJGL discovery trusts target/native-libs relative to cwd");
        }
        if (!engine.contains("findNativeLibsDirForTesting(")) {
            violations.add("Engine native-image LWJGL discovery is not covered by deterministic path-selection tests");
        }
        if (!engine.contains("isSameCanonicalFile(")) {
            violations.add("Engine native-image LWJGL discovery does not canonicalize and compare trusted directories");
        }

        if (!violations.isEmpty()) {
            fail("native-image LWJGL discovery must only trust executable-adjacent packaged libraries:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void branchPolicyShouldValidateCommitTrailersForMasterPullRequests() throws Exception {
        String shellPolicy = Files.readString(Path.of(".githooks/validate-policy.sh"));
        String powershellPolicy = Files.readString(Path.of(".githooks/validate-policy.ps1"));
        List<String> violations = new ArrayList<>();

        if (!shellPolicy.contains("if [ \"$base_ref\" != \"develop\" ] && [ \"$base_ref\" != \"master\" ]; then")) {
            violations.add(".githooks/validate-policy.sh ci-pr mode must continue for base_ref=master");
        }
        if (!powershellPolicy.contains("if ($BaseRef -ne \"develop\" -and $BaseRef -ne \"master\") {")) {
            violations.add(".githooks/validate-policy.ps1 ci-pr mode must continue for BaseRef=master");
        }

        if (!violations.isEmpty()) {
            fail("release PR commits must receive the same non-master branch trailer checks as develop PRs:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void branchPolicyShouldUseReleaseTrailerCutoverForDevelopToMasterHistory() throws Exception {
        String shellPolicy = Files.readString(Path.of(".githooks/validate-policy.sh"));
        String powershellPolicy = Files.readString(Path.of(".githooks/validate-policy.ps1"));
        List<String> violations = new ArrayList<>();

        if (!shellPolicy.contains("RELEASE_TRAILER_CUTOVER_BASE=")) {
            violations.add(".githooks/validate-policy.sh does not define the release trailer cutover baseline");
        }
        if (!shellPolicy.contains("effective_base_for_ci_pr")) {
            violations.add(".githooks/validate-policy.sh does not route ci-pr ranges through an effective base helper");
        }
        if (!shellPolicy.contains("git merge-base --is-ancestor \"$RELEASE_TRAILER_CUTOVER_BASE\" \"$head_sha\"")) {
            violations.add(".githooks/validate-policy.sh does not verify the cutover baseline is reachable from the PR head");
        }
        if (!powershellPolicy.contains("$script:ReleaseTrailerCutoverBase")) {
            violations.add(".githooks/validate-policy.ps1 does not define the release trailer cutover baseline");
        }
        if (!powershellPolicy.contains("Get-EffectiveBaseForCiPr")) {
            violations.add(".githooks/validate-policy.ps1 does not route ci-pr ranges through an effective base helper");
        }
        if (!powershellPolicy.contains("merge-base\", \"--is-ancestor\", $script:ReleaseTrailerCutoverBase, $HeadSha")) {
            violations.add(".githooks/validate-policy.ps1 does not verify the cutover baseline is reachable from the PR head");
        }

        if (!violations.isEmpty()) {
            fail("develop -> master release PR policy must skip only pre-cutover historical commits while validating new work:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void hookDispatcherShouldProbePwshBeforeSkippingPowerShellFallback() throws Exception {
        String runPolicy = Files.readString(Path.of(".githooks/run-policy"));
        List<String> violations = new ArrayList<>();

        if (!runPolicy.contains("for candidate in pwsh powershell.exe")) {
            violations.add(".githooks/run-policy does not iterate over pwsh and powershell.exe candidates");
        }
        if (!runPolicy.contains("-Command \"exit 0\"")) {
            violations.add(".githooks/run-policy does not probe whether the PowerShell candidate can actually launch");
        }
        if (!runPolicy.contains("continue")) {
            violations.add(".githooks/run-policy does not continue to powershell.exe when pwsh is present but unusable");
        }

        if (!violations.isEmpty()) {
            fail("Windows hook dispatch must not stop at a broken pwsh shim before trying powershell.exe:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void repositoryShouldForceLfForHookAndWorkflowScripts() throws Exception {
        String attributes = Files.exists(Path.of(".gitattributes"))
                ? normalizeLineEndings(Files.readString(Path.of(".gitattributes")))
                : "";
        List<String> violations = new ArrayList<>();

        if (!attributes.contains(".githooks/* text eol=lf")) {
            violations.add(".gitattributes does not force LF for .githooks/*");
        }
        if (!attributes.contains("*.sh text eol=lf")) {
            violations.add(".gitattributes does not force LF for shell scripts");
        }
        if (!attributes.contains(".github/workflows/*.yml text eol=lf")) {
            violations.add(".gitattributes does not force LF for GitHub workflow YAML");
        }
        if (!attributes.contains(".mvn/maven.config text eol=lf")) {
            violations.add(".gitattributes does not force LF for .mvn/maven.config");
        }

        if (!violations.isEmpty()) {
            fail("local hooks and CI scripts must not break under core.autocrlf=true:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void protectedShellAndWorkflowFilesShouldNotContainCarriageReturns() throws Exception {
        List<String> violations = new ArrayList<>();
        List<Path> protectedFiles = new ArrayList<>();
        protectedFiles.add(Path.of(".github/workflows/ci.yml"));
        protectedFiles.add(Path.of(".github/workflows/release.yml"));
        protectedFiles.add(Path.of(".mvn/maven.config"));
        protectedFiles.add(Path.of("src/packaging/assemble-macos-app.sh"));
        try (Stream<Path> hooks = Files.walk(Path.of(".githooks"))) {
            hooks.filter(Files::isRegularFile).forEach(protectedFiles::add);
        }

        for (Path path : protectedFiles) {
            byte[] bytes = Files.readAllBytes(path);
            for (byte b : bytes) {
                if (b == '\r') {
                    violations.add(path.toString().replace('\\', '/'));
                    break;
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("protected shell/workflow files must be LF-only in the working tree:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void branchPolicyShouldRejectRootTracePayloadsToo() throws Exception {
        String shellPolicy = Files.readString(Path.of(".githooks/validate-policy.sh"));
        List<String> violations = new ArrayList<>();

        if (!shellPolicy.contains("aux_state*.jsonl|physics*.csv|*/aux_state*.jsonl|*/physics*.csv")) {
            violations.add(".githooks/validate-policy.sh trace payload size case must match root and nested trace files");
        }

        if (!violations.isEmpty()) {
            fail("branch policy must reject uncompressed trace payloads at trace-directory roots and below:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void branchPolicyShouldRejectRomLikeFilesAnywhere() throws Exception {
        String shellPolicy = Files.readString(Path.of(".githooks/validate-policy.sh"));
        String powershellPolicy = Files.readString(Path.of(".githooks/validate-policy.ps1"));
        List<String> gitignoreLines = Files.readAllLines(Path.of(".gitignore"));
        List<String> violations = new ArrayList<>();

        for (String extension : List.of(".gen", ".smd", ".bin", ".sms", ".gg", ".32x")) {
            if (!shellPolicy.contains(extension)) {
                violations.add(".githooks/validate-policy.sh does not deny " + extension + " files");
            }
            if (!powershellPolicy.contains(extension)) {
                violations.add(".githooks/validate-policy.ps1 does not deny " + extension + " files");
            }
            String ignorePattern = "*" + extension;
            if (!gitignoreLines.contains(ignorePattern)) {
                violations.add(".gitignore does not ignore " + ignorePattern + " in nested directories");
            }
        }
        if (!shellPolicy.contains("is_rom_like_path")) {
            violations.add(".githooks/validate-policy.sh does not define a ROM-like path predicate");
        }
        if (!powershellPolicy.contains("Test-RomLikeTrackedPath")) {
            violations.add(".githooks/validate-policy.ps1 does not define a ROM-like path predicate");
        }
        if (!shellPolicy.contains("ROM_LIKE_DENYLIST_EXTENSIONS")) {
            violations.add(".githooks/validate-policy.sh does not name the ROM-like denylist");
        }
        if (!powershellPolicy.contains("RomLikeDenylistExtensions")) {
            violations.add(".githooks/validate-policy.ps1 does not name the ROM-like denylist");
        }

        if (!violations.isEmpty()) {
            fail("branch policy must reject ROM-like binary files in any tracked directory:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void pomShouldDeclareUtf8BuildEncodings() throws Exception {
        Document pom = parsePom("pom.xml");
        List<String> violations = new ArrayList<>();

        if (!"UTF-8".equals(property(pom, "project.build.sourceEncoding"))) {
            violations.add("pom.xml must set project.build.sourceEncoding=UTF-8");
        }
        if (!"UTF-8".equals(property(pom, "project.reporting.outputEncoding"))) {
            violations.add("pom.xml must set project.reporting.outputEncoding=UTF-8");
        }

        if (!violations.isEmpty()) {
            fail("Maven builds should not depend on platform-default encoding:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void generatedRomDerivedReferenceFixturesShouldNotBeTracked() throws Exception {
        Set<String> trackedResources = trackedFiles("src/test/resources");
        List<String> violations = new ArrayList<>();

        for (String path : trackedResources) {
            if (path.startsWith("src/test/resources/audio-reference/")
                    || path.startsWith("src/test/resources/visual-reference/")
                    || (path.startsWith("src/test/resources/EHZ") && (path.endsWith(".kos") || path.endsWith(".raw")))) {
                violations.add(path);
            }
        }

        if (!violations.isEmpty()) {
            fail("Generated ROM-derived reference fixtures must stay local and untracked:\n  "
                    + String.join("\n  ", violations));
        }
    }

    @Test
    void romGatedTestsShouldUseResolvedRomPathsAndAssumeReferenceFixtures() throws Exception {
        String file = "src/test/java/com/openggf/game/sonic3k/TestSonic3kLifeIconAddresses.java";
        String source = Files.readString(Path.of(file));
        List<String> violations = new ArrayList<>();

        if (source.contains("rom.open(\"Sonic and Knuckles & Sonic 3 (W) [!].gen\")")) {
            violations.add(file + " opens the default S3K ROM filename instead of the @RequiresRom resolved path");
        }
        if (!source.contains("RomTestUtils.ensureSonic3kRomAvailable()")) {
            violations.add(file + " does not use RomTestUtils.ensureSonic3kRomAvailable()");
        }
        if (!source.contains("assumeTrue(Files.exists(TAILS_LIFE_ICON_BIN)")) {
            violations.add(file + " does not skip cleanly when the disassembly fixture is absent");
        }

        if (!violations.isEmpty()) {
            fail("@RequiresRom tests should honor configured ROM paths and optional local reference fixtures:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void lightningSparkPatternsShouldUseDedicatedVirtualPatternRange() throws Exception {
        String file = "src/main/java/com/openggf/game/sonic3k/objects/LightningSparkObjectInstance.java";
        String source = Files.readString(Path.of(file));
        List<String> violations = new ArrayList<>();

        if (source.contains("SPARK_PATTERN_BASE = 0x20100")) {
            violations.add(file + " allocates spark tiles inside PatternAtlasRange.OBJECTS");
        }
        if (!source.contains("PatternAtlasRange.TRANSIENT_EFFECTS.base()")) {
            violations.add(file + " should allocate spark tiles from PatternAtlasRange.TRANSIENT_EFFECTS");
        }

        if (!violations.isEmpty()) {
            fail("Lightning shield spark patterns must not collide with shared object art allocation:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void releaseTestsShouldNotHideKnownFailingScenariosBehindDisabledAnnotations() throws Exception {
        List<String> violations = new ArrayList<>();
        Set<String> allowedDisabled = Set.of(
                "src/test/java/com/openggf/game/rewind/TestRewindTorture.java",
                "src/test/java/com/openggf/tests/trace/DebugS1Ghz1RingParity.java");
        try (Stream<Path> paths = Files.walk(Path.of("src/test/java"))) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            if (source.contains("@Disabled")) {
                                String normalized = path.toString().replace('\\', '/');
                                if (!normalized.equals("src/test/java/com/openggf/tests/TestBuildToolingGuard.java")
                                        && !allowedDisabled.contains(normalized)) {
                                    violations.add(normalized);
                                }
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        if (!violations.isEmpty()) {
            fail("known-failing release tests must be fixed, converted to explicit accepted-debt docs, or moved out of the release suite:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void traceReplayBootstrapContractsShouldBeDocumentedAndNotLegacy() throws Exception {
        String bootstrap = Files.readString(Path.of("src/main/java/com/openggf/trace/TraceReplayBootstrap.java"));
        String discrepancies = Files.readString(Path.of("docs/KNOWN_DISCREPANCIES.md"));
        String roadmap = Files.readString(Path.of("docs/RELEASE_READINESS_ROADMAP.md"));
        List<String> violations = new ArrayList<>();

        long legacyTracePredicates = Pattern.compile("\\bboolean\\s+isLegacy\\w*Trace\\s*\\(")
                .matcher(bootstrap)
                .results()
                .count();
        if (legacyTracePredicates != 0) {
            violations.add("TraceReplayBootstrap must not expose legacy trace identity predicates");
        }
        if (bootstrap.contains("ALLOW_LEGACY") || discrepancies.contains("Legacy S3K AIZ Intro Trace Replay Bootstrap")
                || roadmap.contains("Accepted Phase 1 release debt: legacy S3K AIZ intro trace bootstrap")) {
            violations.add("legacy S3K AIZ trace bootstrap debt should be removed, not documented as accepted");
        }
        if (!bootstrap.contains("hasPreLevelIntroPrefix()")) {
            violations.add("TraceReplayBootstrap should use generic pre-level-prefix fixture metadata");
        }
        if (!discrepancies.contains("Pre-Level Intro Prefix Trace Bootstrap Contract")) {
            violations.add("docs/KNOWN_DISCREPANCIES.md does not document the pre-level prefix bootstrap contract");
        }
        if (!discrepancies.contains("S2 Tornado Ride-Start Trace Bootstrap Contract")) {
            violations.add("docs/KNOWN_DISCREPANCIES.md does not document the S2 Tornado ride-start bootstrap contract");
        }
        if (!discrepancies.contains("S2 CNZ Slot-Machine Trace Bootstrap Contract")) {
            violations.add("docs/KNOWN_DISCREPANCIES.md does not document the S2 CNZ slot-machine trace bootstrap contract");
        }
        if (!discrepancies.contains("S3K Sidekick Seed-Frame Trace Bootstrap Debt")) {
            violations.add("docs/KNOWN_DISCREPANCIES.md does not document the S3K sidekick seed-frame trace bootstrap debt");
        }
        if (!discrepancies.contains("S3K Complete-Run Segment Start-Position Bootstrap Debt")) {
            violations.add("docs/KNOWN_DISCREPANCIES.md does not document the S3K complete-run start-position bootstrap debt");
        }
        if (!roadmap.contains("Release-blocking pre-level intro trace bootstrap")) {
            violations.add("docs/RELEASE_READINESS_ROADMAP.md does not classify the pre-level intro bootstrap contract");
        }
        if (!roadmap.contains("S3K complete-run segment metadata start-position")) {
            violations.add("docs/RELEASE_READINESS_ROADMAP.md does not classify the S3K complete-run start-position bootstrap as bounded debt");
        }

        if (!violations.isEmpty()) {
            fail("trace replay exceptions must be explicitly documented and bounded before release:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void s3kStaticSessionStateDebtShouldNotGrow() throws Exception {
        Set<String> signals = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/openggf/game/sonic3k"))) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            signals.addAll(s3kStaticSessionStateSignals(
                                    path.toString().replace('\\', '/'),
                                    Files.readString(path)));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        if (!signals.equals(new TreeSet<>(REVIEWED_S3K_STATIC_SESSION_STATE))) {
            Set<String> added = new TreeSet<>(signals);
            added.removeAll(REVIEWED_S3K_STATIC_SESSION_STATE);
            Set<String> removed = new TreeSet<>(REVIEWED_S3K_STATIC_SESSION_STATE);
            removed.removeAll(signals);
            fail("S3K static session state changed; migrate new active-object/phase bridges to runtime-owned state "
                    + "or document why the release debt list changed"
                    + "\n  added:\n  " + String.join("\n  ", added)
                    + "\n  removed:\n  " + String.join("\n  ", removed));
        }
    }

    @Test
    void regeneratedS3kAizFullRunReplayIsReleaseBlocking() throws Exception {
        String file = "src/test/java/com/openggf/tests/trace/s3k/TestS3kAizTraceReplay.java";
        String source = Files.readString(Path.of(file));
        List<String> violations = new ArrayList<>();

        if (!source.contains("public void replayMatchesTrace() throws Exception")) {
            violations.add(file + " does not override the inherited full replay parity test");
        }
        if (source.contains("@Disabled(")) {
            violations.add(file + " still disables the regenerated full replay");
        }
        if (source.contains("ALLOW_LEGACY_S3K_AIZ_DIAGNOSTIC_HEURISTIC_PROPERTY")) {
            violations.add(file + " still enables the legacy diagnostic AIZ heuristic");
        }
        if (!source.contains("super.replayMatchesTrace();")) {
            violations.add(file + " override should delegate to the base release-blocking implementation");
        }

        if (!violations.isEmpty()) {
            fail("regenerated S3K AIZ full-run replay must count as release parity coverage:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void traceReplayBootstrapPolicySignalsStayBounded() throws Exception {
        Set<String> signals = new TreeSet<>();
        for (String relative : List.of(
                "src/main/java/com/openggf/trace/TraceReplayBootstrap.java",
                "src/main/java/com/openggf/trace/replay/TraceReplaySessionBootstrap.java")) {
            signals.addAll(traceBootstrapPolicySignals(relative, Files.readString(Path.of(relative))));
        }
        Set<String> signalKeys = new TreeSet<>();
        for (String signal : signals) {
            signalKeys.add(tracePolicySignalKey(signal));
        }

        if (!signalKeys.equals(new TreeSet<>(ACCEPTED_TRACE_BOOTSTRAP_POLICY_SIGNALS))) {
            Set<String> added = new TreeSet<>(signalKeys);
            added.removeAll(ACCEPTED_TRACE_BOOTSTRAP_POLICY_SIGNALS);
            Set<String> removed = new TreeSet<>(ACCEPTED_TRACE_BOOTSTRAP_POLICY_SIGNALS);
            removed.removeAll(signalKeys);
            fail("trace replay bootstrap policy signals changed; document and justify any new "
                    + "zone/profile/checkpoint/frame-shape carve-out before release"
                    + "\n  added:\n  " + String.join("\n  ", added)
                    + "\n  removed:\n  " + String.join("\n  ", removed));
        }
    }

    @Test
    void traceReplayBootstrapMustNotHydrateEngineStateFromTraceRows() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String relative : List.of(
                "src/main/java/com/openggf/trace/TraceReplayBootstrap.java",
                "src/main/java/com/openggf/trace/replay/TraceReplaySessionBootstrap.java")) {
            violations.addAll(traceRowHydrationSignals(relative, Files.readString(Path.of(relative))));
        }

        if (!violations.isEmpty()) {
            fail("trace replay bootstrap must not copy trace-row player state back into engine state:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void s2TornadoReplayBootstrapKeepsMetadataCandidateSeparateFromLiveObjectAuthority() throws Exception {
        String sessionBootstrap = Files.readString(Path.of(
                "src/main/java/com/openggf/trace/replay/TraceReplaySessionBootstrap.java"));

        if (sessionBootstrap.contains("usesS2TornadoRideStartForTraceReplay(")) {
            fail("TraceReplaySessionBootstrap must use the metadata-candidate helper name and then "
                    + "narrow through the live ObjB2 Tornado instance before applying Tornado ride-start state");
        }
        if (!sessionBootstrap.contains("isS2TornadoRideStartMetadataCandidate(")) {
            fail("TraceReplaySessionBootstrap should make the metadata/live-object split explicit with "
                    + "isS2TornadoRideStartMetadataCandidate(...)");
        }
    }

    @Test
    void slotMachineReplayBootstrapUsesGenericRuntimeFeatureCapability() throws Exception {
        String bootstrap = Files.readString(Path.of("src/main/java/com/openggf/trace/TraceReplayBootstrap.java"));

        if (bootstrap.contains("hasPerFrameCnzSlotMachineState(")) {
            fail("TraceReplayBootstrap should consume generic slot-machine feature capability metadata, "
                    + "not a CNZ-named fixture predicate.");
        }
        if (!bootstrap.contains("hasPerFrameSlotMachineState(")) {
            fail("TraceReplayBootstrap should use TraceMetadata.hasPerFrameSlotMachineState() "
                    + "for the native slot-machine title-card prelude.");
        }
    }

    @Test
    void s3kSidekickSeedReplayBootstrapUsesExplicitFixtureCapability() throws Exception {
        String bootstrap = Files.readString(Path.of("src/main/java/com/openggf/trace/TraceReplayBootstrap.java"));

        if (!bootstrap.contains("hasSidekickSeedFramePrelude()")) {
            fail("TraceReplayBootstrap should use explicit sidekick seed-frame fixture capability metadata.");
        }
        if (bootstrap.contains("firstFramePrimaryMovementAdvanced(")) {
            fail("TraceReplayBootstrap must not infer S3K sidekick seed-frame prelude from "
                    + "first-frame player movement shape.");
        }
    }

    @Test
    void sampleScannerDetectsTraceBootstrapPolicySignalsButIgnoresComments() {
        List<String> signals = traceBootstrapPolicySignals("sample/TraceReplayBootstrap.java", """
                class TraceReplayBootstrap {
                    /* metadata.zoneId() in docs should not count. */
                    void ok() {
                        // metadata.traceProfile() should not count.
                    }
                    void bad(TraceMetadata metadata, TraceFrame current) {
                        if (metadata.zoneId() == 0 && current.frame() < 4) {
                            run();
                        }
                    }
                }
                """);

        assertEquals(List.of(
                "sample/TraceReplayBootstrap.java:7 - if (metadata.zoneId() == 0 && current.frame() < 4) {"),
                signals);
    }

    @Test
    void sampleScannerDetectsTraceRowHydrationButIgnoresComparisonAndComments() {
        List<String> signals = traceRowHydrationSignals("sample/TraceReplaySessionBootstrap.java", """
                class TraceReplaySessionBootstrap {
                    /* player.setXSpeed(current.xSpeed()) in docs should not count. */
                    void compare(TraceFrame current) {
                        ReplayPrimaryState.fromTraceFrame(current, "trace-vblank");
                    }
                    void bad(TraceFrame current, AbstractPlayableSprite player) {
                        player.setXSpeed(current.xSpeed());
                    }
                }
                """);

        assertEquals(List.of(
                "sample/TraceReplaySessionBootstrap.java:7 - player.setXSpeed(current.xSpeed());"),
                signals);
    }

    private static Document parsePom(String file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(Files.newBufferedReader(Path.of(file))));
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static Set<String> trackedFiles(String pathspec) throws Exception {
        Process process = new ProcessBuilder("git", "ls-files", pathspec)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            fail("git ls-files failed for " + pathspec + ":\n" + output);
        }
        Set<String> paths = new TreeSet<>();
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                paths.add(line.replace('\\', '/'));
            }
        }
        return paths;
    }

    private static String property(Document pom, String name) {
        NodeList nodes = pom.getElementsByTagName(name);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent().trim();
    }

    private static boolean plistValueEquals(String plist, String key, String expectedValue) {
        return Pattern.compile("<key>\\s*" + Pattern.quote(key)
                        + "\\s*</key>\\s*<string>\\s*" + Pattern.quote(expectedValue) + "\\s*</string>",
                Pattern.DOTALL).matcher(plist).find();
    }

    private static boolean surefirePluginUsesSharedArgLine(Document pom) {
        NodeList argLines = pom.getElementsByTagName("argLine");
        for (int i = 0; i < argLines.getLength(); i++) {
            if ("${surefire.argLine}".equals(argLines.item(i).getTextContent().trim())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> traceBootstrapPolicySignals(String relative, String source) {
        String stripped = stripComments(source);
        List<String> signals = new ArrayList<>();
        String[] lines = stripped.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            for (Pattern pattern : TRACE_BOOTSTRAP_POLICY_SIGNALS) {
                if (pattern.matcher(line).find()) {
                    signals.add(relative + ":" + (i + 1) + " - " + line.replaceAll("\\s+", " "));
                    break;
                }
            }
        }
        return signals;
    }

    private static String tracePolicySignalKey(String signal) {
        return signal.replaceFirst(":[0-9]+ - ", " - ");
    }

    private static List<String> traceRowHydrationSignals(String relative, String source) {
        String stripped = stripComments(source);
        List<String> signals = new ArrayList<>();
        String[] lines = stripped.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            if (TRACE_ROW_PLAYER_SETTER_HYDRATION.matcher(line).find()) {
                signals.add(relative + ":" + (i + 1) + " - " + line.replaceAll("\\s+", " "));
            }
        }
        return signals;
    }

    private static List<String> s3kStaticSessionStateSignals(String relative, String source) {
        String normalizedRelative = relative.replace('\\', '/');
        if (normalizedRelative.contains("/constants/")
                || normalizedRelative.endsWith("Sonic3kLevelSelectManager.java")
                || normalizedRelative.endsWith("Sonic3kTitleScreenManager.java")) {
            return List.of();
        }
        String stripped = stripComments(source);
        List<String> signals = new ArrayList<>();
        String[] lines = stripped.split("\\R", -1);
        for (String rawLine : lines) {
            String line = rawLine.strip();
            String lower = line.toLowerCase();
            if (line.isEmpty()
                    || !lower.contains("private static")
                    || lower.contains(" final ")
                    || lower.contains(" class ")
                    || line.contains("(")) {
                continue;
            }
            signals.add(normalizedRelative + " - " + line.replaceAll("\\s+", " "));
        }
        return signals;
    }

    private static String stripComments(String source) {
        StringBuilder stripped = new StringBuilder(source.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
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
                    i++;
                    inBlockComment = false;
                } else {
                    stripped.append(current == '\n' || current == '\r' ? current : ' ');
                }
                continue;
            }
            if (current == '/' && next == '/') {
                stripped.append("  ");
                i++;
                inLineComment = true;
                continue;
            }
            if (current == '/' && next == '*') {
                stripped.append("  ");
                i++;
                inBlockComment = true;
                continue;
            }
            stripped.append(current);
        }
        return stripped.toString();
    }

    private static Element profileById(Document pom, String id) {
        NodeList profiles = pom.getElementsByTagName("profile");
        for (int i = 0; i < profiles.getLength(); i++) {
            Element profile = (Element) profiles.item(i);
            if (id.equals(directChildText(profile, "id"))) {
                return profile;
            }
        }
        return null;
    }

    private static List<String> textValues(Element root, String tagName) {
        NodeList nodes = root.getElementsByTagName(tagName);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            values.add(nodes.item(i).getTextContent().trim());
        }
        return values;
    }

    private static String directChildText(Element root, String tagName) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && tagName.equals(element.getTagName())) {
                return element.getTextContent().trim();
            }
        }
        return null;
    }
}
