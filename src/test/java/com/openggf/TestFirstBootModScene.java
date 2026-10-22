package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameMode;
import com.openggf.game.RomDetectionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.OwnedTitleEntry;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunSpec;
import com.openggf.game.session.GameplayRunPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Title-entry scenes must work before any game has supplied gameplay managers. */
class TestFirstBootModScene {
    @TempDir Path temp;
    GameLoop loop;

    @AfterEach void cleanup() {
        if (loop != null) {
            loop.modSceneHost.cleanup();
            loop.closePresence();
        }
        SessionManager.clear();
        Engine.clearGlobalInstance();
        AudioManager.getInstance().destroy();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
    }

    @Test void titleEntryReceivesInputAndKeepsWorldAbsentBeforeFirstRun() {
        Engine engine = createEngine(RomManager.getInstance());
        var config = EngineServices.current().configuration();
        var graphics = EngineServices.current().graphics();
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));
        loop.setInputHandler(input);
        int[] updates = {0};
        int[] confirms = {0};
        int[] draws = {0};
        var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                ignored -> new ModStateSaveResult.Saved(), ignored -> { });
        var scene = ModContextTestAccess.ownedScene("first-boot", () -> new ModScene() {
            @Override public void enter(SceneContext ctx) { }
            @Override public void update(SceneContext ctx) {
                updates[0]++;
                if (ctx.keyPressed(SceneKeys.ENTER)) confirms[0]++;
            }
            @Override public void draw(SceneContext ctx, SceneCanvas canvas) { draws[0]++; }
        }, boundary);
        ModSceneLauncher.openTitleEntryScene(loop, 0, graphics, 320, 224,
                new OwnedTitleEntry("first-boot", "First boot", scene), null);
        drainFade();

        assertNull(SessionManager.getCurrentGameplayMode());
        input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_PRESS);
        assertDoesNotThrow(loop::step);
        assertDoesNotThrow(loop::step);
        input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_RELEASE);
        assertDoesNotThrow(loop::step);
        assertDoesNotThrow(engine::draw);

        assertEquals(3, updates[0], "the real loop dispatches each fresh-title scene row");
        assertEquals(1, confirms[0], "scene input retains normal press edges");
        assertEquals(1, draws[0], "title-entry drawing needs no gameplay camera");
        assertEquals(GameMode.MOD_SCENE, loop.getCurrentGameMode());
        assertNull(SessionManager.getCurrentGameplayMode(), "a title menu must not bootstrap a gameplay world");
    }

    @Test void engineHostedLaunchAndReturnRetainTheSceneUntilItsRealTitleExit() throws Exception {
        RomManager roms = mock(RomManager.class);
        when(roms.getRom()).thenThrow(new IOException("missing test ROM"));
        Engine engine = createEngine(roms);
        loop.setInputHandler(new InputHandler(InputBindingFactory.supplier(EngineServices.current().configuration())));
        SceneContext[] visit = {null};
        List<RunEndReason> resumed = new ArrayList<>();
        int[] enters = {0};
        int[] exits = {0};
        var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                ignored -> new ModStateSaveResult.Saved(), ignored -> { });
        var scene = ModContextTestAccess.ownedScene("first-boot", () -> new ModScene() {
            @Override public void enter(SceneContext ctx) { visit[0] = ctx; enters[0]++; }
            @Override public void update(SceneContext ctx) { }
            @Override public void draw(SceneContext ctx, SceneCanvas canvas) { }
            @Override public void resumed(SceneContext ctx, RunEndReason reason) {
                assertSame(visit[0], ctx, "hosted returns retain the original visit and its resources");
                resumed.add(reason);
            }
            @Override public void exit(SceneContext ctx) { exits[0]++; }
        }, boundary);
        engine.openTitleEntryScene(new OwnedTitleEntry("first-boot", "First boot", scene), List.of("s1"));
        drainFade();
        var spec = new RunSpec("s1", 0, 0, "sonic", GameplayRunPolicy.isolatedAct());
        for (int attempt = 0; attempt < 2; attempt++) {
            var handle = visit[0].gameplay().launch(spec, new RunHost() { });
            loop.step(); // admits the real deferred Engine.startSceneHostedRun callback
            drainFade();
            assertFalse(handle.isActive());
            assertTrue(loop.modSceneHost.isOpen(), "a failed launch retains the owner for another run");
            assertEquals(GameMode.MOD_SCENE, loop.getCurrentGameMode());
        }
        assertEquals(List.of(RunEndReason.LOAD_FAILED, RunEndReason.LOAD_FAILED), resumed);
        // The normal return owner must preserve the visit for non-failure reasons too.
        loop.setGameMode(GameMode.LEVEL);
        engine.returnToHostScene(RunEndReason.LEFT);
        assertEquals(List.of(RunEndReason.LOAD_FAILED, RunEndReason.LOAD_FAILED, RunEndReason.LEFT), resumed);
        assertEquals(1, enters[0]);
        assertEquals(0, exits[0]);

        var stockLaunchReset = Engine.class.getDeclaredMethod("resetForGameplayFromMasterTitle");
        stockLaunchReset.setAccessible(true);
        stockLaunchReset.invoke(engine);
        loop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
        assertFalse(loop.modSceneHost.isOpen());
        assertEquals(1, exits[0], "ordinary title/stock-game retirement still closes the scene exactly once");
    }

    private void drainFade() {
        for (int i = 0; i < 100 && loop.resolveFadeManager().isActive(); i++) loop.resolveFadeManager().update();
        assertFalse(loop.resolveFadeManager().isActive());
    }

    private Engine createEngine(RomManager roms) {
        SessionManager.clear();
        var config = SonicConfigurationService.createStandalone(temp);
        var graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        var audio = AudioManager.getInstance();
        audio.setBackend(new NullAudioBackend());
        var context = new EngineContext(config, graphics, audio, roms,
                PerformanceProfiler.getInstance(), DebugOverlayManager.getInstance(),
                PlaybackDebugManager.getInstance(), RomDetectionService.getInstance(), CrossGameFeatureProvider.getInstance());
        EngineServices.configure(context);
        Engine engine = new Engine(context);
        loop = engine.getGameLoop();
        return engine;
    }
}
