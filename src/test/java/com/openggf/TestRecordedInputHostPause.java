package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
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

    private static boolean nextPaused(GameMode mode, InputHandler input) {
        return GameLoopPauseInput.nextUserPaused(mode, input, SonicConfigurationService.createStandalone(),
                false, false, false, mock(PlaybackDebugManager.class), () -> false);
    }
}
