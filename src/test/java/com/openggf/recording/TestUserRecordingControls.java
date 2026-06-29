package com.openggf.recording;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.MasterTitleScreen;
import com.openggf.recording.menu.UserRecordingMenu;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F10;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

class TestUserRecordingControls {
    @TempDir
    Path tempDir;

    @Test
    void holdReachesLauncherExactlyAtSixtyFrames() {
        Fixture fixture = new Fixture();
        InputHandler input = recordingChord();

        for (int frame = 0; frame < 59; frame++) {
            fixture.controls.updateLevelControlInput(input);
            assertEquals(0, fixture.beginRecordingCalls);
            assertTrue(fixture.controls.hudState().visible());
        }

        fixture.controls.updateLevelControlInput(input);

        assertEquals(1, fixture.beginRecordingCalls);
        assertEquals(60, fixture.controls.recordHoldFrames());
    }

    @Test
    void releaseAtFiftyNineCancelsHoldWithoutStarting() {
        Fixture fixture = new Fixture();
        InputHandler input = recordingChord();

        for (int frame = 0; frame < 59; frame++) {
            fixture.controls.updateLevelControlInput(input);
        }
        input.handleKeyEvent(fixture.recordKey, GLFW_RELEASE);
        fixture.controls.updateLevelControlInput(input);

        assertEquals(0, fixture.beginRecordingCalls);
        assertEquals(0, fixture.controls.recordHoldFrames());
        assertFalse(fixture.controls.hudState().visible());
    }

    @Test
    void plainRecordStopsActiveRecording() {
        Fixture fixture = new Fixture();
        fixture.activeRecording = true;
        InputHandler input = new InputHandler();
        input.handleKeyEvent(fixture.recordKey, GLFW_PRESS);

        fixture.controls.updateLevelControlInput(input);

        assertEquals(UserRecordingStopReason.USER_STOPPED, fixture.stopReason);
        assertFalse(fixture.activeRecording);
    }

    @Test
    void fastForwardRenderSuppressionClearsOnDesyncAndCompletion() {
        Fixture fixture = new Fixture();
        fixture.playbackOptions = new UserRecordingPlaybackOptions(120, true, true);
        fixture.playbackState = UserRecordingPlaybackState.PLAYING;

        assertTrue(fixture.controls.shouldSuppressSceneRendering());

        fixture.desynced = true;
        fixture.controls.afterPlaybackFrame(42, false, false);

        assertEquals(UserRecordingPlaybackState.PAUSED_ON_DESYNC, fixture.playbackState);
        assertTrue(fixture.enginePausedForPlayback);
        assertFalse(fixture.controls.shouldSuppressSceneRendering());

        fixture = new Fixture();
        fixture.playbackOptions = new UserRecordingPlaybackOptions(120, true, true);
        fixture.playbackState = UserRecordingPlaybackState.PLAYING;
        fixture.controls.afterPlaybackFrame(99, true, true);

        assertEquals(UserRecordingPlaybackState.PAUSED_AT_COMPLETION, fixture.playbackState);
        assertFalse(fixture.controls.shouldSuppressSceneRendering());
    }

    @Test
    void masterTitleShiftRecordUsesConfiguredRecordKey() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.RECORDING_RECORD_KEY, GLFW_KEY_F10);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        MasterTitleScreen screen = new MasterTitleScreen(config);
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        screen.setUserRecordingMenuFactoryForTest((gameId, font) -> new UserRecordingMenu(
                gameId, List.of(), font, (entry, options) -> { }));
        InputHandler input = new InputHandler();

        input.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);
        screen.update(input);
        assertFalse(screen.isUserRecordingMenuOpenForTest());

        input.handleKeyEvent(GLFW_KEY_TAB, GLFW_RELEASE);
        input.update();
        input.handleKeyEvent(GLFW_KEY_F10, GLFW_PRESS);
        screen.update(input);

        assertTrue(screen.isUserRecordingMenuOpenForTest());
    }

    private InputHandler recordingChord() {
        InputHandler input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_F10, GLFW_PRESS);
        return input;
    }

    private final class Fixture implements UserRecordingRuntimeControls.Runtime {
        final int recordKey = GLFW_KEY_F10;
        boolean activeRecording;
        int beginRecordingCalls;
        UserRecordingStopReason stopReason;
        UserRecordingPlaybackOptions playbackOptions;
        UserRecordingPlaybackState playbackState = UserRecordingPlaybackState.STOPPED;
        boolean desynced;
        boolean enginePausedForPlayback;
        final UserRecordingRuntimeControls controls = new UserRecordingRuntimeControls(this);

        @Override
        public int recordKey() {
            return recordKey;
        }

        @Override
        public GameMode currentGameMode() {
            return GameMode.LEVEL;
        }

        @Override
        public boolean traceOrDebugSurfaceOwnsRecordingInput() {
            return false;
        }

        @Override
        public boolean hasActiveRecording() {
            return activeRecording;
        }

        @Override
        public void beginRecordingFromCurrentLevel() {
            beginRecordingCalls++;
        }

        @Override
        public void stopActiveRecording(UserRecordingStopReason reason) {
            stopReason = reason;
            activeRecording = false;
        }

        @Override
        public UserRecordingPlaybackOptions activePlaybackOptions() {
            return playbackOptions;
        }

        @Override
        public UserRecordingPlaybackState activePlaybackState() {
            return playbackState;
        }

        @Override
        public boolean playbackHasDesynced() {
            return desynced;
        }

        @Override
        public void updatePlaybackState(UserRecordingPlaybackState state) {
            playbackState = state;
        }

        @Override
        public void pauseEngineForPlayback() {
            enginePausedForPlayback = true;
        }

        @Override
        public void endPlaybackDebugSession() {
        }
    }
}
