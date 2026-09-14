package com.openggf.game.sonic2.kis2;

import com.openggf.control.PlayerInputState;
import com.openggf.control.InputActionMasks;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestKis2TitleInput {
    private static final int U = AbstractPlayableSprite.INPUT_UP, D = AbstractPlayableSprite.INPUT_DOWN;
    private static final int L = AbstractPlayableSprite.INPUT_LEFT, R = AbstractPlayableSprite.INPUT_RIGHT;
    private static PlayerInputState press(int direction) { return PlayerInputState.of(direction, direction, 0, 0, false, false); }

    @Test void exactEdgesUnlockOnlyAHeldAndWrongOrSimultaneousDirectionsReset() {
        var input = new Kis2TitleInput();
        var a = PlayerInputState.of(0,0,InputActionMasks.ACTION_A,0,true,true);
        assertFalse(input.requestsLevelSelect(a));
        input.update(press(U)); input.update(press(U | R));
        assertFalse(input.update(press(U)));
        assertFalse(input.update(press(U)));
        // Held directions without a new press do not advance the code.
        assertFalse(input.update(PlayerInputState.of(U,0,0,0,false,false)));
        assertFalse(input.update(press(U)));
        for (int direction : new int[]{D,D,D,L,R,L}) assertFalse(input.update(press(direction)));
        assertTrue(input.update(press(R)));
        assertTrue(input.requestsLevelSelect(a));
        assertFalse(input.requestsLevelSelect(PlayerInputState.of(0,0,InputActionMasks.ACTION_B,0,true,true)));
        input.update(press(D));
        assertTrue(input.requestsLevelSelect(a), "wrong later inputs do not clear the unlocked flag");
    }
}
