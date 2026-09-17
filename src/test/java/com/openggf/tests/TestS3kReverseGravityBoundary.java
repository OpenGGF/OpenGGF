package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.camera.Camera;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The death plane moves to the top of the level while the S3K
 * {@code Reverse_gravity_flag} ($FFFFF7C6) is set.
 *
 * <p>{@code Player_Boundary_CheckBottom} (sonic3k.asm:23188-23206) tests the flag after
 * {@code Disable_death_plane} and branches to {@code loc_11722}, which is
 * {@code move.w (Camera_min_Y_pos).w,d0 / cmp.w y_pos(a0),d0 / blt.s <alive>}: the
 * player survives while {@code Camera_min_Y_pos &lt; y_pos} and is killed at or above it.
 * There is no {@code $E0} offset on that side — the upright branch's
 * {@code Camera_max_Y_pos + $E0} has no counterpart.
 *
 * <p>{@code Tails_Check_Screen_Boundaries} {@code loc_14F30}/{@code loc_14F4C}
 * (:28423-28441) is the same code for the sidekick, which is why the engine's single
 * shared boundary owner covers both ROM rows.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kReverseGravityBoundary {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /** Positive control: the upright kill plane still fires, so the gate under test is live. */
    @Test
    void uprightGravityStillKillsBelowTheBottomBoundary() {
        assertTrue(fallsPastTheBoundaryAndDies(false, Edge.BELOW_BOTTOM),
                "Camera_max_Y_pos + $E0 is the upright death plane");
    }

    /** Upright, the top of the level is not fatal — the player just leaves the screen. */
    @Test
    void uprightGravityDoesNotKillAboveTheTopBoundary() {
        assertFalse(fallsPastTheBoundaryAndDies(false, Edge.ABOVE_TOP),
                "Player_Boundary_CheckBottom has no top-side test without the flag");
    }

    /** With the flag set the top becomes the death plane (loc_11722). */
    @Test
    void reverseGravityKillsAtTheTopBoundary() {
        assertTrue(fallsPastTheBoundaryAndDies(true, Edge.ABOVE_TOP),
                "loc_11722 kills once y_pos reaches Camera_min_Y_pos");
    }

    /** And the bottom stops being fatal: the ROM branches away before the $E0 test. */
    @Test
    void reverseGravitySparesTheBottomBoundary() {
        assertFalse(fallsPastTheBoundaryAndDies(true, Edge.BELOW_BOTTOM),
                "the flag test branches to loc_11722 before Camera_max_Y_pos is read");
    }

    private enum Edge { ABOVE_TOP, BELOW_BOTTOM }

    private boolean fallsPastTheBoundaryAndDies(boolean reverseGravity, Edge edge) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .build();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            Camera camera = fixture.camera();
            GameServices.gameState().setReverseGravityActive(reverseGravity);

            int y = edge == Edge.ABOVE_TOP
                    ? camera.getMinY() - 8
                    : Math.max(camera.getMaxY(), camera.getMaxYTarget()) + 224 + 8;
            sprite.setAir(true);
            sprite.setCentreY((short) y);
            sprite.setXSpeed((short) 0);
            sprite.setYSpeed((short) 0);

            fixture.stepIdleFrames(1);
            return sprite.getDead();
        } finally {
            SessionManager.clear();
        }
    }
}
