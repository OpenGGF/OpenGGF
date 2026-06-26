package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.FadeManager;
import com.openggf.timer.TimerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

class TestLiveRewindBoundaryPolicy {
    private SonicConfigurationService config;

    @BeforeEach
    void configureServices() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        config = SonicConfigurationService.getInstance();
        config.resetToDefaults();
    }

    @AfterEach
    void tearDown() {
        config.resetToDefaults();
        SessionManager.clear();
    }

    @Test
    void levelLoadBoundaryRealignsInputSourceAndControllerAtFrameZero() {
        Fixture fixture = installLiveFixture();
        InputHandler input = new InputHandler();
        input.handleKeyEvent(config.getInt(SonicConfiguration.LEFT), GLFW_PRESS);
        fixture.inputSource.appendFrame(input, config);
        fixture.controller.recordExternalStep();
        input.handleKeyEvent(config.getInt(SonicConfiguration.LEFT), GLFW_RELEASE);
        input.handleKeyEvent(config.getInt(SonicConfiguration.RIGHT), GLFW_PRESS);
        fixture.inputSource.appendFrame(input, config);
        fixture.controller.recordExternalStep();
        fixture.inputSource.discardBefore(2);

        fixture.manager.markBoundary(RewindBoundary.LEVEL_LOAD);

        assertEquals(0, fixture.controller.currentFrame());
        assertEquals(0, fixture.inputSource.earliestFrame());
        assertEquals(1, fixture.inputSource.frameCount());
    }

    @Test
    void levelLoadBoundaryLeavesControllerAndInputSourceMutuallyConsistent() {
        Fixture fixture = installLiveFixture();
        InputHandler input = new InputHandler();
        input.handleKeyEvent(config.getInt(SonicConfiguration.LEFT), GLFW_PRESS);
        fixture.inputSource.appendFrame(input, config);
        fixture.controller.recordExternalStep();
        input.handleKeyEvent(config.getInt(SonicConfiguration.LEFT), GLFW_RELEASE);
        input.handleKeyEvent(config.getInt(SonicConfiguration.RIGHT), GLFW_PRESS);
        fixture.inputSource.appendFrame(input, config);
        fixture.controller.recordExternalStep();
        fixture.inputSource.discardBefore(2);

        fixture.manager.markBoundary(RewindBoundary.LEVEL_LOAD);

        assertEquals(0, fixture.controller.currentFrame());
        assertEquals(0, fixture.inputSource.earliestFrame());
        assertEquals(1, fixture.inputSource.frameCount());
        assertTrue(fixture.controller.currentFrame() >= fixture.inputSource.earliestFrame());
        assertTrue(fixture.controller.currentFrame() < fixture.inputSource.frameCount());
    }

    @Test
    void seamlessBoundaryKeepsOnlyCurrentFrame() {
        Fixture fixture = installLiveFixture();
        InputHandler input = new InputHandler();
        input.handleKeyEvent(config.getInt(SonicConfiguration.LEFT), GLFW_PRESS);
        fixture.inputSource.appendFrame(input, config);
        fixture.controller.recordExternalStep();
        input.handleKeyEvent(config.getInt(SonicConfiguration.LEFT), GLFW_RELEASE);
        input.handleKeyEvent(config.getInt(SonicConfiguration.RIGHT), GLFW_PRESS);
        fixture.inputSource.appendFrame(input, config);
        fixture.controller.recordExternalStep();

        fixture.manager.markBoundary(RewindBoundary.SEAMLESS_LEVEL_TRANSITION);

        assertEquals(2, fixture.controller.currentFrame());
        assertEquals(2, fixture.inputSource.earliestFrame());
        assertEquals(3, fixture.inputSource.frameCount());
    }

    @Test
    void modeExitBoundaryClearsInstalledState() {
        Fixture fixture = installLiveFixture();
        assertNotNull(readPrivateField(fixture.manager, "inputSource"));
        assertNotNull(readPrivateField(fixture.manager, "rewindController"));

        fixture.manager.markBoundary(RewindBoundary.MODE_EXIT_TO_NON_REWINDABLE);

        assertNull(readPrivateField(fixture.manager, "inputSource"));
        assertNull(readPrivateField(fixture.manager, "rewindController"));
        assertNull(readPrivateField(fixture.manager, "installedGameplayMode"));
    }

    private Fixture installLiveFixture() {
        SessionManager.clear();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        GameplayModeContext context = SessionManager.openGameplaySession(new Sonic2GameModule());

        Camera camera = new Camera();
        TimerManager timers = new TimerManager();
        GameStateManager gameState = new GameStateManager();
        FadeManager fade = new FadeManager();
        GameRng rng = new GameRng(GameRng.Flavour.S1_S2);
        DefaultSolidExecutionRegistry solid = new DefaultSolidExecutionRegistry();

        context.attachGameplayManagers(camera, timers, gameState, fade, rng, solid);
        context.initializeFreshGameplayState();

        LiveRewindManager manager = new LiveRewindManager(config);
        manager.handleRealtimeRewindInput(GameMode.LEVEL, new InputHandler());

        LiveRewindInputSource inputSource = readPrivateField(manager, "inputSource");
        RewindController controller = readPrivateField(manager, "rewindController");
        assertSame(controller, SessionManager.getCurrentGameplayMode().getRewindController());
        assertNotNull(inputSource);

        return new Fixture(manager, inputSource, controller);
    }

    private static <T> T readPrivateField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            T value = (T) field.get(target);
            return value;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read field " + fieldName, e);
        }
    }

    private record Fixture(
            LiveRewindManager manager,
            LiveRewindInputSource inputSource,
            RewindController controller) {
    }
}
