package com.openggf.game.rewind;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

class TestLiveRewindInputSource {

    private SonicConfigurationService config;

    @BeforeEach
    void resetConfig() {
        config = SonicConfigurationService.getInstance();
        config.resetToDefaults();
    }

    @Test
    void startsWithFrameZeroSoRewindControllerHasAnInitialInput() {
        LiveRewindInputSource source = new LiveRewindInputSource();

        assertEquals(1, source.frameCount());
        assertEquals(0, source.read(0).frameIndex());
        assertEquals(0, source.read(0).p1InputMask());
    }

    @Test
    void appendFrameRecordsP1HeldButtonsAndJumpPressEdge() {
        InputHandler input = new InputHandler();
        LiveRewindInputSource source = new LiveRewindInputSource();

        input.handleKeyEvent(config.getInt(SonicConfiguration.RIGHT), GLFW_PRESS);
        input.handleKeyEvent(config.getInt(SonicConfiguration.JUMP), GLFW_PRESS);
        source.appendFrame(input, config);

        Bk2FrameInput frame = source.read(1);
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP,
                frame.p1InputMask());
        assertEquals(1, frame.p1ActionMask(),
                "P1 action mask should mark a fresh jump press for replay");
    }

    @Test
    void appendFrameDoesNotRepeatJumpPressAfterInputHandlerUpdate() {
        InputHandler input = new InputHandler();
        LiveRewindInputSource source = new LiveRewindInputSource();

        input.handleKeyEvent(config.getInt(SonicConfiguration.JUMP), GLFW_PRESS);
        source.appendFrame(input, config);
        input.update();
        source.appendFrame(input, config);

        assertEquals(1, source.read(1).p1ActionMask());
        assertEquals(0, source.read(2).p1ActionMask());
    }

    @Test
    void appendFrameRecordsP2HeldButtonsAndStartPressEdge() {
        InputHandler input = new InputHandler();
        LiveRewindInputSource source = new LiveRewindInputSource();

        input.handleKeyEvent(config.getInt(SonicConfiguration.P2_LEFT), GLFW_PRESS);
        input.handleKeyEvent(config.getInt(SonicConfiguration.P2_START), GLFW_PRESS);
        source.appendFrame(input, config);

        Bk2FrameInput frame = source.read(1);
        assertEquals(AbstractPlayableSprite.INPUT_LEFT, frame.p2InputMask());
        assertEquals(true, frame.p2StartPressed());

        input.update();
        source.appendFrame(input, config);

        assertEquals(false, source.read(2).p2StartPressed());
    }

    @Test
    void appendFrameRecordsDebugToggleEdgeAndDebugMovementModifiers() {
        InputHandler input = new InputHandler();
        LiveRewindInputSource source = new LiveRewindInputSource();

        input.handleKeyEvent(config.getInt(SonicConfiguration.DEBUG_MODE_KEY), GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_LEFT_CONTROL, GLFW_PRESS);
        source.appendFrame(input, config);

        Bk2FrameInput first = source.read(1);
        assertTrue(first.debugModeTogglePressed());
        assertTrue(first.debugShiftDown());
        assertTrue(first.debugControlDown());

        input.update();
        source.appendFrame(input, config);

        Bk2FrameInput held = source.read(2);
        assertFalse(held.debugModeTogglePressed());
        assertTrue(held.debugShiftDown());
        assertTrue(held.debugControlDown());
    }

    @Test
    void rewindFrameInputHandlerReconstructsDebugToggleAndMovementModifiers() {
        Bk2FrameInput previous = new Bk2FrameInput(0, 0, 0, false, 0, 0, false, "previous");
        Bk2FrameInput current = new Bk2FrameInput(
                1, 0, 0, false, 0, 0, false,
                true, true, true, "current");

        RewindFrameInputHandler replay = new RewindFrameInputHandler(config, current, previous);

        assertTrue(replay.isKeyPressed(config.getInt(SonicConfiguration.DEBUG_MODE_KEY)));
        assertTrue(replay.isShiftDown());
        assertTrue(replay.isControlDown());
    }

    @Test
    void discardAfterDropsFutureFramesWhenLivePlaybackBranchesFromRewind() {
        InputHandler input = new InputHandler();
        LiveRewindInputSource source = new LiveRewindInputSource();

        source.appendFrame(input, config);
        input.handleKeyEvent(config.getInt(SonicConfiguration.LEFT), GLFW_PRESS);
        source.appendFrame(input, config);
        source.discardAfter(1);
        input.handleKeyEvent(config.getInt(SonicConfiguration.LEFT), GLFW_RELEASE);
        input.handleKeyEvent(config.getInt(SonicConfiguration.RIGHT), GLFW_PRESS);
        source.appendFrame(input, config);

        assertEquals(3, source.frameCount());
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, source.read(2).p1InputMask());
    }
}
