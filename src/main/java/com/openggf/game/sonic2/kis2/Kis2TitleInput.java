package com.openggf.game.sonic2.kis2;

import com.openggf.control.InputActionMasks;
import com.openggf.control.PlayerInputState;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** TailsNameCheat's KiS2 branch: directional press edges unlock A+Start level select. */
final class Kis2TitleInput {
    private static final int U = AbstractPlayableSprite.INPUT_UP, D = AbstractPlayableSprite.INPUT_DOWN;
    private static final int L = AbstractPlayableSprite.INPUT_LEFT, R = AbstractPlayableSprite.INPUT_RIGHT;
    private static final int[] CODE = {U, U, U, D, D, D, L, R, L, R};
    private int matched;
    private boolean unlocked;

    void resetSequence() { matched = 0; }

    boolean update(PlayerInputState player) {
        int direction = player.pressedMask() & (U | D | L | R);
        if (direction == 0) return false;
        if (direction != CODE[matched]) {
            matched = 0;
            return false;
        }
        if (++matched != CODE.length) return false;
        matched = 0;
        unlocked = true;
        return true;
    }

    boolean requestsLevelSelect(PlayerInputState player) {
        return unlocked && (player.actionHeldMask() & InputActionMasks.ACTION_A) != 0;
    }
}
