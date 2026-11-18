package com.openggf.game.soniccd.player;

import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_JUMP;
import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCdHurtLogicalInput {
    @Test
    void recoveryCurrentUsesTheHeldDirectionFromBeforeHurt() {
        var player = new TestablePlayableSprite("sonic", (short) 32, (short) 32);
        player.setGameRulesForTest(SonicCdRules.RULES);
        var water = new CdPlayerWater();
        var currents = List.of(new CdWaterCurrent(0, 0, 128, 128, 0x400, 0, 0, 0));
        int x = player.getCentreX();

        player.setJumpInputPressed(true, true);
        player.setDirectionalInputPressed(false, false, false, true);
        player.setLogicalInputState(false, false, false, true, true, true);

        player.setHurt(true);
        player.setJumpInputPressed(false, false);
        player.setDirectionalInputPressed(false, false, true, false);
        player.setLogicalInputState(false, false, true, false, false, false);
        assertTrue(player.isLeftPressed());
        assertFalse(player.isRightPressed());
        assertFalse(player.isJumpPressed());
        assertFalse(player.isRawControllerJumpJustPressed());
        assertEquals(INPUT_RIGHT | INPUT_JUMP, player.getLogicalInputState());
        assertTrue(player.isLogicalJumpPressActive());
        water.checkCurrents(player, currents, i -> 0);
        assertFalse(water.currentActive());
        assertEquals(x, player.getCentreX());

        // The pad is sampled before the hurt pass lands. The later current sees routine 2.
        player.setDirectionalInputPressed(false, false, false, false);
        player.setLogicalInputState(false, false, false, false, false, false);
        player.setHurt(false);
        assertFalse(player.isLeftPressed());
        assertEquals(INPUT_RIGHT | INPUT_JUMP, player.getLogicalInputState());
        assertTrue(player.isLogicalJumpPressActive());
        water.checkCurrents(player, currents, i -> 0);
        assertTrue(water.currentActive());
        assertEquals(0x400, player.getXSpeed());
        assertEquals(x + 1, player.getCentreX());

        player.setLogicalInputState(false, false, false, false, false, false);
        assertEquals(0, player.getLogicalInputState());
        assertFalse(player.isLogicalJumpPressActive());
        water.checkCurrents(player, currents, i -> 0);
        assertEquals(x + 1, player.getCentreX());
    }
}
