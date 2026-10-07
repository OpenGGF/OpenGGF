package com.openggf.mods.code;

import com.openggf.configuration.*;
import com.openggf.control.*;
import com.openggf.game.*;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.session.SessionManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.runtime.OwnerBoundGamePatch;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.tools.modsdk.GgfModCli;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Packages the maintained example, then runs its real module through native frame admission. */
@RequiresRom(SonicGame.SONIC_3K)
class TestHardenedPrototype {
    @TempDir static Path temp;
    static URLClassLoader loader;
    static Path jar;
    SharedLevel bootstrap;
    final InputHandler input = new InputHandler();
    GameModule effective;
    GameplayFrameController controller;
    final ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
    final java.util.concurrent.atomic.AtomicReference<Set<String>> disabled = new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeAll static void packageExample() throws Exception {
        Path project = Path.of("examples/hardened-s3k");
        Path classes = Files.createDirectory(temp.resolve("classes"));
        var args = new ArrayList<>(List.of("--release", "21", "-cp", System.getProperty("java.class.path"),
                "-d", classes.toString()));
        try (var files = Files.walk(project.resolve("src/main/java"))) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
        }
        var output = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, output, output, args.toArray(String[]::new)),
                output.toString(StandardCharsets.UTF_8));
        Files.createDirectories(classes.resolve("META-INF"));
        Files.copy(project.resolve("src/main/resources/META-INF/openggf-mod.yaml"),
                classes.resolve("META-INF/openggf-mod.yaml"));
        jar = temp.resolve("hardened-s3k.jar");
        assertEquals(0, GgfModCli.run(new String[]{"package", "--input", classes.toString(), "--out", jar.toString()},
                System.out), "the real package/API validation boundary accepts the example");
        loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, TestHardenedPrototype.class.getClassLoader());
    }

    @AfterAll static void closeLoader() throws Exception { if (loader != null) loader.close(); }
    @AfterEach void cleanup() {
        SessionManager.clear();
        if (bootstrap != null) bootstrap.dispose();
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    private HeadlessTestFixture launch() throws Exception { return launch(false); }
    private HeadlessTestFixture launch(boolean developmentCapture) throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_3K, 7, 0);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.NATIVE_4_3.name());
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        GameModule base = GameServices.module();
        var boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Saved(), disabled::set);
        if (developmentCapture) effective = DevelopmentPatchLoader.fromJar(jar).apply(base);
        else try (var assets = ModAssetRoot.jar(temp, jar, ModInputLimits.production())) {
            var context = new ModContext("hardened-s3k", "s3k", assets);
            ((GgfMod) loader.loadClass("hardened.HardenedS3kMod").getConstructor().newInstance()).register(context);
            var plan = context.freeze();
            effective = new ModBackedGamePatch(plan, boundary).apply(base, null);
            for (var patch : plan.explicitPatches()) {
                var patchContext = new com.openggf.game.patch.PatchContext(
                        logicalRom -> com.openggf.data.RomByteReader.fromRom(GameServices.rom().getRom()), config);
                effective = OwnerBoundGamePatch.wrap("hardened-s3k", patch, boundary).apply(effective, patchContext);
            }
        }
        SessionManager.clear();
        GameModuleRegistry.setCurrent(effective);
        TestEnvironment.activeGameplayMode();
        var owningEngine = com.openggf.game.session.EngineServices.current();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(7, 0).withSkippedZoneIntro().build();
        // The fixture resets the process engine root but retains this existing
        // mode. Keep static reads and injected object/rewind clocks on one owner.
        com.openggf.game.session.EngineServices.configure(owningEngine);
        // The fixture explicitly reinitializes a cold native camera after its
        // load. Use the real fresh-load owner to test the module admission,
        // without seeding a camera, player, checkpoint or physics value.
        GameServices.level().loadCurrentLevel();
        controller = effective.gameplayFrameController();
        return fixture;
    }

    private static LogicalInputSnapshot action(int direction, boolean accept, boolean start) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(direction, direction,
                accept ? InputActionMasks.ACTION_A : 0, accept ? InputActionMasks.ACTION_A : 0, start, start),
                PlayerInputState.neutral());
    }
    private void step(HeadlessTestFixture fixture, LogicalInputSnapshot snapshot) {
        input.setLogicalOverride(snapshot);
        ControlledFrameRuntime.step(fixture.gameplayMode(), input, snapshot);
    }
    private Object flowSnapshot() {
        for (var adapter : effective.rewindAdapters()) {
            if (adapter.key().equals("hardened-s3k:flow")) return adapter.capture();
        }
        throw new AssertionError("Missing session-owned flow adapter");
    }
    private String screen() throws Exception {
        Object snapshot = flowSnapshot();
        return snapshot.getClass().getMethod("screen").invoke(snapshot).toString();
    }
    private int presentationTicks() throws Exception {
        Object snapshot = flowSnapshot();
        return (int) snapshot.getClass().getMethod("presentationTicks").invoke(snapshot);
    }
    private void releaseEntry(HeadlessTestFixture fixture) throws Exception {
        for (int frame = 0; frame < 1200 && !screen().equals("PLAY"); frame++) {
            step(fixture, LogicalInputSnapshot.neutral());
        }
        assertEquals("PLAY", screen(), "production entry presentation must release movement");
    }

    @Test void titleLessonLaunchAndNoSavePolicyUseThePackagedSource() throws Exception {
        launch();
        assertTrue(effective.requiresNoSaveSession());
        assertTrue(controller.nativePlayerInput());
        assertNull(effective.getDataSelectProvider());
        var title = effective.getTitleScreenProvider();
        title.initialize();
        // Raw keyboard events must not select or launch a movie-owned row.
        input.setLogicalOverride(LogicalInputSnapshot.neutral());
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        title.update(input);
        assertFalse(title.isExiting(), "physical Enter cannot launch a neutral replay row");
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        input.update();
        input.setLogicalOverride(action(com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_DOWN, false, false));
        title.update(input);
        input.setLogicalOverride(action(0, true, false)); title.update(input);
        assertFalse(title.isExiting(), "How to Play opens a readable lesson before starting");
        input.setLogicalOverride(LogicalInputSnapshot.neutral()); title.update(input);
        input.setLogicalOverride(action(0, true, false)); title.update(input);
        assertTrue(title.isExiting());
        assertEquals(TitleScreenProvider.TitleScreenAction.ONE_PLAYER, title.consumeExitAction());
        assertEquals(7, title.startZoneIndex()); assertEquals(0, title.startActIndex());
        title.reset(); assertFalse(title.isActive());
        title.initialize(); assertEquals(TitleScreenProvider.State.ACTIVE, title.getState());
    }

    @Test void developerWalkthroughLoaderPublishesPlacementWithTheSameOwnerBoundary() throws Exception {
        var fixture = launch(true);
        releaseEntry(fixture);
        step(fixture, LogicalInputSnapshot.neutral());
        assertEquals(1, GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(object -> object.getClass().getName().equals("hardened.Sentry")).count());
        assertTrue(effective.requiresNoSaveSession());
        assertEquals(0x1D30, GameServices.sprites().getMainPlayable().getCentreX());
    }

    @Test void physicalPostPauseAndNativeCheckpointRetryKeepTheEntryOverrideOutOfRespawn() throws Exception {
        var fixture = launch();
        assertInstanceOf(com.openggf.game.sonic3k.Sonic3kLevel.class, GameServices.level().getCurrentLevel());
        assertEquals(0x1D30, fixture.sprite().getCentreX());
        releaseEntry(fixture);
        var post = assertInstanceOf(CheckpointState.class, GameServices.level().getCheckpointState());
        for (int frame = 0; frame < 40 && !post.isActive(); frame++) {
            step(fixture, action(com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT, false, false));
        }
        assertEquals(2, post.getLastCheckpointIndex(), "native collision must touch the ROM post");
        int savedX = post.getSavedX(), savedY = post.getSavedY();
        assertNotEquals(0x1D30, savedX, "post retry is distinct from fresh entry");
        Object previousSentry = null;
        for (int cycle = 0; cycle < 2; cycle++) {
            step(fixture, LogicalInputSnapshot.neutral());
            var sentries = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(object -> object.getClass().getName().equals("hardened.Sentry")).toList();
            assertEquals(1, sentries.size());
            if (previousSentry != null) assertNotSame(previousSentry, sentries.getFirst(),
                    "native reload must recreate the authored object graph");
            previousSentry = sentries.getFirst();
            step(fixture, action(0, false, true)); assertEquals("PAUSED", screen());
            var player = GameServices.sprites().getMainPlayable();
            int x = player.getCentreX(), y = player.getCentreY();
            for (int frame = 0; frame < 30; frame++) step(fixture,
                    action(com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT, false, false));
            assertEquals(x, player.getCentreX()); assertEquals(y, player.getCentreY());
            step(fixture, action(com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_DOWN, false, false));
            step(fixture, action(0, true, false)); assertEquals("RETRY", screen());
            var previousLevel = GameServices.level().getCurrentLevel();
            for (int frame = 0; frame < 18; frame++) step(fixture, LogicalInputSnapshot.neutral());
            assertNotSame(previousLevel, GameServices.level().getCurrentLevel());
            assertEquals(savedX, GameServices.sprites().getMainPlayable().getCentreX());
            assertEquals(savedY, GameServices.sprites().getMainPlayable().getCentreY());
            assertEquals(2, GameServices.level().getCheckpointState().getLastCheckpointIndex());
            assertEquals(1, GameServices.level().getCurrentLevel().getObjects().stream()
                    .filter(spawn -> "hardened-s3k:spore-sentry".equals(spawn.objectKey())).count(),
                    "checkpoint reload installs the placement exactly once");
            releaseEntry(fixture);
            assertFalse(GameServices.sprites().getMainPlayable().getDead());
        }
        step(fixture, LogicalInputSnapshot.neutral());
        assertNotSame(previousSentry, GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(object -> object.getClass().getName().equals("hardened.Sentry")).findFirst().orElseThrow());
    }

    @Test void retryBeforePostIsAnExplicitFreshLoadAndDoesNotDriftToTheDistantNativeStart() throws Exception {
        var fixture = launch();
        for (int cycle = 0; cycle < 2; cycle++) {
            releaseEntry(fixture);
            assertFalse(GameServices.level().getCheckpointState().isActive());
            step(fixture, action(0, false, true));
            for (int frame = 0; frame < 10; frame++) step(fixture, LogicalInputSnapshot.neutral());
            step(fixture, action(com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_DOWN, false, false));
            step(fixture, action(0, true, false)); assertEquals("RETRY", screen());
            var oldLevel = GameServices.level().getCurrentLevel();
            for (int frame = 0; frame < 18; frame++) step(fixture, LogicalInputSnapshot.neutral());
            assertNotSame(oldLevel, GameServices.level().getCurrentLevel());
            assertEquals(0x1D30, GameServices.sprites().getMainPlayable().getCentreX());
            assertEquals(0x1A8, GameServices.sprites().getMainPlayable().getCentreY());
            assertFalse(GameServices.level().getCheckpointState().isActive());
            assertEquals(1, GameServices.level().getCurrentLevel().getObjects().stream()
                    .filter(spawn -> "hardened-s3k:spore-sentry".equals(spawn.objectKey())).count(),
                    "a fresh load installs the authored placement once");
        }
    }

    @Test void aRealControllerFaultAbortsTheOwnerAndTearsDownTheEncounter() throws Exception {
        var fixture = launch();
        // Setup-only rows do not call the controller. Wait for a real held
        // entry callback to install its level ledger before injecting the fault.
        for (int frame = 0; frame < 1200 && presentationTicks() == 0; frame++) {
            step(fixture, LogicalInputSnapshot.neutral());
        }
        assertEquals("ENTRY", screen());
        assertTrue(presentationTicks() > 0);
        Object encounter = effective.getGameService(loader.loadClass("hardened.EncounterState"));
        // Fault stimulus: a creator accidentally begins its run twice. The next
        // actual entry-release callback throws; no physics or checkpoint is seeded.
        encounter.getClass().getMethod("begin").invoke(encounter);
        var returned = new java.util.concurrent.atomic.AtomicBoolean();
        var gate = com.openggf.Engine.class.getDeclaredMethod("runFrameWithModAbort", Runnable.class, Runnable.class);
        gate.setAccessible(true);
        boolean[] admitted = {true};
        for (int frame = 0; frame < 1200 && admitted[0]; frame++) {
            admitted[0] = (boolean) gate.invoke(null,
                    (Runnable) () -> step(fixture, LogicalInputSnapshot.neutral()),
                    (Runnable) () -> { SessionManager.clear(); returned.set(true); });
        }
        assertFalse(admitted[0], "the production outer-frame abort gate consumes the attributed failure");
        assertTrue(returned.get()); assertEquals(Set.of("hardened-s3k"), disabled.get());
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("hardened-s3k").getFirst().code());
        assertEquals("ABORTED", encounter.getClass().getMethod("status").invoke(encounter).toString());
        assertNull(SessionManager.getCurrentGameplayMode(), "no failed encounter remains current");
    }
}
