package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.DataSelectProvider;
import com.openggf.game.EndingPhase;
import com.openggf.game.EndingProvider;
import com.openggf.game.GameMode;
import com.openggf.game.LevelSelectProvider;
import com.openggf.game.ResultsScreen;
import com.openggf.game.RomDetectionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestViewportLifecycleContract {

    private static final List<String> EXPECTED_NATIVE_PRESENTATION = List.of(
            "width:320", "present:0");
    private static final List<String> EXPECTED_REENTRY_AND_RESIZE = List.of(
            "width:400", "present:40",
            "width:352", "present:16");

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        Engine.clearGlobalInstance();
        SessionManager.clear();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
    }

    @Test
    void levelSelectReceivesNativeReentryAndResizeWidthsBeforeDraw() throws Exception {
        assertDrawLifecycle(
                GameMode.LEVEL_SELECT,
                RecordingLevelSelect::new,
                (loop, owner) -> when(loop.getLevelSelectProvider()).thenAnswer(ignored -> owner.get()));
    }

    @Test
    void dataSelectReceivesNativeReentryAndResizeWidthsBeforeDraw() throws Exception {
        assertDrawLifecycle(
                GameMode.DATA_SELECT,
                RecordingDataSelect::new,
                (loop, owner) -> when(loop.getDataSelectProvider()).thenAnswer(ignored -> owner.get()));
    }

    @Test
    void resultsReceiveNativeReentryAndResizeWidthsBeforeCommandAppend() throws Exception {
        assertDrawLifecycle(
                GameMode.SPECIAL_STAGE_RESULTS,
                RecordingResults::new,
                (loop, owner) -> when(loop.getResultsScreen()).thenAnswer(ignored -> owner.get()));
    }

    @Test
    void resultsDecoratorForwardsWidthsBeforeCommandAppendThroughEngineDraw() throws Exception {
        assertDrawLifecycle(
                GameMode.SPECIAL_STAGE_RESULTS,
                probe -> ResultsScreen.withBeforeUpdate(new RecordingResults(probe), () -> { }),
                (loop, owner) -> when(loop.getResultsScreen()).thenAnswer(ignored -> owner.get()));
    }

    @Test
    void endingCutsceneReceivesNativeReentryAndResizeWidthsBeforeDraw() throws Exception {
        assertEndingDrawLifecycle(GameMode.ENDING_CUTSCENE);
    }

    @Test
    void creditsTextReceivesNativeReentryAndResizeWidthsBeforeDraw() throws Exception {
        assertEndingDrawLifecycle(GameMode.CREDITS_TEXT);
    }

    @Test
    void creditsDemoReceivesNativeReentryAndResizeWidthsBeforeLevelPresentation() throws Exception {
        EngineHarness harness = newHarness(GameMode.CREDITS_DEMO);
        ViewportProbe nativeProbe = new ViewportProbe();
        AtomicReference<EndingProvider> owner = new AtomicReference<>(new RecordingEnding(nativeProbe));
        when(harness.gameLoop.getEndingProvider()).thenAnswer(ignored -> owner.get());
        doAnswer(ignored -> {
            nativeProbe.present();
            return null;
        }).when(harness.levelManager).drawWithSpritePriority(
                harness.spriteManager, true);

        drawAt(harness.engine, 320);

        assertEquals(EXPECTED_NATIVE_PRESENTATION, nativeProbe.events);

        ViewportProbe reenteredProbe = new ViewportProbe();
        owner.set(new RecordingEnding(reenteredProbe));
        doAnswer(ignored -> {
            reenteredProbe.present();
            return null;
        }).when(harness.levelManager).drawWithSpritePriority(
                harness.spriteManager, true);

        drawAt(harness.engine, 400);
        drawAt(harness.engine, 352);

        assertEquals(EXPECTED_REENTRY_AND_RESIZE, reenteredProbe.events);
    }

    @Test
    void tryAgainEndReceivesNativeReentryAndResizeWidthsBeforeDraw() throws Exception {
        assertEndingDrawLifecycle(GameMode.TRY_AGAIN_END);
    }

    private void assertEndingDrawLifecycle(GameMode mode) throws Exception {
        assertDrawLifecycle(
                mode,
                RecordingEnding::new,
                (loop, owner) -> when(loop.getEndingProvider()).thenAnswer(ignored -> owner.get()));
    }

    private <T> void assertDrawLifecycle(
            GameMode mode,
            Function<ViewportProbe, T> ownerFactory,
            BiConsumer<GameLoop, AtomicReference<T>> installOwner) throws Exception {
        EngineHarness harness = newHarness(mode);
        ViewportProbe nativeProbe = new ViewportProbe();
        AtomicReference<T> owner = new AtomicReference<>(ownerFactory.apply(nativeProbe));
        installOwner.accept(harness.gameLoop, owner);

        drawAt(harness.engine, 320);

        assertEquals(EXPECTED_NATIVE_PRESENTATION, nativeProbe.events);

        ViewportProbe reenteredProbe = new ViewportProbe();
        owner.set(ownerFactory.apply(reenteredProbe));

        drawAt(harness.engine, 400);
        drawAt(harness.engine, 352);

        assertEquals(EXPECTED_REENTRY_AND_RESIZE, reenteredProbe.events);
    }

    private EngineHarness newHarness(GameMode mode) throws Exception {
        GraphicsManager graphics = mock(GraphicsManager.class);
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        Engine engine = new Engine(new EngineContext(
                config,
                graphics,
                mock(AudioManager.class),
                mock(RomManager.class),
                mock(PerformanceProfiler.class),
                mock(DebugOverlayManager.class),
                mock(PlaybackDebugManager.class),
                mock(RomDetectionService.class),
                mock(CrossGameFeatureProvider.class)));
        GameLoop gameLoop = mock(GameLoop.class);
        when(gameLoop.getCurrentGameMode()).thenReturn(mode);
        Camera camera = mock(Camera.class);
        LevelManager levelManager = mock(LevelManager.class);
        SpriteManager spriteManager = mock(SpriteManager.class);
        setField(engine, "gameLoop", gameLoop);
        setField(engine, "camera", camera);
        setField(engine, "levelManager", levelManager);
        setField(engine, "spriteManager", spriteManager);
        return new EngineHarness(engine, gameLoop, levelManager, spriteManager);
    }

    private static void drawAt(Engine engine, int width) throws Exception {
        setField(engine, "projectionWidth", (double) width);
        engine.draw();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record EngineHarness(
            Engine engine,
            GameLoop gameLoop,
            LevelManager levelManager,
            SpriteManager spriteManager) {
    }

    private static final class ViewportProbe {
        private final List<String> events = new ArrayList<>();
        private int offset = Integer.MIN_VALUE;

        private void setViewportWidth(int width) {
            events.add("width:" + width);
            offset = (width - 320) / 2;
        }

        private void present() {
            events.add("present:" + offset);
        }
    }

    private static final class RecordingLevelSelect implements LevelSelectProvider {
        private final ViewportProbe probe;

        private RecordingLevelSelect(ViewportProbe probe) {
            this.probe = probe;
        }

        @Override public void setViewportWidth(int width) { probe.setViewportWidth(width); }
        @Override public void initialize() {}
        @Override public void update(InputHandler input) {}
        @Override public void draw() { probe.present(); }
        @Override public void setClearColor() {}
        @Override public void reset() {}
        @Override public State getState() { return State.ACTIVE; }
        @Override public boolean isExiting() { return false; }
        @Override public boolean isActive() { return true; }
        @Override public boolean isSpecialStageSelected() { return false; }
        @Override public boolean isSoundTestSelected() { return false; }
        @Override public int getSelectedZone() { return 0; }
        @Override public int getSelectedAct() { return 0; }
        @Override public int getSelectedZoneAct() { return 0; }
        @Override public int getSelectedIndex() { return 0; }
        @Override public int getSoundTestValue() { return 0; }
    }

    private static final class RecordingDataSelect implements DataSelectProvider {
        private final ViewportProbe probe;

        private RecordingDataSelect(ViewportProbe probe) {
            this.probe = probe;
        }

        @Override public void setViewportWidth(int width) { probe.setViewportWidth(width); }
        @Override public void initialize() {}
        @Override public void update(InputHandler input) {}
        @Override public void draw() { probe.present(); }
        @Override public void setClearColor() {}
        @Override public void reset() {}
        @Override public State getState() { return State.ACTIVE; }
        @Override public boolean isExiting() { return false; }
        @Override public boolean isActive() { return true; }
    }

    private static final class RecordingResults implements ResultsScreen {
        private final ViewportProbe probe;

        private RecordingResults(ViewportProbe probe) {
            this.probe = probe;
        }

        @Override public void setViewportWidth(int width) { probe.setViewportWidth(width); }
        @Override public void update(int frameCounter, Object context) {}
        @Override public boolean isComplete() { return false; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { probe.present(); }
    }

    private static final class RecordingEnding implements EndingProvider {
        private final ViewportProbe probe;

        private RecordingEnding(ViewportProbe probe) {
            this.probe = probe;
        }

        @Override public void setViewportWidth(int width) { probe.setViewportWidth(width); }
        @Override public void initialize() {}
        @Override public void update() {}
        @Override public void draw() { probe.present(); }
        @Override public EndingPhase getCurrentPhase() { return EndingPhase.CUTSCENE; }
        @Override public boolean isComplete() { return false; }
    }
}
