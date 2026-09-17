package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step 2a-2 measured against real Death Egg terrain: the five reverse-gravity push-out
 * sites in {@code SonicKnux_DoLevelCollision}.
 *
 * <p>The corridor. {@code DEZ2_Sprites} ($1FA188, 495 records) places an
 * {@code Obj_DEZGravitySwitch} ({@code $58}) at x=$1AAC, y=$0588 — one of the five in act 2.
 * Terrain either side of it was measured with {@code ObjectTerrainUtils}: at <b>x=$1ACC</b>
 * the floor surface is <b>y=$055F</b> and the ceiling surface <b>y=$051F</b>, a 64 px gap
 * that a 19 px-radius player fits inside with room to fall either way. Both are terrain,
 * not objects, which is what makes this the fixture the rest of slice 2 needs: the level
 * designers put solid floor <em>and</em> solid ceiling wherever gravity is meant to flip.
 * {@link #theCorridorIsStillWhereItWasMeasured()} re-derives all four numbers from the
 * level data, so the fixture fails loudly rather than silently drifting.
 *
 * <p>The flag is seeded through {@code GameStateManager.setReverseGravityActive}, declared
 * test setup: {@code $58} itself is a placeholder until slice 3, so nothing in the running
 * game writes the flag yet.
 *
 * <p><strong>The inverted cases are deliberately absent.</strong> They were written, run and
 * failed, and the cause is not reverse gravity: the player's upward (ceiling) sensors return
 * nothing anywhere in Death Egg act 2. See "Upward player sensors never fire in Death Egg act 2"
 * in {@code docs/status/s3k-known-bugs.md}. Until that is resolved there is no way to land an
 * inverted player on terrain, so asserting it here would only encode the gap as a pass.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kReverseGravityDezCorridor {

    /** Measured 2026-09-17 from the act 2 collision data; see the class javadoc. */
    private static final int CORRIDOR_X = 0x1ACC;
    private static final int FLOOR_SURFACE_Y = 0x055F;
    private static final int CEILING_SURFACE_Y = 0x051F;
    private static final int PLAYER_Y_RADIUS = 19;

    /** Centre Y at which the player rests on each surface. */
    private static final int RESTING_ON_FLOOR_Y = FLOOR_SURFACE_Y - PLAYER_Y_RADIUS;    // $054C
    private static final int RESTING_ON_CEILING_Y = CEILING_SURFACE_Y + PLAYER_Y_RADIUS; // $0532

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /** Break-the-fixture guard: the corridor must still be where it was measured. */
    @Test
    void theCorridorIsStillWhereItWasMeasured() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();

        // Just above the floor: the probe sees it below and is not inside it.
        TerrainCheckResult aboveFloor = ObjectTerrainUtils.checkFloorDist(
                CORRIDOR_X, FLOOR_SURFACE_Y - PLAYER_Y_RADIUS - 4, PLAYER_Y_RADIUS);
        assertEquals(4, aboveFloor.distance(), "floor surface should sit at y=$055F");

        // Just below the ceiling, likewise.
        TerrainCheckResult belowCeiling = ObjectTerrainUtils.checkCeilingDist(
                CORRIDOR_X, CEILING_SURFACE_Y + PLAYER_Y_RADIUS + 4, PLAYER_Y_RADIUS);
        assertEquals(4, belowCeiling.distance(), "ceiling surface should sit at y=$051F");

        assertTrue(ObjectTerrainUtils.checkFloorDist(
                        CORRIDOR_X, RESTING_ON_FLOOR_Y + 8, PLAYER_Y_RADIUS).hasCollision(),
                "the floor must be solid terrain, not an object");
        assertTrue(ObjectTerrainUtils.checkCeilingDist(
                        CORRIDOR_X, RESTING_ON_CEILING_Y - 8, PLAYER_Y_RADIUS).hasCollision(),
                "the ceiling must be solid terrain, not an object");
    }

    /**
     * Positive control: upright, a falling player lands on the corridor floor.
     * {@code loc_11F9C} (sonic3k.asm:24098-24102) zeroes {@code y_vel} and copies
     * {@code x_vel} into {@code ground_vel} on a flat landing.
     */
    @Test
    void uprightGravityLandsOnTheCorridorFloor() {
        Outcome outcome = fall(false, RESTING_ON_FLOOR_Y - 12, 0x0400);
        assertFalse(outcome.air, "the corridor floor must catch an upright falling player");
        assertEquals(0, outcome.yVel, "loc_11F9C: move.w #0,y_vel(a0)");
        assertEquals(RESTING_ON_FLOOR_Y, outcome.centreY,
                "landing snaps the player to the floor surface less its y_radius");
    }

    private record Outcome(boolean air, int yVel, int centreY) { }

    private Outcome fall(boolean reverseGravity, int startCentreY, int yVel) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(reverseGravity);

            NativePositionOps.writeXPosResetSubpixel(sprite, CORRIDOR_X);
            NativePositionOps.writeYPosResetSubpixel(sprite, startCentreY);
            sprite.setAir(true);
            sprite.setOnObject(false);
            sprite.setXSpeed((short) 0);
            sprite.setGSpeed((short) 0);
            sprite.setYSpeed((short) yVel);
            assertFalse(sprite.isObjectControlSuppressesMovement(),
                    "the corridor probe must own the player's movement");

            for (int frame = 0; frame < 8 && sprite.getAir(); frame++) {
                fixture.stepIdleFrames(1);
            }
            return new Outcome(sprite.getAir(), sprite.getYSpeed(), sprite.getCentreY());
        } finally {
            SessionManager.clear();
        }
    }
}
