package com.openggf.tests;

import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shared entry-matrix replay drives ordinary held Right. Its earlier
 * argument order pressed Down instead (a54c1a8ae..0197c5a5a), so P1 crouched in
 * place; this observes the production joypad path and ordinary ground movement.
 * CNZ2 Sonic starts grounded on flat floor; MGZ1's start is airborne by design.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCanonicalEntryRightInput {
    private final CanonicalEntryMatrixSupport support = new CanonicalEntryMatrixSupport() {
        @Override TestS3kCanonicalEntryLifecycle.Act binding() {
            return new TestS3kCanonicalEntryLifecycle.Act("CNZ2", 3, 1);
        }
    };
    @BeforeEach void saveConfiguration() { support.saveConfiguration(); }
    @AfterEach void restoreConfiguration() { support.restoreConfiguration(); }

    @Test
    void entryReplayInputHoldsRightAndWalksTheMainPlayerForward() {
        var row = new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, "off", "");
        var fixture = support.boot(row);
        support.advanceEntry(fixture, row, 6, false);
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
        int startX = GameServices.sprites().getMainPlayable().getCentreX();
        for (int frame = 0; frame < 45; frame++) {
            support.advanceEntry(fixture, row, 1, true);
            var main = GameServices.sprites().getMainPlayable();
            String at = "Right frame " + frame;
            assertTrue(main.isRightPressed(), at);
            assertFalse(main.isDownPressed(), at);
            assertFalse(main.isLeftPressed(), at);
            assertFalse(main.isUpPressed(), at);
            assertFalse(main.getCrouching(), at);
        }
        var main = GameServices.sprites().getMainPlayable();
        assertFalse(main.getAir(), "flat CNZ2 start floor");
        // Sonic_Move: button_right in Ctrl_1_held_logical adds Acceleration ($C,
        // Sonic init) to ground_vel each frame, below Max_speed $600 here.
        assertEquals(45 * 0xC, main.getGSpeed(), "held Right accelerates P1 along the ground");
        assertTrue(main.getCentreX() > startX,
                "held Right moves P1 forward: x " + startX + " -> " + main.getCentreX());
    }
}
