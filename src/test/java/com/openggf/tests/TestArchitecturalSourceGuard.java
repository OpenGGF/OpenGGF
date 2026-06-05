package com.openggf.tests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TestArchitecturalSourceGuard {
    private static final Path SRC_MAIN = Path.of("src", "main", "java");
    private static final String ENGINE_PATH = "com/openggf/Engine.java";
    private static final String GAME_LOOP_PATH = "com/openggf/GameLoop.java";
    private static final String OBJECT_MANAGER_PATH = "com/openggf/level/objects/ObjectManager.java";
    private static final int OBJECT_MANAGER_MAX_LINES = 3900;
    private static final int ENGINE_MAX_LARGE_METHODS = 3;
    private static final int ENGINE_LARGE_METHOD_THRESHOLD = 100;
    private static final Map<String, Integer> ROOT_CONCRETE_SONIC_REFERENCE_BUDGETS = Map.of(
            ENGINE_PATH, 3,
            GAME_LOOP_PATH, 15
    );
    private static final int OBJECT_MANAGER_CONCRETE_SONIC_REFERENCE_BUDGET = 0;
    private static final int LOW_LEVEL_GAMEPLAY_SERVICE_LOOKUP_BUDGET = 2;
    private static final List<MethodBudget> ROOT_DISPATCH_METHOD_BUDGETS = List.of(
            new MethodBudget(ENGINE_PATH, "draw", 3),
            new MethodBudget(ENGINE_PATH, "init", 180),
            new MethodBudget(ENGINE_PATH, "display", 174),
            new MethodBudget(GAME_LOOP_PATH, "stepInternal", 207),
            new MethodBudget(GAME_LOOP_PATH, "doExitBonusStage", 142),
            new MethodBudget(GAME_LOOP_PATH, "updateSpecialStageInput", 101),
            new MethodBudget(GAME_LOOP_PATH, "loadEndingDemoZone", 95),
            new MethodBudget(GAME_LOOP_PATH, "enterTitleCardFromResults", 91),
            new MethodBudget(GAME_LOOP_PATH, "enterBonusStage", 86)
    );
    private static final List<String> LOW_LEVEL_SERVICE_SCAN_ROOTS = List.of(
            "com/openggf/graphics",
            "com/openggf/audio"
    );

    private static final Set<String> GAME_ID_BRANCH_APPROVED_FILES = Set.of(
            "com/openggf/Engine.java",
            "com/openggf/GameLoop.java",
            "com/openggf/game/CrossGameFeatureProvider.java",
            "com/openggf/game/session/GameplayTeamBootstrap.java",
            "com/openggf/game/startup/DataSelectPresentationResolution.java"
    );
    private static final List<String> GAME_ID_BRANCH_APPROVED_PREFIXES = List.of(
            "com/openggf/game/dataselect/"
    );

    private static final Set<String> OBJECT_ART_ALLOWED_LEGACY_NAMES = Set.of(
            "checkpointSheet",
            "checkpointStarSheet",
            "hexBumperSheet",
            "bonusBlockSheet",
            "flipperSheet",
            "speedBoosterSheet",
            "blueBallsSheet",
            "resultsSheet",
            "leavesSheet",
            "checkpointAnimations",
            "flipperAnimations",
            "pipeExitSpringAnimations",
            "tippingFloorAnimations",
            "springboardAnimations"
    );
    private static final Pattern OBJECT_ART_FORBIDDEN_NAME =
            Pattern.compile("(?i).*(sonic|s1|s2|s3k|ghz|lz|mz|slz|syz|sbz|ehz|cpz|arz|cnz|htz|mcz|ooz|mtz|wfz|scz|dez|aiz|hcz|mgz|cnz|icz|lbz|mhz|fbz|soz|lrz|hpz|ssz|ddz|obj\\d+|plc|dplc|eager|conditional|provider|romAddr|artAddr|mappingAddr|patternBase).*");

    private static final Set<String> COORDINATE_HAZARD_ALLOWED = Set.of(
            "com/openggf/game/sonic2/objects/CheckpointStarInstance.java",
            "com/openggf/game/sonic2/objects/FallingPillarObjectInstance.java",
            "com/openggf/game/sonic2/objects/PipeExitSpringObjectInstance.java",
            "com/openggf/game/sonic3k/objects/Aiz2BossEndSequenceController.java",
            "com/openggf/game/sonic3k/objects/AizEmeraldScatterInstance.java",
            "com/openggf/game/sonic3k/objects/AizHollowTreeObjectInstance.java",
            // Cnz2CutsceneButtonInstance compares the cutscene Knuckles NPC's
            // object position (knuckles.getX/getY) against the button's proximity
            // box — object-to-object, not a player top-left hazard. Same pattern as
            // the allowlisted Hcz2CutsceneButtonInstance / S3kCutsceneButtonObjectInstance.
            "com/openggf/game/sonic3k/objects/Cnz2CutsceneButtonInstance.java",
            "com/openggf/game/sonic3k/objects/Hcz2CutsceneButtonInstance.java",
            "com/openggf/game/sonic3k/objects/HCZLargeFanObjectInstance.java",
            "com/openggf/game/sonic3k/objects/PachinkoFlipperObjectInstance.java",
            "com/openggf/game/sonic3k/objects/S3kCutsceneButtonObjectInstance.java"
    );
    private static final List<String> COORDINATE_SCAN_PREFIXES = List.of(
            "com/openggf/level/objects/",
            "com/openggf/game/sonic1/objects/",
            "com/openggf/game/sonic2/objects/",
            "com/openggf/game/sonic3k/objects/"
    );

    private static final Pattern GAME_ID_BRANCH = Pattern.compile(
            "\\b(?:if|while)\\s*\\([^)]*(?:getGameId\\s*\\(\\s*\\)|\\b\\w*GameId\\b)[^)]*(?:==|!=)[^)]*GameId\\s*\\."
                    + "|\\bswitch\\s*\\([^)]*(?:getGameId\\s*\\(\\s*\\)|\\b\\w*GameId\\b)[^)]*\\)");
    private static final Pattern RUNTIME_DISASM_PATH_LITERAL = Pattern.compile(
            "\"(?:docs/|docs\\\\\\\\)?(?:s1disasm|s2disasm|skdisasm)(?:/|\\\\\\\\)");
    private static final Pattern OBJECT_ART_DECLARATION = Pattern.compile(
            "\\b(?:private\\s+final\\s+[^;=]+|public\\s+[^;=]+|[A-Za-z0-9_<>\\[\\]]+)\\s+(\\w+)\\s*(?:[;,)=]|$)");
    private static final Pattern OBJECT_ART_ACCESSOR = Pattern.compile(
            "\\bpublic\\s+[^;=()]+\\s+(\\w+)\\s*\\(\\s*\\)");
    private static final Pattern CONCRETE_SONIC_REFERENCE = Pattern.compile(
            "\\b(?:Sonic1|Sonic2|Sonic3k|S1|S2|S3k)[A-Z][A-Za-z0-9_]*\\b"
                    + "|\\bcom\\.openggf\\.game\\.sonic(?:1|2|3k)(?:\\.[A-Za-z_][A-Za-z0-9_]*)+");
    private static final Pattern LOW_LEVEL_GAMEPLAY_SERVICE_LOOKUP = Pattern.compile(
            "\\bGameServices\\s*\\.\\s*"
                    + "(?:camera|cameraOrNull|level|levelOrNull|sprites|spritesOrNull|gameState|gameStateOrNull"
                    + "|timers|timersOrNull|fade|fadeOrNull|collision|collisionOrNull|water|waterOrNull"
                    + "|parallax|parallaxOrNull|zoneLayoutMutationPipeline|zoneLayoutMutationPipelineOrNull"
                    + "|zoneRuntimeRegistry|paletteOwnershipRegistry|animatedTileChannelGraph"
                    + "|specialRenderEffectRegistry|advancedRenderModeController)\\s*\\(");
    private static final Pattern PLAYER_TOP_LEFT_ACCESS = Pattern.compile(
            "\\b(?:player|playable|entity|sonic|tails|knuckles)\\s*\\.\\s*get[XY]\\s*\\(\\s*\\)");
    private static final Pattern COORDINATE_CONTEXT = Pattern.compile(
            "(?i)\\b(rom|parity|x_pos|y_pos|distance|threshold|trigger|dx|dy|Math\\s*\\.\\s*abs|>=|<=|>|<)\\b");

    @Test
    void productionGameIdBehaviorBranchesStayInRoutingAndCompositionCode() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            String relative = relative(file);
            if (isApprovedGameIdBranchFile(relative)) {
                continue;
            }
            String source = stripCommentsAndStrings(Files.readString(file));
            Matcher matcher = GAME_ID_BRANCH.matcher(source);
            while (matcher.find()) {
                violations.add(relative + ":" + lineNumberForOffset(source, matcher.start())
                        + " - branch on GameId outside routing/composition surface");
            }
        }

        assertNoViolations("GameId behavior branches must use feature flags or approved routing/composition code",
                violations);
    }

    @Test
    void runtimeProductionCodeDoesNotReadDisassemblyAssets() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            String relative = relative(file);
            if (relative.startsWith("com/openggf/tools/")) {
                continue;
            }
            String source = stripComments(Files.readString(file));
            Matcher matcher = RUNTIME_DISASM_PATH_LITERAL.matcher(source);
            while (matcher.find()) {
                violations.add(relative + ":" + lineNumberForOffset(source, matcher.start())
                        + " - runtime read from docs disassembly asset");
            }
        }

        assertNoViolations("Runtime production code must load gameplay assets from ROM, not docs disassembly trees",
                violations);
    }

    @Test
    void engineDoesNotGainNewLargeMethods() throws IOException {
        // Intent: Engine.java shouldn't grow uncontrollably by gaining a new
        // large method. We deliberately do NOT enforce specific line counts on
        // init/display/draw -- that would trip on any legitimate edit and push
        // people toward unrelated cleanup to make room. The structural signal
        // is just "no fourth >=100-line method appears here without review."
        SourceFile engine = SourceFile.read(SRC_MAIN.resolve(ENGINE_PATH));
        List<MethodSpan> largeMethods = engine.methods().stream()
                .filter(method -> method.lineCount() >= ENGINE_LARGE_METHOD_THRESHOLD)
                .toList();
        if (largeMethods.size() > ENGINE_MAX_LARGE_METHODS) {
            List<String> names = largeMethods.stream()
                    .map(method -> method.name() + " (" + method.lineCount() + " lines)")
                    .toList();
            fail("Engine.java now has " + largeMethods.size() + " methods at or above "
                    + ENGINE_LARGE_METHOD_THRESHOLD + " lines; cap is " + ENGINE_MAX_LARGE_METHODS
                    + ". Extract responsibilities into focused collaborators before adding new large methods.\n"
                    + "  Current large methods: " + String.join(", ", names));
        }
    }

    @Test
    void rootGameLoopAndEngineDoNotGainConcreteSonicDependencies() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Integer> budget : ROOT_CONCRETE_SONIC_REFERENCE_BUDGETS.entrySet()) {
            String relative = budget.getKey();
            List<String> references = scanPattern(relative,
                    Files.readString(SRC_MAIN.resolve(relative)),
                    CONCRETE_SONIC_REFERENCE);
            if (references.size() > budget.getValue()) {
                violations.add(relative + " has " + references.size()
                        + " concrete Sonic references; budget is " + budget.getValue()
                        + ". Route new game-specific behavior through GameModule/provider contracts.\n    "
                        + String.join("\n    ", references));
            }
        }

        assertNoViolations("Root Engine/GameLoop concrete Sonic dependency ratchet failed", violations);
    }

    @Test
    void objectManagerDoesNotGainConcreteSonicDependencies() throws IOException {
        List<String> references = scanPattern(OBJECT_MANAGER_PATH,
                Files.readString(SRC_MAIN.resolve(OBJECT_MANAGER_PATH)),
                CONCRETE_SONIC_REFERENCE);

        assertTrue(references.size() <= OBJECT_MANAGER_CONCRETE_SONIC_REFERENCE_BUDGET,
                "ObjectManager has " + references.size()
                        + " concrete Sonic references; budget is "
                        + OBJECT_MANAGER_CONCRETE_SONIC_REFERENCE_BUDGET
                        + ". Move new rewind/dynamic-object recreation through registered factories or codecs.\n    "
                        + String.join("\n    ", references));
    }

    @Test
    void lowLevelGraphicsAndAudioDoNotGainGameplayServiceLookups() throws IOException {
        List<String> references = new ArrayList<>();
        for (String root : LOW_LEVEL_SERVICE_SCAN_ROOTS) {
            for (Path file : productionFilesUnder(root)) {
                String relative = relative(file);
                references.addAll(scanPattern(relative,
                        Files.readString(file),
                        LOW_LEVEL_GAMEPLAY_SERVICE_LOOKUP));
            }
        }

        assertTrue(references.size() <= LOW_LEVEL_GAMEPLAY_SERVICE_LOOKUP_BUDGET,
                "Low-level graphics/audio code has " + references.size()
                        + " gameplay-scoped GameServices lookups; budget is "
                        + LOW_LEVEL_GAMEPLAY_SERVICE_LOOKUP_BUDGET
                        + ". Pass gameplay state from render/audio orchestration instead.\n    "
                        + String.join("\n    ", references));
    }

    @Test
    void rootDispatchMethodsDoNotGrowBeyondCurrentBudgets() throws IOException {
        List<String> violations = new ArrayList<>();
        for (MethodBudget budget : ROOT_DISPATCH_METHOD_BUDGETS) {
            SourceFile source = SourceFile.read(SRC_MAIN.resolve(budget.relativePath()));
            MethodSpan method = source.methodNamed(budget.methodName());
            if (method == null) {
                violations.add(budget.relativePath() + " no longer contains method "
                        + budget.methodName() + "; update the ratchet if it was extracted");
                continue;
            }
            if (method.lineCount() > budget.maxLines()) {
                violations.add(budget.relativePath() + "#" + budget.methodName()
                        + " is " + method.lineCount() + " lines; budget is "
                        + budget.maxLines()
                        + ". Extract mode/render work into focused collaborators before growing this dispatcher.");
            }
        }

        assertNoViolations("Root mode/render dispatcher size ratchet failed", violations);
    }

    @Test
    void engineStartupWarmupDoesNotDependOnConcreteSonicDataSelectWarmupClasses() throws IOException {
        SourceFile engine = SourceFile.read(SRC_MAIN.resolve(ENGINE_PATH));
        String source = engine.text();

        assertTrue(!source.contains("S1DataSelectImageWarmup")
                        && !source.contains("S2DataSelectImageWarmup")
                        && !source.contains("S1DataSelectImageCacheManager")
                        && !source.contains("S2DataSelectImageCacheManager"),
                "Engine startup warmup must use the GameModule/provider hook, not concrete S1/S2 data-select classes");
    }

    @Test
    void levelFrameStepDoesNotUseAmbientGameServices() throws IOException {
        String source = Files.readString(SRC_MAIN.resolve("com/openggf/LevelFrameStep.java"));

        assertTrue(!source.contains("GameServices."),
                "LevelFrameStep must receive frame dependencies through LevelFrameContext, not GameServices");
    }

    @Test
    void levelManagerDelegatesRewindSnapshotAdapterToNamedCollaborator() throws IOException {
        String source = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/level/LevelManager.java")));
        Pattern inlineRewindAdapter = Pattern.compile(
                "new\\s+com\\.openggf\\.game\\.rewind\\.RewindSnapshottable\\s*<");

        assertTrue(!inlineRewindAdapter.matcher(source).find(),
                "LevelManager.levelRewindSnapshottable() should delegate to a named level.rewind collaborator, "
                        + "not own an inline anonymous rewind adapter");
        assertTrue(!source.contains("new LevelRewindSnapshotAdapter("),
                "Use LevelRewindSnapshotAdapter.create(...) so LevelManager owns construction policy only, "
                + "not snapshot capture/restore implementation details");
    }

    @Test
    void gameLoopRoutesMenuScreenUpdatesThroughModeControllers() throws IOException {
        String source = stripCommentsAndStrings(Files.readString(SRC_MAIN.resolve("com/openggf/GameLoop.java")));
        List<String> forbiddenDirectUpdates = List.of(
                "titleScreen.update(inputHandler)",
                "levelSelect.update(inputHandler)",
                "dataSelect.update(inputHandler)");
        List<String> violations = forbiddenDirectUpdates.stream()
                .filter(source::contains)
                .toList();

        assertEquals(List.of(), violations,
                "GameLoop should delegate title, level-select, and data-select update sequencing "
                        + "to a focused game.mode controller");
    }

    @Test
    void playableMovementRoutesTerrainCollisionThroughFramePlans() throws IOException {
        String relative = "com/openggf/sprites/managers/PlayableSpriteMovement.java";
        String source = stripCommentsAndStrings(Files.readString(SRC_MAIN.resolve(relative)));

        assertTrue(source.contains("FrameCollisionPlan.terrainOnly()"),
                "Playable movement terrain collision paths must name the FrameCollisionPlan they run");

        Pattern directPlayableTerrainCollision = Pattern.compile(
                "collisionSystem\\s*\\(\\s*\\)\\s*\\.\\s*"
                        + "(resolveGroundAttachment|resolveAirCollision|resolveGroundWallCollision)"
                        + "\\s*\\(\\s*sprite\\b");
        Matcher matcher = directPlayableTerrainCollision.matcher(source);
        List<String> violations = new ArrayList<>();
        while (matcher.find()) {
            violations.add(relative + ":" + lineNumberForOffset(source, matcher.start())
                    + " - direct " + matcher.group(1) + " call without FrameCollisionPlan");
        }

        assertNoViolations("Playable movement collision orchestration must route through FrameCollisionPlan",
                violations);
    }

    @Test
    void objectManagerFacadeStaysWithinExtractedCollaboratorBudget() throws IOException {
        SourceFile objectManager = SourceFile.read(SRC_MAIN.resolve(OBJECT_MANAGER_PATH));
        int lineCount = objectManager.lines().size();
        assertTrue(lineCount <= OBJECT_MANAGER_MAX_LINES,
                "ObjectManager.java is " + lineCount + " lines; budget is " + OBJECT_MANAGER_MAX_LINES
                        + " after extracting ObjectPlacementController, ObjectTouchResponseController, "
                        + "and ObjectSolidContactController. Keep new placement, touch-response, "
                        + "and solid-contact logic in those collaborators.");
    }

    @Test
    void objectArtDataDoesNotGainNewGameOrZoneSpecificSurface() throws IOException {
        String source = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/level/objects/ObjectArtData.java")));
        List<String> names = objectArtDataSurfaceNames(source);
        List<String> violations = names.stream()
                .filter(name -> OBJECT_ART_FORBIDDEN_NAME.matcher(name).matches())
                .filter(name -> !OBJECT_ART_ALLOWED_LEGACY_NAMES.contains(name))
                .distinct()
                .sorted()
                .toList();

        assertNoViolations("ObjectArtData must stay game-agnostic; add game/zone art to object art providers instead",
                violations);
    }

    @Test
    void sharedObjectArtSplitTypesExistAtProviderBoundary() {
        assertTrue(Files.exists(SRC_MAIN.resolve("com/openggf/level/objects/art/ObjectArtBundle.java")),
                "ObjectArtBundle should be the shared, game-agnostic render data contract");
        assertTrue(Files.exists(SRC_MAIN.resolve("com/openggf/level/objects/art/ObjectArtRegistration.java")),
                "ObjectArtRegistration should hold provider-owned art registration metadata");
    }

    @Test
    void objectArtDataDoesNotExposeSonic2ProviderSpecificFields() throws IOException {
        String source = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/level/objects/ObjectArtData.java")));
        List<String> names = objectArtDataSurfaceNames(source);
        List<String> forbidden = names.stream()
                .filter(Set.of(
                        "breakableBlockSheet",
                        "cpzPlatformSheet",
                        "cpzStairBlockSheet",
                        "sidewaysPformSheet",
                        "cpzPylonSheet",
                        "pipeExitSpringSheet",
                        "tippingFloorSheet",
                        "barrierSheet",
                        "springboardSheet")::contains)
                .sorted()
                .toList();

        assertEquals(List.of(), forbidden,
                "Sonic 2 conditional/eager sheets belong in Sonic2ObjectArtProvider registrations, not ObjectArtData");
    }

    @Test
    void productionCodeDoesNotCallDeprecatedCollisionSystemStep() throws IOException {
        List<String> violations = new ArrayList<>();
        Pattern deprecatedStepCall = Pattern.compile("\\.step\\s*\\(");
        for (Path file : productionFiles()) {
            String relative = relative(file);
            if (relative.equals("com/openggf/physics/CollisionSystem.java")) {
                continue;
            }
            String stripped = stripCommentsAndStrings(Files.readString(file));
            if (!stripped.contains("CollisionSystem")) {
                continue;
            }
            String[] lines = stripped.split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                if (deprecatedStepCall.matcher(lines[i]).find()) {
                    violations.add(relative + ":" + (i + 1) + " - use a named FrameCollisionPlan instead");
                }
            }
        }

        assertNoViolations("Production code must not call deprecated CollisionSystem.step()", violations);
    }

    @Test
    void objectCodeDoesNotAddSuspiciousPlayerTopLeftCoordinateHazards() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            String relative = relative(file);
            if (!isCoordinateScanned(relative) || COORDINATE_HAZARD_ALLOWED.contains(relative)) {
                continue;
            }
            violations.addAll(scanCoordinateHazards(relative, Files.readString(file)));
        }

        assertNoViolations("Suspicious player.getX()/getY() top-left coordinate use in object code", violations);
    }

    @Test
    void sampleScannerDetectsGameIdBranchButAllowsFeatureFlagBranch() {
        List<String> violations = scanGameIdBranches("sample/Physics.java", """
                class Physics {
                    void bad(GameModule module) {
                        if (module.getGameId() == GameId.S1) runS1();
                    }
                    void good(PhysicsFeatureSet features) {
                        if (features.spindashEnabled()) run();
                    }
                }
                """);

        assertEquals(List.of("sample/Physics.java:3 - branch on GameId outside routing/composition surface"),
                violations);
    }

    @Test
    void sampleScannerDetectsRuntimeDisassemblyAssetRead() {
        List<String> violations = scanRuntimeDisasmReads("sample/RuntimeLoader.java", """
                class RuntimeLoader {
                    byte[] bad() throws Exception {
                        return Files.readAllBytes(Path.of("docs/skdisasm/General/Sprites.bin"));
                    }
                }
                """);

        assertEquals(List.of("sample/RuntimeLoader.java:3 - runtime read from docs disassembly asset"),
                violations);
    }

    @Test
    void sampleScannerDetectsObjectArtDataGameSpecificName() {
        List<String> names = objectArtDataSurfaceNames("""
                class ObjectArtData {
                    private final ObjectSpriteSheet monitorSheet;
                    private final ObjectSpriteSheet ghzBridgeSheet;
                    private final List<SpriteMappingFrame> obj26Mappings;
                    public ObjectArtData(ObjectSpriteSheet monitorSheet, ObjectSpriteSheet ghzBridgeSheet) {}
                    public ObjectSpriteSheet s3kMonitorSheet() { return null; }
                }
                """);

        List<String> violations = names.stream()
                .filter(name -> OBJECT_ART_FORBIDDEN_NAME.matcher(name).matches())
                .filter(name -> !OBJECT_ART_ALLOWED_LEGACY_NAMES.contains(name))
                .distinct()
                .sorted()
                .toList();

        assertEquals(List.of("ghzBridgeSheet", "obj26Mappings", "s3kMonitorSheet"), violations);
    }

    @Test
    void sampleScannerDetectsConcreteSonicReferences() {
        List<String> violations = scanPattern("sample/Root.java", """
                import com.openggf.game.sonic2.Sonic2GameModule;

                class Root {
                    private final S3kDataSelectImageWarmup warmup = null;
                    Object bad() {
                        return new com.openggf.game.sonic3k.objects.PachinkoEnergyTrapObjectInstance(null);
                    }
                }
                """, CONCRETE_SONIC_REFERENCE);

        assertEquals(List.of(
                "sample/Root.java:1 - com.openggf.game.sonic2.Sonic2GameModule",
                "sample/Root.java:4 - S3kDataSelectImageWarmup",
                "sample/Root.java:6 - com.openggf.game.sonic3k.objects.PachinkoEnergyTrapObjectInstance"),
                violations);
    }

    @Test
    void sampleScannerDetectsGameplayServiceLookupInLowLevelCode() {
        List<String> violations = scanPattern("sample/Renderer.java", """
                class Renderer {
                    void render() {
                        GameServices.cameraOrNull();
                        GameServices.configuration();
                    }
                }
                """, LOW_LEVEL_GAMEPLAY_SERVICE_LOOKUP);

        assertEquals(List.of("sample/Renderer.java:3 - GameServices.cameraOrNull("), violations);
    }

    @Test
    void sampleScannerDetectsCoordinateHazardNearDistanceExpression() {
        List<String> violations = scanCoordinateHazards("sample/Object.java", """
                class Object {
                    void update(PlayableEntity player) {
                        int dx = Math.abs(player.getX() - x);
                    }
                }
                """);

        assertEquals(List.of("sample/Object.java:3 - player.getX() near coordinate hazard context"),
                violations);
    }

    private static List<Path> productionFiles() throws IOException {
        if (!Files.isDirectory(SRC_MAIN)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(SRC_MAIN)) {
            return stream.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static List<Path> productionFilesUnder(String relativeRoot) throws IOException {
        Path root = SRC_MAIN.resolve(relativeRoot);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static boolean isApprovedGameIdBranchFile(String relative) {
        return GAME_ID_BRANCH_APPROVED_FILES.contains(relative)
                || GAME_ID_BRANCH_APPROVED_PREFIXES.stream().anyMatch(relative::startsWith);
    }

    private static boolean isCoordinateScanned(String relative) {
        return COORDINATE_SCAN_PREFIXES.stream().anyMatch(relative::startsWith);
    }

    private static List<String> scanGameIdBranches(String relative, String source) {
        String stripped = stripCommentsAndStrings(source);
        List<String> violations = new ArrayList<>();
        Matcher matcher = GAME_ID_BRANCH.matcher(stripped);
        while (matcher.find()) {
            violations.add(relative + ":" + lineNumberForOffset(stripped, matcher.start())
                    + " - branch on GameId outside routing/composition surface");
        }
        return violations;
    }

    private static List<String> scanRuntimeDisasmReads(String relative, String source) {
        String stripped = stripComments(source);
        List<String> violations = new ArrayList<>();
        Matcher matcher = RUNTIME_DISASM_PATH_LITERAL.matcher(stripped);
        while (matcher.find()) {
            violations.add(relative + ":" + lineNumberForOffset(stripped, matcher.start())
                    + " - runtime read from docs disassembly asset");
        }
        return violations;
    }

    private static List<String> scanPattern(String relative, String source, Pattern pattern) {
        String stripped = stripCommentsAndStrings(source);
        List<String> violations = new ArrayList<>();
        Matcher matcher = pattern.matcher(stripped);
        while (matcher.find()) {
            violations.add(relative + ":" + lineNumberForOffset(stripped, matcher.start())
                    + " - " + matcher.group().replaceAll("\\s+", ""));
        }
        return violations;
    }

    private static List<String> objectArtDataSurfaceNames(String source) {
        Set<String> names = new HashSet<>();
        Matcher matcher = OBJECT_ART_DECLARATION.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!Set.of("class", "return", "new", "if").contains(name)) {
                names.add(name);
            }
        }
        matcher = OBJECT_ART_ACCESSOR.matcher(source);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names.stream().sorted().toList();
    }

    private static List<String> scanCoordinateHazards(String relative, String source) {
        String stripped = stripCommentsAndStrings(source);
        List<String> violations = new ArrayList<>();
        Matcher matcher = PLAYER_TOP_LEFT_ACCESS.matcher(stripped);
        while (matcher.find()) {
            int start = Math.max(0, matcher.start() - 100);
            int end = Math.min(stripped.length(), matcher.end() + 100);
            String context = stripped.substring(start, end);
            if (COORDINATE_CONTEXT.matcher(context).find()) {
                violations.add(relative + ":" + lineNumberForOffset(stripped, matcher.start())
                        + " - " + matcher.group().replaceAll("\\s+", "") + " near coordinate hazard context");
            }
        }
        return violations;
    }

    private static void assertNoViolations(String message, List<String> violations) {
        if (!violations.isEmpty()) {
            fail(message + ":\n  " + String.join("\n  ", violations));
        }
    }

    private static String relative(Path file) {
        return SRC_MAIN.relativize(file).toString().replace('\\', '/');
    }

    private static String stripCommentsAndStrings(String source) {
        return strip(source, true);
    }

    private static String stripComments(String source) {
        return strip(source, false);
    }

    private static String strip(String source, boolean stripStrings) {
        StringBuilder stripped = new StringBuilder(source.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaping = false;

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
            if (inString) {
                stripped.append(stripStrings && current != '\n' && current != '\r' && current != '"' ? ' ' : current);
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                stripped.append(stripStrings && current != '\n' && current != '\r' && current != '\'' ? ' ' : current);
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '\'') {
                    inChar = false;
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
            if (current == '"') {
                inString = true;
                stripped.append(current);
                continue;
            }
            if (current == '\'') {
                inChar = true;
                stripped.append(current);
                continue;
            }
            stripped.append(current);
        }
        return stripped.toString();
    }

    private static int lineNumberForOffset(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private record MethodBudget(String relativePath, String methodName, int maxLines) {
    }

    private record SourceFile(String text, List<String> lines) {
        private static final Pattern METHOD_START = Pattern.compile(
                "^\\s*(?:public|private|protected)\\s+(?:static\\s+)?(?:synchronized\\s+)?[\\w<>\\[\\].?, ]+\\s+(\\w+)\\s*\\([^;]*\\)\\s*\\{\\s*$");

        static SourceFile read(Path path) throws IOException {
            return new SourceFile(Files.readString(path), Files.readAllLines(path));
        }

        List<MethodSpan> methods() {
            List<MethodSpan> methods = new ArrayList<>();
            boolean inMethod = false;
            int startLine = 0;
            int braceDepth = 0;
            String methodName = "";

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!inMethod) {
                    Matcher matcher = METHOD_START.matcher(line);
                    if (matcher.matches()) {
                        inMethod = true;
                        startLine = i + 1;
                        methodName = matcher.group(1);
                        braceDepth = braceDelta(line);
                    }
                } else {
                    braceDepth += braceDelta(line);
                    if (braceDepth <= 0) {
                        methods.add(new MethodSpan(methodName, startLine, i + 1));
                        inMethod = false;
                    }
                }
            }
            return methods;
        }

        MethodSpan methodNamed(String name) {
            return methods().stream()
                    .filter(method -> method.name().equals(name))
                    .findFirst()
                    .orElse(null);
        }

        private static int braceDelta(String line) {
            int delta = 0;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == '{') {
                    delta++;
                } else if (line.charAt(i) == '}') {
                    delta--;
                }
            }
            return delta;
        }
    }

    private record MethodSpan(String name, int startLine, int endLine) {
        int lineCount() {
            return endLine - startLine + 1;
        }
    }
}
