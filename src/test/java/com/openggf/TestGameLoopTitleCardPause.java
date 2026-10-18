package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.LevelManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.mockito.Mockito.mock;

@Isolated
class TestGameLoopTitleCardPause {

    @Test void admittedLevelDoesNotProcessTheOuterRecordingInputAgain() throws Exception {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        var loop=new GameLoop(new InputHandler());
        set(loop,"currentGameMode",GameMode.LEVEL);
        set(loop,"gameplayMode",SessionManager.getCurrentGameplayMode());
        set(loop,"levelManager",mock(LevelManager.class));
        var controls=mock(com.openggf.game.recording.UserRecordingRuntimeControls.class);
        set(loop,"userRecordingControls",controls);
        Method admission=GameLoop.class.getDeclaredMethod("prepareAdmittedIteration",boolean.class,boolean.class);
        admission.setAccessible(true);admission.invoke(loop,false,false);
        org.mockito.Mockito.verify(controls,org.mockito.Mockito.never()).updateLevelControlInput(org.mockito.ArgumentMatchers.any());
    }

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void heldUnpausePressIsConsumedByTheLockedTitleIteration() throws Exception {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        InputHandler input = new InputHandler();
        GameLoop loop = new GameLoop(input);
        set(loop, "currentGameMode", GameMode.TITLE_CARD);
        set(loop, "gameplayMode", new GameplayModeContext(new WorldSession(new Sonic2GameModule())));
        set(loop, "levelManager", mock(LevelManager.class));
        set(loop, "camera", mock(com.openggf.camera.Camera.class));
        set(loop, "titleCardProvider", mock(TitleCardProvider.class));
        set(loop, "activePlcLifecycleFrame", mock(PlcLifecycleFrame.class));
        int pauseKey = SonicConfigurationService.getInstance().getInt(SonicConfiguration.PAUSE_KEY);
        input.handleKeyEvent(pauseKey, GLFW_PRESS);

        Method admission = GameLoop.class.getDeclaredMethod("prepareAdmittedIteration", boolean.class, boolean.class);
        admission.setAccessible(true);
        assertFalse((boolean) admission.invoke(loop, false, false));

        assertFalse(input.isKeyPressed(pauseKey),
                "a title-card setup iteration must consume the unpause edge before returning");
        assertFalse(GameLoopPauseInput.nextUserPaused(GameMode.TITLE_CARD, input,
                SonicConfigurationService.getInstance(), false, false, false,
                mock(com.openggf.debug.playback.PlaybackDebugManager.class), () -> false),
                "holding the unpause key must not pause again on the next title iteration");
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
