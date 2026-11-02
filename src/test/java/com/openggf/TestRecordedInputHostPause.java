package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.InputHandlerInternalAccess;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.control.RecordedInputOwnership;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.mockito.Mockito.mock;

class TestRecordedInputHostPause {
    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"LEVEL", "TITLE_CARD", "SPECIAL_STAGE", "SPECIAL_STAGE_RESULTS", "BONUS_STAGE"})
    void recordedStartRemainsControllerInputWithoutBecomingHostPause(GameMode mode) {
        var input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()));
        try (var lease = RecordedInputOwnership.acquire(input)) {
            assertFalse(nextPaused(mode, input));
            assertTrue(input.logical().player1().startPressed(), "native controller consumers still see Start");
            var unrelated = new InputHandler();
            unrelated.setLogicalOverride(input.logical());
            assertTrue(nextPaused(mode, unrelated), "another handler has its own host ownership");
        }
        assertTrue(nextPaused(mode, input), "ordinary live logical Start retains host pause");
    }

    @Test
    void physicalPauseKeyStillPausesDuringRecordedInputOwnership() {
        var config = SonicConfigurationService.createStandalone();
        var input = new InputHandler();
        input.handleKeyEvent(config.getInt(SonicConfiguration.PAUSE_KEY), GLFW_PRESS);
        try (var lease = RecordedInputOwnership.acquire(input)) {
            assertTrue(GameLoopPauseInput.nextUserPaused(GameMode.TITLE_CARD, input, config,
                    false, false, false, mock(PlaybackDebugManager.class), () -> false));
        }
    }

    @Test
    void ownedPassAcknowledgesPhysicalPauseAndStepWithoutAcknowledgingRecordedNativeStart() {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.PAUSE_KEY, org.lwjgl.glfw.GLFW.GLFW_KEY_P);
        config.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, org.lwjgl.glfw.GLFW.GLFW_KEY_F6);
        var input = new InputHandler(InputBindingFactory.supplier(config));
        var recorded = LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral());
        input.setLogicalOverride(recorded);
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_P, GLFW_PRESS);
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_F6, GLFW_PRESS);
        try (var pass = InputHandlerInternalAccess.openInputPass(input);
             var lease = RecordedInputOwnership.acquire(input)) {
            input.refreshLogicalSnapshot(); input.refreshLogicalSnapshot();
            assertTrue(GameLoopPauseInput.nextUserPaused(GameMode.LEVEL, input, config,
                    false, false, false, mock(PlaybackDebugManager.class), () -> false));
            assertTrue(GameLoopPauseInput.frameStep(input, config, true));
            input.update();
            assertFalse(GameLoopPauseInput.nextUserPaused(GameMode.LEVEL, input, config,
                    false, false, false, mock(PlaybackDebugManager.class), () -> false));
            assertFalse(GameLoopPauseInput.frameStep(input, config, true));
            assertSame(recorded, input.logical(), "host acknowledgment cannot consume a movie's native Start");
        }
    }

    @Test
    void liveStartPausesAndResumesWithoutLeavingANativePauseEdge() {
        var config = SonicConfigurationService.createStandalone();
        assertEquals(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, config.getInt(SonicConfiguration.START));
        var input = new InputHandler(InputBindingFactory.supplier(config));
        var playback = mock(PlaybackDebugManager.class);
        boolean paused = false;
        for (boolean expected : new boolean[]{true, false, true, false}) {
            input.handleKeyEvent(config.getInt(SonicConfiguration.START), GLFW_PRESS);
            input.refreshLogicalSnapshot();
            paused = GameLoopPauseInput.nextUserPaused(GameMode.LEVEL, input, config,
                    paused, false, false, playback, () -> false);
            assertEquals(expected, paused);
            assertFalse(GameLoopPauseInput.nativeStartEdge(input, playback));
            input.update();
            input.handleKeyEvent(config.getInt(SonicConfiguration.START), org.lwjgl.glfw.GLFW.GLFW_RELEASE);
            input.update();
        }
    }

    @Test
    void remappedStartUsesHostPauseAndRecordedStartStillUsesNativePause() {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.START, org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE);
        var input = new InputHandler(InputBindingFactory.supplier(config));
        var playback = mock(PlaybackDebugManager.class);
        input.handleKeyEvent(config.getInt(SonicConfiguration.START), GLFW_PRESS);
        input.refreshLogicalSnapshot();
        assertTrue(GameLoopPauseInput.nextUserPaused(GameMode.LEVEL, input, config,
                false, false, false, playback, () -> false));
        assertFalse(GameLoopPauseInput.nativeStartEdge(input, playback));
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()));
        try (var lease = RecordedInputOwnership.acquire(input)) {
            assertTrue(GameLoopPauseInput.nativeStartEdge(input, playback));
        }
    }

    @Test
    void forcedPlaybackStartReachesNativeAdmissionWithoutBecomingHostPause() {
        var input = new InputHandler();
        var playback = mock(PlaybackDebugManager.class);
        org.mockito.Mockito.when(playback.isCurrentForcedStartPress()).thenReturn(true);
        assertTrue(GameLoopPauseInput.nativeStartEdge(input, playback));
        assertFalse(GameLoopPauseInput.nextUserPaused(GameMode.LEVEL, input,
                SonicConfigurationService.createStandalone(), false, false, false, playback, () -> false));
        org.mockito.Mockito.when(playback.isCurrentForcedStartPress()).thenReturn(false);
        assertFalse(GameLoopPauseInput.nativeStartEdge(input, playback));
    }

    private static boolean nextPaused(GameMode mode, InputHandler input) {
        return GameLoopPauseInput.nextUserPaused(mode, input, SonicConfigurationService.createStandalone(),
                false, false, false, mock(PlaybackDebugManager.class), () -> false);
    }
}
