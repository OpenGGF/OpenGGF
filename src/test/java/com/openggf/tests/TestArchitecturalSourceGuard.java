package com.openggf.tests;

import com.openggf.game.rules.GameRules;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TestArchitecturalSourceGuard {
    private static final Path SRC_MAIN = Path.of("src", "main", "java");
    private static final String ENGINE_PATH = "com/openggf/Engine.java";
    private static final String GAME_LOOP_PATH = "com/openggf/GameLoop.java";
    private static final String OBJECT_MANAGER_PATH = "com/openggf/level/objects/ObjectManager.java";

    private static final String SUSPENSION_PATH = "com/openggf/GameLoopSuspension.java";
    private static final List<String> SUSPENSION_FIELDS = List.of(
            "private SuspendedGame suspendedGame;", "private SuspendedGame.Pending pendingSuspend;",
            "private SuspendedGame.Thumbnail pendingSuspendThumbnail;", "private boolean suspendThumbnailRequested;",
            "private boolean parked;", "private boolean preparing;", "private boolean userPausedWhenParked;",
            "private com.openggf.game.session.GameplayModeContext parkedOwner;");
    private static final Map<String, String> SUSPENSION_EFFECT_CONTRACTS = Map.of(
            "CaptureEffects", """
                WorldSession publishedWorld(); boolean editorPlaytestActive(); AudioLogicalSnapshot captureAudioIfEnabled();
                void fadeMusic(); RgbaImage captureViewportThumbnail(); int thumbnailWidth(); int thumbnailHeight();
                SuspendedSettings suspendSettings(); SuspendedGame.CameraPosition captureCameraPosition();
                void resumeSettings(SuspendedSettings settings, MasterTitleScreen.GameEntry entry, boolean inLevel);
                void restoreCamera(SuspendedGame.CameraPosition position); void restoreAudio(AudioLogicalSnapshot snapshot);
                """,
            "HubEffects", """
                MasterTitleScreen currentScreen(); void publishScreen(MasterTitleScreen screen); MasterTitleScreen createScreen();
                void refreshCachedConfig(); void applyDisplayDimensions(); void cancelBootstrapFade();
                """,
            "RetirementEffects", """
                void resetForGameplayFromMasterTitle(); void clearGameplayReference(); void ensureTitleAudioBackend();
                """);
    private static final Set<String> SUSPENSION_METHODS = Set.of("leave", "suspendable", "modOwnsEscapeTap",
            "completePreparedSuspend", "park", "unpark", "discard", "routeResume", "betweenFrames", "prepareSuspend",
            "afterScene", "suspendToMasterTitle", "resumeSuspendedGame", "installCard", "discardSuspendedGame", "retireGameplay");

    @Test
    void suspendedVisitOwnsItsCompleteLifetimeWithColdNarrowEffects() throws IOException {
        assertNoViolations("Suspended visit ownership boundary failed", suspensionOwnershipViolations(
                Files.readString(SRC_MAIN.resolve(ENGINE_PATH)), Files.readString(SRC_MAIN.resolve(SUSPENSION_PATH))));
    }

    @Test
    void suspendedVisitSamplesPermitTheKnownLoopButRejectMirrorsFacadesAndEagerBinding() throws IOException {
        String root = Files.readString(SRC_MAIN.resolve(ENGINE_PATH));
        String owner = Files.readString(SRC_MAIN.resolve(SUSPENSION_PATH));
        assertTrue(suspensionOwnershipViolations(root, owner).isEmpty(), "the established GameLoop reference is deliberate");
        for (String field : SUSPENSION_FIELDS) {
            assertFalse(suspensionOwnershipViolations(root, owner.replace(field, "")).isEmpty(), field);
        }
        for (String field : List.of("private SuspendedGame mirror;", "private SuspendedGame.Pending pending;",
                "private SuspendedGame.Thumbnail picture;", "private boolean suspendThumbnailRequested;")) {
            assertFalse(suspensionOwnershipViolations(root.replace("public class Engine implements com.openggf.graphics.RenderProjection {",
                    "public class Engine implements com.openggf.graphics.RenderProjection { " + field), owner).isEmpty(), field);
        }
        for (String forbidden : List.of("private Engine engine;", "private EngineContext context;",
                "void locate() { EngineServices.current(); }", "void locate() { Engine.currentGameLoop(); }",
                "EngineContext context() { return null; }", "Object lookup(Class key) { return null; }")) {
            assertFalse(suspensionOwnershipViolations(root, owner.replace("final class GameLoopSuspension {",
                    "final class GameLoopSuspension { " + forbidden)).isEmpty(), forbidden);
        }
        assertFalse(suspensionOwnershipViolations(root, owner.replace("void fadeMusic();", "void suspend();")).isEmpty());
        assertFalse(suspensionOwnershipViolations(root, owner.replace("void fadeMusic();", "void execute(Runnable action);")).isEmpty());
        assertFalse(suspensionOwnershipViolations(root, owner.replace("this.effects = effects;",
                "this.effects = effects; effects.capture().publishedWorld();")).isEmpty());
        assertFalse(suspensionOwnershipViolations(root, owner.replace("this.loop = Objects.requireNonNull(loop, \"loop\");",
                "this.loop = Objects.requireNonNull(loop, \"loop\"); loop.getCurrentGameMode();")).isEmpty());
        assertFalse(suspensionOwnershipViolations(root, owner.replace("void retireGameplay()", "void forgetVisit()")).isEmpty());
        assertFalse(suspensionOwnershipViolations(root.replace("suspension.betweenFrames(suspensionEffects);",
                "if (suspension.parked()) suspension.betweenFrames(suspensionEffects);"), owner).isEmpty());
        assertFalse(suspensionOwnershipViolations(root.replace("private final GameLoopSuspension suspension;",
                "private final GameLoopSuspension suspension; private void resumeSuspendedGame() {}"), owner).isEmpty());
    }

    private static List<String> suspensionOwnershipViolations(String root, String owner) {
        List<String> violations = new ArrayList<>();
        String rootCode = stripCommentsAndStrings(root), ownerCode = stripCommentsAndStrings(owner);
        String compactOwner = ownerCode.replaceAll("\\s+", "");
        if (!rootCode.contains("private final GameLoopSuspension suspension;")
                || !rootCode.contains("this.suspension = gameLoop.suspension;")) violations.add("same loop owner alias");
        if (Pattern.compile("private\\s+(?:final\\s+)?SuspendedGame(?:\\.Pending|\\.Thumbnail)?\\s+\\w+\\s*[;=]").matcher(rootCode).find())
            violations.add("root retains visit data or a mirror");
        for (String field : SUSPENSION_FIELDS) {
            if (!compactOwner.contains(field.replaceAll("\\s+", ""))) violations.add("missing visit state: " + field);
            String name = field.substring(field.lastIndexOf(' ') + 1, field.length() - 1);
            if (Pattern.compile("\\b" + name + "\\b").matcher(rootCode).find()) violations.add("root retains visit decision: " + name);
        }
        long fields = Pattern.compile("private\\s+(?:(?:static|final)\\s+)*[\\w.<>]+\\s+\\w+\\s*[;=]").matcher(ownerCode).results().count();
        if (fields != 11) violations.add("eight visit values, Loop, replaceable effects and logger only");
        if (Pattern.compile("\\b(?:Engine|EngineContext)\\b|\\b(?:SonicConfigurationService|AudioManager|GraphicsManager)\\s+\\w+\\s*[;,)=]"
                + "|(?:EngineServices|GameServices)\\s*\\.|\\bObject\\s+\\w+\\s*\\(").matcher(ownerCode).find())
            violations.add("no application/context/manager backreference or erased/global locator");
        Set<String> rootMethods = SourceFile.fromText(root).methods().stream().filter(method -> method.identity().startsWith("Engine#"))
                .map(MethodSpan::name).collect(java.util.stream.Collectors.toSet());
        for (String method : List.of("prepareSuspend", "captureSuspendThumbnailIfRequested", "suspendToMasterTitle", "resumeSuspendedGame", "discardSuspendedGame"))
            if (rootMethods.contains(method)) violations.add("whole visit operation remains on Engine: " + method);
        Set<String> methods = SourceFile.fromText(owner).methods().stream().filter(method -> method.identity().startsWith("GameLoopSuspension#"))
                .map(MethodSpan::name).collect(java.util.stream.Collectors.toSet());
        if (!methods.containsAll(SUSPENSION_METHODS)) violations.add("complete visit choreography belongs to owner");
        if (!Pattern.compile("boolean\\s+requiresScenePicture\\(\\)\\s*\\{\\s*return\\s+suspendThumbnailRequested;\\s*}")
                .matcher(ownerCode).find()) violations.add("scheduler reads the owner's picture request only");
        Set<String> rootEntries = Set.of("setEffects", "retireGameplay", "discardSuspendedGame", "installCard", "betweenFrames", "afterScene", "requiresScenePicture");
        Matcher entries = Pattern.compile("\\bsuspension\\.(\\w+)\\(").matcher(rootCode);
        while (entries.find()) if (!rootEntries.contains(entries.group(1))) violations.add("root retains decision: " + entries.group(1));
        for (var contract : SUSPENSION_EFFECT_CONTRACTS.entrySet()) {
            Matcher group = Pattern.compile("interface\\s+" + contract.getKey() + "\\s*\\{([^}]*)}").matcher(ownerCode);
            if (!group.find() || !group.group(1).replaceAll("\\s+", "").equals(contract.getValue().replaceAll("\\s+", "")))
                violations.add("narrow typed effects only: " + contract.getKey());
        }
        Matcher constructor = Pattern.compile("GameLoopSuspension\\(GameLoop loop\\)\\s*\\{([^}]*)}").matcher(stripComments(owner));
        if (!constructor.find() || !constructor.group(1).replaceAll("\\s+", "").equals("this.loop=Objects.requireNonNull(loop,\"loop\");"))
            violations.add("construction stores Loop only");
        Matcher binding = Pattern.compile("void setEffects\\(Effects effects\\)\\s*\\{([^}]*)}").matcher(ownerCode);
        if (!binding.find() || !binding.group(1).replaceAll("\\s+", "").equals("this.effects=effects;")) violations.add("effect installation stores only");
        if (!Pattern.compile("record Effects\\(CaptureEffects capture, HubEffects hub, RetirementEffects retirement\\)\\s*\\{\\s*}").matcher(ownerCode).find())
            violations.add("bundle constructor stores references only");
        return violations;
    }

    private static final String TITLE_CARD_LIFECYCLE_PATH = "com/openggf/GameLoopTitleCardLifecycle.java";
    private static final String TITLE_CARD_HOST_CONTRACT = """
        LevelManager level(); SpriteManager sprites(); Camera camera(); InputHandler input();
        GameModule module(); GameMode currentMode(); boolean endOfLevelActive();
        String mainCharacterCode(); AbstractPlayableSprite mainPlayable();
        void lockTitleControls(boolean locked); void restorePendingShield(AbstractPlayableSprite playable);
        GameMode changeMode(GameMode mode); GameMode changeModeWithoutRewindBoundary(GameMode mode);
        void markBonusEntryBoundary(); boolean returningFromSpecialStage(); void completeTitleReturn();
        void applyDeferredBonusStageSetup(); void syncPlaybackInput(); void admitDestination();
        void recordLevelRelease(); void notifyModeChanged(GameMode oldMode);
        """;
    private static final List<String> TITLE_CARD_FIELDS = List.of(
            "private TitleCardProvider titleCardProvider;",
            "private PostTitleCardDestination postTitleCardDestination = PostTitleCardDestination.LEVEL;",
            "private LevelFrameResult titleReleaseResult = LevelFrameResult.GAMEPLAY_FRAME;");
    private static final Set<String> TITLE_CARD_METHODS = Set.of("provider", "cachedProvider", "clearProviderCache",
            "destination", "releaseResult", "enterTitleCard", "startPendingInLevelTitleCard",
            "applyTitleCardControlLock", "runInputNeutralTitleCardPlayerPrelude", "update", "exitTitleCard",
            "enterBonusTitleCardAfterLevelLoadBoundary");
    private static final Map<String, String> TITLE_CARD_ROOT_DELEGATES = Map.of(
            "enterTitleCard", "titleCardLifecycle.enterTitleCard(zoneIndex, actIndex);",
            "startPendingInLevelTitleCard", "titleCardLifecycle.startPendingInLevelTitleCard();",
            "applyTitleCardControlLock", "titleCardLifecycle.applyTitleCardControlLock(locked);",
            "enterBonusTitleCardAfterLevelLoadBoundary", "return titleCardLifecycle.enterBonusTitleCardAfterLevelLoadBoundary();",
            "getTitleCardProviderLazy", "return titleCardLifecycle.provider();");

    @Test
    void titleLifecycleOwnsPresentationBindingAndSharedReleaseWithNarrowLiveHost() throws IOException {
        assertNoViolations("Title ownership boundary failed", titleOwnershipViolations(
                Files.readString(SRC_MAIN.resolve(GAME_LOOP_PATH)), Files.readString(SRC_MAIN.resolve(TITLE_CARD_LIFECYCLE_PATH))));
        String root = stripCommentsAndStrings(Files.readString(SRC_MAIN.resolve(GAME_LOOP_PATH)));
        int update = root.indexOf("private boolean updateTitleCardMode(");
        int acquire = root.indexOf("getTitleCardProviderLazy()", update);
        int snapshots = root.indexOf("activePlcLifecycleFrame,", update);
        assertTrue(update >= 0 && acquire > update && snapshots > acquire,
                "lazy acquisition must finish before the update's later argument snapshots");
        assertEquals(3, countMatches(Pattern.compile("titleCardLifecycle\\.cachedProvider\\(\\)"), root),
                "the policy/pacing observations stay cold");
        int reset = root.indexOf("public void resetModuleScopedProviders()");
        assertTrue(root.indexOf("continueScreen.reset();", reset) < root.indexOf("titleCardLifecycle.clearProviderCache();", reset));
    }

    @Test
    void titleOwnershipSamplesAllowCompatibilityDelegatesAndTypeReferencesButRejectRetainedDecisions() {
        String root = "class GameLoop {\n private final GameLoopTitleCardLifecycle titleCardLifecycle = null;\n"
                + "public void enterTitleCard(int zoneIndex, int actIndex) {\n titleCardLifecycle.enterTitleCard(zoneIndex, actIndex);\n }\n }";
        String owner = "final class GameLoopTitleCardLifecycle { private static final Logger LOGGER = Logger.getLogger(GameLoop.class.getName()); "
                + "private final Host host; " + String.join(" ", TITLE_CARD_FIELDS)
                + " GameLoopTitleCardLifecycle(Host host) { this.host = host; } interface Host { " + TITLE_CARD_HOST_CONTRACT + " } "
                + TITLE_CARD_METHODS.stream().sorted().map(name -> "void " + name + "() {}")
                        .collect(java.util.stream.Collectors.joining(" ")) + " }";
        assertTrue(titleOwnershipViolations(root, owner).isEmpty(), "a virtual delegate and GameLoop.class are legitimate");
        for (String field : TITLE_CARD_FIELDS) {
            assertFalse(titleOwnershipViolations(root.replace("class GameLoop {", "class GameLoop { " + field), owner).isEmpty(), field);
            assertFalse(titleOwnershipViolations(root, owner.replace(field, "")).isEmpty(), field);
        }
        for (String decision : List.of("if (currentGameMode != GameMode.LEVEL) return; ", "applyPendingBonusStageShieldRestore(null); ")) {
            assertFalse(titleOwnershipViolations(root.replace("titleCardLifecycle.enterTitleCard(zoneIndex, actIndex);",
                    decision + "titleCardLifecycle.enterTitleCard(zoneIndex, actIndex);"), owner).isEmpty(), decision);
        }
        for (String retained : List.of("private TitleCardProvider mirror;", "private PostTitleCardDestination destination;",
                "private LevelFrameResult titleResult;", "private void exitTitleCard() {}",
                "private void runInputNeutralTitleCardPlayerPrelude() {}")) {
            assertFalse(titleOwnershipViolations(root.replace("class GameLoop {", "class GameLoop { " + retained), owner).isEmpty(), retained);
        }
        for (String forbidden : List.of("private GameLoop loop;", "private EngineContext context;", "private boolean pending;",
                "void call() { GameLoop.this.step(); }", "Object lookup(Class key) { return null; }",
                "void call() { GameServices.module(); }")) {
            assertFalse(titleOwnershipViolations(root, owner.replace("final class GameLoopTitleCardLifecycle {",
                    "final class GameLoopTitleCardLifecycle { " + forbidden)).isEmpty(), forbidden);
        }
        for (String wide : List.of("void runWholeFrame(Runnable frame);", "GameLoop root();", "GameplayModeContext context();",
                "boolean releaseTitleIntoLevel();")) {
            assertFalse(titleOwnershipViolations(root, owner.replace("void notifyModeChanged(GameMode oldMode);", wide)).isEmpty(), wide);
        }
        assertFalse(titleOwnershipViolations(root, owner.replace("this.host = host;", "this.host = host; host.module();")).isEmpty());
        assertFalse(titleOwnershipViolations(root, owner.replace("void exitTitleCard() {}", "")).isEmpty());
    }

    private static List<String> titleOwnershipViolations(String root, String owner) {
        List<String> violations = new ArrayList<>();
        String rootCode = stripCommentsAndStrings(root), ownerCode = stripCommentsAndStrings(owner);
        if (!Pattern.compile("private\\s+final\\s+GameLoopTitleCardLifecycle\\s+titleCardLifecycle\\b").matcher(rootCode).find())
            violations.add("one loop-lifetime title owner");
        if (Pattern.compile("private\\s+(?:final\\s+)?(?:TitleCardProvider|PostTitleCardDestination)\\s+\\w+\\s*[;=]"
                + "|private\\s+(?:final\\s+)?LevelFrameResult\\s+\\w+\\s*[;=]").matcher(rootCode).find())
            violations.add("root retains title state or mirror");
        for (String field : TITLE_CARD_FIELDS) {
            if (!ownerCode.replaceAll("\\s+", "").contains(field.replaceAll("\\s+", ""))) violations.add("missing state: " + field);
        }
        long fields = Pattern.compile("private\\s+(?:(?:static|final)\\s+)*[\\w.<>]+\\s+\\w+\\s*[;=]").matcher(ownerCode).results().count();
        if (fields != 5) violations.add("exactly three retained values plus immutable Host/logger dependencies");
        if (!Pattern.compile("(?<!public )final\\s+class\\s+GameLoopTitleCardLifecycle").matcher(ownerCode).find()
                || Pattern.compile("public\\s+final\\s+class\\s+GameLoopTitleCardLifecycle").matcher(ownerCode).find())
            violations.add("package-private owner");
        if (Pattern.compile("\\b(?:GameLoop|EngineContext)\\s+\\w+\\s*[;,)=]|GameLoop\\.this|GameServices\\s*\\.|\\bObject\\s+\\w+\\s*\\(").matcher(ownerCode).find())
            violations.add("root/context backreference or erased/global locator");
        var rootSource = SourceFile.fromText(root);
        for (MethodSpan method : rootSource.methods()) {
            if (!method.identity().startsWith("GameLoop#")) continue;
            if (Set.of("exitTitleCard", "runInputNeutralTitleCardPlayerPrelude").contains(method.name()))
                violations.add("private root choreography: " + method.name());
            String delegate = TITLE_CARD_ROOT_DELEGATES.get(method.name());
            if (delegate != null) {
                String text = stripCommentsAndStrings(String.join("\n", rootSource.lines().subList(method.startLine()-1, method.endLine())));
                text = text.substring(text.indexOf('{')+1, text.lastIndexOf('}')).replaceAll("\\s+", "");
                if (!text.equals(delegate.replaceAll("\\s+", ""))) violations.add("root decision/effect in " + method.name());
            }
        }
        Set<String> methods = SourceFile.fromText(owner).methods().stream()
                .filter(method -> method.identity().startsWith("GameLoopTitleCardLifecycle#"))
                .map(MethodSpan::name).collect(java.util.stream.Collectors.toSet());
        if (!methods.containsAll(TITLE_CARD_METHODS)) violations.add("complete title choreography belongs to owner");
        Matcher host = Pattern.compile("interface\\s+Host\\s*\\{([^}]*)}").matcher(ownerCode);
        if (!host.find() || !host.group(1).replaceAll("\\s+", "").equals(TITLE_CARD_HOST_CONTRACT.replaceAll("\\s+", "")))
            violations.add("narrow typed Host, no delegated title decisions or whole-frame/load callbacks");
        Matcher constructor = Pattern.compile("GameLoopTitleCardLifecycle\\(Host host\\)\\s*\\{([^}]*)}").matcher(ownerCode);
        if (!constructor.find() || !constructor.group(1).replaceAll("\\s+", "").equals("this.host=host;"))
            violations.add("construction only retains Host");
        return violations;
    }

    private static final String SPECIAL_STAGE_LIFECYCLE_PATH = "com/openggf/GameLoopSpecialStageLifecycle.java";
    private static final String SPECIAL_STAGE_HOST_CONTRACT = """
        LevelManager level(); SpriteManager sprites(); Camera camera(); FadeManager fade();
        GameStateManager gameState(); GameModule module(); GameMode currentMode(); GameplayRunPolicy runPolicy();
        SonicConfigurationService config(); InputHandler input(); PlaybackDebugManager playback();
        GameplayModeContext boundGameplayContext(); GameplayModeContext resolvedGameplayContext();
        PlcLifecycleFrame plcFrame(); LiveRewindManager rewind(); GameLoopSpecialStageInput pacingInput();
        SpecialStageProvider activeProvider(); String mainCharacterCode(); TitleCardProvider titleCard();
        GameMode changeMode(GameMode mode); boolean debugKeyPressed(int key); boolean rewindable();
        Runnable debugCompletionCallback(); Runnable debugFailureCallback(); void deregisterSpecialStageAdapter();
        void doEnterSpecialStage(SpecialStageProvider provider, int stage, boolean black);
        void doEnterResultsScreen(); void lockTitleControls(boolean locked);
        void restoreBigRingReturn(AbstractPlayableSprite playable); void notifyModeChanged(GameMode oldMode);
        void requestSessionSave(SaveReason reason);
            """;
    private static final List<String> SPECIAL_STAGE_FIELDS = List.of(
            "private SpecialStageProvider activeSpecialStageProvider = NoOpSpecialStageProvider.INSTANCE;",
            "private GameLoop.SpecialStageObservationPacing specialStageObservationPacing;",
            "private ResultsScreen resultsScreen;",
            "private int ssRingsCollected;",
            "private boolean ssEmeraldCollected;",
            "private int ssStageIndex;",
            "private EmeraldRewardKind activeSpecialStageRewardKind = EmeraldRewardKind.CHAOS_EMERALD;",
            "private int resultsFrameCounter = 0;",
            "private boolean returningFromSpecialStage = false;",
            "private boolean specialStageTransitionPending = false;",
            "private final SpecialStageEntryPresentationController specialStageEntryPresentation =\n            new SpecialStageEntryPresentationController();",
            "private boolean resultsExitFadeCompleted;",
            "private boolean resultsExitToWhite = true;",
            "private boolean resultsExitReady;",
            "private int resultsExitPreLevelFadeFramesRemaining = -1;");
    private static final Set<String> SPECIAL_STAGE_METHODS = Set.of("updateSpecialStageMode", "updateSpecialStageResultsMode", "runSpecialStageResultsIteration", "enterResultsScreenWithDebugRings", "defaultSpecialStageStartupPolicy", "enterResultsScreen", "exitResultsScreen", "doExitResultsScreen", "startResultsReturnFadeIn", "enterTitleCardFromResults", "updateSpecialStageInput", "completeTitleReturn");

    @Test
    void specialStageLifecycleOwnsEntryResultsAndReturnWithTypedLiveHost() throws IOException {
        assertNoViolations("Special-stage lifecycle ownership failed", specialStageOwnershipViolations(
                Files.readString(SRC_MAIN.resolve(GAME_LOOP_PATH)),
                Files.readString(SRC_MAIN.resolve(SPECIAL_STAGE_LIFECYCLE_PATH))));
    }

    @Test
    void specialStageOwnershipAllowsNestedPacingTypeAndRejectsActualRootAccessAndMirrors() {
        String root = "class GameLoop { private final GameLoopSpecialStageLifecycle specialStageLifecycle = null; "
                + "private boolean specialStageRewindBoundaryThisFrame; private final GameLoopSpecialStageInput specialStagePacingInput = null; }";
        String owner = "final class GameLoopSpecialStageLifecycle { " + String.join(" ", SPECIAL_STAGE_FIELDS)
                + " GameLoopSpecialStageLifecycle(Host host, AudioManager audioManager) { this.host = host; this.audioManager = audioManager; } "
                + "interface Host { " + SPECIAL_STAGE_HOST_CONTRACT + " } "
                + SPECIAL_STAGE_METHODS.stream().sorted().map(name -> "void " + name + "() {}")
                        .collect(java.util.stream.Collectors.joining(" ")) + " }";
        assertTrue(specialStageOwnershipViolations(root, owner).isEmpty(), "the existing nested pacing type is permitted");
        for (String field : SPECIAL_STAGE_FIELDS) {
            assertFalse(specialStageOwnershipViolations(root.replace("class GameLoop {", "class GameLoop { " + field), owner).isEmpty(), field);
            assertFalse(specialStageOwnershipViolations(root, owner.replace(field, "")).isEmpty(), field);
        }
        for (String forbidden : List.of("GameLoop loop;", "void call() { GameLoop.this.step(); }",
                "void call() { GameLoop.shouldPlaySpecialStageEntrySfx(false); }", "EngineContext context;",
                "Object lookup(Class key) { return null; }", "void call() { GameServices.module(); }",
                "private SpecialStageProvider publishedProvider;", "private boolean pendingMirror;",
                "private boolean specialStageRewindBoundaryThisFrame;", "private GameLoopSpecialStageInput specialStagePacingInput;")) {
            assertFalse(specialStageOwnershipViolations(root, owner.replace("final class GameLoopSpecialStageLifecycle {",
                    "final class GameLoopSpecialStageLifecycle { " + forbidden)).isEmpty(), forbidden);
        }
        for (String method : SPECIAL_STAGE_METHODS) {
            assertFalse(specialStageOwnershipViolations(root.replace("class GameLoop {", "class GameLoop { private void " + method + "() {}"), owner).isEmpty(), method);
            assertFalse(specialStageOwnershipViolations(root, owner.replace("void " + method + "() {}", "")).isEmpty(), method);
        }
        assertFalse(specialStageOwnershipViolations(root, owner.replace("void notifyModeChanged(GameMode oldMode);",
                "void runWholeFrame(Runnable frame);")).isEmpty(), "no concealed whole-frame callback");
        assertFalse(specialStageOwnershipViolations(root, owner.replace("this.host = host;", "this.host = host; host.module();")).isEmpty(), "constructor has no eager Host reads");
        assertFalse(specialStageOwnershipViolations(root.replace("class GameLoop {", "class GameLoop { private ResultsScreen mirror;"), owner).isEmpty());
    }

    private static List<String> specialStageOwnershipViolations(String root, String owner) {
        List<String> violations = new ArrayList<>();
        String rootCode = stripCommentsAndStrings(root), ownerCode = stripCommentsAndStrings(owner);
        if (!Pattern.compile("private\\s+final\\s+GameLoopSpecialStageLifecycle\\s+specialStageLifecycle\\b").matcher(rootCode).find())
            violations.add("root retains one loop-lifetime special lifecycle");
        if (Pattern.compile("private\\s+(?:final\\s+)?(?:SpecialStageProvider|ResultsScreen|SpecialStageEntryPresentationController|(?:GameLoop\\.)?SpecialStageObservationPacing)\\s+\\w+\\s*[;=]").matcher(rootCode).find())
            violations.add("root retains special state or a mirror");
        for (String field : SPECIAL_STAGE_FIELDS) {
            // Exact declaration checks preserve every initial value, including the -1 exit sentinel.
            if (rootCode.replaceAll("\\s+", "").contains(field.replaceAll("\\s+", ""))) violations.add("root state: " + field);
            if (!ownerCode.replaceAll("\\s+", "").contains(field.replaceAll("\\s+", ""))) violations.add("missing state: " + field);
        }
        long stateCount = Pattern.compile("private\\s+(?:final\\s+)?(?:SpecialStageProvider|GameLoop\\.SpecialStageObservationPacing|ResultsScreen|int|boolean|EmeraldRewardKind|SpecialStageEntryPresentationController)\\s+\\w+\\s*[;=]")
                .matcher(ownerCode).results().count();
        if (stateCount != 15) violations.add("exactly fifteen session fields, no mirrors");
        String withoutPacingType = ownerCode.replace("GameLoop.SpecialStageObservationPacing", "AllowedPacingType");
        if (Pattern.compile("\\b(?:GameLoop|EngineContext|GameServices|Object|specialStageRewindBoundaryThisFrame|specialStagePacingInput)\\b").matcher(withoutPacingType).find())
            violations.add("root/context/global backreference, erased locator or shared iteration state");
        if (!Pattern.compile("(?<!public )final\\s+class\\s+GameLoopSpecialStageLifecycle").matcher(ownerCode).find()
                || Pattern.compile("public\\s+final\\s+class\\s+GameLoopSpecialStageLifecycle").matcher(ownerCode).find())
            violations.add("special lifecycle remains package-private");
        for (MethodSpan method : SourceFile.fromText(root).methods()) {
            if (method.identity().startsWith("GameLoop#") && SPECIAL_STAGE_METHODS.contains(method.name())) violations.add("root choreography: " + method.identity());
        }
        Set<String> methods = SourceFile.fromText(owner).methods().stream()
                .filter(method -> method.identity().startsWith("GameLoopSpecialStageLifecycle#"))
                .map(MethodSpan::name).collect(java.util.stream.Collectors.toSet());
        if (!methods.containsAll(SPECIAL_STAGE_METHODS)) violations.add("complete lifecycle belongs to owner");
        Matcher host = Pattern.compile("interface\\s+Host\\s*\\{([^}]*)}").matcher(ownerCode);
        if (!host.find() || !host.group(1).replaceAll("\\s+", "").equals(SPECIAL_STAGE_HOST_CONTRACT.replaceAll("\\s+", "")))
            violations.add("host retains narrow typed live capabilities");
        Matcher constructor = Pattern.compile("GameLoopSpecialStageLifecycle\\(Host host, AudioManager audioManager\\)\\s*\\{([^}]*)}").matcher(ownerCode);
        if (!constructor.find() || !constructor.group(1).replaceAll("\\s+", "").equals("this.host=host;this.audioManager=audioManager;"))
            violations.add("constructor only retains collaborators");
        return violations;
    }

    private static final String BONUS_SESSION_PATH = "com/openggf/GameLoopBonusStageSession.java";
    private static final Set<String> BONUS_VISIT_METHODS = Set.of("enterBonusStage", "doEnterBonusStage",
            "exitBonusStage", "doExitBonusStage", "prepareBonusStageForTitleCard", "applyDeferredBonusStageSetup",
            "restorePlayableStateForBonusTitleCard", "refreshPlayableSpriteArtCaches", "ensureBonusStageBootstrapObjectPresent",
            "forcePlayerHighPriorityInBonusStage", "handleBonusStageDebugKey");
    private static final String BONUS_HOST_CONTRACT = """
            LevelManager level(); SpriteManager sprites(); Camera camera(); WaterSystem water(); FadeManager fade();
            GameStateManager gameState(); GameModule module(); GameMode currentMode(); GameplayRunPolicy runPolicy();
            GameplayModeContext publishedGameplayContext(); GameplayModeContext resolvedGameplayContext();
            String mainCharacterCode(); AbstractPlayableSprite mainPlayable(); TitleCardProvider titleCard();
            GameMode enterBonusTitleCardAfterLevelLoadBoundary(); GameMode changeMode(GameMode mode);
            void titleDestination(PostTitleCardDestination destination); void lockTitleControls(boolean locked);
            void notifyModeChanged(GameMode oldMode);
            """;
    private static final List<String> BONUS_VISIT_FIELDS = List.of(
            "private final BonusStageTransitionCoordinator bonusStageTransitionCoordinator = new BonusStageTransitionCoordinator();",
            "private boolean bonusStageTransitionPending;", "private BonusStageProvider activeBonusStageProvider;",
            "private int pendingBonusReturnStarPostMark = -1;", "private BonusStageProvider deferredBonusProvider;",
            "private ShieldType pendingBonusStageShieldRestore;");

    @Test
    void bonusVisitSessionOwnsStateAndChoreographyWithTypedLiveBindings() throws IOException {
        Path owner = SRC_MAIN.resolve(BONUS_SESSION_PATH);
        assertNoViolations("Bonus visit ownership boundary failed", bonusOwnershipViolations(
                Files.readString(SRC_MAIN.resolve(GAME_LOOP_PATH)), Files.exists(owner) ? Files.readString(owner) : ""));
    }

    @Test
    void bonusOwnershipSamplesRejectMirrorsBackreferencesDeadStateAndWideHosts() {
        String root = "class GameLoop { private final GameLoopBonusStageSession bonusSession = null; }";
        String owner = "final class GameLoopBonusStageSession { " + String.join(" ", BONUS_VISIT_FIELDS)
                + " interface Host { " + BONUS_HOST_CONTRACT + " } "
                + BONUS_VISIT_METHODS.stream().sorted().map(name -> "void " + name + "() {}")
                        .collect(java.util.stream.Collectors.joining(" ")) + " }";
        assertTrue(bonusOwnershipViolations(root, owner).isEmpty());
        String compatible = root.replace("class GameLoop {", "class GameLoop { static ShieldType savedShieldType(int bits) { "
                + "return GameLoopBonusStageSession.savedShieldType(bits); } void applyPendingBonusStageShieldRestore(Player p) { "
                + "bonusSession.applyPendingBonusStageShieldRestore(p); }");
        assertTrue(bonusOwnershipViolations(compatible, owner).isEmpty(), "maintained compatibility delegates are allowed");
        for (String field : BONUS_VISIT_FIELDS) {
            assertFalse(bonusOwnershipViolations(root.replace("class GameLoop {", "class GameLoop { " + field), owner).isEmpty(), field);
            assertFalse(bonusOwnershipViolations(root, owner.replace(field, "")).isEmpty(), field);
        }
        for (String method : BONUS_VISIT_METHODS) {
            assertFalse(bonusOwnershipViolations(root.replace("class GameLoop {", "class GameLoop { private void " + method + "() {}"), owner).isEmpty(), method);
            assertFalse(bonusOwnershipViolations(root, owner.replace("void " + method + "() {}", "")).isEmpty(), method);
        }
        for (String forbidden : List.of("GameLoop loop;", "EngineContext context;", "Object lookup(Class key) { return null; }",
                "void frame() { GameServices.module(); }", "private BonusStageProvider publishedBonusStageProvider;", "BonusStageType deferredBonusType;", "BonusStageState deferredBonusState;",
                "void dispose() { LevelFrameStep.execute(); }")) {
            assertFalse(bonusOwnershipViolations(root, owner.replace("final class GameLoopBonusStageSession {",
                    "final class GameLoopBonusStageSession { " + forbidden)).isEmpty(), forbidden);
        }
        assertFalse(bonusOwnershipViolations(root, owner.replace("void notifyModeChanged(GameMode oldMode);",
                "void runWholeLoad(Runnable body);")).isEmpty(), "the host cannot conceal whole-load choreography");
    }

    private static List<String> bonusOwnershipViolations(String root, String owner) {
        List<String> violations = new ArrayList<>();
        String rootCode = stripCommentsAndStrings(root), ownerCode = stripCommentsAndStrings(owner);
        if (!Pattern.compile("private\\s+final\\s+GameLoopBonusStageSession\\s+bonusSession\\b").matcher(rootCode).find()) {
            violations.add("root retains one loop-lifetime bonus session");
        }
        if (Pattern.compile("private\\s+(?:final\\s+)?(?:BonusStageTransitionCoordinator|BonusStageProvider)\\s+\\w+\\s*[;=]"
                + "|\\b(?:bonusStageTransitionPending|pendingBonusReturnStarPostMark|pendingBonusStageShieldRestore|deferredBonusType|deferredBonusState)\\s*[;=]")
                .matcher(rootCode).find()) violations.add("bonus visit state remains in root");
        for (MethodSpan method : SourceFile.fromText(root).methods()) {
            if (method.identity().startsWith("GameLoop#") && BONUS_VISIT_METHODS.contains(method.name())) {
                violations.add("root visit choreography: " + method.identity());
            }
        }
        if (!Pattern.compile("(?<!public )final\\s+class\\s+GameLoopBonusStageSession").matcher(ownerCode).find()
                || Pattern.compile("public\\s+final\\s+class\\s+GameLoopBonusStageSession").matcher(ownerCode).find()) {
            violations.add("bonus owner must remain package-private");
        }
        if (Pattern.compile("\\b(?:GameLoop|EngineContext|GameServices|Object|LevelFrameStep|LiveRewindManager|PlaybackDebugManager|deferredBonusType|deferredBonusState)\\b")
                .matcher(ownerCode).find()) violations.add("owner has backreference, erased lookup, shared driver or dead storage");
        for (String field : BONUS_VISIT_FIELDS) {
            if (!ownerCode.replaceAll("\\s+", "").contains(field.replaceAll("\\s+", ""))) violations.add("missing visit field: " + field);
        }
        long visitFields = Pattern.compile("private\\s+(?:final\\s+)?(?:BonusStageTransitionCoordinator|boolean|BonusStageProvider|int|ShieldType)\\s+\\w+\\s*[;=]")
                .matcher(ownerCode).results().count();
        if (visitFields != BONUS_VISIT_FIELDS.size()) violations.add("visit state must not acquire mirrored publication or pending fields");
        Set<String> methods = SourceFile.fromText(owner).methods().stream()
                .filter(method -> method.identity().startsWith("GameLoopBonusStageSession#"))
                .map(MethodSpan::name).collect(java.util.stream.Collectors.toSet());
        if (!methods.containsAll(BONUS_VISIT_METHODS)) violations.add("complete visit choreography must be session-owned");
        Matcher host = Pattern.compile("interface\\s+Host\\s*\\{([^}]*)}").matcher(ownerCode);
        if (!host.find() || !host.group(1).replaceAll("\\s+", "").equals(BONUS_HOST_CONTRACT.replaceAll("\\s+", ""))) {
            violations.add("bonus host must retain explicit typed live capabilities");
        }
        return violations;
    }

    private static final String ENDING_SESSION_PATH = "com/openggf/GameLoopEndingSession.java";
    private static final Set<String> ENDING_METHODS = Set.of("startEndingFade", "doEnterEnding", "updateEnding",
            "updateEndingCreditsText", "updateEndingCreditsDemo", "updateEndingPostCredits", "updateEndingCutscene",
            "loadEndingDemoZone", "returnFromEndingDemo", "exitEndingToTitleScreen", "doExitEndingToTitleScreen",
            "gameModeForPhase");
    private static final String ENDING_HOST_CONTRACT = """
            LevelManager level(); SpriteManager sprites(); Camera camera(); WaterSystem water(); FadeManager fade();
            GameModule module(); InputHandler input(); String mainCharacterCode(); GameMode currentMode();
            GameplayModeContext boundGameplayContext(); GameplayModeContext resolvedGameplayContext();
            void setGameMode(GameMode mode); void requestSessionSave(SaveReason reason);
            TitleScreenProvider titleScreen(); void returnToTitleScreen();
            """;

    @Test
    void endingSessionOwnsStateAndChoreographyBehindTheVirtualFacade() throws IOException {
        Path owner = SRC_MAIN.resolve(ENDING_SESSION_PATH);
        assertNoViolations("Ending session ownership boundary failed", endingOwnershipViolations(
                Files.readString(SRC_MAIN.resolve(GAME_LOOP_PATH)), Files.exists(owner) ? Files.readString(owner) : ""));
    }

    @Test
    void endingOwnershipSamplesAllowTheGetterAndRejectBackreferencesMirrorsAndWrappers() {
        String root = "class GameLoop { private final GameLoopEndingSession endingSession = null; "
                + "public EndingProvider getEndingProvider() { return endingSession.provider(); } }";
        String owner = "class GameLoopEndingSession { private EndingProvider endingProvider; "
                + "private boolean endingTransitionPending; interface Host { " + ENDING_HOST_CONTRACT + " } "
                + ENDING_METHODS.stream().sorted().map(name -> "void " + name + "() {}")
                        .collect(java.util.stream.Collectors.joining(" ")) + " }";
        assertTrue(endingOwnershipViolations(root, owner).isEmpty(), "the virtual facade remains permitted");
        assertFalse(endingOwnershipViolations(root.replace("return endingSession.provider();", "return null;"), owner).isEmpty());
        for (String retained : List.of("private EndingProvider endingProvider;", "private boolean endingTransitionPending;",
                "private void updateEnding() {}", "private void loadEndingDemoZone() {}")) {
            assertFalse(endingOwnershipViolations(root.replace("class GameLoop {", "class GameLoop { " + retained), owner).isEmpty(), retained);
        }
        for (String forbidden : List.of("GameLoop loop;", "EngineContext context;", "Object locate(Class type) { return null; }",
                "void frame() { GameServices.module(); }")) {
            assertFalse(endingOwnershipViolations(root, owner.replace("class GameLoopEndingSession {",
                    "class GameLoopEndingSession { " + forbidden)).isEmpty(), forbidden);
        }
        assertFalse(endingOwnershipViolations(root, owner.replace("private EndingProvider endingProvider;", "")).isEmpty());
        assertFalse(endingOwnershipViolations(root, owner.replace("private boolean endingTransitionPending;", "")).isEmpty());
        for (String method : ENDING_METHODS) {
            assertFalse(endingOwnershipViolations(root, owner.replace("void " + method + "() {}", "")).isEmpty(), method);
        }
        assertFalse(endingOwnershipViolations(root, owner.replace("void returnToTitleScreen();",
                "void runWholeFrame();")).isEmpty(), "the host cannot conceal whole-frame work");
    }

    private static List<String> endingOwnershipViolations(String root, String owner) {
        List<String> violations = new ArrayList<>();
        String rootCode = stripCommentsAndStrings(root);
        String ownerCode = stripCommentsAndStrings(owner);
        if (Pattern.compile("\\bEndingProvider\\s+\\w+\\s*[;=]|\\bboolean\\s+endingTransitionPending\\b")
                .matcher(rootCode).find()) violations.add("ending state remains in GameLoop");
        for (MethodSpan method : SourceFile.fromText(root).methods()) {
            if (ENDING_METHODS.contains(method.name())) violations.add("root ending choreography: " + method.identity());
        }
        if (!Pattern.compile("public\\s+EndingProvider\\s+getEndingProvider\\s*\\(\\s*\\)\\s*\\{\\s*return\\s+endingSession\\.provider\\(\\);\\s*}")
                .matcher(rootCode).find()) violations.add("retain the public virtual delegating getter");
        if (Pattern.compile("\\b(?:GameLoop|EngineContext|GameServices|Object)\\b").matcher(ownerCode).find()) {
            violations.add("ending owner has a root/context/service backreference or erased locator");
        }
        if (!Pattern.compile("private\\s+EndingProvider\\s+endingProvider\\s*;").matcher(ownerCode).find()
                || !Pattern.compile("private\\s+boolean\\s+endingTransitionPending\\s*;").matcher(ownerCode).find()) {
            violations.add("the session must own its provider and single pending bit");
        }
        Set<String> ownedMethods = SourceFile.fromText(owner).methods().stream()
                .filter(method -> method.identity().startsWith("GameLoopEndingSession#"))
                .map(MethodSpan::name).collect(java.util.stream.Collectors.toSet());
        if (!ownedMethods.containsAll(ENDING_METHODS)) violations.add("ending choreography must remain in the session");
        Matcher host = Pattern.compile("interface\\s+Host\\s*\\{([^}]*)}").matcher(ownerCode);
        if (!host.find() || !host.group(1).replaceAll("\\s+", "").equals(ENDING_HOST_CONTRACT.replaceAll("\\s+", ""))) {
            violations.add("ending host must retain only its explicit typed live capabilities");
        }
        return violations;
    }

    @Test
    void titlePreviewOrchestrationDoesNotHideConcreteGameDependenciesInConstants() throws IOException {
        List<Path> paths = new ArrayList<>();
        paths.add(SRC_MAIN.resolve("com/openggf/game/MasterTitleRomPreview.java"));
        try (Stream<Path> files = Files.walk(SRC_MAIN.resolve("com/openggf/game/preview"))) {
            paths.addAll(files.filter(path -> path.toString().endsWith(".java")).toList());
        }
        Pattern concrete = Pattern.compile("com\\.openggf\\.game\\.sonic(?:1|2|3k)\\b|\\bSONIC_(?:1|2|3K)\\b");
        for (Path path : paths) {
            assertFalse(concrete.matcher(stripCommentsAndStrings(Files.readString(path))).find(),
                    () -> path + " must use the preview capability, including inlined constants");
        }
    }

    @Test
    void engineLiveCaptureOrderingStaysAtThePresentationBoundary() throws IOException {
        String source = Files.readString(SRC_MAIN.resolve(ENGINE_PATH));
        assertOrdered(source, "applyDisplayShaderPhase(ShaderPhase.FINAL)",
                "liveCapturePresentation.present(");
        assertOrdered(source, "handleLiveCaptureShortcut();",
                "updateDisplayShaderInput()");
        assertTrue(source.contains("createLiveFrameRuntime(this::renderScheduledFrame, () -> glfwSwapBuffers(window))"));
        assertTrue(source.contains("sceneFocusPresentation.swapPrepared(swap, liveSceneSubmissionsBefore, gameLoop.modSceneHost.renderedSubmissions())"));
        String coordinator = Files.readString(SRC_MAIN.resolve("com/openggf/LiveFrameRuntime.java"));
        assertOrdered(coordinator, "owner.tick()", "owner.draw(owner.captureActive())");
        assertOrdered(coordinator, "owner.draw(false)", "owner.swap()");
        String presenter = Files.readString(SRC_MAIN.resolve("com/openggf/SceneFocusPresentation.java"));
        assertOrdered(presenter, "Engine.displayAndSwap(display, swap)",
                "scenePresented = sceneSubmissions.getAsLong() != before");
    }

    @Test
    void suppressedLevelMusicPreservesTheActiveAudioSource() throws IOException {
        SourceFile source = SourceFile.read(SRC_MAIN.resolve("com/openggf/level/LevelManager.java"));
        MethodSpan span = source.methodNamed("initAudio");
        assertTrue(span != null, "LevelManager must retain the level audio initializer");
        String method = String.join("\n",
                source.lines().subList(span.startLine() - 1, span.endLine()));

        assertOrdered(method, "transitions.isSuppressNextMusicChange()", "configureAudio();");
    }

    @Test
    void levelStartClearsReusedPlayableAnimationState() throws IOException {
        SourceFile source = SourceFile.read(SRC_MAIN.resolve(
                "com/openggf/sprites/playable/AbstractPlayableSprite.java"));
        MethodSpan span = source.methodNamed("resetState");
        assertTrue(span != null, "Playable sprites must retain the level-start reset owner");
        String method = String.join("\n",
                source.lines().subList(span.startLine() - 1, span.endLine()));

        assertTrue(method.contains("this.animationId = 0;"));
        assertTrue(method.contains("this.mappingFrame = 0;"));
        assertTrue(method.contains("this.animationFrameIndex = 0;"));
        assertTrue(method.contains("this.animationTick = 0;"));
        assertTrue(method.contains("forceAnimationRestart();"));
    }

    private static void assertOrdered(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second, firstIndex + first.length());
        assertTrue(firstIndex >= 0 && secondIndex > firstIndex,
                () -> first + " must precede " + second);
    }
    // 2026-07-02: re-ratcheted 2747 -> 2821 after the S2 trace-parity batch
    // (8766a6889..0f7794de8) grew reserved-slot spawn and placement handling,
    // then 2821 -> 2823 for the rewind capture of the S2 post-camera unload
    // latch (PlacementSnapshot restore path).
    // 2026-07-06: 2823 -> 2869 for dependency-ordered dynamic rewind
    // reconstruction. The growth is rewind orchestration, not placement,
    // touch-response, or solid-contact logic owned by extracted collaborators.
    // Rewind child reconstruction adds a narrowly scoped lifecycle hook at the
    // existing restore orchestration boundary; it does not move collaborator logic back here.
    // 2026-07-12: ratchet inherited develop growth to 2910, plus a four-line public
    // delegate into ObjectRewindReferenceClosureValidator. Traversal stays extracted.
    // 2026-08-26: the merged facade combines next's callback-routed ownership with
    // develop's ObjectLoopSlotBudget entry points. Freeze the exact merged inventory;
    // placement, touch-response and solid-contact logic remain in collaborators.
    private static final int OBJECT_MANAGER_MAX_EFFECTIVE_SOURCE_LINES = 3073;
    private static final Map<String, Integer> RELEASE_CRITICAL_CLASS_EFFECTIVE_SOURCE_LINE_BUDGETS = Map.of(
            "com/openggf/game/sonic1/Sonic1ObjectArtProvider.java", 2045,
            // 2026-07-02: 3065 -> 3115 after S2 trace fixes + the GameRules typed-rule
            // refactor (d9b727925) settled the sprite at 3115 effective lines.
            // 2026-07-09: 3115 -> 3159 for drowning, speed-shoes, and fixed-skid
            // controller state captured by the playable rewind schema.
            // 2026-08-26: exact merged inventory for next's Mod API hooks and develop's
            // native player-SST reset/restore ownership.
            // 2026-09-08: upstream resets the reused player animation before the
            // pre-fade BuildSprites pass and removes duplicate invincibility resets:
            // net +2 effective lines over next. Freeze the combined inventory.
            "com/openggf/sprites/playable/AbstractPlayableSprite.java", 3257,
            // 2026-08-26: exact merged inventory for next's mod/editor/session and
            // seamless-transition surface plus develop's bootstrap, VBlank and
            // transient oscillation/respawn handoff wiring.
            // 2026-10-09: 3145 -> 3144; the time-attack gate became a generic host-return request.
            "com/openggf/level/LevelManager.java", 3142,
            // 2026-07-02: 2888 -> 2890 for the live-rewind VHS effect envelope tick
            // (RewindEffectEnvelope wiring + intensity/speed accessors).
            // 2026-07-04: 2890 -> 2962. The solo-ghost-racing phase-1 tasks (time
            // attack runtime frame hooks, retry-key handling, HUD overlay wiring)
            // grew this file past 2890 without re-ratcheting; this bump also folds
            // in Task 13's master-title Time Attack launch-handler wiring
            // (setTimeAttackLaunchHandler/installTimeAttackLaunchHandler/
            // getTimeAttackRuntime()) and the deactivate() call in
            // returnToMasterTitle().
            // 2026-07-06: 2890 -> 2910 for the Gumball/Pachinko bonus-stage live-rewind
            // integration (isBonusStageRewindable + the updateBonusStageMode capture hook
            // + coordinator-adapter register/deregister on bonus entry/exit, incl. the
            // failed-load error-path deregister for lifecycle symmetry). The edits are
            // scattered across stepInternal/updateBonusStageMode/doEnter/doExit and do
            // not form an extractable collaborator.
            // 2026-07-09: 2910 -> 2985 for the live special-stage trace visual session
            // skip gate in updateSpecialStageMode (recorded-input override + lag-row
            // provider.update() gate + trace-cursor advance + launcher-owned finish
            // guard). Prior work had already grown the file to 2973 effective lines
            // without a budget bump; this raises the ratchet to the true current count
            // including the +12 SS-gate lines. The gate is a handful of guarded calls
            // to TraceSessionLauncher and does not form an extractable collaborator.
            // 2026-08-26: exact merged inventory for multi-segment dispatch,
            // transition-freeze admission, time-attack/multiplayer hooks and the
            // special-stage return boundary. State machines remain collaborator-owned.
            // 2026-10-09: 3381 -> 3313 after Time Attack and multiplayer moved into the
            // bundled mod; the loop keeps only generic hosted-run hooks (HostedRunController).
            // 2026-10-24: ending state, phase execution and cleanup move as one session.
            ENDING_SESSION_PATH, 320,
            BONUS_SESSION_PATH, 357,
            SPECIAL_STAGE_LIFECYCLE_PATH, 571,
            TITLE_CARD_LIFECYCLE_PATH, 253,
            GAME_LOOP_PATH, 2367
    );
    private static final List<String> ENGINE_LARGE_METHOD_OWNERS = List.of(
            "Engine#init()", "Engine#renderAdvancedFrame(FrameAdvance,boolean,boolean)");
    private static final int ENGINE_LARGE_METHOD_THRESHOLD = 100;
    private static final Map<String, Integer> ROOT_CONCRETE_SONIC_REFERENCE_BUDGETS = Map.of(
            ENGINE_PATH, 0,
            GAME_LOOP_PATH, 0
    );
    private static final int OBJECT_MANAGER_CONCRETE_SONIC_REFERENCE_BUDGET = 0;
    private static final int LOW_LEVEL_GAMEPLAY_SERVICE_LOOKUP_BUDGET = 0;
    private static final List<MethodBudget> ROOT_DISPATCH_METHOD_BUDGETS = List.of(
            new MethodBudget(ENGINE_PATH, "draw", 3),
            new MethodBudget(ENGINE_PATH, "init", 159),
            // 2026-07-02: display 206 -> 217 for the live-rewind VHS picture-search
            // pass (RewindVhsEffectPass.apply between the fade pass and the
            // PRESENTATION display-shader phase; the pass itself is a collaborator).
            // 2026-07-02: display 217 -> 220 for the final-review fix hoisting the
            // zero-intensity check above the RewindVhsEffectPass.apply call so the
            // config lookups and gameLoop accessors are skipped when intensity==0.
            // 2026-07-03: display 220 -> 221 for the rewind.vhsTearBands config
            // argument threaded into the RewindVhsEffectPass.apply call.
            // 2026-07-04: display 221 -> 228 for the time-attack HUD render call
            // (gameLoop.renderTimeAttackHud) added by the solo-ghost-racing
            // phase-1 HUD-overlay task; not re-ratcheted at the time.
            // The profiling wrappers must not conceal growth in the actual dispatchers.
            new MethodBudget(ENGINE_PATH, "display", 4),
            // The live advance/presentation extraction keeps each new owner ratcheted.
            new MethodBudget(ENGINE_PATH, "displayFrame", 5),
            new MethodBudget(ENGINE_PATH, "renderScheduledFrame", 32),
            new MethodBudget(ENGINE_PATH, "advanceFrame", 32),
            new MethodBudget(ENGINE_PATH, "renderAdvancedFrame", 153),
            // 2026-07-07: stepInternal 213 -> 235 in the develop->next merge (develop's
            // bonus-stage rewind capture/engage hooks stack onto next's time-attack hooks).
            new MethodBudget(TITLE_CARD_LIFECYCLE_PATH, "update", 131),
            new MethodBudget(TITLE_CARD_LIFECYCLE_PATH, "enterTitleCard", 47),
            new MethodBudget(TITLE_CARD_LIFECYCLE_PATH, "exitTitleCard", 65),
            new MethodBudget(TITLE_CARD_LIFECYCLE_PATH, "runInputNeutralTitleCardPlayerPrelude", 19),
            new MethodBudget(GAME_LOOP_PATH, "stepInternal", 52),
            // 2026-10-27: native code mods remove the obsolete boot-notice dispatch.
            new MethodBudget(GAME_LOOP_PATH, "stepInternalBody", 256),
            new MethodBudget(BONUS_SESSION_PATH, "doExitBonusStage", 111),
            new MethodBudget(SPECIAL_STAGE_LIFECYCLE_PATH, "updateSpecialStageInput", 9),
            new MethodBudget(ENDING_SESSION_PATH, "loadEndingDemoZone", 84),
            // 2026-08-26: exact merged spans after title-card destination/return
            // synthesis and bonus-stage rewind-adapter handoff.
            new MethodBudget(SPECIAL_STAGE_LIFECYCLE_PATH, "enterTitleCardFromResults", 60),
            new MethodBudget(BONUS_SESSION_PATH, "enterBonusStage", 45)
    );
    private static final List<String> LOW_LEVEL_SERVICE_SCAN_ROOTS = List.of(
            "com/openggf/graphics",
            "com/openggf/audio"
    );
    private static final List<String> OBJECT_CHILD_SPAWN_MIGRATED_FILES = List.of(
            "com/openggf/game/sonic2/objects/CheckpointObjectInstance.java",
            "com/openggf/game/sonic2/objects/ArrowShooterObjectInstance.java",
            "com/openggf/game/sonic2/objects/BubbleGeneratorObjectInstance.java",
            "com/openggf/game/sonic2/objects/BonusBlockObjectInstance.java",
            "com/openggf/game/sonic2/objects/BreakableBlockObjectInstance.java",
            "com/openggf/game/sonic2/objects/BreakablePlatingObjectInstance.java",
            "com/openggf/game/sonic2/objects/BumperObjectInstance.java",
            "com/openggf/game/sonic2/objects/CollapsingPlatformObjectInstance.java",
            "com/openggf/game/sonic2/objects/ConveyorObjectInstance.java",
            "com/openggf/game/sonic2/objects/EggPrisonObjectInstance.java",
            "com/openggf/game/sonic2/objects/FallingPillarObjectInstance.java",
            "com/openggf/game/sonic2/objects/LeavesGeneratorObjectInstance.java",
            "com/openggf/game/sonic2/objects/MonitorObjectInstance.java",
            "com/openggf/game/sonic2/objects/OOZLauncherObjectInstance.java",
            "com/openggf/game/sonic2/objects/OOZPoppingPlatformObjectInstance.java",
            "com/openggf/game/sonic2/objects/PointPokeyObjectInstance.java",
            "com/openggf/game/sonic2/objects/RivetObjectInstance.java",
            "com/openggf/game/sonic2/objects/RisingPillarObjectInstance.java",
            "com/openggf/game/sonic2/objects/SeesawObjectInstance.java",
            "com/openggf/game/sonic2/objects/HTZLiftObjectInstance.java",
            "com/openggf/game/sonic2/objects/SidewaysPformObjectInstance.java",
            "com/openggf/game/sonic2/objects/SignpostObjectInstance.java",
            "com/openggf/game/sonic2/objects/SmashableGroundObjectInstance.java",
            "com/openggf/game/sonic2/objects/SmallMetalPformObjectInstance.java",
            "com/openggf/game/sonic2/objects/SteamSpringObjectInstance.java",
            "com/openggf/game/sonic2/objects/TiltingPlatformObjectInstance.java",
            "com/openggf/game/sonic2/objects/TornadoObjectInstance.java",
            "com/openggf/game/sonic2/objects/badniks/CluckerBadnikInstance.java",
            "com/openggf/game/sonic2/objects/badniks/CoconutsBadnikInstance.java",
            "com/openggf/game/sonic2/objects/badniks/GrounderBadnikInstance.java",
            "com/openggf/game/sonic2/objects/badniks/NebulaBadnikInstance.java",
            "com/openggf/game/sonic2/objects/badniks/OctusBadnikInstance.java",
            "com/openggf/game/sonic2/objects/badniks/RexonBadnikInstance.java",
            "com/openggf/game/sonic2/objects/badniks/RexonHeadObjectInstance.java",
            "com/openggf/game/sonic2/objects/badniks/SolBadnikInstance.java",
            "com/openggf/game/sonic2/objects/badniks/SpikerBadnikInstance.java",
            "com/openggf/game/sonic2/objects/badniks/SpinyBadnikInstance.java",
            "com/openggf/game/sonic2/objects/badniks/TurtloidBadnikInstance.java",
            "com/openggf/game/sonic2/objects/bosses/CNZBossElectricBall.java",
            "com/openggf/game/sonic2/objects/bosses/CPZBossContainer.java",
            "com/openggf/game/sonic2/objects/bosses/CPZBossContainerExtend.java",
            "com/openggf/game/sonic2/objects/bosses/CPZBossFallingPart.java",
            "com/openggf/game/sonic2/objects/bosses/CPZBossGunk.java",
            "com/openggf/game/sonic2/objects/bosses/CPZBossPipe.java",
            "com/openggf/game/sonic2/objects/bosses/CPZBossPipeSegment.java",
            "com/openggf/game/sonic2/objects/bosses/CPZBossPump.java",
            "com/openggf/game/sonic2/objects/bosses/HTZBossLavaBall.java",
            "com/openggf/game/sonic2/objects/bosses/Sonic2ARZBossInstance.java",
            "com/openggf/game/sonic2/objects/bosses/Sonic2CNZBossInstance.java",
            "com/openggf/game/sonic2/objects/bosses/Sonic2CPZBossInstance.java",
            "com/openggf/game/sonic2/objects/bosses/Sonic2DEZEggmanInstance.java",
            "com/openggf/game/sonic2/objects/bosses/Sonic2DeathEggRobotInstance.java",
            "com/openggf/game/sonic2/objects/bosses/Sonic2EHZBossInstance.java",
            "com/openggf/game/sonic2/objects/bosses/Sonic2HTZBossInstance.java",
            "com/openggf/game/sonic2/objects/bosses/Sonic2MechaSonicInstance.java",
            "com/openggf/game/sonic2/objects/bosses/Sonic2MCZBossInstance.java",
            "com/openggf/game/sonic2/objects/bosses/Sonic2MTZBossInstance.java",
            "com/openggf/game/sonic3k/objects/AizEndBossArmChild.java",
            "com/openggf/game/sonic3k/objects/AizEndBossBombChild.java",
            "com/openggf/game/sonic3k/objects/AizEndBossDebrisChild.java",
            "com/openggf/game/sonic3k/objects/AizEndBossFlameChild.java",
            "com/openggf/game/sonic3k/objects/AizEndBossPropellerChild.java",
            "com/openggf/game/sonic3k/objects/AizBattleshipInstance.java",
            "com/openggf/game/sonic3k/objects/AizBgTreeSpawnerInstance.java",
            "com/openggf/game/sonic3k/objects/AizEndBossInstance.java",
            "com/openggf/game/sonic3k/objects/AizShipBombInstance.java",
            "com/openggf/game/sonic3k/objects/AizMinibossBarrelShotChild.java",
            "com/openggf/game/sonic3k/objects/AizMinibossCutsceneInstance.java",
            "com/openggf/game/sonic3k/objects/AizMinibossFlameBarrelChild.java",
            "com/openggf/game/sonic3k/objects/AizMinibossInstance.java",
            "com/openggf/game/sonic3k/objects/CutsceneKnucklesRockChild.java",
            "com/openggf/game/sonic3k/objects/CnzBumperObjectInstance.java",
            "com/openggf/game/sonic3k/objects/MGZHeadTriggerObjectInstance.java",
            "com/openggf/game/sonic3k/objects/Sonic3kStarPostObjectInstance.java",
            "com/openggf/game/sonic3k/objects/badniks/AbstractS3kBadnikInstance.java",
            "com/openggf/game/sonic3k/objects/badniks/BlastoidBadnikInstance.java",
            "com/openggf/game/sonic3k/objects/badniks/BuggernautBadnikInstance.java",
            "com/openggf/game/sonic3k/objects/badniks/CaterkillerJrHeadInstance.java"
    );
    private static final Map<String, Integer> SONIC2_NATIVE_LEVEL_ART_RENDER_PATTERN_BUDGETS = Map.ofEntries(
            Map.entry("com/openggf/game/sonic2/objects/ARZPlatformObjectInstance.java", 1),
            Map.entry("com/openggf/game/sonic2/objects/ARZRotPformsObjectInstance.java", 1),
            Map.entry("com/openggf/game/sonic2/objects/CollapsingPlatformObjectInstance.java", 2),
            Map.entry("com/openggf/game/sonic2/objects/FallingPillarObjectInstance.java", 1),
            Map.entry("com/openggf/game/sonic2/objects/GrounderWallInstance.java", 1),
            Map.entry("com/openggf/game/sonic2/objects/LargeRotPformObjectInstance.java", 1),
            Map.entry("com/openggf/game/sonic2/objects/MCZBrickObjectInstance.java", 1),
            Map.entry("com/openggf/game/sonic2/objects/MCZRotPformsObjectInstance.java", 1),
            Map.entry("com/openggf/game/sonic2/objects/MTZLongPlatformObjectInstance.java", 1),
            Map.entry("com/openggf/game/sonic2/objects/MTZPlatformObjectInstance.java", 1),
            Map.entry("com/openggf/game/sonic2/objects/MTZTwinStompersObjectInstance.java", 1),
            Map.entry("com/openggf/game/sonic2/objects/RisingPillarObjectInstance.java", 2),
            Map.entry("com/openggf/game/sonic2/objects/SidewaysPformObjectInstance.java", 1),
            Map.entry("com/openggf/game/sonic2/objects/SlidingSpikesObjectInstance.java", 1),
            Map.entry("com/openggf/game/sonic2/objects/StomperObjectInstance.java", 1),
            Map.entry("com/openggf/game/sonic2/objects/SwingingPformObjectInstance.java", 1),
            Map.entry("com/openggf/game/sonic2/objects/SwingingPlatformObjectInstance.java", 1)
    );
    private static final List<SourceSignal> S3K_PATTERN_ATLAS_RANGE_SIGNALS = List.of(
            new SourceSignal(
                    "com/openggf/game/sonic3k/dataselect/S3kDataSelectRenderer.java",
                    "DATA_SELECT_PATTERN_BASE = PatternAtlasRange.MENU_AND_DATA_SELECT.base()"),
            new SourceSignal(
                    "com/openggf/game/sonic3k/titlecard/Sonic3kTitleCardManager.java",
                    "PATTERN_BASE = PatternAtlasRange.MENU_AND_DATA_SELECT.base()"),
            new SourceSignal(
                    "com/openggf/game/sonic3k/levelselect/Sonic3kLevelSelectConstants.java",
                    "PATTERN_BASE = PatternAtlasRange.MENU_AND_DATA_SELECT.base()"),
            new SourceSignal(
                    "com/openggf/game/sonic3k/objects/S3kResultsScreenObjectInstance.java",
                    "PATTERN_BASE = PatternAtlasRange.RESULTS_SCREENS.base()"),
            new SourceSignal(
                    "com/openggf/game/sonic3k/specialstage/S3kSpecialStageResultsScreen.java",
                    "PATTERN_BASE = PatternAtlasRange.SPECIAL_STAGE_RESULTS.base()"),
            new SourceSignal(
                    "com/openggf/game/sonic3k/titlescreen/Sonic3kTitleScreenDataLoader.java",
                    "ANIM_PATTERN_BASE = PatternAtlasRange.S3K_TITLE_SCREEN_ANIMATION.base()"),
            new SourceSignal(
                    "com/openggf/game/sonic3k/titlescreen/Sonic3kTitleScreenDataLoader.java",
                    "SPRITE_PATTERN_BASE = PatternAtlasRange.S3K_TITLE_SCREEN_SPRITES.base()")
    );

    private static final Set<String> GAME_ID_BRANCH_APPROVED_FILES = Set.of(
            "com/openggf/Engine.java",
            "com/openggf/GameLoop.java",
            "com/openggf/game/CrossGameFeatureProvider.java",
            "com/openggf/game/session/GameplayTeamBootstrap.java",
            "com/openggf/game/startup/DataSelectPresentationResolution.java",
            // Presentation routing: maps GameId -> human-readable rich-presence title.
            // No gameplay behavior branches; there is no per-game display-name owner to
            // route through, so this is the same enum->text routing surface as the sibling
            // menuName(GameMode) switch in the same class.
            "com/openggf/integration/presence/RuntimePresenceSnapshotProvider.java",
            // Composition-root precondition guards: validate that a module registered/booted
            // on the standalone (no-ROM mod) join point actually reports GameId.STANDALONE.
            // These are wiring-time invariant checks, not per-game runtime behavior branches.
            "com/openggf/mods/code/ModContext.java",
            "com/openggf/mods/code/ModRegistrationPlan.java",
            "com/openggf/tools/HeadlessGameBoot.java",
            // Native screen composition validates the base ROM identity once when
            // assembling its decorator; no gameplay behavior is selected by GameId.
            "com/openggf/game/sonic3k/competition/S3kCompetitionRuntimeModule.java",
            "com/openggf/game/sonic2/competition/Sonic2CompetitionRuntimeModule.java"
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
            "com/openggf/game/sonic3k/objects/Aiz2BossEndSequenceController.java",
            "com/openggf/game/sonic3k/objects/AizEmeraldScatterInstance.java",
            "com/openggf/game/sonic3k/objects/AizHollowTreeObjectInstance.java",
            // Cnz2CutsceneButtonInstance compares the cutscene Knuckles NPC's
            // object position (knuckles.getX/getY) against the button's proximity
            // box — object-to-object, not a player top-left hazard. Same pattern as
            // the allowlisted Hcz2CutsceneButtonInstance / S3kCutsceneButtonObjectInstance.
            "com/openggf/game/sonic3k/objects/Cnz2CutsceneButtonInstance.java",
            "com/openggf/game/sonic3k/objects/Hcz2CutsceneButtonInstance.java",
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
    private static final Pattern CROSS_GAME_DONOR_CONCRETE_REFERENCE = Pattern.compile(
            "\\b(?:Sonic1|Sonic2|Sonic3k|S3k)[A-Z][A-Za-z0-9_]*\\b"
                    + "|\\bcom\\.openggf\\.game\\.sonic(?:1|2|3k)(?:\\.[A-Za-z_][A-Za-z0-9_]*)+");
    private static final Pattern LOW_LEVEL_GAMEPLAY_SERVICE_LOOKUP = Pattern.compile(
            "\\bGameServices\\s*\\.\\s*"
                    + "(?:camera|cameraOrNull|level|levelOrNull|sprites|spritesOrNull|gameState|gameStateOrNull"
                    + "|timers|timersOrNull|fade|fadeOrNull|collision|collisionOrNull|water|waterOrNull"
                    + "|parallax|parallaxOrNull|zoneLayoutMutationPipeline|zoneLayoutMutationPipelineOrNull"
                    + "|zoneRuntimeRegistry|paletteOwnershipRegistry|animatedTileChannelGraph"
                    + "|specialRenderEffectRegistry|advancedRenderModeController)\\s*\\(");
    private static final Pattern GAME_MODULE_REGISTRY_MUTATION = Pattern.compile(
            "\\bGameModuleRegistry\\s*\\.\\s*(?:setCurrent|detectAndSetModule)\\s*\\(");
    private static final Pattern RAW_OBJECT_CHILD_SPAWN = Pattern.compile(
            "\\b(?:addDynamicObject(?:NextFrame|AfterCurrent(?:NextFrame)?)?|ObjectLifetimeOps\\s*\\.\\s*assignFindNextFreeChildSlot)\\s*\\(");
    private static final Pattern RAW_RENDER_PATTERN = Pattern.compile("\\.\\s*renderPattern\\s*\\(");
    private static final Set<String> REGISTRY_BACKED_PALETTE_CYCLE_CLASSES = Set.of(
            "IczCycle",
            "LbzCycle",
            "Lrz1Cycle",
            "Lrz2Cycle",
            "BpzCycle",
            "CgzCycle",
            "EmzCycle"
    );
    private static final Pattern DIRECT_CACHE_PALETTE_TEXTURE = Pattern.compile("\\.\\s*cachePaletteTexture\\s*\\(");
    private static final Pattern PLAYER_TOP_LEFT_ACCESS = Pattern.compile(
            "\\b(?:player|playable|entity|sonic|tails|knuckles)\\s*\\.\\s*get[XY]\\s*\\(\\s*\\)");
    private static final Pattern COORDINATE_CONTEXT = Pattern.compile(
            "(?i)\\b(rom|parity|x_pos|y_pos|distance|threshold|trigger|dx|dy|Math\\s*\\.\\s*abs|>=|<=|>|<)\\b");
    private static final Pattern RAW_LEVEL_MUTATOR = Pattern.compile(
            "\\b(?:map|level\\s*\\.\\s*getMap\\s*\\(\\s*\\)|block|chunk|blocks\\s*\\[[^]]+]|chunks\\s*\\[[^]]+])"
                    + "\\s*\\.\\s*(?:setValue|setChunkDesc|setPatternDesc|setSolidTileIndex|setSolidTileAltIndex)"
                    + "\\s*\\(");
    private static final Set<String> RAW_LEVEL_MUTATOR_ALLOWED_FILES = Set.of(
            "com/openggf/level/Map.java",
            "com/openggf/level/Block.java",
            "com/openggf/level/Chunk.java",
            "com/openggf/level/MutableLevel.java",
            "com/openggf/game/mutation/DirectLevelMutationSurface.java",
            "com/openggf/game/sonic3k/Sonic3kLevel.java"
    );

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

        assertNoViolations("GameId behavior branches must use typed rules or approved routing/composition code",
                violations);
    }

    @Test
    void crossGameFeatureProviderDoesNotNameConcreteSonicDonors() throws IOException {
        String source = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/game/CrossGameFeatureProvider.java")));
        Matcher matcher = CROSS_GAME_DONOR_CONCRETE_REFERENCE.matcher(source);
        List<String> violations = new ArrayList<>();
        while (matcher.find()) {
            violations.add("line " + lineNumberForOffset(source, matcher.start()) + ": " + matcher.group());
        }

        assertNoViolations(
                "CrossGameFeatureProvider should request donor behavior through shared provider/factory contracts",
                violations);
    }

    @Test
    void gameRulesPositionalConstructionStaysInsideOwnedFactories() throws IOException {
        Pattern positionalConstructor = Pattern.compile("\\bnew\\s+GameRules\\s*\\(");
        List<String> violations = new ArrayList<>();
        Path root = Path.of("src/main/java");
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path file : stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (relative.equals("com/openggf/game/rules/GameRules.java")
                        || relative.equals("com/openggf/game/rules/CrossGameRuleComposer.java")) {
                    continue;
                }
                String source = stripCommentsAndStrings(Files.readString(file));
                Matcher matcher = positionalConstructor.matcher(source);
                while (matcher.find()) {
                    violations.add(relative + ":" + lineNumberForOffset(source, matcher.start())
                            + " - use GameRules constants or CrossGameRuleComposer instead of positional construction");
                }
            }
        }

        assertNoViolations("GameRules positional construction should stay inside owned rule factories",
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
    void sonic1EmbeddedRuntimeDataExceptionsStayDocumentedAndBounded() throws IOException {
        String discrepancies = Files.readString(Path.of("docs", "status", "known-discrepancies.md"));
        List<String> violations = new ArrayList<>();
        if (!discrepancies.contains("Sonic 1 Embedded Runtime Data Ratchet")) {
            violations.add("docs/status/known-discrepancies.md must document the bounded Sonic 1 embedded runtime data debt");
        }

        List<EmbeddedRuntimeDataBudget> budgets = List.of(
                new EmbeddedRuntimeDataBudget(
                        "com/openggf/game/sonic1/Sonic1PaletteCycler.java",
                        "embedded Sonic 1 palette-cycle arrays",
                        Pattern.compile("private\\s+static\\s+final\\s+byte\\s*\\[\\s*]\\s+PAL_"),
                        0),
                new EmbeddedRuntimeDataBudget(
                        "com/openggf/game/sonic1/objects/Sonic1LZConveyorObjectInstance.java",
                        "embedded LZ conveyor waypoint tables",
                        Pattern.compile("private\\s+static\\s+final\\s+int\\s*\\[\\s*]\\s*\\[\\s*]\\s+PATH_"),
                        0),
                new EmbeddedRuntimeDataBudget(
                        "com/openggf/game/sonic1/objects/Sonic1LZConveyorObjectInstance.java",
                        "embedded LZ conveyor spawner switch tables",
                        Pattern.compile("case\\s+\\d+\\s+->\\s+new\\s+int\\s*\\[\\s*]\\s*\\[\\s*]"),
                        0),
                new EmbeddedRuntimeDataBudget(
                        "com/openggf/game/sonic1/objects/Sonic1SpinConveyorObjectInstance.java",
                        "embedded SBZ spin-conveyor waypoint tables",
                        Pattern.compile("private\\s+static\\s+final\\s+int\\s*\\[\\s*]\\s*\\[\\s*]\\s+PATH_"),
                        0),
                new EmbeddedRuntimeDataBudget(
                        "com/openggf/game/sonic1/objects/Sonic1SpinConveyorObjectInstance.java",
                        "embedded SBZ spin-conveyor spawner tables",
                        Pattern.compile("private\\s+static\\s+final\\s+int\\s*\\[\\s*]\\s*\\[\\s*]\\s+SPAWN_DATA_"),
                        0),
                new EmbeddedRuntimeDataBudget(
                        "com/openggf/game/sonic1/objects/Sonic1BridgeObjectInstance.java",
                        "embedded GHZ bridge bend tables",
                        Pattern.compile("private\\s+static\\s+final\\s+int\\s*\\[\\s*]\\s*\\[\\s*]\\s+BEND_DATA_"),
                        0),
                new EmbeddedRuntimeDataBudget(
                        "com/openggf/game/sonic1/Sonic1ObjectArtProvider.java",
                        "handwritten Sonic 1 object mapping pieces",
                        Pattern.compile("new\\s+SpriteMappingPiece\\s*\\("),
                        0),
                new EmbeddedRuntimeDataBudget(
                        "com/openggf/game/sonic1/objects/bosses/Sonic1BossMappings.java",
                        "handwritten Sonic 1 boss mapping pieces",
                        Pattern.compile("new\\s+SpriteMappingPiece\\s*\\("),
                        0)
        );

        for (EmbeddedRuntimeDataBudget budget : budgets) {
            Path sourcePath = SRC_MAIN.resolve(budget.relativePath());
            if (!Files.exists(sourcePath) && budget.expectedCount() == 0) {
                continue;
            }
            String source = Files.readString(sourcePath);
            int actual = countMatches(budget.pattern(), source);
            if (actual != budget.expectedCount()) {
                violations.add(budget.relativePath() + " has " + actual + " "
                        + budget.description() + "; expected exactly " + budget.expectedCount()
                        + ". ROM-back this data before adding new embedded runtime tables.");
            }
        }

        assertNoViolations("Sonic 1 embedded runtime data exceptions must stay documented and bounded",
                violations);
    }

    @Test
    void hardwareTimingAuthorityExceptionStaysDocumentedAndAgentGuidanceStaysMirrored() throws IOException {
        Path agents = Path.of("AGENTS.md");
        Path claude = Path.of("CLAUDE.md");
        byte[] agentsBytes = Files.readAllBytes(agents);
        assertArrayEquals(agentsBytes, Files.readAllBytes(claude),
                "AGENTS.md and CLAUDE.md must remain byte-identical");

        String agentGuidance = new String(agentsBytes, StandardCharsets.UTF_8);
        assertTrue(agentGuidance.contains("Trace data is comparison-only by default."),
                "agent guidance must retain the comparison-only trace rule");
        assertTrue(agentGuidance.contains("dedicated hardware-timing input contract"),
                "agent guidance must retain the dedicated hardware-timing exception");
        assertTrue(agentGuidance.contains("It may release only the readiness of a matching, prepared, production-submitted"),
                "agent guidance must retain the bounded timing-release rule");

        String discrepancies = Files.readString(Path.of("docs", "status", "known-discrepancies.md"));
        assertTrue(discrepancies.contains("## Hardware-Timing Replay Input Exception"),
                "known discrepancies must retain the hardware-timing exception");
        assertTrue(discrepancies.contains("Physics CSV and auxiliary events remain comparison-only."),
                "known discrepancies must retain the physics/aux comparison-only boundary");
    }

    @Test
    void engineDoesNotGainNewLargeMethods() throws IOException {
        SourceFile engine = SourceFile.read(SRC_MAIN.resolve(ENGINE_PATH));
        List<String> largeMethods = new ArrayList<>();
        for (MethodSpan method : engine.methods()) {
            if (method.lineCount() >= ENGINE_LARGE_METHOD_THRESHOLD) {
                largeMethods.add(method.identity());
            }
        }
        largeMethods.sort(String::compareTo);
        assertEquals(ENGINE_LARGE_METHOD_OWNERS, largeMethods,
                "Engine large-method allowances identify owners, not interchangeable slots. "
                        + "Extract new responsibilities; prune an owner once it falls below "
                        + ENGINE_LARGE_METHOD_THRESHOLD + " lines.");
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
    void sharedSpriteCodeDoesNotGainConcreteSonicDependencies() throws IOException {
        List<String> references = new ArrayList<>();
        for (Path file : productionFilesUnder("com/openggf/sprites")) {
            String relative = relative(file);
            references.addAll(scanPattern(relative,
                    Files.readString(file),
                    CONCRETE_SONIC_REFERENCE));
        }

        assertNoViolations(
                "Shared sprite code must use GameRules, providers, or shared contracts instead of concrete Sonic packages",
                references);
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
            if (!budget.owns(method)) {
                violations.add(budget.relativePath() + "#" + budget.methodName()
                        + " moved to " + method.identity() + "; review and retarget the owner explicitly");
                continue;
            }
            if (method.lineCount() != budget.maxLines()) {
                violations.add(budget.relativePath() + "#" + budget.methodName()
                        + " is " + method.lineCount() + " lines; budget is "
                        + budget.maxLines()
                        + ". Extract mode/render work before growth; lower stale budgets after extraction.");
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
    void runtimeProductionCodeDoesNotMutateGameModuleRegistry() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            String relative = relative(file);
            if (isApprovedGameModuleRegistryMutationFile(relative)) {
                continue;
            }
            violations.addAll(scanPattern(relative,
                    Files.readString(file),
                    GAME_MODULE_REGISTRY_MUTATION));
        }

        assertNoViolations("Active gameplay modules are owned by WorldSession; runtime code must not mutate "
                + "GameModuleRegistry bootstrap compatibility state", violations);
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
    void levelManagerDelegatesPlayableArtInitializationToNamedCollaborator() throws IOException {
        String source = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/level/LevelManager.java")));
        List<String> forbiddenSignals = List.of(
                "PlayerSpriteRenderer",
                "SpindashDustController",
                "TailsTailsController",
                "initPlayerSpriteArt(",
                "initSpindashDust(",
                "initTailsTails(",
                "initSuperState(",
                "createSidekickPaletteContext(",
                "propagateSidekickPaletteContext(");
        List<String> violations = forbiddenSignals.stream()
                .filter(source::contains)
                .toList();
        assertTrue(violations.isEmpty(),
                "LevelManager should coordinate playable art through LevelPlayableArtInitializer, "
                        + "not own renderer/controller setup directly. Found: " + violations);
    }

    @Test
    void levelManagerDelegatesDirtyRegionDispatchToNamedCollaborator() throws IOException {
        String source = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/level/LevelManager.java")));
        List<String> forbiddenSignals = List.of(
                "consumeDirtyPatterns(",
                "consumeDirtyBlocks(",
                "consumeDirtyMapCells(",
                "consumeDirtySolidTiles(",
                "consumeObjectsDirty(",
                "consumeRingsDirty(",
                "effects.hasDirtyPatterns(",
                "effects.objectResyncRequired(",
                "effects.ringResyncRequired(");
        List<String> violations = forbiddenSignals.stream()
                .filter(source::contains)
                .toList();
        assertTrue(violations.isEmpty(),
                "LevelManager should delegate dirty-region and mutation-effect dispatch "
                        + "to LevelDirtyRegionDispatcher. Found: " + violations);
    }

    @Test
    void levelManagerDelegatesWaterLifecycleToNamedCollaborator() throws IOException {
        String source = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/level/LevelManager.java")));
        List<String> forbiddenSignals = List.of(
                "WaterDataProvider",
                "loadForLevelFromProvider(",
                "waterSystem.loadForLevel(",
                "waterSystem.updateDynamic(",
                "getGameplayWaterLevelY(",
                "updateWaterStateObjectControlled(",
                "zoneFeatureProvider.shouldSuppressUnderwaterPalette(");
        List<String> violations = forbiddenSignals.stream()
                .filter(source::contains)
                .toList();
        assertTrue(violations.isEmpty(),
                "LevelManager should delegate water load, movement, and playable-water state "
                        + "to LevelWaterCoordinator. Found: " + violations);
    }

    @Test
    void levelManagerDelegatesCheckpointStateToNamedCollaborator() throws IOException {
        String source = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/level/LevelManager.java")));
        List<String> forbiddenSignals = List.of(
                "checkpointState",
                "createRespawnState(",
                "restoreFromSaved(",
                "saveWaterState(",
                "saveS3kRuntimeState(",
                "saveSolidBits(",
                "restoreEventRoutineState(");
        List<String> violations = forbiddenSignals.stream()
                .filter(source::contains)
                .toList();
        assertTrue(violations.isEmpty(),
                "LevelManager should delegate checkpoint storage and restore details "
                        + "to LevelCheckpointCoordinator. Found: " + violations);
    }

    @Test
    void levelManagerDelegatesTransientOscillationAndRespawnHandoffs() throws IOException {
        String levelManager = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/level/LevelManager.java")));
        String gameLoop = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/GameLoop.java")));
        String checkpointCoordinator = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/level/LevelCheckpointCoordinator.java")));

        assertTrue(!levelManager.contains("suppressGlobalOscillationForTitleCardPass"),
                "Title-card oscillator suppression belongs to OscillationManager");
        String titleCardLifecycle = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/GameLoopTitleCardLifecycle.java")));
        assertTrue(gameLoop.contains("OscillationManager.suppressNextFrames(1)")
                        || titleCardLifecycle.contains("OscillationManager.suppressNextFrames(1)"),
                "The GameLoop title-card path should arm the oscillator-owned one-shot suppression directly");
        String frameUpdater = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/level/LevelFrameRuntimeUpdater.java")));
        assertTrue(frameUpdater.indexOf("consumeSuppressedUpdate(")
                        < frameUpdater.indexOf("shouldAdvanceGlobalOscillation("),
                "Oscillator-owned suppression must be consumed before zone advance policy");
        assertTrue(!levelManager.contains("persistentRespawnStateForNextObjectReset"),
                "Pending bonus-return respawn ownership belongs to LevelCheckpointCoordinator");
        assertTrue(checkpointCoordinator.contains("pendingPersistentRespawn"),
                "LevelCheckpointCoordinator should own the pending respawn handoff");
        assertTrue(checkpointCoordinator.contains("consumePersistentRespawn()"),
                "LevelCheckpointCoordinator should expose one-shot consumption for object reset");
    }

    @Test
    void levelManagerDelegatesActTransitionExecutionToNamedCollaborator() throws IOException {
        Path path = SRC_MAIN.resolve("com/openggf/level/LevelManager.java");
        SourceFile sourceFile = SourceFile.read(path);
        MethodSpan method = sourceFile.methodNamed("executeActTransition");
        assertTrue(method != null, "LevelManager should retain the public executeActTransition facade");
        assertTrue(method.lineCount() <= 4,
                "LevelManager.executeActTransition should delegate to LevelActTransitionExecutor");

        String source = stripCommentsAndStrings(String.join("\n",
                sourceFile.lines().subList(method.startLine() - 1, method.endLine())));
        List<String> forbiddenSignals = List.of(
                "reloadStandaloneArtForActTransition(",
                "snapshotPersistentTransitionOccupants(",
                "preserveLevelGamestate()",
                "showInLevelTitleCard()");
        List<String> violations = forbiddenSignals.stream()
                .filter(source::contains)
                .toList();
        assertTrue(violations.isEmpty(),
                "LevelManager.executeActTransition should delegate act-transition reload choreography "
                        + "to LevelActTransitionExecutor. Found: " + violations);
    }

    @Test
    void everyS3kSeamlessTransitionBuilderDeclaresRuntimeArtAdmissionPolicy()
            throws IOException {
        Map<String, String> eventFiles = Map.of(
                "Sonic3kAIZEvents.java", "PRESERVE_CURRENT",
                "Sonic3kCNZEvents.java", "TITLE_OWNER",
                "Sonic3kICZEvents.java", "RESOURCE_HANDOFF_OWNER",
                "Sonic3kLBZEvents.java", "TITLE_OWNER",
                "Sonic3kMGZEvents.java", "TITLE_OWNER",
                "Sonic3kHCZEvents.java", "TITLE_OWNER",
                "Sonic3kMHZEvents.java", "TITLE_OWNER");
        for (var entry : eventFiles.entrySet()) {
            String eventFile = entry.getKey();
            String source = stripCommentsAndStrings(Files.readString(SRC_MAIN.resolve(
                    "com/openggf/game/sonic3k/events/" + eventFile)));
            assertS3kBuilderPolicies(eventFile, source, entry.getValue());
        }
    }

    @Test
    void s3kPolicyGuardRejectsDuplicateAndMissingPoliciesOnSeparateBuilders() {
        String duplicate = """
                SeamlessLevelTransitionRequest.builder(TYPE)
                    .runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.IMMEDIATE)
                    .runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.IMMEDIATE)
                    .build();
                """;
        String missing = """
                SeamlessLevelTransitionRequest.builder(TYPE)
                    .build();
                """;

        assertThrows(AssertionError.class, () ->
                assertS3kBuilderPolicies(
                        "DuplicatePolicyEvents.java", duplicate, "IMMEDIATE"));
        assertThrows(AssertionError.class, () ->
                assertS3kBuilderPolicies(
                        "MissingPolicyEvents.java", missing, "IMMEDIATE"));
    }

    private static void assertS3kBuilderPolicies(
            String eventFile, String source, String expectedPolicy) {
        List<String> builderChains = seamlessTransitionBuilderChains(source);
        assertFalse(builderChains.isEmpty(),
                eventFile + " must retain its transition builder");
        for (int index = 0; index < builderChains.size(); index++) {
            String chain = builderChains.get(index);
            int policies = chain.split(
                    "\\.runtimeArtAdmissionPolicy\\(", -1).length - 1;
            assertEquals(1, policies,
                    eventFile + " builder " + index
                            + " must declare exactly one admission policy");
            assertTrue(chain.contains(
                            "RuntimeArtAdmissionPolicy." + expectedPolicy),
                    eventFile + " builder " + index
                            + " must retain its reviewed admission policy");
        }
    }

    private static List<String> seamlessTransitionBuilderChains(String source) {
        String marker = "SeamlessLevelTransitionRequest.builder(";
        String terminator = ".build()";
        List<String> chains = new ArrayList<>();
        int cursor = 0;
        while (true) {
            int start = source.indexOf(marker, cursor);
            if (start < 0) {
                return chains;
            }
            int end = source.indexOf(terminator, start + marker.length());
            if (end < 0) {
                throw new IllegalStateException(
                        "unterminated seamless transition builder at offset " + start);
            }
            chains.add(source.substring(start, end + terminator.length()));
            cursor = end + terminator.length();
        }
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
        int lineCount = objectManager.effectiveSourceLineCount();
        assertTrue(lineCount <= OBJECT_MANAGER_MAX_EFFECTIVE_SOURCE_LINES,
                "ObjectManager.java is " + lineCount + " effective source lines; budget is "
                        + OBJECT_MANAGER_MAX_EFFECTIVE_SOURCE_LINES
                        + " after extracting ObjectPlacementController, ObjectTouchResponseController, "
                        + "and ObjectSolidContactController. Keep new placement, touch-response, "
                        + "and solid-contact logic in those collaborators.");
    }

    @Test
    void objectManagerDelegatesPlacementLifecycleAndVerticalFiltering() throws IOException {
        String manager = Files.readString(SRC_MAIN.resolve(OBJECT_MANAGER_PATH));

        assertTrue(manager.contains("placement.reallocateToFirstFreeDynamicSlot("));
        assertTrue(manager.contains("placement.releaseSpawnForRespawn("));
        assertTrue(manager.contains("placement.dispatchDestroyRemoveFromActive("));
        assertTrue(manager.contains("placement.isSpawnVerticallyEligibleForTwoAxisYPass("));
        assertTrue(manager.contains("ObjectPlacementController.isNonCounterSpawnVerticallyEligible("));
        assertTrue(manager.contains("solidContacts.captureExecStartPlayerCentreY("));
    }

    @Test
    void releaseCriticalLargeClassesDoNotGrowWithoutExtraction() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Integer> budget
                : RELEASE_CRITICAL_CLASS_EFFECTIVE_SOURCE_LINE_BUDGETS.entrySet()) {
            String relative = budget.getKey();
            int lineCount = SourceFile.read(SRC_MAIN.resolve(relative)).effectiveSourceLineCount();
            if (lineCount > budget.getValue()) {
                violations.add(relative + " is " + lineCount + " effective source lines; budget is "
                        + budget.getValue()
                        + ". Extract focused collaborators before growing this release-critical file.");
            }
        }

        assertNoViolations("Release-critical large class size ratchet failed", violations);
    }

    @Test
    void playableSpriteDelegatesFrameStateAnimationAndControlledContactBehavior() throws IOException {
        String sprite = Files.readString(SRC_MAIN.resolve(
                "com/openggf/sprites/playable/AbstractPlayableSprite.java"));
        String controller = Files.readString(SRC_MAIN.resolve(
                "com/openggf/sprites/playable/PlayableSpriteController.java"));

        assertTrue(sprite.contains("controller.captureFrameStartState();"));
        assertTrue(sprite.contains("controller.publishRawAnimation(CanonicalAnimation.HURT);"));
        assertTrue(sprite.contains("controller.notifyObjectControlledSolidContact(candidate, contact);"));
        assertTrue(controller.contains("void captureFrameStartState()"));
        assertTrue(controller.contains("void publishRawAnimation(CanonicalAnimation animation)"));
        assertTrue(controller.contains("void notifyObjectControlledSolidContact("));
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
    void sonic3kObjectRegistryDocsDoNotClaimPlaceholderOnlyCoverage() throws IOException {
        String source = Files.readString(SRC_MAIN.resolve(
                "com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java"));

        assertTrue(!source.contains("Currently all objects use")
                        && !source.contains("all objects use PlaceholderObjectInstance"),
                "Sonic3kObjectRegistry docs must not claim placeholder-only coverage after concrete S3K factories exist");
    }

    @Test
    void sonic3kConstantsDoNotInferLockOnHalfFromZoneEra() throws IOException {
        String source = Files.readString(SRC_MAIN.resolve(
                "com/openggf/game/sonic3k/constants/Sonic3kConstants.java"));

        assertTrue(!source.contains("S3 half (>= 0x200000) addresses for S3-era zones")
                        && !source.contains("S&K half for S&K-era zones"),
                "S3K ROM address comments must document the verified source half per table, "
                + "not infer S3/S&K half from zone era");
    }

    @Test
    void sonic3kBossDocsDoNotDescribeImplementedHczEndBossAsStubbed() throws IOException {
        String source = Files.readString(SRC_MAIN.resolve(
                "com/openggf/game/sonic3k/objects/bosses/HczEndBossInstance.java"));

        assertTrue(!source.contains("routines and defeat logic are stubbed for later tasks."),
                "HczEndBossInstance docs must not describe implemented movement/defeat logic as stubbed");
        assertTrue(!source.contains("Child spawning (placeholders for Tasks 3-5)"),
                "HczEndBossInstance docs must not describe implemented child spawning as placeholder-only");
        assertTrue(!source.contains("Children will be spawned when their classes are created:"),
                "HczEndBossInstance docs must not claim existing child classes are still pending");
    }

    @Test
    void sonic3kMgzDocsDoNotDescribeImplementedEndBossAsStubbed() throws IOException {
        String source = Files.readString(SRC_MAIN.resolve(
                "com/openggf/game/sonic3k/events/Sonic3kMGZEvents.java"));

        assertTrue(!source.contains("(currently unimplemented) end-of-act boss"),
                "Sonic3kMGZEvents docs must not describe implemented MGZ end-boss handoff as unimplemented");
        assertTrue(!source.contains("for that stubbed step."),
                "Sonic3kMGZEvents docs must not describe implemented MGZ boss handoff as stubbed");
        assertTrue(!source.contains("end boss not yet implemented"),
                "Sonic3kMGZEvents logs must not describe implemented MGZ end-boss path as unimplemented");
    }

    @Test
    void sonic3kMonitorDocsDoNotClaimPlayerCharacterSystemIsMissing() throws IOException {
        String source = Files.readString(SRC_MAIN.resolve(
                "com/openggf/game/sonic3k/objects/Sonic3kMonitorObjectInstance.java"));

        assertTrue(!source.contains("PlayerCharacter system (not yet implemented)"),
                "Sonic3kMonitorObjectInstance docs must not claim the PlayerCharacter system is missing");
    }

    @Test
    void sonic3kCnzDocsDoNotClaimImplementedObjectsAreStillTaskScaffolds() throws IOException {
        List<String> files = List.of(
                "com/openggf/game/sonic3k/constants/Sonic3kConstants.java",
                "com/openggf/game/sonic3k/constants/Sonic3kObjectIds.java",
                "com/openggf/game/sonic3k/Sonic3k.java",
                "com/openggf/game/sonic3k/Sonic3kGameModule.java",
                "com/openggf/game/sonic3k/Sonic3kObjectArt.java",
                "com/openggf/game/sonic3k/Sonic3kObjectArtProvider.java"
        );
        List<String> stalePhrases = List.of(
                "Task 6 infrastructure only",
                "behavior work, Task 6 only needs",
                "Task 6 only needs the renderer",
                "object behavior for Tasks 7 and 8",
                "actual miniboss object implementation remains deferred to Task 7",
                "Task 8 can attach real behavior later",
                "Controller-only scaffold for Task 1",
                "Phase 1 supports terrain, collision",
                "Phase 1: terrain/collision only",
                "Task 1 visual scaffold",
                "Cannon.bin data lives in the S&K half"
        );
        List<String> violations = new ArrayList<>();

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN.resolve(relative));
            for (String stalePhrase : stalePhrases) {
                if (source.contains(stalePhrase)) {
                    violations.add(relative + " - " + stalePhrase);
                }
            }
        }

        assertNoViolations("S3K CNZ/module docs must not describe implemented objects as pending task scaffolds",
                violations);
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
    void collisionSystemDoesNotContainSbzCoordinateWindows() throws IOException {
        String relative = "com/openggf/physics/CollisionSystem.java";
        String stripped = stripCommentsAndStrings(Files.readString(SRC_MAIN.resolve(relative)));
        List<String> violations = new ArrayList<>();
        for (String forbidden : List.of(
                "isS1Sbz1RightWallLipWindow",
                "s1Sbz1FloorLipSlopeResult",
                "0x1694",
                "0x1720",
                "0x172B",
                "0x029C",
                "0x02AC")) {
            if (stripped.contains(forbidden)) {
                violations.add(relative + " - " + forbidden);
            }
        }

        assertNoViolations("Shared collision must use sensor/ROM-state predicates, not SBZ coordinate windows",
                violations);
    }

    @Test
    void migratedObjectChildSpawnsStayOnManagedHelpers() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String relative : OBJECT_CHILD_SPAWN_MIGRATED_FILES) {
            violations.addAll(scanPattern(relative,
                    Files.readString(SRC_MAIN.resolve(relative)),
                    RAW_OBJECT_CHILD_SPAWN));
        }

        assertNoViolations("Migrated object child spawns must use spawnChild()/spawnFreeChild() helpers",
                violations);
    }

    @Test
    void cnzPrizeObjectsUseBaseDestroyedState() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String relative : List.of(
                "com/openggf/game/sonic2/objects/BombPrizeObjectInstance.java",
                "com/openggf/game/sonic2/objects/RingPrizeObjectInstance.java")) {
            String source = stripCommentsAndStrings(Files.readString(SRC_MAIN.resolve(relative)));
            if (source.contains("boolean destroyed")) {
                violations.add(relative + " declares a local destroyed field instead of using AbstractObjectInstance");
            }
            if (source.contains("boolean isDestroyed()")) {
                violations.add(relative + " overrides isDestroyed() instead of using AbstractObjectInstance");
            }
        }

        assertNoViolations("CNZ prize objects must use the base object lifecycle destroyed state", violations);
    }

    @Test
    void s3kUiPatternBasesStayCentralizedInPatternAtlasRange() throws IOException {
        List<String> violations = new ArrayList<>();
        String ranges = Files.readString(SRC_MAIN.resolve("com/openggf/graphics/PatternAtlasRange.java"));
        for (String rangeName : List.of(
                "MENU_AND_DATA_SELECT",
                "RESULTS_SCREENS",
                "SPECIAL_STAGE_RESULTS",
                "S3K_TITLE_SCREEN_ANIMATION",
                "S3K_TITLE_SCREEN_SPRITES")) {
            if (!ranges.contains(rangeName)) {
                violations.add("PatternAtlasRange is missing " + rangeName);
            }
        }
        for (SourceSignal signal : S3K_PATTERN_ATLAS_RANGE_SIGNALS) {
            String source = Files.readString(SRC_MAIN.resolve(signal.relativePath()));
            if (!source.contains(signal.requiredText())) {
                violations.add(signal.relativePath() + " must allocate its virtual pattern base through "
                        + signal.requiredText());
            }
        }

        assertNoViolations("S3K UI/result virtual pattern bases must be owned by PatternAtlasRange",
                violations);
    }

    @Test
    void sonic3kHczStandaloneMappingsStayRomBacked() throws IOException {
        String provider = Files.readString(SRC_MAIN.resolve("com/openggf/game/sonic3k/Sonic3kObjectArtProvider.java"));
        String constants = Files.readString(SRC_MAIN.resolve(
                "com/openggf/game/sonic3k/constants/Sonic3kConstants.java"));
        String pitfalls = Files.readString(Path.of("docs", "architecture", "implementation-pitfalls.md"));
        List<String> violations = new ArrayList<>();
        for (String constant : List.of(
                "MAP_HCZ_MINIBOSS_ADDR",
                "MAP_HCZ_END_BOSS_ADDR",
                "MAP_HCZ_WATERWALL_ADDR")) {
            if (!provider.contains("Sonic3kConstants." + constant)) {
                violations.add("Sonic3kObjectArtProvider must load " + constant + " through ROM-backed mappings");
            }
            if (!constants.contains("public static final int " + constant)) {
                violations.add("Sonic3kConstants must expose ROM address " + constant);
            }
            if (!pitfalls.contains(constant)) {
                violations.add("docs/architecture/implementation-pitfalls.md must document " + constant);
            }
        }

        assertNoViolations("HCZ standalone object mappings must stay ROM-backed, not handwritten runtime data",
                violations);
    }

    @Test
    void enginePostFadeRenderingStaysDiagnosticOnly() throws IOException {
        String source = Files.readString(SRC_MAIN.resolve(ENGINE_PATH));
        int fadeCall = source.indexOf("uiPipeline.renderFadePass();");
        int screenshotMarker = source.indexOf("// F12 screenshot capture", fadeCall);
        assertTrue(fadeCall >= 0 && screenshotMarker > fadeCall,
                "Engine.display() must keep a recognizable post-fade block before screenshot capture");

        String postFadeBlock = source.substring(fadeCall, screenshotMarker);
        List<String> violations = new ArrayList<>();
        for (String forbidden : List.of(
                "drawActiveLevelTitleCardOverlay(",
                "levelManager.draw(",
                "levelManager.drawWithSpritePriority(")) {
            if (postFadeBlock.contains(forbidden)) {
                violations.add("post-fade block contains general gameplay rendering call " + forbidden);
            }
        }
        if (!postFadeBlock.contains("Post-fade diagnostic overlays")) {
            violations.add("post-fade block must be explicitly labeled as diagnostic-only");
        }
        if (!postFadeBlock.contains("shouldRenderDemoSpritesOverFade()")
                || !postFadeBlock.contains("levelManager.renderSpriteObjectPass(spriteManager, true)")) {
            violations.add("credits-demo sprite-over-fade exception must remain explicit and guarded");
        }

        assertNoViolations("Engine post-fade rendering must stay diagnostic-only except the credits-demo pass",
                violations);
    }

    @Test
    void mgz2QuakeChunkS3HalfAddressIsReviewedAndDocumented() throws IOException {
        String source = Files.readString(SRC_MAIN.resolve(
                "com/openggf/game/sonic3k/events/Sonic3kMGZEvents.java"));
        String discrepancies = Files.readString(Path.of("docs", "S3K_KNOWN_DISCREPANCIES.md"));
        List<String> violations = new ArrayList<>();
        if (!source.contains("MGZ_QUAKE_CHUNK_ROM_ADDR = 0x3CBBB4")) {
            violations.add("Sonic3kMGZEvents.MGZ_QUAKE_CHUNK_ROM_ADDR must stay at reviewed ROM address 0x3CBBB4");
        }
        if (!discrepancies.contains("MGZ2 Quake Chunk Source Address")
                || !discrepancies.contains("0x3CBBB4")) {
            violations.add("docs/S3K_KNOWN_DISCREPANCIES.md must document the MGZ2 quake chunk address exception");
        }

        assertNoViolations("MGZ2 quake chunk S3-half address must stay reviewed and documented", violations);
    }

    @Test
    void sonic2ObjectRawPatternDrawsStayNativeLevelArtOnly() throws IOException {
        List<String> violations = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Path file : productionFilesUnder("com/openggf/game/sonic2/objects")) {
            String relative = relative(file);
            String stripped = stripCommentsAndStrings(Files.readString(file));
            int actual = countMatches(RAW_RENDER_PATTERN, stripped);
            if (actual == 0) {
                continue;
            }
            seen.add(relative);
            Integer expected = SONIC2_NATIVE_LEVEL_ART_RENDER_PATTERN_BUDGETS.get(relative);
            if (expected == null) {
                violations.add(relative + " has " + actual
                        + " raw renderPattern calls. Use renderPatternWithId for virtual pattern IDs, "
                        + "or document and budget native level-art rendering explicitly.");
            } else if (actual != expected) {
                violations.add(relative + " has " + actual
                        + " raw renderPattern calls; expected exactly " + expected
                        + " reviewed native level-art calls.");
            }
        }
        for (String relative : SONIC2_NATIVE_LEVEL_ART_RENDER_PATTERN_BUDGETS.keySet()) {
            if (!seen.contains(relative)) {
                violations.add(relative + " no longer has its reviewed native level-art renderPattern call; "
                        + "remove it from the guard budget.");
            }
        }

        assertNoViolations("Sonic 2 object raw renderPattern calls must stay bounded to native level art",
                violations);
    }

    @Test
    void registryBackedS3kPaletteCyclesDoNotDirectlyUploadTextures() throws IOException {
        String relative = "com/openggf/game/sonic3k/Sonic3kPaletteCycler.java";
        String source = Files.readString(SRC_MAIN.resolve(relative));
        String stripped = stripCommentsAndStrings(source);
        List<String> violations = new ArrayList<>();
        for (String className : REGISTRY_BACKED_PALETTE_CYCLE_CLASSES) {
            ClassBody body = classBody(stripped, className);
            Matcher matcher = DIRECT_CACHE_PALETTE_TEXTURE.matcher(body.source());
            while (matcher.find()) {
                violations.add(relative + ":" + lineNumberForOffset(stripped, body.startOffset() + matcher.start())
                        + " - " + className + " calls cachePaletteTexture directly after ownership migration");
            }
        }

        assertNoViolations("Registry-backed S3K palette cycles must let PaletteOwnershipRegistry own GPU upload",
                violations);
    }

    @Test
    void mgzPostBossPaletteFadeUsesRomRowsAndOwnershipRegistry() throws IOException {
        String relative = "com/openggf/game/sonic3k/objects/Mgz2PostBossPaletteFadeController.java";
        String stripped = stripCommentsAndStrings(Files.readString(SRC_MAIN.resolve(relative)));

        assertTrue(!stripped.contains("updatePalette("),
                "MGZ2 post-boss fade should not bypass PaletteOwnershipRegistry with updatePalette(...)");
        assertTrue(stripped.contains("S3kPaletteWriteSupport.applyLine("),
                "MGZ2 post-boss fade should route line writes through S3kPaletteWriteSupport");
        assertTrue(stripped.contains("PAL_MGZ_FADE_CNZ_ADDR"),
                "MGZ2 post-boss fade rows should be loaded from the ROM-backed Pal_MGZFadeCNZ constant");
        assertTrue(!stripped.contains("int[][]"),
                "MGZ2 post-boss fade should not embed Pal_MGZFadeCNZ palette rows in source");
    }

    @Test
    void mgzTopPlatformPlayerGrabStateHasExplicitRewindCoverage() throws IOException {
        String platform = Files.readString(SRC_MAIN.resolve(
                "com/openggf/game/sonic3k/objects/MGZTopPlatformObjectInstance.java"));
        String codecs = Files.readString(SRC_MAIN.resolve(
                "com/openggf/game/rewind/schema/RewindCodecs.java"));

        assertTrue(platform.contains("PlayerGrabState implements RewindStateful<PlayerGrabState.Snapshot>"),
                "MGZ top platform per-player grab helper must expose explicit rewind state");
        assertTrue(platform.contains("private record Snapshot("),
                "MGZ top platform grab state must snapshot every mutable per-player scalar");
        assertTrue(codecs.contains("RewindStateful.class.isAssignableFrom(type)"),
                "rewind collection/map codecs must accept concrete RewindStateful values");
    }

    @Test
    void productionGameplayCodeDoesNotBypassLevelMutationSurfaceWithRawLevelMutators() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            String relative = relative(file);
            if (RAW_LEVEL_MUTATOR_ALLOWED_FILES.contains(relative)) {
                continue;
            }
            String stripped = stripCommentsAndStrings(Files.readString(file));
            Matcher matcher = RAW_LEVEL_MUTATOR.matcher(stripped);
            while (matcher.find()) {
                violations.add(relative + ":" + lineNumberForOffset(stripped, matcher.start())
                        + " - raw level mutator " + matcher.group().replaceAll("\\s+", ""));
            }
        }

        assertNoViolations("Gameplay/runtime code must route level edits through MutableLevel or "
                + "LevelMutationSurface so rewind copy-on-write isolation cannot be bypassed", violations);
    }

    @Test
    void specialStageManagerRoutesPaletteWritesThroughLocalPaletteModel() throws IOException {
        String relative = "com/openggf/game/sonic3k/specialstage/Sonic3kSpecialStageManager.java";
        String source = Files.readString(SRC_MAIN.resolve(relative));
        String stripped = stripCommentsAndStrings(source);
        List<String> violations = new ArrayList<>();

        Matcher uploadMatcher = DIRECT_CACHE_PALETTE_TEXTURE.matcher(stripped);
        while (uploadMatcher.find()) {
            violations.add(relative + ":" + lineNumberForOffset(stripped, uploadMatcher.start())
                    + " - route special-stage palette uploads through the local palette helper");
        }

        Pattern directColorArray = Pattern.compile("\\.\\s*colors\\s*\\[");
        Matcher colorMatcher = directColorArray.matcher(stripped);
        while (colorMatcher.find()) {
            violations.add(relative + ":" + lineNumberForOffset(stripped, colorMatcher.start())
                    + " - keep special-stage color mutation inside Sonic3kSpecialStagePalette");
        }

        assertNoViolations("S3K special-stage manager should not own direct palette mutation/upload",
                violations);
    }

    @Test
    void specialStageResultsScreenUsesSharedPaletteDecoder() throws IOException {
        String relative = "com/openggf/game/sonic3k/specialstage/S3kSpecialStageResultsScreen.java";
        String source = Files.readString(SRC_MAIN.resolve(relative));
        String stripped = stripCommentsAndStrings(source);

        assertTrue(!stripped.contains(".setColor("),
                "S3K special-stage results palette should use the shared Sega palette decoder");
        assertTrue(stripped.contains("PaletteLoader.fromBytes("),
                "S3K special-stage results palette should decode Pal_Results through PaletteLoader");
        assertTrue(!stripped.contains(".cachePaletteTexture("),
                "S3K special-stage results palette upload should use Sonic3kSpecialStagePaletteUploader");
    }

    @Test
    void specialStageResultsAudioFailuresAreLogged() throws IOException {
        String relative = "com/openggf/game/sonic3k/specialstage/S3kSpecialStageResultsScreen.java";
        String source = Files.readString(SRC_MAIN.resolve(relative));

        assertTrue(!source.contains("catch (Exception e) { /* ignore */ }"),
                "S3K special-stage results audio helper failures should be logged, not swallowed");
        assertTrue(source.contains("LOG.log("),
                "S3K special-stage results screen should report audio helper failures through its logger");
    }

    @Test
    void sonic3kSpecialStageManagerSupportsSuperEmeraldArtAndState() throws IOException {
        String relative = "com/openggf/game/sonic3k/specialstage/Sonic3kSpecialStageManager.java";
        String source = Files.readString(SRC_MAIN.resolve(relative));
        String stripped = stripCommentsAndStrings(source);

        assertTrue(stripped.contains("getSuperEmeraldArt()"),
                "S3K special-stage manager should load Super Emerald art for Super Emerald stages");
        assertTrue(stripped.contains("CELL_SUPER_EMERALD"),
                "S3K special-stage manager should place Super Emerald cells when in Super Emerald mode");
        assertTrue(stripped.contains("markSuperEmeraldCollected("),
                "S3K special-stage manager should mark Super Emerald collection separately from Chaos Emeralds");
    }

    @Test
    void bridgeStakeGroundEdgeSubtypesDoNotRenderInvisible() throws IOException {
        String relative = "com/openggf/game/sonic2/objects/BridgeStakeObjectInstance.java";
        String source = Files.readString(SRC_MAIN.resolve(relative));
        String stripped = stripCommentsAndStrings(source);
        String compact = stripped.replaceAll("\\s+", "");

        assertTrue(!compact.contains("case7,8->{return;}"),
                "BridgeStake subtypes 7/8 should render a visible fallback instead of returning early");
    }

    @Test
    void specialStagePaletteCodeUsesPaletteAccessorsInsteadOfPublicColorArray() throws IOException {
        List<String> files = List.of(
                "com/openggf/game/sonic3k/specialstage/Sonic3kSpecialStageDataLoader.java",
                "com/openggf/game/sonic3k/specialstage/Sonic3kSpecialStagePalette.java"
        );
        List<String> violations = new ArrayList<>();

        Pattern publicColorArray = Pattern.compile("\\.\\s*colors\\s*\\[");
        for (String relative : files) {
            String stripped = stripCommentsAndStrings(Files.readString(SRC_MAIN.resolve(relative)));
            Matcher matcher = publicColorArray.matcher(stripped);
            while (matcher.find()) {
                violations.add(relative + ":" + lineNumberForOffset(stripped, matcher.start())
                        + " - use Palette.getColor(...) for special-stage palette construction/mutation");
            }
        }

        assertNoViolations("S3K special-stage palette code should not access Palette.colors[] directly",
                violations);
    }

    @Test
    void sonic3kFrontendScreensRoutePaletteUploadsThroughLocalHelper() throws IOException {
        List<String> files = List.of(
                "com/openggf/game/sonic3k/dataselect/S3kDataSelectRenderer.java",
                "com/openggf/game/sonic3k/levelselect/Sonic3kLevelSelectDataLoader.java",
                "com/openggf/game/sonic3k/levelselect/Sonic3kLevelSelectManager.java",
                "com/openggf/game/sonic3k/titlescreen/Sonic3kTitleScreenDataLoader.java",
                "com/openggf/game/sonic3k/titlescreen/Sonic3kTitleScreenManager.java"
        );
        List<String> violations = new ArrayList<>();

        for (String relative : files) {
            String stripped = stripCommentsAndStrings(Files.readString(SRC_MAIN.resolve(relative)));
            Matcher matcher = DIRECT_CACHE_PALETTE_TEXTURE.matcher(stripped);
            while (matcher.find()) {
                violations.add(relative + ":" + lineNumberForOffset(stripped, matcher.start())
                        + " - route S3K frontend palette uploads through S3kFrontendPaletteUploader");
            }
        }

        assertNoViolations("S3K frontend/menu screens should centralize palette texture uploads",
                violations);
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
    void sampleScannerDetectsGameIdBranchButAllowsTypedRuleBranch() {
        List<String> violations = scanGameIdBranches("sample/Physics.java", """
                class Physics {
                    void bad(GameModule module) {
                        if (module.getGameId() == GameId.S1) runS1();
                    }
                    void good(GameRules rules) {
                        if (rules.playerCapability().spindashEnabled()) run();
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
    void sampleScannerDetectsNextFrameRawObjectChildSpawn() {
        List<String> violations = scanPattern("sample/Object.java", """
                class Object {
                    void spawn(ObjectManager objectManager, ObjectInstance child) {
                        objectManager.addDynamicObjectNextFrame(child);
                    }
                }
                """, RAW_OBJECT_CHILD_SPAWN);

        assertEquals(List.of("sample/Object.java:3 - addDynamicObjectNextFrame("), violations);
    }

    @Test
    void sampleScannerDetectsGameModuleRegistryMutation() {
        List<String> violations = scanPattern("sample/Runtime.java", """
                class Runtime {
                    void bad(GameModule module, Rom rom) {
                        GameModuleRegistry.setCurrent(module);
                        GameModuleRegistry.detectAndSetModule(rom);
                    }
                }
                """, GAME_MODULE_REGISTRY_MUTATION);

        assertEquals(List.of(
                "sample/Runtime.java:3 - GameModuleRegistry.setCurrent(",
                "sample/Runtime.java:4 - GameModuleRegistry.detectAndSetModule("), violations);
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

    @Test
    void sampleSourceBudgetIgnoresCommentsAndBlankLines() {
        SourceFile source = SourceFile.fromText("""
                class Budgeted {
                    // This comment documents a non-obvious invariant.

                    void work() {
                        run();
                    }

                    /*
                     * Multi-line explanations should not consume architecture budget.
                     */
                    void moreWork() {
                        runAgain();
                    }
                }
                """);

        assertEquals(8, source.effectiveSourceLineCount());
    }

    @Test
    void sampleMethodScannerFindsMultilineAndSingleLineBodies() {
        SourceFile source = SourceFile.fromText("""
                class Example {
                    public static <T> T multiline(
                            T value,
                            int count)
                            throws IllegalArgumentException {
                        return value;
                    }
                    void compact() { run(); }
                }
                """);

        assertEquals(List.of(new MethodSpan("multiline", "Example#multiline(T,int)", 2, 7),
                new MethodSpan("compact", "Example#compact()", 8, 8)), source.methods());
    }

    @Test
    void sampleMethodScannerRejectsInvalidSourceInsteadOfDroppingItsBudget() {
        SourceFile source = SourceFile.fromText("class Example { void broken() {");
        assertThrows(IllegalArgumentException.class, source::methods);
    }

    @Test
    void sampleMethodScannerRejectsAmbiguousBudgetTargets() {
        SourceFile overloaded = SourceFile.fromText("""
                class Example {
                    void init() { }
                    void init(int count) { run(count); }
                }
                """);
        SourceFile nested = SourceFile.fromText("""
                class Example {
                    void init() { }
                    static class Nested { void init() { run(); } }
                }
                """);
        assertThrows(IllegalArgumentException.class, () -> overloaded.methodNamed("init"));
        assertThrows(IllegalArgumentException.class, () -> nested.methodNamed("init"));
        assertEquals(List.of("Example#init()", "Example#init(int)"),
                overloaded.methods().stream().map(MethodSpan::identity).toList());
        assertEquals(List.of("Example#init()", "Example.Nested#init()"),
                nested.methods().stream().map(MethodSpan::identity).toList());
        var budget = new MethodBudget("Example.java", "init", 1);
        assertTrue(budget.owns(nested.methods().getFirst()));
        assertFalse(budget.owns(nested.methods().get(1)));
    }

    @Test
    void sampleMethodScannerIgnoresBracesInCommentsAndLiterals() {
        SourceFile source = SourceFile.fromText(String.join("\n",
                "class Example {",
                "    private void work() {",
                "        String quoted = \"}\";",
                "        char marker = '{';",
                "        /* } } */",
                "        String block = \"\"\"",
                "                { private void fake() {",
                "                \"\"\";",
                "        Runnable action = () -> { run(); };",
                "    }",
                "    private void following() { run(); }",
                "}"));

        assertEquals(List.of(new MethodSpan("work", "Example#work()", 2, 10),
                new MethodSpan("following", "Example#following()", 11, 11)), source.methods());
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
            fail("production scan root is missing: " + relativeRoot);
        }
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> files = stream.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
            assertTrue(!files.isEmpty(), "production scan root must contain Java files: " + relativeRoot);
            return files;
        }
    }

    private static boolean isApprovedGameIdBranchFile(String relative) {
        return GAME_ID_BRANCH_APPROVED_FILES.contains(relative)
                || GAME_ID_BRANCH_APPROVED_PREFIXES.stream().anyMatch(relative::startsWith);
    }

    private static boolean isCoordinateScanned(String relative) {
        return COORDINATE_SCAN_PREFIXES.stream().anyMatch(relative::startsWith);
    }

    private static boolean isApprovedGameModuleRegistryMutationFile(String relative) {
        return relative.equals("com/openggf/game/GameModuleRegistry.java")
                || relative.equals("com/openggf/game/RomDetectionService.java")
                || relative.startsWith("com/openggf/tools/");
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

    private static int countMatches(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private record ClassBody(String source, int startOffset) {
    }

    private static ClassBody classBody(String source, String className) {
        Matcher matcher = Pattern.compile("\\bclass\\s+" + Pattern.quote(className) + "\\b").matcher(source);
        if (!matcher.find()) {
            throw new AssertionError("Could not find class " + className);
        }
        int openBrace = source.indexOf('{', matcher.end());
        if (openBrace < 0) {
            throw new AssertionError("Could not find body for class " + className);
        }
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return new ClassBody(source.substring(openBrace + 1, i), openBrace + 1);
                }
            }
        }
        throw new AssertionError("Could not find closing brace for class " + className);
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
        boolean owns(MethodSpan method) {
            String file = relativePath.substring(relativePath.lastIndexOf('/') + 1);
            String owner = file.substring(0, file.length() - ".java".length());
            return method.identity().startsWith(owner + "#" + methodName + "(");
        }
    }

    private record EmbeddedRuntimeDataBudget(
            String relativePath,
            String description,
            Pattern pattern,
            int expectedCount) {
    }

    private record SourceSignal(String relativePath, String requiredText) {
    }

    private record SourceFile(String text, List<String> lines) {
        static SourceFile read(Path path) throws IOException {
            return new SourceFile(Files.readString(path), Files.readAllLines(path));
        }

        static SourceFile fromText(String text) {
            return new SourceFile(text, text.lines().toList());
        }

        int effectiveSourceLineCount() {
            return (int) stripComments(text).lines()
                    .filter(line -> !line.isBlank())
                    // Compatibility metadata does not grow runtime implementation complexity.
                    .filter(line -> !line.trim().matches("@(?:com\\.openggf\\.game\\.)?ModApi"))
                    .count();
        }

        List<MethodSpan> methods() {
            List<MethodSpan> methods = new ArrayList<>();
            var compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException("Source guards require the build JDK's Java parser");
            }
            var diagnostics = new DiagnosticCollector<JavaFileObject>();
            var source = new SimpleJavaFileObject(URI.create("string:///GuardSource.java"),
                    JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return text;
                }
            };
            // Parse only: no type resolution, code generation or annotation processors.
            // Java owns comments, text blocks, multiline signatures and nested braces.
            try (var files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                var task = (JavacTask) compiler.getTask(null, files, diagnostics,
                        List.of("-proc:none"), null, List.of(source));
                var positions = Trees.instance(task).getSourcePositions();
                for (CompilationUnitTree unit : task.parse()) {
                    new TreePathScanner<Void, Void>() {
                        @Override
                        public Void visitMethod(MethodTree method, Void ignored) {
                            if (method.getReturnType() != null && method.getBody() != null) {
                                long start = positions.getStartPosition(unit, method);
                                long end = positions.getEndPosition(unit, method);
                                var owners = new java.util.ArrayDeque<String>();
                                for (var path = getCurrentPath(); path != null; path = path.getParentPath()) {
                                    if (path.getLeaf() instanceof ClassTree type) {
                                        owners.addFirst(type.getSimpleName().isEmpty()
                                                ? "<anonymous>" : type.getSimpleName().toString());
                                    }
                                }
                                String parameters = method.getParameters().stream()
                                        .map(parameter -> parameter.getType().toString().replaceAll("\\s+", ""))
                                        .collect(java.util.stream.Collectors.joining(","));
                                String identity = String.join(".", owners) + "#" + method.getName()
                                        + "(" + parameters + ")";
                                methods.add(new MethodSpan(method.getName().toString(), identity,
                                        (int) unit.getLineMap().getLineNumber(start),
                                        (int) unit.getLineMap().getLineNumber(end - 1)));
                            }
                            // Local/anonymous-class methods belong to this enclosing body.
                            return null;
                        }
                    }.scan(unit, null);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot parse source for architecture guard", e);
            }
            var errors = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .toList();
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("Invalid Java source in architecture guard: " + errors);
            }
            return methods;
        }

        MethodSpan methodNamed(String name) {
            List<MethodSpan> matches = methods().stream()
                    .filter(method -> method.name().equals(name)).toList();
            if (matches.size() > 1) {
                throw new IllegalArgumentException("Ambiguous source budget target " + name + ": " + matches);
            }
            return matches.isEmpty() ? null : matches.getFirst();
        }

    }

    private record MethodSpan(String name, String identity, int startLine, int endLine) {
        int lineCount() {
            return endLine - startLine + 1;
        }
    }
}
