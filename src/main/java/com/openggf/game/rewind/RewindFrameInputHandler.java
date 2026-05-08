package com.openggf.game.rewind;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Objects;

/**
 * Read-only {@link InputHandler} view over one recorded rewind input row.
 */
final class RewindFrameInputHandler extends InputHandler {

    private final SonicConfigurationService config;
    private final Bk2FrameInput current;
    private final Bk2FrameInput previous;

    RewindFrameInputHandler(SonicConfigurationService config,
                            Bk2FrameInput current,
                            Bk2FrameInput previous) {
        this.config = Objects.requireNonNull(config, "config");
        this.current = Objects.requireNonNull(current, "current");
        this.previous = Objects.requireNonNull(previous, "previous");
    }

    @Override
    public boolean isKeyDown(int keyCode) {
        return matchesHeld(keyCode, SonicConfiguration.UP, current.p1InputMask(), AbstractPlayableSprite.INPUT_UP)
                || matchesHeld(keyCode, SonicConfiguration.DOWN, current.p1InputMask(), AbstractPlayableSprite.INPUT_DOWN)
                || matchesHeld(keyCode, SonicConfiguration.LEFT, current.p1InputMask(), AbstractPlayableSprite.INPUT_LEFT)
                || matchesHeld(keyCode, SonicConfiguration.RIGHT, current.p1InputMask(), AbstractPlayableSprite.INPUT_RIGHT)
                || matchesHeld(keyCode, SonicConfiguration.JUMP, current.p1InputMask(), AbstractPlayableSprite.INPUT_JUMP)
                || matchesHeld(keyCode, SonicConfiguration.P2_UP, current.p2InputMask(), AbstractPlayableSprite.INPUT_UP)
                || matchesHeld(keyCode, SonicConfiguration.P2_DOWN, current.p2InputMask(), AbstractPlayableSprite.INPUT_DOWN)
                || matchesHeld(keyCode, SonicConfiguration.P2_LEFT, current.p2InputMask(), AbstractPlayableSprite.INPUT_LEFT)
                || matchesHeld(keyCode, SonicConfiguration.P2_RIGHT, current.p2InputMask(), AbstractPlayableSprite.INPUT_RIGHT)
                || matchesHeld(keyCode, SonicConfiguration.P2_JUMP, current.p2InputMask(), AbstractPlayableSprite.INPUT_JUMP);
    }

    @Override
    public boolean isKeyPressed(int keyCode) {
        return matchesPressed(keyCode, SonicConfiguration.UP, current.p1InputMask(), previous.p1InputMask(),
                        AbstractPlayableSprite.INPUT_UP)
                || matchesPressed(keyCode, SonicConfiguration.DOWN, current.p1InputMask(), previous.p1InputMask(),
                        AbstractPlayableSprite.INPUT_DOWN)
                || matchesPressed(keyCode, SonicConfiguration.LEFT, current.p1InputMask(), previous.p1InputMask(),
                        AbstractPlayableSprite.INPUT_LEFT)
                || matchesPressed(keyCode, SonicConfiguration.RIGHT, current.p1InputMask(), previous.p1InputMask(),
                        AbstractPlayableSprite.INPUT_RIGHT)
                || matchesPressed(keyCode, SonicConfiguration.JUMP, current.p1InputMask(), previous.p1InputMask(),
                        AbstractPlayableSprite.INPUT_JUMP)
                || matchesPressed(keyCode, SonicConfiguration.P2_UP, current.p2InputMask(), previous.p2InputMask(),
                        AbstractPlayableSprite.INPUT_UP)
                || matchesPressed(keyCode, SonicConfiguration.P2_DOWN, current.p2InputMask(), previous.p2InputMask(),
                        AbstractPlayableSprite.INPUT_DOWN)
                || matchesPressed(keyCode, SonicConfiguration.P2_LEFT, current.p2InputMask(), previous.p2InputMask(),
                        AbstractPlayableSprite.INPUT_LEFT)
                || matchesPressed(keyCode, SonicConfiguration.P2_RIGHT, current.p2InputMask(), previous.p2InputMask(),
                        AbstractPlayableSprite.INPUT_RIGHT)
                || matchesPressed(keyCode, SonicConfiguration.P2_JUMP, current.p2InputMask(), previous.p2InputMask(),
                        AbstractPlayableSprite.INPUT_JUMP)
                || (keyCode == config.getInt(SonicConfiguration.P2_START) && current.p2StartPressed());
    }

    private boolean matchesHeld(int keyCode, SonicConfiguration key, int mask, int bit) {
        return keyCode == config.getInt(key) && (mask & bit) != 0;
    }

    private boolean matchesPressed(int keyCode, SonicConfiguration key, int currentMask, int previousMask, int bit) {
        return keyCode == config.getInt(key)
                && (currentMask & bit) != 0
                && (previousMask & bit) == 0;
    }
}
