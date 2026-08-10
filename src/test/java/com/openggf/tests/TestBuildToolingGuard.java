package com.openggf.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TestBuildToolingGuard {

    private static final Path POLICY_SCRIPT = Path.of(".githooks/validate-policy.sh").toAbsolutePath();
    private static final Path POWERSHELL_POLICY_SCRIPT =
            Path.of(".githooks/validate-policy.ps1").toAbsolutePath();
    private static final Path MACHINE_LOCAL_PATH_GRANDFATHER =
            Path.of(".githooks/machine-local-path-grandfather.sha256").toAbsolutePath();
    private static final Path POST_CHECKOUT_HOOK = Path.of(".githooks/post-checkout").toAbsolutePath();
    private static final Path PROJECT_GITIGNORE = Path.of(".gitignore").toAbsolutePath();
    private static final String ALL_ZERO_OID = "0000000000000000000000000000000000000000";
    private static final String RESOURCE_POLICY_CUTOVER = "ccdd33edf4f9cd4a7937791f1d4c2f37cbeeb5e0";
    private static final String FRONTIER_GRANDFATHER_BASELINE = "53de63da2";
    private static final String FRONTIER_LOG_PATH = "docs/status/trace-frontier-log.md";

    /** {@code -DforkCount=...} — the flag Maven ignores here. */
    private static final Pattern STALE_FORK_COUNT_FLAG =
            Pattern.compile("-D\"?forkCount\"?\\s*=");

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
    private static final List<Pattern> FIRST_ROW_REPLAY_SCHEDULING_SIGNALS = List.of(
            Pattern.compile("\\btrace\\s*\\.\\s*getFrame\\s*\\(\\s*0\\s*\\)\\s*\\.\\s*"
                    + "(?:x|y|xSub|ySub|xSpeed|ySpeed|gSpeed|animationId|mappingFrame|sidekick)\\s*\\("),
            Pattern.compile("\\b(?:firstFrame|firstRow|seedFrame)\\s*\\.\\s*"
                    + "(?:x|y|xSub|ySub|xSpeed|ySpeed|gSpeed|animationId|mappingFrame|sidekick)\\s*\\("),
            Pattern.compile("\\btrace\\s*\\.\\s*(?:oscillationStateForFrame|vOscillateForFrame)"
                    + "\\s*\\(\\s*0\\s*\\)"));
    private static final Set<String> ACCEPTED_TRACE_BOOTSTRAP_POLICY_SIGNALS = Set.of(
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - if (!meta.hasPerFrameSlotMachineState()) {",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - && \"level_gated_reset_aware\".equals(metadata.traceProfile())",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - if (current.frame() < firstLevelFrame) {",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - if (current.frame() == firstLevelFrame) {",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - if (previous != null || current.frame() != 0) {",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - int gameplayStartFrame = findCheckpointFrame(trace, \"gameplay_start\");",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - return gameplayStartFrame >= 0 && current.frame() <= gameplayStartFrame;",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - || !\"complete_run\".equals(metadata.traceProfile())",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - private static int findCheckpointFrame(TraceData trace, String checkpointName) {",
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - && checkpointName.equals(checkpoint.name())) {",
            // s3k_bonus_stage profile discriminator gates the bonus-stage entry
            // bootstrap seam (spec docs/architecture/designs/2026-07-18-multi-stage-trace-runs-design.md,
            // engine addition #7); data-driven trace_profile gate, not a
            // zone/route/frame carve-out; comparison-only -- seeds only the
            // bootstrap "load save state" set (frame-0 rings, mirroring ROM
            // Saved_ring_count restore).
            "src/main/java/com/openggf/trace/replay/TraceReplaySessionBootstrap.java - if (!\"s3k_bonus_stage\".equals(meta.traceProfile())) {",
            // s3k_bonus_stage discriminator in the pre-frame-0 ground-snap gate
            // (green-campaign round 1): bonus-stage segments must NOT receive
            // the generic fixture ground-snap terrain probe -- the ROM enters
            // bonus stages with Special_bonus_entry_flag set and skips the
            // zone air/animation branches (sonic3k.asm:8117-8118), so snapping
            // at bootstrap forced Status_InAir one tick early and desynced
            // frame 0. Data-driven trace_profile gate, not a zone/route/frame
            // carve-out; comparison-only (removes a fixture-side mutation).
            "src/main/java/com/openggf/trace/TraceReplayBootstrap.java - && \"s3k_bonus_stage\".equals(metadata.traceProfile());");
    private static final Set<String> REVIEWED_S3K_STATIC_SESSION_STATE = Set.of(
            // Immutable ROM-derived terrain bytes only; no readiness or
            // timing-produced runtime payload is stored in this cache.
            "src/main/java/com/openggf/game/sonic3k/events/S3kSeamlessMutationExecutor.java - private static volatile AizFireTerrainData cachedAizFireTerrain;",
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
            // Both cross-act latches are captured by Aiz2BossEndSequenceStaticAdapter.
            "src/main/java/com/openggf/game/sonic3k/objects/Aiz2BossEndSequenceState.java - private static volatile boolean buttonBeforeBridgeDispatch;",
            "src/main/java/com/openggf/game/sonic3k/objects/Aiz2BossEndSequenceState.java - private static volatile int tailsControlReleaseDelay = -1;",
            // This cross-act latch is explicitly captured by Aiz2BossEndSequenceStaticAdapter.
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
            "src/main/java/com/openggf/game/sonic3k/objects/AizPlaneIntroInstance.java - private static AizPlaneIntroInstance activeIntroInstance;",
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

    /**
     * Every Surefire {@code <forkCount>} in the POM must read the same
     * {@code ${surefire.forkCount}} property, and the name of that property is
     * the only lever that changes fork count on the command line.
     *
     * <p>This exists because {@code -DforkCount=1} silently does nothing:
     * {@code pom.xml} binds {@code <forkCount>} to {@code ${surefire.forkCount}}
     * (default 4, set to 1 only by the {@code ci} profile), so a user-supplied
     * {@code forkCount} property is never consulted and the run stays on four
     * forks. Trace measurements were taken under that misapprehension and had to
     * be redone; the correction is recorded in
     * {@code docs/status/trace-frontier-log.md}. The failure mode is the same
     * one the fixture alignment guard targets — a knob that reads as set but is
     * not — so this pins the property name and forbids prescriptive
     * documentation from teaching the flag that does nothing.
     */
    @Test
    void surefireForkCountShouldBeOverridableOnlyBySurefireForkCountProperty() throws Exception {
        String file = "pom.xml";
        Document pom = parsePom(file);
        List<String> violations = new ArrayList<>();

        NodeList forkCounts = pom.getElementsByTagName("forkCount");
        if (forkCounts.getLength() == 0) {
            violations.add(file + " defines no Surefire forkCount at all");
        }
        for (int i = 0; i < forkCounts.getLength(); i++) {
            String value = forkCounts.item(i).getTextContent().trim();
            if (!"${surefire.forkCount}".equals(value)) {
                violations.add(file + " binds a Surefire forkCount to '" + value
                        + "'; every one must read ${surefire.forkCount} so that a single"
                        + " documented property controls fork count everywhere");
            }
        }
        if (property(pom, "surefire.forkCount") == null) {
            violations.add(file + " does not define a default surefire.forkCount property");
        }

        // A doc that tells a reader to pass -DforkCount is teaching a no-op.
        // The frontier log is an append-only historical record of what was
        // actually run (including the runs that were wrong), so it is read as
        // evidence rather than instruction and is not rewritten here.
        List<Path> prescriptive = new ArrayList<>();
        for (String root : List.of("docs/agent-workflow", "docs/guide", ".agents", ".claude")) {
            Path base = Path.of(root);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(base)) {
                tree.filter(Files::isRegularFile)
                        .filter(candidate -> candidate.toString().endsWith(".md"))
                        // .claude/worktrees/ holds whole checkouts of this repository
                        // created by agent tooling. Their docs are copies of some other
                        // commit's docs, not this tree's instructions, so scanning them
                        // reports violations nobody here can fix (and that a later
                        // checkout resurrects). Only this repository's own docs are
                        // prescriptive.
                        .filter(candidate -> !candidate.toString()
                                .replace('\\', '/').contains("/worktrees/"))
                        .forEach(prescriptive::add);
            }
        }
        for (Path doc : List.of(Path.of("CLAUDE.md"), Path.of("AGENTS.md"),
                Path.of("AGENTS_S3K.md"))) {
            if (Files.isRegularFile(doc)) {
                prescriptive.add(doc);
            }
        }
        for (Path doc : prescriptive) {
            String text = Files.readString(doc, StandardCharsets.UTF_8);
            if (STALE_FORK_COUNT_FLAG.matcher(text).find()) {
                violations.add(doc.toString().replace('\\', '/')
                        + " prescribes -DforkCount=, which Maven ignores here; use"
                        + " -Dsurefire.forkCount= instead");
            }
        }

        if (!violations.isEmpty()) {
            fail("Surefire fork configuration does not match what the docs can honestly"
                    + " promise:\n  " + String.join("\n  ", new TreeSet<>(violations)));
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
        if (!workflow.contains("fetch-depth: 0")) {
            violations.add(".github/workflows/release.yml push validation does not fetch full cutover history");
        }
        if (workflow.contains("\"refs/remotes/origin/${{ github.ref_name }}\"")) {
            violations.add(".github/workflows/release.yml still supplies a peer-influenced pushed remote ref");
        }

        if (!violations.isEmpty()) {
            fail("release PRs into master must not bypass branch policy validation:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void modApiDestinationValidationShouldCoverEveryPullRequestAndPushTestPath() throws Exception {
        String ci = normalizeLineEndings(Files.readString(Path.of(".github/workflows/ci.yml")));
        String release = normalizeLineEndings(Files.readString(Path.of(".github/workflows/release.yml")));
        List<String> violations = new ArrayList<>();

        if (!ci.contains("pull_request:\n    branches:\n      - next\n      - develop")) {
            violations.add(".github/workflows/ci.yml must validate pull requests targeting next and develop");
        }
        // Pushes are policy-validated on every branch; the lightweight all-branch
        // push trigger still reaches next and develop.
        if (!ci.contains("push:\n    branches:\n      - '**'")) {
            violations.add(".github/workflows/ci.yml must validate pushes on every branch");
        }
        if (!release.contains("pull_request:\n    branches: [master]")) {
            violations.add(".github/workflows/release.yml must retain pull-request ownership for master only");
        }
        if (!release.contains("push:\n    branches: [master]")) {
            violations.add(".github/workflows/release.yml must retain push ownership for master only");
        }

        assertDestinationAwareMavenStep(ci, ".github/workflows/ci.yml", "Run tests (pull request)",
                "github.event_name == 'pull_request'", "github.base_ref", violations);
        assertDestinationAwareMavenStep(ci, ".github/workflows/ci.yml", "Run tests (push)",
                "github.event_name == 'push'", "github.ref_name", violations);
        assertMavenStepOmitsDestination(ci, ".github/workflows/ci.yml", "Run tests (manual or scheduled)",
                violations);

        for (String step : List.of("Run tests", "Run trace replay policy tests")) {
            assertDestinationAwareMavenStep(release, ".github/workflows/release.yml", step + " (pull request)",
                    "github.event_name == 'pull_request'", "github.base_ref", violations);
            assertDestinationAwareMavenStep(release, ".github/workflows/release.yml", step + " (push)",
                    "github.event_name == 'push'", "github.ref_name", violations);
            assertMavenStepOmitsDestination(release, ".github/workflows/release.yml",
                    step + " (manual)", violations);
        }
        assertEveryEventGatedMavenTestHasDestination(ci, ".github/workflows/ci.yml", violations);
        assertEveryEventGatedMavenTestHasDestination(release, ".github/workflows/release.yml", violations);

        if (!violations.isEmpty()) {
            fail("Mod API destination validation must be explicit on every PR/push broad-test path:\n  "
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
    void candidateCiShouldProtectPullRequestsAndPushes() throws Exception {
        // 2026-07-02: the full Maven suite on direct develop pushes was
        // deliberately removed by f18d4d9be ("fix: stop develop push CI").
        // The lightweight all-branch push policy is a separate backstop; this
        // guard still covers the PR path and scheduled develop trace replay.
        String ci = Files.readString(Path.of(".github/workflows/ci.yml"));
        String shellPolicy = Files.readString(Path.of(".githooks/validate-policy.sh"));
        String powershellPolicy = Files.readString(Path.of(".githooks/validate-policy.ps1"));
        List<String> violations = new ArrayList<>();

        if (!ci.contains("github.event_name == 'pull_request'")) {
            violations.add(".github/workflows/ci.yml policy job must keep pull-request branch policy validation");
        }
        if (!ci.contains(".githooks/validate-policy.sh ci-push")) {
            violations.add(".github/workflows/ci.yml policy job must validate direct pushes to next and develop");
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
            fail("candidate CI must protect pull requests, pushes, and the scheduled trace replay:\n  "
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

        if (!shellPolicy.contains("if [ \"$base_ref\" != \"next\" ] && [ \"$base_ref\" != \"develop\" ] && [ \"$base_ref\" != \"master\" ]; then")) {
            violations.add(".githooks/validate-policy.sh ci-pr mode must continue for next, develop, and master");
        }
        if (!powershellPolicy.contains("if ($BaseRef -cne \"next\" -and $BaseRef -cne \"develop\" -and $BaseRef -cne \"master\") {")) {
            violations.add(".githooks/validate-policy.ps1 ci-pr mode must continue for next, develop, and master");
        }

        if (!violations.isEmpty()) {
            fail("release PR commits must receive the same non-master branch trailer checks as next/develop PRs:\n  "
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
    void resourcePolicyImplementationsShouldKeepConstantsAndControlFlowInParity() throws Exception {
        String shellPolicy = Files.readString(Path.of(".githooks/validate-policy.sh"));
        String powershellPolicy = Files.readString(Path.of(".githooks/validate-policy.ps1"));
        List<String> violations = new ArrayList<>();

        Map<String, String> expectedConstants = Map.ofEntries(
                Map.entry("GITHUB_FILE_SIZE_LIMIT_BYTES", "100000000"),
                Map.entry("TRACE_COMPRESSION_THRESHOLD_BYTES", "1048576"),
                Map.entry("RELEASE_TRAILER_CUTOVER_BASE", "677447024a08db9e25f3461588d661c23ba26848"),
                Map.entry("RESOURCE_POLICY_CUTOVER", RESOURCE_POLICY_CUTOVER),
                Map.entry("EMPTY_TREE_OID", "4b825dc642cb6eb9a060e54bf8d69288fbee4904"),
                Map.entry("ALL_ZERO_OID", ALL_ZERO_OID),
                Map.entry("POSIX_HOME_ROOT", "/home"),
                Map.entry("VAR_HOME_ROOT", "/var/home"),
                Map.entry("MACOS_HOME_ROOT", "/Users"),
                Map.entry("WINDOWS_USERS_ROOT", "[A-Za-z]:[\\\\/]+[Uu][Ss][Ee][Rr][Ss]"));
        Map<String, String> powershellNames = Map.ofEntries(
                Map.entry("GITHUB_FILE_SIZE_LIMIT_BYTES", "GithubFileSizeLimitBytes"),
                Map.entry("TRACE_COMPRESSION_THRESHOLD_BYTES", "TraceCompressionThresholdBytes"),
                Map.entry("RELEASE_TRAILER_CUTOVER_BASE", "ReleaseTrailerCutoverBase"),
                Map.entry("RESOURCE_POLICY_CUTOVER", "ResourcePolicyCutover"),
                Map.entry("EMPTY_TREE_OID", "EmptyTreeOid"),
                Map.entry("ALL_ZERO_OID", "AllZeroOid"),
                Map.entry("POSIX_HOME_ROOT", "PosixHomeRoot"),
                Map.entry("VAR_HOME_ROOT", "VarHomeRoot"),
                Map.entry("MACOS_HOME_ROOT", "MacosHomeRoot"),
                Map.entry("WINDOWS_USERS_ROOT", "WindowsUsersRoot"));
        expectedConstants.forEach((shellName, expectedValue) -> {
            String shellValue = policyAssignment(shellPolicy, shellName);
            String powershellValue = policyAssignment(powershellPolicy, "script:" + powershellNames.get(shellName));
            if (!expectedValue.equals(shellValue)) {
                violations.add(".githooks/validate-policy.sh assigns " + shellName + "=" + shellValue
                        + " instead of " + expectedValue);
            }
            if (!expectedValue.equals(powershellValue)) {
                violations.add(".githooks/validate-policy.ps1 assigns " + powershellNames.get(shellName)
                        + "=" + powershellValue + " instead of " + expectedValue);
            }
        });

        String shellStagedCandidates = scriptFunction(shellPolicy, "staged_candidates() {");
        String powershellStagedCandidates = scriptFunction(powershellPolicy, "function Get-StagedCandidates()");
        String shellCommitCandidates = scriptFunction(shellPolicy, "commit_candidates() {");
        String powershellCommitCandidates = scriptFunction(powershellPolicy, "function Get-CommitCandidates(");
        for (String commandArgument : List.of("--no-renames", "--diff-filter=AMT")) {
            if (!shellStagedCandidates.contains(commandArgument)
                    || !powershellStagedCandidates.contains(commandArgument)) {
                violations.add("staged candidate implementations do not both use " + commandArgument);
            }
            if (!shellCommitCandidates.contains(commandArgument)
                    || !powershellCommitCandidates.contains(commandArgument)) {
                violations.add("commit candidate implementations do not both use " + commandArgument);
            }
        }

        String shellCommitMsg = scriptFunction(shellPolicy, "validate_commit_msg_hook() {");
        String powershellCommitMsg = scriptFunction(powershellPolicy, "function Validate-CommitMsgHook(");
        int shellStagedValidation = shellCommitMsg.indexOf("validate_staged_content");
        int shellMergeReturn = shellCommitMsg.indexOf("is_merge_in_progress");
        if (shellStagedValidation < 0 || shellMergeReturn < 0 || shellStagedValidation > shellMergeReturn) {
            violations.add(".githooks/validate-policy.sh commit-msg validates staged content after its merge return");
        }
        int powershellStagedValidation = powershellCommitMsg.indexOf("Validate-StagedContent");
        int powershellMergeReturn = powershellCommitMsg.indexOf("Test-MergeInProgress");
        if (powershellStagedValidation < 0
                || powershellMergeReturn < 0
                || powershellStagedValidation > powershellMergeReturn) {
            violations.add(".githooks/validate-policy.ps1 commit-msg validates staged content after its merge return");
        }

        String shellCiRange = scriptFunction(shellPolicy, "validate_ci_commit_range() {");
        String powershellCiRange = scriptFunction(powershellPolicy, "function Validate-CiCommitRange(");
        if (!shellCiRange.contains("validate_content_commit_list \"$commits\" \"$head_sha\"")
                || !powershellCiRange.contains("Validate-ContentCommitList $commits $HeadSha")) {
            violations.add("CI commit-range implementations do not validate all commit content and the delivered tip");
        }
        if (!shellCiRange.contains("if [ \"$#\" -gt 2 ]; then")
                || !powershellCiRange.contains("if ($parentFields.Count -gt 2)")) {
            violations.add("CI commit-range implementations do not both limit trailer checks to non-merge commits");
        }

        if (!violations.isEmpty()) {
            fail("shell and PowerShell must share policy values, Git candidate commands, and validation order:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void powershellResourcePolicyShouldUseFixedCutoverAndCaseSensitiveBranchIdentity() throws Exception {
        String powershellPolicy = Files.readString(POWERSHELL_POLICY_SCRIPT);
        String ciPush = scriptFunction(powershellPolicy, "function Validate-CiPush(");
        String ciNewRef = scriptFunction(powershellPolicy, "function Validate-CiNewRef(");
        List<String> violations = new ArrayList<>();

        if (!ciPush.contains("Validate-CiNewRef $AfterSha")
                || !ciNewRef.contains("$script:ResourcePolicyCutover")
                || !ciNewRef.contains("\"merge-base\"")
                || !ciNewRef.contains("\"--is-ancestor\"")) {
            violations.add("Validate-CiPush does not gate new refs on the fixed resource-policy cutover");
        }
        if (powershellPolicy.contains("function Resolve-PushedRemoteRef(")
                || powershellPolicy.contains("function Get-CiNewRefCommits(")) {
            violations.add("PowerShell still derives new-ref history from mutable peer refs");
        }
        if (!ciPush.contains("$RefName -ceq \"develop\"") || !ciPush.contains("$RefName -ceq \"master\"")) {
            violations.add("Validate-CiPush routes branch names with case-insensitive comparisons");
        }
        if (!violations.isEmpty()) {
            fail("PowerShell CI must use the fixed cutover and preserve case-sensitive Git branch identity:\n  "
                    + String.join("\n  ", violations));
        }
    }

    @Test
    void powershellResourcePolicyShouldMatchFailClosedShellDiagnostics() throws Exception {
        String shellPolicy = Files.readString(POLICY_SCRIPT);
        String powershellPolicy = Files.readString(POWERSHELL_POLICY_SCRIPT);
        List<String> violations = new ArrayList<>();

        List<String> shellDiagnostics = List.of(
                "\\`$path\\` is a symlink whose committed target blob could not be read.",
                "delivered tip tree $tip contains a malformed object id for $path.",
                "delivered tip tree $tip contains a truncated object id for $path.");
        List<String> powershellDiagnostics = List.of(
                "``$path`` is a symlink whose committed target blob could not be read.",
                "delivered tip tree $Tip contains a malformed object id for $path.",
                "delivered tip tree $Tip contains a truncated object id for $path.");
        for (int i = 0; i < shellDiagnostics.size(); i++) {
            if (!shellPolicy.contains(shellDiagnostics.get(i))) {
                violations.add(".githooks/validate-policy.sh is missing expected diagnostic "
                        + shellDiagnostics.get(i));
            }
            if (!powershellPolicy.contains(powershellDiagnostics.get(i))) {
                violations.add(".githooks/validate-policy.ps1 is missing matching diagnostic "
                        + powershellDiagnostics.get(i));
            }
        }

        if (!violations.isEmpty()) {
            fail("fail-closed PowerShell diagnostics must communicate the same reason as shell:\n  "
                    + String.join("\n  ", violations));
        }
    }

    @Test
    void resourcePolicyHookDispatchersShouldBeTrackedExecutable() throws Exception {
        String runPolicy = Files.readString(Path.of(".githooks/run-policy"));
        List<String> violations = new ArrayList<>();

        for (String hook : List.of("pre-commit", "commit-msg", "pre-push")) {
            String path = ".githooks/" + hook;
            String indexEntry = gitOutput(Path.of("."), "ls-files", "--stage", "--", path);
            if (!indexEntry.startsWith("100755 ")) {
                violations.add(path + " must be tracked executable");
            }
            String dispatcher = Files.readString(Path.of(path));
            if (!dispatcher.contains("\"$HOOK_DIR/run-policy\" " + hook)) {
                violations.add(path + " must dispatch " + hook + " through .githooks/run-policy");
            }
        }
        if (!runPolicy.contains("exec \"$HOOK_DIR/validate-policy.sh\" \"$@\"")) {
            violations.add(".githooks/run-policy must preserve the shell fallback and its arguments");
        }
        if (!runPolicy.contains("\"$@\" <&0")) {
            violations.add(".githooks/run-policy must preserve Git hook standard input for PowerShell pre-push");
        }

        if (!violations.isEmpty()) {
            fail("resource policy hook entry points must be executable portable dispatchers:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void allBranchPushPolicyShouldRemainLightweight() throws Exception {
        String workflow = normalizeLineEndings(Files.readString(Path.of(".github/workflows/ci.yml")));
        List<String> violations = new ArrayList<>();

        String pushTrigger = yamlIndentedBlock(workflow, "  push:", 2);
        if (!pushTrigger.contains("branches:\n") || !pushTrigger.contains("- '**'")) {
            violations.add(".github/workflows/ci.yml push trigger is not explicitly limited to all branches");
        }
        if (pushTrigger.contains("tags:")) {
            violations.add(".github/workflows/ci.yml branch policy push trigger also declares tag reachability");
        }
        String policyJob = yamlIndentedBlock(workflow, "  policy:", 2);
        if (!policyJob.contains("if: github.event_name == 'pull_request' || github.event_name == 'push'")) {
            violations.add(".github/workflows/ci.yml policy job is not limited to PR and push events");
        }
        if (!policyJob.contains("fetch-depth: 0")) {
            violations.add(".github/workflows/ci.yml policy checkout does not fetch full cutover history");
        }
        if (!policyJob.contains("Validate pushed branch resource policy")) {
            violations.add(".github/workflows/ci.yml does not define a push-range policy step");
        }
        if (!policyJob.contains(".githooks/validate-policy.sh ci-push")) {
            violations.add(".github/workflows/ci.yml does not invoke ci-push policy");
        }
        if (policyJob.contains("\"refs/remotes/origin/${{ github.ref_name }}\"")
                || policyJob.contains("+refs/heads/*:refs/remotes/origin/*")) {
            violations.add(".github/workflows/ci.yml still lets fetched peer refs influence new-branch selection");
        }

        for (Map.Entry<String, String> job : yamlJobBlocks(workflow).entrySet()) {
            if (!job.getValue().contains("mvn ")) {
                continue;
            }
            String jobCondition = yamlJobCondition(job.getValue());
            if (!conditionExcludesPush(jobCondition)) {
                violations.add(".github/workflows/ci.yml Maven-bearing job " + job.getKey()
                        + " is reachable from a branch push");
            }
        }

        if (!violations.isEmpty()) {
            fail("all-branch pushes need a lightweight policy-only CI path with fetched remote context:\n  "
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
    void romGatedTestsShouldUseResolvedRomPathsWithoutDisassemblyFixtures() throws Exception {
        String file = "src/test/java/com/openggf/game/sonic3k/TestSonic3kLifeIconAddresses.java";
        String source = Files.readString(Path.of(file));
        List<String> violations = new ArrayList<>();

        if (source.contains("rom.open(\"s3k.gen\")")) {
            violations.add(file + " opens the default S3K ROM filename instead of the @RequiresRom resolved path");
        }
        if (!source.contains("RomTestUtils.ensureSonic3kRomAvailable()")) {
            violations.add(file + " does not use RomTestUtils.ensureSonic3kRomAvailable()");
        }
        if (source.contains("docs/skdisasm") || source.contains("TAILS_LIFE_ICON_BIN")) {
            violations.add(file + " depends on a local disassembly fixture");
        }

        if (!violations.isEmpty()) {
            fail("@RequiresRom tests should honor configured ROM paths without local disassembly fixtures:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void executableTestsMustNotReadLocalDisassemblyTrees() throws Exception {
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(Path.of("src/test/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = file.toString().replace('\\', '/');
                if (relative.endsWith("TestBuildToolingGuard.java")
                        || relative.endsWith("TestArchitecturalSourceGuard.java")) {
                    continue;
                }
                String source = Files.readString(file);
                if (source.matches("(?s).*Path\\.of\\([^;]{0,300}docs(?:[/\\\", ]+)(?:s1|s2|sk)disasm.*")
                        || source.matches("(?s).*new File\\([^;]{0,300}docs(?:[/\\\", ]+)(?:s1|s2|sk)disasm.*")
                        || source.contains("SKDISASM_PATH")
                        || source.contains("resolveS3kDisassembly")
                        || source.contains("resolveSaveMenuReferenceDir")) {
                    violations.add(relative);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Tests must use configured ROMs or committed test resources, never local disassembly trees: "
                        + violations);
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
    void traceReplayBootstrapContractsShouldBeDocumentedAndOutcomeFree() throws Exception {
        String bootstrap = Files.readString(Path.of("src/main/java/com/openggf/trace/TraceReplayBootstrap.java"));
        String discrepancies = Files.readString(Path.of("docs/status/known-discrepancies.md"));
        String roadmap = Files.readString(Path.of("docs/project/release-readiness-roadmap.md"));
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
        if (!bootstrap.contains("hasRecordedPreLevelPrefix(TraceData trace)")) {
            violations.add("TraceReplayBootstrap should classify recorded pre-level prefixes structurally");
        }
        if (bootstrap.contains("hasPreLevelIntroPrefix()")) {
            violations.add("TraceReplayBootstrap must not schedule replay from legacy pre-level-prefix metadata");
        }
        if (!discrepancies.contains("Pre-Level Intro Prefix Trace Bootstrap Contract")) {
            violations.add("docs/status/known-discrepancies.md does not document the pre-level prefix bootstrap contract");
        }
        if (!discrepancies.contains("S2 Tornado Ride-Start Trace Bootstrap Contract")) {
            violations.add("docs/status/known-discrepancies.md does not document the S2 Tornado ride-start bootstrap contract");
        }
        if (!discrepancies.contains("S2 CNZ Slot-Machine Trace Bootstrap Contract")) {
            violations.add("docs/status/known-discrepancies.md does not document the S2 CNZ slot-machine trace bootstrap contract");
        }
        for (String relative : List.of(
                "src/main/java/com/openggf/trace/TraceReplayBootstrap.java",
                "src/main/java/com/openggf/trace/replay/TraceReplaySessionBootstrap.java")) {
            String source = stripComments(Files.readString(Path.of(relative)));
            for (String retiredAccessor : List.of(
                    "hasSidekickSeedFramePrelude()",
                    "hasPreLevelIntroPrefix()",
                    "preTraceOscillationFrames()")) {
                if (source.contains(retiredAccessor)) {
                    violations.add(relative + " schedules replay from retired metadata "
                            + retiredAccessor);
                }
            }
            violations.addAll(firstRowReplaySchedulingSignals(relative, source));
            if (relative.endsWith("/TraceReplayBootstrap.java")) {
                violations.addAll(completeRunOutcomePhaseSchedulingSignals(relative, source));
            }
        }
        if (!discrepancies.contains("S3K Complete-Run Segment Start-Position Bootstrap Debt")) {
            violations.add("docs/status/known-discrepancies.md does not document the S3K complete-run start-position bootstrap debt");
        }
        if (!roadmap.contains("Release-blocking pre-level intro trace bootstrap")) {
            violations.add("docs/project/release-readiness-roadmap.md does not classify the pre-level intro bootstrap contract");
        }
        if (!roadmap.contains("S3K complete-run segment metadata start-position")) {
            violations.add("docs/project/release-readiness-roadmap.md does not classify the S3K complete-run start-position bootstrap as bounded debt");
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
    void s3kReplaySchedulingIgnoresLegacyPhaseControlMetadata() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String relative : List.of(
                "src/main/java/com/openggf/trace/TraceReplayBootstrap.java",
                "src/main/java/com/openggf/trace/replay/TraceReplaySessionBootstrap.java")) {
            String source = stripComments(Files.readString(Path.of(relative)));
            for (String accessor : List.of(
                    "hasSidekickSeedFramePrelude()",
                    "hasPreLevelIntroPrefix()",
                    "preTraceOscillationFrames()")) {
                if (source.contains(accessor)) {
                    violations.add(relative + " schedules S3K replay from " + accessor);
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("S3K replay scheduling must ignore legacy phase-control metadata:\n  "
                    + String.join("\n  ", violations));
        }
    }

    @Test
    void traceReplaySchedulingDoesNotInferPhaseFromFirstRowOutcomeValues() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String relative : List.of(
                "src/main/java/com/openggf/trace/TraceReplayBootstrap.java",
                "src/main/java/com/openggf/trace/replay/TraceReplaySessionBootstrap.java")) {
            String source = Files.readString(Path.of(relative));
            violations.addAll(firstRowReplaySchedulingSignals(relative, source));
            if (relative.endsWith("/TraceReplayBootstrap.java")) {
                violations.addAll(completeRunOutcomePhaseSchedulingSignals(relative, source));
            }
        }
        if (!violations.isEmpty()) {
            fail("trace replay scheduling must not infer execution phase from frame-zero "
                    + "player/sidekick/oscillator outcome values:\n  "
                    + String.join("\n  ", violations));
        }
    }

    @Test
    void s3kOscillatorPreludeDoesNotCallFrameZeroOutcomeHelpers() throws Exception {
        String source = stripComments(Files.readString(
                Path.of("src/main/java/com/openggf/trace/TraceReplayBootstrap.java")));
        int methodStart = source.indexOf(
                "public static int preTraceOscillationFramesForTraceReplay(");
        int methodEnd = source.indexOf(
                "public static int initialOscillationSuppressionFramesForTraceReplay(",
                methodStart);
        if (methodStart < 0 || methodEnd < 0) {
            fail("Could not locate the pre-trace oscillator scheduling method.");
        }

        String schedulingMethod = source.substring(methodStart, methodEnd);
        List<String> violations = List.of(
                        "trace.getFrame(0)",
                        "isS3kCompleteRunInitialHandoffRow(",
                        "isS3kCompleteRunVisibleVelocityHoldRow(",
                        "hasNativeInitialVelocity(",
                        ".stateEquals(",
                        ".xSpeed(",
                        ".ySpeed(",
                        ".gSpeed(")
                .stream()
                .filter(schedulingMethod::contains)
                .toList();

        if (!violations.isEmpty()) {
            fail("S3K oscillator prelude scheduling must not call frame-zero outcome helpers "
                    + "or inspect frame-zero player outcomes:\n  "
                    + String.join("\n  ", violations));
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

    @Test
    void sampleScannerDetectsFirstRowOutcomeSchedulingButIgnoresComments() {
        List<String> signals = firstRowReplaySchedulingSignals(
                "sample/TraceReplayBootstrap.java", """
                class TraceReplayBootstrap {
                    // TraceFrame firstFrame = trace.getFrame(0); firstFrame.xSpeed();
                    void bad(TraceData trace) {
                        TraceFrame firstFrame = trace.getFrame(0);
                        if (firstFrame.sidekick().mappingFrame() == 0) {
                            schedulePrelude();
                        }
                    }
                }
                """);

        assertEquals(List.of(
                "sample/TraceReplayBootstrap.java:5 - if (firstFrame.sidekick().mappingFrame() == 0) {"),
                signals);
    }

    @Test
    void preCommitRejectsProtectedRelativeDisassemblyLink(@TempDir Path temporaryDirectory) throws Exception {
        Path repository = newRepository(temporaryDirectory, "protected-relative-link");
        stageSymlink(repository, "docs/skdisasm", "../../shared/skdisasm", true);

        assertPolicyRejects(runPolicy(repository, "pre-commit"), "docs/skdisasm");
    }

    @Test
    void preCommitRejectsUnprotectedAbsolutePosixWindowsAndUncLinks(@TempDir Path temporaryDirectory) throws Exception {
        Path posixRepository = newRepository(temporaryDirectory, "absolute-posix-link");
        String posixPath = "/" + "opt" + "/shared-resource";
        stageSymlink(posixRepository, "docs/external-link", posixPath, false);
        assertPolicyRejects(runPolicy(posixRepository, "pre-commit"), "docs/external-link");

        Path posixHomeRepository = newRepository(temporaryDirectory, "absolute-posix-home-link");
        String posixHomePath = "/" + "home" + "/" + "policy-user" + "/workspace";
        stageSymlink(posixHomeRepository, "docs/home-link", posixHomePath, false);
        assertPolicyRejects(runPolicy(posixHomeRepository, "pre-commit"), "docs/home-link");

        Path windowsRepository = newRepository(temporaryDirectory, "absolute-windows-link");
        String windowsHomePath = "C:" + "\\" + "Users" + "\\" + "policy-user" + "\\workspace";
        stageSymlink(windowsRepository, "docs/windows-link", windowsHomePath, false);
        assertPolicyRejects(runPolicy(windowsRepository, "pre-commit"), "docs/windows-link");

        Path uncRepository = newRepository(temporaryDirectory, "absolute-unc-link");
        String uncPath = "\\" + "\\" + "server" + "\\" + "shared-resource";
        stageSymlink(uncRepository, "docs/unc-link", uncPath, false);
        assertPolicyRejects(runPolicy(uncRepository, "pre-commit"), "docs/unc-link");
    }

    @Test
    void preCommitAcceptsUnprotectedRelativeLink(@TempDir Path temporaryDirectory) throws Exception {
        Path repository = newRepository(temporaryDirectory, "unprotected-relative-link");
        stageSymlink(repository, "docs/portable-link", "../shared/portable-resource", false);

        assertPolicyAccepts(runPolicy(repository, "pre-commit"));
    }

    @Test
    void preCommitTreatsHomebrewTextAsOrdinaryText(@TempDir Path temporaryDirectory) throws Exception {
        Path repository = newRepository(temporaryDirectory, "homebrew-text");
        writeAndStage(repository, "docs/architecture/audits/homebrew.md", "/" + "homebrew" + "/example\n");

        assertPolicyAccepts(runPolicy(repository, "pre-commit"));
    }

    @Test
    void preCommitRejectsPosixAndWindowsUserHomePathsInAddedText(@TempDir Path temporaryDirectory) throws Exception {
        Path posixRepository = newRepository(temporaryDirectory, "posix-home-text");
        String posixHomePath = "/" + "home" + "/policy-user/workspace";
        writeAndStage(posixRepository, "docs/architecture/audits/posix.md", "local checkout: " + posixHomePath + "\n");
        assertPolicyRejects(runPolicy(posixRepository, "pre-commit"), "docs/architecture/audits/posix.md");

        Path windowsRepository = newRepository(temporaryDirectory, "windows-home-text");
        String windowsHomePath = "D:" + "\\" + "Users" + "\\policy-user\\workspace";
        writeAndStage(windowsRepository, "docs/architecture/audits/windows.md", "local checkout: " + windowsHomePath + "\n");
        assertPolicyRejects(runPolicy(windowsRepository, "pre-commit"), "docs/architecture/audits/windows.md");

        Path varHomeRepository = newRepository(temporaryDirectory, "var-home-text");
        String varHomePath = "/" + "var" + "/" + "home" + "/policy-user/workspace";
        writeAndStage(varHomeRepository, "docs/architecture/audits/var-home.md", "local checkout: " + varHomePath + "\n");
        assertPolicyRejects(runPolicy(varHomeRepository, "pre-commit"), "docs/architecture/audits/var-home.md");

        Path macHomeRepository = newRepository(temporaryDirectory, "mac-home-text");
        String macHomePath = "/" + "Users" + "/policy-user/workspace";
        writeAndStage(macHomeRepository, "docs/architecture/audits/mac.md", "local checkout: " + macHomePath + "\n");
        assertPolicyRejects(runPolicy(macHomeRepository, "pre-commit"), "docs/architecture/audits/mac.md");
    }

    @Test
    void shellAndPowerShellInspectOnlyIntroducedMachineLocalPathsInExistingText(
            @TempDir Path temporaryDirectory) throws Exception {
        String grandfatheredHome = "/" + "home" + "/historic-user/checkout";
        String introducedHome = "/" + "home" + "/new-user/checkout";
        String powershell = availablePowerShell();

        Path unchangedRepository = newRepository(temporaryDirectory, "grandfathered-existing-text");
        String existingPath = "docs/architecture/audits/history.md";
        writeAndStage(unchangedRepository, existingPath, "historic checkout: " + grandfatheredHome + "\n");
        commit(unchangedRepository, "historic audit");
        writeAndStage(unchangedRepository, existingPath,
                "historic checkout: " + grandfatheredHome + "\nportable follow-up\n");
        assertPolicyAccepts(runPolicy(unchangedRepository, "pre-commit"));
        if (powershell != null) {
            assertPolicyAccepts(runPowerShellPolicy(unchangedRepository, powershell, "pre-commit"));
        }

        Path modifiedRepository = newRepository(temporaryDirectory, "introduced-existing-text");
        writeAndStage(modifiedRepository, existingPath, "historic checkout: " + grandfatheredHome + "\n");
        commit(modifiedRepository, "historic audit");
        writeAndStage(modifiedRepository, existingPath,
                "historic checkout: " + grandfatheredHome + "\nnew checkout: " + introducedHome + "\n");
        assertPolicyRejects(runPolicy(modifiedRepository, "pre-commit"), existingPath);
        if (powershell != null) {
            assertPolicyRejects(runPowerShellPolicy(modifiedRepository, powershell, "pre-commit"), existingPath);
        }

        Path addedRepository = newRepository(temporaryDirectory, "introduced-new-text");
        String addedPath = "docs/architecture/audits/new.md";
        writeAndStage(addedRepository, addedPath, "new checkout: " + introducedHome + "\n");
        assertPolicyRejects(runPolicy(addedRepository, "pre-commit"), addedPath);
        if (powershell != null) {
            assertPolicyRejects(runPowerShellPolicy(addedRepository, powershell, "pre-commit"), addedPath);
        }
    }

    @Test
    void machineLocalPathGrandfatherExactlyMatchesVerifiedFrontierBaseline() throws Exception {
        byte[] baselineBytes = gitOutput(
                Path.of(".").toAbsolutePath(), "show",
                FRONTIER_GRANDFATHER_BASELINE + ":" + FRONTIER_LOG_PATH)
                .getBytes(StandardCharsets.UTF_8);
        Map<String, Integer> expected = new TreeMap<>();
        for (String line : new String(baselineBytes, StandardCharsets.UTF_8).split("\\R", -1)) {
            if (containsMachineLocalHome(line)) {
                expected.merge(sha256(line), 1, Integer::sum);
            }
        }

        Map<String, Integer> actual = new TreeMap<>();
        String expectedPrefix = "# baseline-prefix\t" + baselineBytes.length + "\t"
                + sha256(baselineBytes) + "\t" + FRONTIER_LOG_PATH;
        int prefixEntries = 0;
        for (String line : Files.readAllLines(MACHINE_LOCAL_PATH_GRANDFATHER)) {
            if (line.equals(expectedPrefix)) {
                prefixEntries++;
                continue;
            }
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\\t", -1);
            assertEquals(3, fields.length, "grandfather entries must be hash<TAB>count<TAB>path");
            assertEquals(FRONTIER_LOG_PATH, fields[2], "grandfather entries must remain path-scoped");
            assertTrue(fields[0].matches("[0-9a-f]{64}"), "grandfather hash must be lowercase SHA-256");
            assertTrue(Integer.parseInt(fields[1]) > 0, "grandfather occurrence count must be positive");
            assertEquals(null, actual.put(fields[0], Integer.parseInt(fields[1])),
                    "grandfather entries must not be duplicated");
        }

        assertEquals(1, prefixEntries, "grandfather manifest must pin one exact verified prefix");
        assertEquals(expected, actual, "grandfather manifest must have no missing or extra baseline entries");

        String baseline = new String(baselineBytes, StandardCharsets.UTF_8);
        for (Map.Entry<String, Integer> entry : expected.entrySet()) {
            if (entry.getValue() <= 1) {
                continue;
            }
            String repeatedLine = baseline.lines()
                    .filter(line -> {
                        try {
                            return sha256(line).equals(entry.getKey());
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .findFirst()
                    .orElseThrow();
            String replayed = baseline.replaceFirst(Pattern.quote(repeatedLine + "\n"), "")
                    + repeatedLine + "\n";
            assertFalse(sha256(replayed.getBytes(StandardCharsets.UTF_8)).equals(sha256(baselineBytes)),
                    "moving any multiplicity>1 occurrence must invalidate the prefix");
        }
    }

    @Test
    void shellAndPowerShellGrandfatherOnlyExactHistoricFrontierOccurrences(
            @TempDir Path temporaryDirectory) throws Exception {
        String baseline = gitOutput(
                Path.of(".").toAbsolutePath(), "show",
                FRONTIER_GRANDFATHER_BASELINE + ":" + FRONTIER_LOG_PATH);
        String historicLine = baseline.lines()
                .filter(TestBuildToolingGuard::containsMachineLocalHome)
                .findFirst()
                .orElseThrow();
        String powershell = availablePowerShell();

        Path exactRepository = newRepository(temporaryDirectory, "grandfather-exact");
        writeAndStage(exactRepository, FRONTIER_LOG_PATH, "portable baseline\n");
        commit(exactRepository, "neutralized frontier");
        writeAndStage(exactRepository, FRONTIER_LOG_PATH, baseline + "portable append\n");
        assertPolicyAccepts(runPolicy(exactRepository, "pre-commit"));
        if (powershell != null) {
            assertPolicyAccepts(runPowerShellPolicy(exactRepository, powershell, "pre-commit"));
        }

        Path alteredRepository = newRepository(temporaryDirectory, "grandfather-altered");
        writeAndStage(alteredRepository, FRONTIER_LOG_PATH, "portable baseline\n");
        commit(alteredRepository, "neutralized frontier");
        writeAndStage(alteredRepository, FRONTIER_LOG_PATH,
                baseline.replaceFirst(Pattern.quote(historicLine), historicLine + "-altered"));
        assertPolicyRejects(runPolicy(alteredRepository, "pre-commit"), FRONTIER_LOG_PATH);
        if (powershell != null) {
            assertPolicyRejects(runPowerShellPolicy(alteredRepository, powershell, "pre-commit"), FRONTIER_LOG_PATH);
        }

        Path replayRepository = newRepository(temporaryDirectory, "grandfather-replay");
        writeAndStage(replayRepository, FRONTIER_LOG_PATH, "portable baseline\n");
        commit(replayRepository, "neutralized frontier");
        writeAndStage(replayRepository, FRONTIER_LOG_PATH, baseline + historicLine + "\n");
        assertPolicyRejects(runPolicy(replayRepository, "pre-commit"), FRONTIER_LOG_PATH);
        if (powershell != null) {
            assertPolicyRejects(runPowerShellPolicy(replayRepository, powershell, "pre-commit"), FRONTIER_LOG_PATH);
        }

        Path deletedRepository = newRepository(temporaryDirectory, "grandfather-deletion");
        writeAndStage(deletedRepository, FRONTIER_LOG_PATH, baseline);
        commit(deletedRepository, "historic frontier");
        String deleted = baseline.replaceFirst(Pattern.quote(historicLine + "\n"), "");
        writeAndStage(deletedRepository, FRONTIER_LOG_PATH, deleted);
        assertPolicyRejects(runPolicy(deletedRepository, "pre-commit"), FRONTIER_LOG_PATH);
        if (powershell != null) {
            assertPolicyRejects(runPowerShellPolicy(deletedRepository, powershell, "pre-commit"), FRONTIER_LOG_PATH);
        }

        Path replayAfterDeletionRepository = newRepository(temporaryDirectory, "grandfather-replay-after-deletion");
        writeAndStage(replayAfterDeletionRepository, FRONTIER_LOG_PATH, baseline);
        commit(replayAfterDeletionRepository, "historic frontier");
        writeAndStage(replayAfterDeletionRepository, FRONTIER_LOG_PATH, deleted + historicLine + "\n");
        assertPolicyRejects(runPolicy(replayAfterDeletionRepository, "pre-commit"), FRONTIER_LOG_PATH);
        if (powershell != null) {
            assertPolicyRejects(runPowerShellPolicy(
                    replayAfterDeletionRepository, powershell, "pre-commit"), FRONTIER_LOG_PATH);
        }

        int firstBreak = baseline.indexOf('\n');
        int secondBreak = baseline.indexOf('\n', firstBreak + 1);
        String reordered = baseline.substring(firstBreak + 1, secondBreak + 1)
                + baseline.substring(0, firstBreak + 1)
                + baseline.substring(secondBreak + 1);
        Path reorderedRepository = newRepository(temporaryDirectory, "grandfather-reorder");
        writeAndStage(reorderedRepository, FRONTIER_LOG_PATH, baseline);
        commit(reorderedRepository, "historic frontier");
        writeAndStage(reorderedRepository, FRONTIER_LOG_PATH, reordered);
        assertPolicyRejects(runPolicy(reorderedRepository, "pre-commit"), FRONTIER_LOG_PATH);
        if (powershell != null) {
            assertPolicyRejects(runPowerShellPolicy(reorderedRepository, powershell, "pre-commit"), FRONTIER_LOG_PATH);
        }

        Path wrongPathRepository = newRepository(temporaryDirectory, "grandfather-wrong-path");
        String wrongPath = "docs/architecture/audits/copied-history.md";
        writeAndStage(wrongPathRepository, wrongPath, "portable baseline\n");
        commit(wrongPathRepository, "portable audit");
        writeAndStage(wrongPathRepository, wrongPath, historicLine + "\n");
        assertPolicyRejects(runPolicy(wrongPathRepository, "pre-commit"), wrongPath);
        if (powershell != null) {
            assertPolicyRejects(runPowerShellPolicy(wrongPathRepository, powershell, "pre-commit"), wrongPath);
        }

        Path newFileRepository = newRepository(temporaryDirectory, "grandfather-new-file");
        writeAndStage(newFileRepository, FRONTIER_LOG_PATH, historicLine + "\n");
        assertPolicyRejects(runPolicy(newFileRepository, "pre-commit"), FRONTIER_LOG_PATH);
        if (powershell != null) {
            assertPolicyRejects(runPowerShellPolicy(newFileRepository, powershell, "pre-commit"), FRONTIER_LOG_PATH);
        }
    }

    @Test
    void shellAndPowerShellRejectWindowsHomePathsWithPortableSeparatorForms(
            @TempDir Path temporaryDirectory) throws Exception {
        Map<String, String> forms = new LinkedHashMap<>();
        forms.put("forward", "C:" + "/" + "Users" + "/policy-user/workspace");
        forms.put("backward", "D:" + "\\" + "Users" + "\\policy-user\\workspace");
        forms.put("mixed", "E:" + "\\" + "Users" + "/policy-user\\workspace");
        forms.put("doubled", "F:" + "\\\\" + "Users" + "\\\\policy-user\\\\workspace");
        String powershell = availablePowerShell();

        for (Map.Entry<String, String> form : forms.entrySet()) {
            Path repository = newRepository(temporaryDirectory, "windows-home-" + form.getKey());
            String relativePath = "docs/architecture/audits/" + form.getKey() + ".md";
            writeAndStage(repository, relativePath, "local checkout: " + form.getValue() + "\n");

            assertPolicyRejects(runPolicy(repository, "pre-commit"), relativePath);
            if (powershell != null) {
                assertPolicyRejects(runPowerShellPolicy(repository, powershell, "pre-commit"), relativePath);
            }
        }
    }

    @Test
    void preCommitRejectsRootMergeAndHandoverScratchArtifacts(@TempDir Path temporaryDirectory) throws Exception {
        Path mergeRepository = newRepository(temporaryDirectory, "merge-status-scratch");
        writeAndStage(mergeRepository, "MERGE-STATUS-incident.md", "temporary status\n");
        assertPolicyRejects(runPolicy(mergeRepository, "pre-commit"), "MERGE-STATUS-incident.md");

        Path handoverRepository = newRepository(temporaryDirectory, "handover-scratch");
        writeAndStage(handoverRepository, "HANDOVER-incident.md", "temporary handover\n");
        assertPolicyRejects(runPolicy(handoverRepository, "pre-commit"), "HANDOVER-incident.md");
    }

    @Test
    void preCommitAcceptsClassifiedArchitectureAudit(@TempDir Path temporaryDirectory) throws Exception {
        Path repository = newRepository(temporaryDirectory, "classified-audit");
        writeAndStage(repository, "docs/architecture/audits/HANDOVER-incident.md", "retained engineering audit\n");

        assertPolicyAccepts(runPolicy(repository, "pre-commit"));
    }

    @Test
    void preCommitAcceptsNestedPathBelowHandoverNamedDirectory(@TempDir Path temporaryDirectory) throws Exception {
        Path repository = newRepository(temporaryDirectory, "nested-handover-guide");
        writeAndStage(repository, "HANDOVER-guides/summary.md", "retained contributor guide\n");

        assertPolicyAccepts(runPolicy(repository, "pre-commit"));
    }

    @Test
    void stagedAndPublishedTypeChangesCannotHideAbsoluteSymlinkHistory(@TempDir Path temporaryDirectory) throws Exception {
        Path remote = newBareRemote(temporaryDirectory, "type-change-remote");
        Path repository = newRepository(temporaryDirectory, "type-change-local");
        writeAndStage(repository, "docs/portable-resource", "regular base\n");
        commit(repository, "initial regular resource");
        addRemoteAndPush(repository, remote, "main");
        git(repository, "switch", "-c", "feature/type-change");

        Files.delete(repository.resolve("docs/portable-resource"));
        stageSymlink(repository, "docs/portable-resource", "/" + "opt" + "/machine-resource", false);
        assertPolicyRejects(runPolicy(repository, "pre-commit"), "docs/portable-resource");
        commit(repository, "replace regular resource with absolute link");

        Files.delete(repository.resolve("docs/portable-resource"));
        writeAndStage(repository, "docs/portable-resource", "regular restored\n");
        commit(repository, "restore regular resource");
        String localOid = gitOutput(repository, "rev-parse", "HEAD").trim();

        assertPolicyRejects(runPolicyWithInput(repository, "refs/heads/feature/type-change " + localOid
                + " refs/heads/feature/type-change " + ALL_ZERO_OID + "\n",
                "pre-push", "origin", remote.toString()), "docs/portable-resource");

        Path ciRepository = newCutoverRepository(
                temporaryDirectory, "type-change-ci", "feature/type-change-ci");
        writeAndStage(ciRepository, "docs/portable-resource", "regular base\n");
        commit(ciRepository, "add regular resource");
        Files.delete(ciRepository.resolve("docs/portable-resource"));
        stageSymlink(ciRepository, "docs/portable-resource", "/" + "opt" + "/machine-resource", false);
        commit(ciRepository, "replace regular resource with absolute link");
        Files.delete(ciRepository.resolve("docs/portable-resource"));
        writeAndStage(ciRepository, "docs/portable-resource", "regular restored\n");
        commit(ciRepository, "restore regular resource");
        String ciTip = gitOutput(ciRepository, "rev-parse", "HEAD").trim();
        assertPolicyRejects(runPolicy(
                ciRepository, "ci-push", ALL_ZERO_OID, ciTip, "feature/type-change-ci"),
                "docs/portable-resource");
    }

    @Test
    void everyDisassemblyPathIsIgnoredAsDirectoryAndSymlink(@TempDir Path temporaryDirectory) throws Exception {
        Path repository = newRepository(temporaryDirectory, "disassembly-ignore");
        installProjectIgnoreRules(repository);
        for (String disassembly : disassemblyPaths()) {
            Path directory = repository.resolve(disassembly);
            Files.createDirectories(directory);
            assertGitSucceeds(repository, "check-ignore", "-q", "--", disassembly);
            Files.delete(directory);

            Files.createSymbolicLink(directory, Path.of("../shared/" + directory.getFileName()));
            assertGitSucceeds(repository, "check-ignore", "-q", "--", disassembly);
            Files.delete(directory);
        }
    }

    @Test
    void mergeHooksRejectProtectedLinkStagedDuringConflictResolution(@TempDir Path temporaryDirectory) throws Exception {
        Path repository = newRepository(temporaryDirectory, "merge-resolution-link");
        prepareConflictedMerge(repository);
        stageSymlink(repository, "docs/skdisasm", "../../shared/skdisasm", true);
        writeAndStage(repository, "conflict.txt", "resolved\n");
        Path message = repository.resolve("merge-message.txt");
        Files.writeString(message, "Merge topic\n");

        assertPolicyRejects(runPolicy(repository, "pre-commit"), "docs/skdisasm");
        assertPolicyRejects(runPolicy(repository, "commit-msg", message.toString()), "docs/skdisasm");
    }

    @Test
    void prePushRejectsExistingBranchUpdateContainingBadMerge(@TempDir Path temporaryDirectory) throws Exception {
        Path remote = newBareRemote(temporaryDirectory, "existing-branch-remote");
        Path repository = newRepository(temporaryDirectory, "existing-branch-local");
        createInitialCommit(repository);
        addRemoteAndPush(repository, remote, "main");
        createBadMerge(repository);
        String localOid = gitOutput(repository, "rev-parse", "HEAD").trim();
        String remoteOid = gitOutput(repository, "rev-parse", "origin/main").trim();

        assertPolicyRejects(runPolicyWithInput(repository, "refs/heads/main " + localOid
                + " refs/heads/main " + remoteOid + "\n", "pre-push", "origin", remote.toString()), "docs/skdisasm");
    }

    @Test
    void existingCiRangeAcceptsCommitThatRemovesBaseViolation(@TempDir Path temporaryDirectory) throws Exception {
        Path repository = newRepository(temporaryDirectory, "existing-range-removal");
        createInitialCommit(repository);
        stageSymlink(repository, "docs/skdisasm", "../../shared/skdisasm", true);
        commit(repository, "base contains old generated link");
        String baseOid = gitOutput(repository, "rev-parse", "HEAD").trim();
        deleteAndStage(repository, "docs/skdisasm");
        commit(repository, "remove old generated link");
        String cleanTipOid = gitOutput(repository, "rev-parse", "HEAD").trim();

        assertPolicyAccepts(runPolicy(
                repository, "ci-push", baseOid, cleanTipOid, "feature/removal-only"));
    }

    @Test
    void prePushAndCiPushRejectNewBranchWithEarlierUniqueBadCommit(@TempDir Path temporaryDirectory) throws Exception {
        Path remote = newBareRemote(temporaryDirectory, "new-branch-remote");
        Path repository = newRepository(temporaryDirectory, "new-branch-local");
        createInitialCommit(repository);
        addRemoteAndPush(repository, remote, "main");
        git(repository, "switch", "-c", "feature/clean-tip");
        stageSymlink(repository, "docs/skdisasm", "../../shared/skdisasm", true);
        commit(repository, "bad link");
        deleteAndStage(repository, "docs/skdisasm");
        commit(repository, "remove generated link");
        String localOid = gitOutput(repository, "rev-parse", "HEAD").trim();

        assertPolicyRejects(runPolicyWithInput(repository, "refs/heads/feature/clean-tip " + localOid
                + " refs/heads/feature/clean-tip " + ALL_ZERO_OID + "\n", "pre-push", "origin", remote.toString()), "docs/skdisasm");

        Path ciRepository = newCutoverRepository(
                temporaryDirectory, "new-branch-ci", "feature/clean-tip-ci");
        stageSymlink(ciRepository, "docs/skdisasm", "../../shared/skdisasm", true);
        commit(ciRepository, "bad post-cutover link");
        deleteAndStage(ciRepository, "docs/skdisasm");
        commit(ciRepository, "remove post-cutover link");
        String ciTip = gitOutput(ciRepository, "rev-parse", "HEAD").trim();
        assertPolicyRejects(runPolicy(
                ciRepository, "ci-push", ALL_ZERO_OID, ciTip, "feature/clean-tip-ci"), "docs/skdisasm");
    }

    @Test
    void newBranchFromRemediatedRemoteHistoryDoesNotRescanPublishedBadCommit(@TempDir Path temporaryDirectory) throws Exception {
        Path remote = newBareRemote(temporaryDirectory, "remediated-remote");
        Path repository = newRepository(temporaryDirectory, "remediated-local");
        createInitialCommit(repository);
        stageSymlink(repository, "docs/skdisasm", "../../shared/skdisasm", true);
        commit(repository, "old bad link");
        deleteAndStage(repository, "docs/skdisasm");
        commit(repository, "remediate old link");
        addRemoteAndPush(repository, remote, "main");
        git(repository, "switch", "-c", "feature/clean-from-remediated", "origin/main");
        writeAndStage(repository, "docs/architecture/audits/new-work.md", "clean unpublished work\n");
        commit(repository, "clean feature work");
        String localOid = gitOutput(repository, "rev-parse", "HEAD").trim();

        assertPolicyAccepts(runPolicyWithInput(repository, "refs/heads/feature/clean-from-remediated " + localOid
                + " refs/heads/feature/clean-from-remediated " + ALL_ZERO_OID + "\n", "pre-push", "origin", remote.toString()));

        Path ciRepository = newCutoverRepository(
                temporaryDirectory, "remediated-ci", "feature/clean-from-cutover");
        writeAndStage(ciRepository, "docs/architecture/audits/new-work.md", "clean post-cutover work\n");
        commit(ciRepository, "clean post-cutover work");
        String ciTip = gitOutput(ciRepository, "rev-parse", "HEAD").trim();
        assertPolicyAccepts(runPolicy(
                ciRepository, "ci-push", ALL_ZERO_OID, ciTip, "feature/clean-from-cutover"));
    }

    @Test
    void ciNewBranchesRejectSharedBadHistoryRegardlessOfPeerRefs(@TempDir Path temporaryDirectory)
            throws Exception {
        Path repository = newCutoverRepository(
                temporaryDirectory, "peer-new-refs", "feature/peer-a");
        stageSymlink(repository, "docs/skdisasm", "../../shared/skdisasm", true);
        commit(repository, "shared bad post-cutover link");
        deleteAndStage(repository, "docs/skdisasm");
        commit(repository, "shared clean removal");
        String localOid = gitOutput(repository, "rev-parse", "HEAD").trim();
        git(repository, "update-ref", "refs/remotes/origin/feature/peer-a", localOid);
        git(repository, "update-ref", "refs/remotes/origin/feature/peer-b", localOid);

        assertPolicyRejects(runPolicy(
                repository, "ci-push", ALL_ZERO_OID, localOid, "feature/peer-a"), "docs/skdisasm");
        assertPolicyRejects(runPolicy(
                repository, "ci-push", ALL_ZERO_OID, localOid, "feature/peer-b"), "docs/skdisasm");
    }

    @Test
    void ciNewBranchFailsClosedWhenCutoverIsMissingOrUnreachable(@TempDir Path temporaryDirectory)
            throws Exception {
        Path missingRepository = newRepository(temporaryDirectory, "missing-cutover");
        createInitialCommit(missingRepository);
        String missingTip = gitOutput(missingRepository, "rev-parse", "HEAD").trim();
        ProcessResult missingResult = runPolicy(
                missingRepository, "ci-push", ALL_ZERO_OID, missingTip, "feature/missing-cutover");
        assertTrue(missingResult.exitCode() != 0,
                () -> "missing cutover object must reject new-ref CI:\n" + missingResult.output());
        assertTrue(missingResult.output().contains(RESOURCE_POLICY_CUTOVER),
                () -> "missing-cutover rejection must identify the fixed boundary:\n" + missingResult.output());

        Path unrelatedRepository = newRepository(temporaryDirectory, "unreachable-cutover");
        createInitialCommit(unrelatedRepository);
        String unrelatedTip = gitOutput(unrelatedRepository, "rev-parse", "HEAD").trim();
        git(unrelatedRepository, "fetch", "--no-tags",
                Path.of(".").toAbsolutePath().normalize().toString(), RESOURCE_POLICY_CUTOVER);
        ProcessResult unrelatedResult = runPolicy(
                unrelatedRepository, "ci-push", ALL_ZERO_OID, unrelatedTip, "feature/unreachable-cutover");
        assertTrue(unrelatedResult.exitCode() != 0,
                () -> "unreachable cutover must reject new-ref CI:\n" + unrelatedResult.output());
        assertTrue(unrelatedResult.output().contains("ancestor"),
                () -> "unreachable-cutover rejection must explain ancestry:\n" + unrelatedResult.output());
    }

    @Test
    void ciPushFailsClosedWhenTipTreeEnumerationIsMalformed(@TempDir Path temporaryDirectory) throws Exception {
        Path repository = newRepository(temporaryDirectory, "tip-tree-failure");
        createInitialCommit(repository);
        String baseOid = gitOutput(repository, "rev-parse", "HEAD").trim();
        writeAndStage(repository, "clean.txt", "clean tip\n");
        commit(repository, "clean tip");
        String localOid = gitOutput(repository, "rev-parse", "HEAD").trim();

        Path fakeBin = temporaryDirectory.resolve("tip-tree-fake-bin");
        Files.createDirectories(fakeBin);
        Path fakeGit = fakeBin.resolve("git");
        Files.writeString(fakeGit, """
                #!/bin/sh
                if [ "$1" = "ls-tree" ] && [ "$2" = "-r" ]; then
                    printf '100644 blob not-an-object-id\tclean.txt\n'
                    exit 0
                fi
                PATH="$ORIGINAL_PATH"
                export PATH
                exec git "$@"
                """);
        assertTrue(fakeGit.toFile().setExecutable(true), "test git wrapper must be executable");
        String originalPath = System.getenv("PATH");
        String path = fakeBin + System.getProperty("path.separator") + originalPath;

        ProcessResult result = runPolicy(repository,
                Map.of("PATH", path, "ORIGINAL_PATH", originalPath),
                "ci-push", baseOid, localOid, "feature/tree-failure");

        assertTrue(result.exitCode() != 0, () -> "malformed tree enumeration must reject the push:\n" + result.output());
        assertTrue(result.output().contains("delivered tip tree"),
                () -> "failure must identify malformed delivered tip tree data:\n" + result.output());
    }

    @Test
    void prePushAcceptsDeletedRef(@TempDir Path temporaryDirectory) throws Exception {
        Path remote = newBareRemote(temporaryDirectory, "deleted-ref-remote");
        Path repository = newRepository(temporaryDirectory, "deleted-ref-local");
        createInitialCommit(repository);
        addRemoteAndPush(repository, remote, "main");
        String remoteOid = gitOutput(repository, "rev-parse", "origin/main").trim();

        assertPolicyAccepts(runPolicyWithInput(repository, "(delete) " + ALL_ZERO_OID
                + " refs/heads/obsolete " + remoteOid + "\n", "pre-push", "origin", remote.toString()));
    }

    @Test
    void postCheckoutCreatesRelativeDisassemblyLinkToMainRepository(@TempDir Path temporaryDirectory) throws Exception {
        Path mainRepository = newRepository(temporaryDirectory, "checkout-main");
        createInitialCommit(mainRepository);
        Files.createDirectories(mainRepository.resolve("docs/skdisasm"));
        Files.writeString(mainRepository.resolve("docs/skdisasm/marker.txt"), "source\n");
        Path linkedWorktree = temporaryDirectory.resolve("checkout-linked-worktree");
        git(mainRepository, "worktree", "add", "-b", "feature/linked", linkedWorktree.toString());
        Files.createDirectories(linkedWorktree.resolve("docs"));

        ProcessResult result = run(linkedWorktree, List.of("bash", POST_CHECKOUT_HOOK.toString(), ALL_ZERO_OID, ALL_ZERO_OID, "1"), null);
        assertEquals(0, result.exitCode(), () -> "post-checkout failed:\n" + result.output());
        Path link = linkedWorktree.resolve("docs/skdisasm");
        assertTrue(Files.isSymbolicLink(link), "post-checkout must create the missing disassembly link");
        Path target = Files.readSymbolicLink(link);
        assertFalse(target.isAbsolute(), "worktree link target must not contain an absolute machine path: " + target);
        assertEquals(mainRepository.resolve("docs/skdisasm").toRealPath(), link.getParent().resolve(target).toRealPath());
    }

    @Test
    void postCheckoutMigratesLegacyAbsoluteLinkToExpectedMainResource(@TempDir Path temporaryDirectory)
            throws Exception {
        Path mainRepository = newRepository(temporaryDirectory, "legacy-link-main");
        createInitialCommit(mainRepository);
        Path source = mainRepository.resolve("docs/skdisasm");
        Files.createDirectories(source);
        Files.writeString(source.resolve("marker.txt"), "source\n");
        Path linkedWorktree = temporaryDirectory.resolve("legacy-link-worktree");
        git(mainRepository, "worktree", "add", "-b", "feature/legacy-link", linkedWorktree.toString());
        Path link = linkedWorktree.resolve("docs/skdisasm");
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, source.toAbsolutePath());

        ProcessResult result = run(linkedWorktree,
                List.of("bash", POST_CHECKOUT_HOOK.toString(), ALL_ZERO_OID, ALL_ZERO_OID, "1"), null);

        assertEquals(0, result.exitCode(), () -> "post-checkout failed:\n" + result.output());
        Path target = Files.readSymbolicLink(link);
        assertFalse(target.isAbsolute(), "legacy absolute link must be replaced with a relative target: " + target);
        assertEquals(source.toRealPath(), link.getParent().resolve(target).toRealPath());
    }

    @Test
    void postCheckoutPreservesUnknownAbsoluteSymlink(@TempDir Path temporaryDirectory) throws Exception {
        Path mainRepository = newRepository(temporaryDirectory, "unknown-link-main");
        createInitialCommit(mainRepository);
        Files.createDirectories(mainRepository.resolve("docs/skdisasm"));
        Path unknownSource = temporaryDirectory.resolve("user-owned-skdisasm");
        Files.createDirectories(unknownSource);
        Path linkedWorktree = temporaryDirectory.resolve("unknown-link-worktree");
        git(mainRepository, "worktree", "add", "-b", "feature/unknown-link", linkedWorktree.toString());
        Path link = linkedWorktree.resolve("docs/skdisasm");
        Files.createDirectories(link.getParent());
        Path originalTarget = unknownSource.toAbsolutePath();
        Files.createSymbolicLink(link, originalTarget);

        ProcessResult result = run(linkedWorktree,
                List.of("bash", POST_CHECKOUT_HOOK.toString(), ALL_ZERO_OID, ALL_ZERO_OID, "1"), null);

        assertEquals(0, result.exitCode(), () -> "post-checkout failed:\n" + result.output());
        assertEquals(originalTarget, Files.readSymbolicLink(link),
                "unknown user-authored symlink must remain untouched");
        assertEquals(unknownSource.toRealPath(), link.toRealPath());
    }

    @Test
    void postCheckoutCreatesRelativeDisassemblyLinkWhenDestinationParentIsMissing(@TempDir Path temporaryDirectory) throws Exception {
        Path mainRepository = newRepository(temporaryDirectory, "missing-parent-main");
        createInitialCommit(mainRepository);
        Files.createDirectories(mainRepository.resolve("docs/skdisasm"));
        Files.writeString(mainRepository.resolve("docs/skdisasm/marker.txt"), "source\n");
        Path linkedWorktree = temporaryDirectory.resolve("missing-parent-worktree");
        git(mainRepository, "worktree", "add", "-b", "feature/missing-parent", linkedWorktree.toString());
        assertFalse(Files.exists(linkedWorktree.resolve("docs")), "fixture must start without the destination parent");

        ProcessResult result = run(linkedWorktree, List.of("bash", POST_CHECKOUT_HOOK.toString(), ALL_ZERO_OID, ALL_ZERO_OID, "1"), null);
        assertEquals(0, result.exitCode(), () -> "post-checkout failed:\n" + result.output());
        Path link = linkedWorktree.resolve("docs/skdisasm");
        assertTrue(Files.isSymbolicLink(link), "post-checkout must create the missing-parent disassembly link");
        Path target = Files.readSymbolicLink(link);
        assertFalse(target.isAbsolute(), "missing-parent worktree link target must remain relative: " + target);
        assertEquals(mainRepository.resolve("docs/skdisasm").toRealPath(), link.getParent().resolve(target).toRealPath());
    }

    @Test
    void postCheckoutFailsWithoutRemovingDestinationForUnsupportedCommonGitDirectory(@TempDir Path temporaryDirectory) throws Exception {
        Path mainRepository = newRepository(temporaryDirectory, "unsupported-common-dir-main");
        createInitialCommit(mainRepository);
        Files.createDirectories(mainRepository.resolve("docs/skdisasm"));
        Path linkedWorktree = temporaryDirectory.resolve("unsupported-common-dir-worktree");
        git(mainRepository, "worktree", "add", "-b", "feature/unsupported-common-dir", linkedWorktree.toString());
        Path destination = linkedWorktree.resolve("docs/skdisasm");
        Files.createDirectories(destination);

        Path fakeBin = temporaryDirectory.resolve("fake-bin");
        Files.createDirectories(fakeBin);
        Path fakeGit = fakeBin.resolve("git");
        Files.writeString(fakeGit, """
                #!/bin/sh
                if [ \"$1\" = \"rev-parse\" ] && [ \"$2\" = \"--path-format=relative\" ] && [ \"$3\" = \"--git-common-dir\" ]; then
                    echo unsupported-common-dir
                    exit 0
                fi
                PATH=\"$ORIGINAL_PATH\"
                export PATH
                exec git \"$@\"
                """);
        assertTrue(fakeGit.toFile().setExecutable(true), "test git wrapper must be executable");
        String originalPath = System.getenv("PATH");
        String path = fakeBin + System.getProperty("path.separator") + originalPath;

        ProcessResult result = run(linkedWorktree,
                List.of("bash", POST_CHECKOUT_HOOK.toString(), ALL_ZERO_OID, ALL_ZERO_OID, "1"),
                null,
                Map.of("PATH", path, "ORIGINAL_PATH", originalPath));

        assertTrue(result.exitCode() != 0, () -> "unsupported common Git directory must fail:\n" + result.output());
        assertTrue(result.output().contains("Unsupported common Git directory"),
                () -> "failure must explain the unsupported common Git directory:\n" + result.output());
        assertTrue(Files.isDirectory(destination), "unsupported layout must not remove the empty destination directory");
        assertFalse(Files.isSymbolicLink(destination), "unsupported layout must not create a destination link");
    }

    private static Path newRepository(Path temporaryDirectory, String name) throws Exception {
        Path repository = temporaryDirectory.resolve(name);
        Files.createDirectories(repository);
        git(repository, "init", "-b", "main");
        git(repository, "config", "user.name", "Policy Test");
        git(repository, "config", "user.email", "policy-test@example.invalid");
        git(repository, "config", "gc.auto", "0");
        git(repository, "config", "maintenance.auto", "false");
        return repository;
    }

    private static Path newCutoverRepository(Path temporaryDirectory, String name, String branch) throws Exception {
        Path repository = temporaryDirectory.resolve(name);
        Path sourceRepository = Path.of(".").toAbsolutePath().normalize();
        ProcessResult clone = run(temporaryDirectory,
                List.of("git", "clone", "--shared", "--no-checkout",
                        sourceRepository.toString(), repository.toString()),
                null);
        assertEquals(0, clone.exitCode(), () -> "could not create shared cutover fixture:\n" + clone.output());
        git(repository, "config", "user.name", "Policy Test");
        git(repository, "config", "user.email", "policy-test@example.invalid");
        git(repository, "config", "gc.auto", "0");
        git(repository, "config", "maintenance.auto", "false");
        git(repository, "switch", "-c", branch, RESOURCE_POLICY_CUTOVER);
        return repository;
    }

    private static Path newBareRemote(Path temporaryDirectory, String name) throws Exception {
        Path remote = temporaryDirectory.resolve(name + ".git");
        ProcessResult result = run(temporaryDirectory, List.of("git", "init", "--bare", remote.toString()), null);
        assertEquals(0, result.exitCode(), () -> "could not create bare remote:\n" + result.output());
        return remote;
    }

    private static void createInitialCommit(Path repository) throws Exception {
        writeAndStage(repository, "README.md", "fixture\n");
        commit(repository, "initial fixture");
    }

    private static void installProjectIgnoreRules(Path repository) throws Exception {
        Files.copy(PROJECT_GITIGNORE, repository.resolve(".gitignore"));
    }

    private static void addRemoteAndPush(Path repository, Path remote, String branch) throws Exception {
        git(repository, "remote", "add", "origin", remote.toString());
        git(repository, "push", "-u", "origin", branch);
    }

    private static void prepareConflictedMerge(Path repository) throws Exception {
        writeAndStage(repository, "conflict.txt", "base\n");
        commit(repository, "base conflict file");
        git(repository, "switch", "-c", "topic");
        writeAndStage(repository, "conflict.txt", "topic\n");
        commit(repository, "topic conflict");
        git(repository, "switch", "main");
        writeAndStage(repository, "conflict.txt", "main\n");
        commit(repository, "main conflict");
        ProcessResult merge = run(repository, List.of("git", "merge", "topic"), null);
        assertTrue(merge.exitCode() != 0, "fixture must enter a conflicted merge");
        assertTrue(Files.exists(repository.resolve(".git/MERGE_HEAD")), "fixture must retain MERGE_HEAD");
    }

    private static void createBadMerge(Path repository) throws Exception {
        writeAndStage(repository, "conflict.txt", "base\n");
        commit(repository, "base merge file");
        git(repository, "switch", "-c", "topic");
        writeAndStage(repository, "conflict.txt", "topic\n");
        commit(repository, "topic merge change");
        git(repository, "switch", "main");
        writeAndStage(repository, "conflict.txt", "main\n");
        commit(repository, "main merge change");
        ProcessResult merge = run(repository, List.of("git", "merge", "topic"), null);
        assertTrue(merge.exitCode() != 0, "fixture must enter a conflicted merge");
        writeAndStage(repository, "conflict.txt", "resolved\n");
        stageSymlink(repository, "docs/skdisasm", "../../shared/skdisasm", true);
        commit(repository, "merge topic with generated link");
    }

    private static void writeAndStage(Path repository, String relativePath, String contents) throws Exception {
        Path file = repository.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
        git(repository, "add", "--", relativePath);
    }

    private static void stageSymlink(Path repository, String relativePath, String target, boolean force) throws Exception {
        Path link = repository.resolve(relativePath);
        Files.createDirectories(link.getParent());
        Files.deleteIfExists(link);
        Files.createSymbolicLink(link, Path.of(target));
        if (force) {
            git(repository, "add", "-f", "--", relativePath);
        } else {
            git(repository, "add", "--", relativePath);
        }
        String indexEntry = gitOutput(repository, "ls-files", "--stage", "--", relativePath);
        assertTrue(indexEntry.startsWith("120000 "), "staged symlink must retain mode 120000: " + indexEntry);
    }

    private static void deleteAndStage(Path repository, String relativePath) throws Exception {
        Files.delete(repository.resolve(relativePath));
        git(repository, "add", "-u", "--", relativePath);
    }

    private static void commit(Path repository, String subject) throws Exception {
        git(repository, "commit", "-m", subject);
    }

    private static ProcessResult runPolicy(Path repository, String mode, String... arguments) throws Exception {
        return runPolicy(repository, Map.of(), mode, arguments);
    }

    private static ProcessResult runPolicy(
            Path repository,
            Map<String, String> environment,
            String mode,
            String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("sh", POLICY_SCRIPT.toString(), mode));
        command.addAll(List.of(arguments));
        return run(repository, command, null, environment);
    }

    private static ProcessResult runPolicyWithInput(Path repository, String input, String mode, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("sh", POLICY_SCRIPT.toString(), mode));
        command.addAll(List.of(arguments));
        return run(repository, command, input);
    }

    private static String availablePowerShell() throws Exception {
        for (String candidate : List.of("pwsh", "powershell.exe")) {
            try {
                ProcessResult result = run(
                        Path.of("."),
                        List.of(candidate, "-NoLogo", "-NoProfile", "-Command", "exit 0"),
                        null);
                if (result.exitCode() == 0) {
                    return candidate;
                }
            } catch (java.io.IOException ignored) {
                // The static semantic assertions still run when PowerShell is
                // unavailable in the current build environment.
            }
        }
        return null;
    }

    private static ProcessResult runPowerShellPolicy(
            Path repository,
            String powershell,
            String mode,
            String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                powershell,
                "-NoLogo",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                POWERSHELL_POLICY_SCRIPT.toString(),
                mode));
        command.addAll(List.of(arguments));
        return run(repository, command, null);
    }

    private static ProcessResult run(Path repository, List<String> command, String input) throws Exception {
        return run(repository, command, input, Map.of());
    }

    private static ProcessResult run(Path repository, List<String> command, String input, Map<String, String> environment) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(repository.toFile())
                .redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        if (input != null) {
            process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        }
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private static void git(Path repository, String... arguments) throws Exception {
        assertGitSucceeds(repository, arguments);
    }

    private static void assertGitSucceeds(Path repository, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        ProcessResult result = run(repository, command, null);
        assertEquals(0, result.exitCode(), () -> String.join(" ", command) + " failed:\n" + result.output());
    }

    private static String gitOutput(Path repository, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        ProcessResult result = run(repository, command, null);
        assertEquals(0, result.exitCode(), () -> String.join(" ", command) + " failed:\n" + result.output());
        return result.output();
    }

    private static void assertPolicyRejects(ProcessResult result, String offendingPath) {
        assertTrue(result.exitCode() != 0, () -> "policy unexpectedly accepted " + offendingPath + ":\n" + result.output());
        assertTrue(result.output().contains(offendingPath), () -> "policy rejection must identify " + offendingPath + ":\n" + result.output());
    }

    private static void assertPolicyAccepts(ProcessResult result) {
        assertEquals(0, result.exitCode(), () -> "policy unexpectedly rejected valid input:\n" + result.output());
    }

    private static boolean containsMachineLocalHome(String line) {
        return Pattern.compile(
                "(?:/home|/var/home|/Users)/[^/$<\\s][^/\\s]*/|"
                        + "[A-Za-z]:[\\\\/]+[Uu][Ss][Ee][Rr][Ss][\\\\/]+"
                        + "[^\\\\/$<%\\s][^\\\\/\\s]*[\\\\/]")
                .matcher(line)
                .find();
    }

    private static String sha256(String text) throws Exception {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static List<String> disassemblyPaths() {
        return List.of("docs/s1disasm", "docs/s2disasm", "docs/kis2disasm", "docs/scddisasm", "docs/skdisasm");
    }

    private record ProcessResult(int exitCode, String output) {
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

    private static String policyAssignment(String source, String variableName) {
        Pattern assignment = Pattern.compile(
                "(?m)^\\$?" + Pattern.quote(variableName)
                        + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\r\\n]+))$");
        var matcher = assignment.matcher(source);
        if (!matcher.find()) {
            return null;
        }
        for (int group = 1; group <= 3; group++) {
            if (matcher.group(group) != null) {
                return matcher.group(group).trim();
            }
        }
        return null;
    }

    private static String scriptFunction(String source, String declaration) {
        int start = source.indexOf(declaration);
        if (start < 0) {
            fail("missing script function declaration: " + declaration);
        }
        int end = source.indexOf("\n}\n", start);
        if (end < 0) {
            fail("unterminated script function declaration: " + declaration);
        }
        return source.substring(start, end + 2);
    }

    private static String yamlIndentedBlock(String source, String declaration, int indentation) {
        int start = source.indexOf(declaration);
        if (start < 0) {
            return "";
        }
        int cursor = source.indexOf('\n', start);
        if (cursor < 0) {
            return source.substring(start);
        }
        cursor++;
        while (cursor < source.length()) {
            int lineEnd = source.indexOf('\n', cursor);
            if (lineEnd < 0) {
                lineEnd = source.length();
            }
            String line = source.substring(cursor, lineEnd);
            if (!line.isBlank()) {
                int leadingSpaces = 0;
                while (leadingSpaces < line.length() && line.charAt(leadingSpaces) == ' ') {
                    leadingSpaces++;
                }
                if (leadingSpaces <= indentation) {
                    return source.substring(start, cursor);
                }
            }
            cursor = lineEnd + 1;
        }
        return source.substring(start);
    }

    private static Map<String, String> yamlJobBlocks(String workflow) {
        int jobsStart = workflow.indexOf("\njobs:\n");
        if (jobsStart < 0) {
            return Map.of();
        }
        String jobs = workflow.substring(jobsStart + 1);
        var matcher = Pattern.compile("(?m)^  ([A-Za-z0-9_-]+):\\n").matcher(jobs);
        List<String> names = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
            starts.add(matcher.start());
        }
        Map<String, String> blocks = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : jobs.length();
            blocks.put(names.get(i), jobs.substring(starts.get(i), end));
        }
        return blocks;
    }

    private static String yamlJobCondition(String jobBlock) {
        var matcher = Pattern.compile("(?m)^    if:\\s*(.+)$").matcher(jobBlock);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static boolean conditionExcludesPush(String condition) {
        if (condition.contains("github.event_name != 'push'")) {
            return true;
        }
        var eventMatches = Pattern.compile("github\\.event_name == '([^']+)'").matcher(condition);
        boolean constrainsEvent = false;
        while (eventMatches.find()) {
            constrainsEvent = true;
            if ("push".equals(eventMatches.group(1))) {
                return false;
            }
        }
        return constrainsEvent;
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

    private static List<String> firstRowReplaySchedulingSignals(String relative, String source) {
        String stripped = stripComments(source);
        List<String> signals = new ArrayList<>();
        String[] lines = stripped.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            for (Pattern pattern : FIRST_ROW_REPLAY_SCHEDULING_SIGNALS) {
                if (pattern.matcher(line).find()) {
                    signals.add(relative + ":" + (i + 1) + " - " + line.replaceAll("\\s+", " "));
                    break;
                }
            }
        }
        return signals;
    }

    private static List<String> completeRunOutcomePhaseSchedulingSignals(
            String relative, String source) {
        String stripped = stripComments(source);
        int methodStart = stripped.indexOf("public static TraceExecutionPhase phaseForReplay(");
        int methodEnd = stripped.indexOf(
                "private static boolean isSidekickAnimationHeldAfterRawTransition(",
                methodStart);
        if (methodStart < 0 || methodEnd < 0) {
            return List.of(relative + " - could not locate phaseForReplay");
        }

        Pattern outcomeHelper = Pattern.compile(
                "\\b(?:isS3kCompleteRun\\w*|hasNativeInitialVelocity)\\s*\\(");
        List<String> signals = new ArrayList<>();
        String[] lines = stripped.substring(methodStart, methodEnd).split("\\R", -1);
        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (outcomeHelper.matcher(line).find()) {
                signals.add(relative + " phaseForReplay - " + line.replaceAll("\\s+", " "));
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

    private static void assertDestinationAwareMavenStep(String workflow, String file, String stepName,
                                                        String eventCondition, String destinationExpression,
                                                        List<String> violations) {
        String step = workflowStep(workflow, stepName);
        if (step == null) {
            violations.add(file + " does not define step '" + stepName + "'");
            return;
        }
        if (!step.contains("if: " + eventCondition)) {
            violations.add(file + " step '" + stepName + "' is not gated to " + eventCondition);
        }
        if (!step.contains("mvn ")) {
            violations.add(file + " step '" + stepName + "' is not a Maven test path");
        }
        String property = "-DmodApi.destinationBranch=\"${{ " + destinationExpression + " }}\"";
        if (!step.contains(property)) {
            violations.add(file + " step '" + stepName + "' does not pass " + property);
        }
    }

    private static void assertMavenStepOmitsDestination(String workflow, String file, String stepName,
                                                        List<String> violations) {
        String step = workflowStep(workflow, stepName);
        if (step == null) {
            violations.add(file + " does not define step '" + stepName + "'");
            return;
        }
        if (!step.contains("mvn ")) {
            violations.add(file + " step '" + stepName + "' is not a Maven test path");
        }
        if (step.contains("modApi.destinationBranch")) {
            violations.add(file + " step '" + stepName + "' must omit destination validation");
        }
    }

    private static String workflowStep(String workflow, String stepName) {
        String marker = "      - name: " + stepName + "\n";
        int start = workflow.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int end = workflow.indexOf("\n      - name: ", start + marker.length());
        return end < 0 ? workflow.substring(start) : workflow.substring(start, end);
    }

    private static void assertEveryEventGatedMavenTestHasDestination(String workflow, String file,
                                                                      List<String> violations) {
        String[] steps = workflow.split("(?m)(?=^      - name: )", -1);
        for (String step : steps) {
            if (!step.contains("mvn ") || !step.contains(" test ")) {
                continue;
            }
            String name = step.lines().findFirst().orElse("unnamed Maven step").strip();
            if (step.contains("github.event_name == 'pull_request'")
                    && !step.contains("-DmodApi.destinationBranch=\"${{ github.base_ref }}\"")) {
                violations.add(file + " " + name + " is a PR Maven test path without github.base_ref");
            }
            if (step.contains("github.event_name == 'push'")
                    && !step.contains("-DmodApi.destinationBranch=\"${{ github.ref_name }}\"")) {
                violations.add(file + " " + name + " is a push Maven test path without github.ref_name");
            }
        }
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
