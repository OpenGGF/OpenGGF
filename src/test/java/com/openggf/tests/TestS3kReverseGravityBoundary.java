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

    /**
     * {@code sub_12318} (sonic3k.asm:24471-24491) is the hurt routine's own kill test,
     * run before it hands off to the terrain pass. Under the flag it branches to
     * {@code loc_12336} and reads {@code Camera_min_Y_pos} with no {@code $E0} offset;
     * {@code sub_15716} (:29220) and {@code sub_17C10} (:32911) are the Tails and
     * Knuckles copies.
     *
     * <p><strong>This pair asserts the outcome, not the owner.</strong> Measured
     * 2026-09-17 by disabling the {@code applyHurtStopBottomKill} flag branch: both still
     * pass, because {@code Player_LevelBound}'s own kill plane fires later in the same
     * frame and the engine has no observable difference between the two. They are kept as
     * behaviour guards; the hurt-site branch itself stays uncredited in the reference
     * table for that reason.
     */
    @Test
    void reverseGravityKillsAHurtPlayerAtTheTopBoundary() {
        assertTrue(fallsPastTheBoundaryAndDies(true, Edge.ABOVE_TOP, true),
                "loc_12336 kills a hurt player once y_pos reaches Camera_min_Y_pos");
    }

    /** Upright, the hurt routine's test looks at the bottom only. */
    @Test
    void uprightGravitySparesAHurtPlayerAtTheTopBoundary() {
        assertFalse(fallsPastTheBoundaryAndDies(false, Edge.ABOVE_TOP, true),
                "sub_12318's upright branch has no top-side test");
    }

    private enum Edge { ABOVE_TOP, BELOW_BOTTOM }

    private boolean fallsPastTheBoundaryAndDies(boolean reverseGravity, Edge edge) {
        return fallsPastTheBoundaryAndDies(reverseGravity, edge, false);
    }

    private boolean fallsPastTheBoundaryAndDies(boolean reverseGravity, Edge edge, boolean hurt) {
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
            sprite.setHurt(hurt);

            fixture.stepIdleFrames(1);
            return sprite.getDead();
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_123DE} (sonic3k.asm:24549-24560) is the <em>dead</em> player's own
     * off-screen test, the one that spends the life and restarts the act. Upright it is
     * {@code addi.w #$100,d0 / cmp.w y_pos(a0),d0 / bge locret}: the restart waits until
     * the corpse has fallen {@code $100} <em>below</em> {@code Camera_Y_pos}. Under the
     * flag the ROM branches first and rebuilds it as {@code subi.w #$10,d0 / cmp.w
     * y_pos(a0),d0 / bge loc_12410}, so the restart fires once the corpse has risen to
     * {@code Camera_Y_pos - $10} — off the <em>top</em> of the screen, which is the only
     * direction an inverted corpse can leave, because {@code MoveSprite_TestGravity}
     * integrates its growing positive {@code y_vel} upward.
     *
     * <p>Note the offsets are not mirrors of each other: {@code $100} against {@code $10}.
     * The reverse-gravity side has no competition-mode {@code $70} adjustment either.
     */
    @Test
    void reverseGravityRestartsOnceTheCorpseLeavesTheTopOfTheScreen() {
        assertTrue(corpseTriggersTheRestart(true, Corner.ABOVE_CAMERA),
                "loc_123DE: Camera_Y_pos - $10 is the inverted restart row");
    }

    /** And the bottom row stops triggering it: the flag branch returns before {@code $100}. */
    @Test
    void reverseGravityDoesNotRestartFromTheBottomOfTheScreen() {
        assertFalse(corpseTriggersTheRestart(true, Corner.BELOW_CAMERA),
                "the flag test branches away before addi.w #$100,d0");
    }

    /** Positive control: upright the corpse still has to fall {@code $100} below the camera. */
    @Test
    void uprightGravityRestartsOnceTheCorpseLeavesTheBottomOfTheScreen() {
        assertTrue(corpseTriggersTheRestart(false, Corner.BELOW_CAMERA),
                "Camera_Y_pos + $100 is the upright restart row");
    }

    /** And upright, a corpse above the camera is not off-screen in the ROM's sense. */
    @Test
    void uprightGravityDoesNotRestartFromTheTopOfTheScreen() {
        assertFalse(corpseTriggersTheRestart(false, Corner.ABOVE_CAMERA),
                "loc_123DE's upright branch only looks downward");
    }

    private enum Corner { ABOVE_CAMERA, BELOW_CAMERA }

    /**
     * Places an already-dead player one pixel past the row under test and steps one frame.
     * The restart routine is the observable: {@code isInDeathRestartRoutine()} is what
     * {@code enterDeathRestartRoutine} sets after {@code loseLife}.
     */
    private boolean corpseTriggersTheRestart(boolean reverseGravity, Corner corner) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .build();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            Camera camera = fixture.camera();
            GameServices.gameState().setReverseGravityActive(reverseGravity);

            sprite.applyPitDeath();
            assertTrue(sprite.getDead(), "precondition: the player must be in routine 6");

            int reference = camera.getY();
            int y = corner == Corner.ABOVE_CAMERA
                    ? reference - 0x10 - 1
                    : reference + 0x100 + 1;
            sprite.setCentreY((short) y);
            sprite.setYSpeed((short) 0);

            fixture.stepIdleFrames(1);
            return sprite.isInDeathRestartRoutine();
        } finally {
            SessionManager.clear();
        }
    }
}
