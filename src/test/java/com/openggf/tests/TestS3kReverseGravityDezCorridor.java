package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step 2a-2 measured against real Death Egg terrain: the reverse-gravity floor snap and
 * ceiling push-out of {@code SonicKnux_DoLevelCollision} and its Tails and Knuckles twins.
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
 * <p><strong>Every inverted case is checked against its own upright control, measured in the
 * same corridor in the same test.</strong> Under {@code Reverse_gravity_flag} the ROM swaps
 * which routine each probe runs ({@code sub_11FD6}/{@code sub_11FEE}, sonic3k.asm:24127-24151)
 * and negates the Y adjustment at each push-out site, so an inverted landing on the ceiling
 * must come to rest exactly where an upright head-bonk does, and an inverted push-out off the
 * world floor exactly where an upright landing does. Comparing the two makes the assertion
 * independent of which of the engine's two terrain helpers owns the "surface" row.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kReverseGravityDezCorridor {

    /** Measured 2026-09-17 from the act 2 collision data; see the class javadoc. */
    private static final int CORRIDOR_X = 0x1ACC;
    private static final int FLOOR_SURFACE_Y = 0x055F;
    private static final int CEILING_SURFACE_Y = 0x051F;
    private static final int PROBE_Y_RADIUS = 19;

    /**
     * The first row below the ceiling that the ceiling <em>sensor</em> reads as clear — one
     * lower than {@code ObjectTerrainUtils.checkCeilingDist}'s zero-distance row. The two
     * helpers disagree by one pixel and it is the sensor that shipped play runs through
     * ({@code FindFloor}'s {@code eori.w #$F,d2} form, sonic3k.asm:20242-20256), so the
     * sensor defines the surface here. Recorded in the implementation-pitfall catalogue.
     */
    private static final int CEILING_CLEAR_Y = CEILING_SURFACE_Y + 1; // $0520

    /**
     * Half-width of the flat span around {@code CORRIDOR_X}. The ceiling steps down at
     * x=$1AE0, so the sideways cases are given a start close enough to the surface that
     * they land inside this span; the guard below fails if the span shrinks.
     */
    private static final int FLAT_SPAN = 16;

    private String savedMain;
    private String savedSidekicks;

    @AfterEach
    void tearDown() {
        if (savedMain != null || savedSidekicks != null) {
            SonicConfigurationService configuration = SonicConfigurationService.getInstance();
            configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                    savedMain == null ? "sonic" : savedMain);
            configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    savedSidekicks == null ? "tails" : savedSidekicks);
            savedMain = null;
            savedSidekicks = null;
        }
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    /** The three playable characters, each with its own {@code DoLevelCollision} routine. */
    enum Character {
        /** {@code SonicKnux_DoLevelCollision}, sonic3k.asm:24035. */
        SONIC("sonic"),
        /** {@code Tails_DoLevelCollision}, sonic3k.asm:28880. */
        TAILS("tails"),
        /** {@code Knux_DoLevelCollision}, sonic3k.asm:32630. */
        KNUCKLES("knuckles");

        private final String code;

        Character(String code) {
            this.code = code;
        }
    }

    /** Break-the-fixture guard: the corridor must still be where it was measured. */
    @Test
    void theCorridorIsStillWhereItWasMeasured() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();

        // Just above the floor: the probe sees it below and is not inside it.
        TerrainCheckResult aboveFloor = ObjectTerrainUtils.checkFloorDist(
                CORRIDOR_X, FLOOR_SURFACE_Y - PROBE_Y_RADIUS - 4, PROBE_Y_RADIUS);
        assertEquals(4, aboveFloor.distance(), "floor surface should sit at y=$055F");

        // Just below the ceiling, likewise.
        TerrainCheckResult belowCeiling = ObjectTerrainUtils.checkCeilingDist(
                CORRIDOR_X, CEILING_SURFACE_Y + PROBE_Y_RADIUS + 4, PROBE_Y_RADIUS);
        assertEquals(4, belowCeiling.distance(), "ceiling surface should sit at y=$051F");

        assertTrue(ObjectTerrainUtils.checkFloorDist(
                        CORRIDOR_X, FLOOR_SURFACE_Y - PROBE_Y_RADIUS + 8, PROBE_Y_RADIUS).hasCollision(),
                "the floor must be solid terrain, not an object");
        assertTrue(ObjectTerrainUtils.checkCeilingDist(
                        CORRIDOR_X, CEILING_SURFACE_Y + PROBE_Y_RADIUS - 8, PROBE_Y_RADIUS).hasCollision(),
                "the ceiling must be solid terrain, not an object");

        // The sideways cases travel in x while they fall, so both surfaces must be flat
        // across the span they can reach inside the fixture's frame budget.
        for (int x = CORRIDOR_X - FLAT_SPAN; x <= CORRIDOR_X + FLAT_SPAN; x++) {
            assertEquals(4, ObjectTerrainUtils.checkFloorDist(
                            x, FLOOR_SURFACE_Y - PROBE_Y_RADIUS - 4, PROBE_Y_RADIUS).distance(),
                    "floor must stay flat at x=$" + Integer.toHexString(x));
            assertEquals(4, ObjectTerrainUtils.checkCeilingDist(
                            x, CEILING_SURFACE_Y + PROBE_Y_RADIUS + 4, PROBE_Y_RADIUS).distance(),
                    "ceiling must stay flat at x=$" + Integer.toHexString(x));
        }
    }

    /**
     * Positive control: upright, a falling player lands on the corridor floor.
     * {@code loc_11F9C} (sonic3k.asm:24098-24102) zeroes {@code y_vel} and copies
     * {@code x_vel} into {@code ground_vel} on a flat landing.
     */
    @ParameterizedTest
    @EnumSource(Character.class)
    void uprightGravityLandsOnTheCorridorFloor(Character character) {
        int restY = FLOOR_SURFACE_Y - radiusOf(character);
        Outcome outcome = fall(character, false, restY - 12, 0x0400);
        assertFalse(outcome.air, "the corridor floor must catch an upright falling player");
        assertEquals(0, outcome.yVel, "loc_11F9C: move.w #0,y_vel(a0)");
        assertEquals(restY, outcome.centreY,
                "landing snaps the player to the floor surface less its y_radius");
    }

    /**
     * Inverted, a player "falling" (positive {@code y_vel}, which
     * {@code MoveSprite_TestGravity} integrates upward, sonic3k.asm:36073-36078) lands on
     * the corridor ceiling. {@code sub_11FD6} (:24127) has made the ceiling probe the floor
     * probe, and {@code loc_11F6E} (:24079-24084) negates the push-out before
     * {@code add.w d1,y_pos}; {@code loc_11F9C} (:24099) zeroes {@code y_vel}.
     *
     * <p>The expected rest position is the one an upright head-bonk produces in the same
     * corridor, measured here rather than asserted from a constant.
     */
    @ParameterizedTest
    @EnumSource(Character.class)
    void invertedGravityLandsOnTheCorridorCeiling(Character character) {
        int uprightBonkRest = rise(character, false, CEILING_CLEAR_Y + radiusOf(character) + 12, -0x0400).centreY;
        assertEquals(CEILING_CLEAR_Y + radiusOf(character), uprightBonkRest,
                "the upright control must separate to the sensor's first clear row");

        Outcome outcome = fall(character, true, uprightBonkRest + 12, 0x0400);
        assertFalse(outcome.air, "the corridor ceiling must catch an inverted falling player");
        assertEquals(0, outcome.yVel, "loc_11F9C: move.w #0,y_vel(a0)");
        assertEquals(uprightBonkRest, outcome.centreY,
                "the inverted landing rests where the upright head-bonk rests");
    }

    /**
     * The mirror of the case above. Inverted with negative {@code y_vel} the player moves
     * <em>down</em> the screen, toward the world floor, and meets it as a ceiling:
     * {@code Player_HitCeilingAndWalls} into {@code loc_120C2} (:24242-24252) runs
     * {@code sub_11FEE}, which under the flag is {@code Sonic_CheckFloor}, and negates the
     * push-out before {@code sub.w d1,y_pos}. The rest position is the upright landing's.
     */
    @ParameterizedTest
    @EnumSource(Character.class)
    void invertedGravityIsPushedOutOfTheCorridorFloor(Character character) {
        int floorRest = FLOOR_SURFACE_Y - radiusOf(character);
        Outcome outcome = rise(character, true, floorRest + 12, -0x0400);
        assertEquals(0, outcome.yVel, "loc_120D2: move.w #0,y_vel(a0) on a flat surface");
        assertEquals(floorRest, outcome.centreY,
                "the inverted push-out clears the world floor by the same margin as an upright landing");
    }

    /**
     * The two horizontal quadrants' floor sites. Moving mostly sideways,
     * {@code loc_12148} (:24304-24312, right) and {@code loc_12074} (:24209-24217, left)
     * run {@code sub_11FD6} — the ceiling probe under the flag — and negate the snap
     * before {@code add.w d1,y_pos}. Both must land the inverted player on the ceiling
     * exactly where the vertical quadrant does.
     */
    @ParameterizedTest
    @EnumSource(Character.class)
    void invertedGravityLandsOnTheCeilingWhileMovingSideways(Character character) {
        int ceilingRest = CEILING_CLEAR_Y + radiusOf(character);
        Outcome right = fall(character, true, ceilingRest + 4, 0x0100, 0x0600);
        assertFalse(right.air, "loc_12148: the right-moving quadrant must land on the ceiling");
        assertEquals(ceilingRest, right.centreY, "loc_12148 snap");

        Outcome left = fall(character, true, ceilingRest + 4, 0x0100, -0x0600);
        assertFalse(left.air, "loc_12074: the left-moving quadrant must land on the ceiling");
        assertEquals(ceilingRest, left.centreY, "loc_12074 snap");
    }

    /**
     * The same two quadrants' ceiling sites. {@code loc_1211A} (:24280-24288, right) and
     * {@code Player_HitCeiling} (:24171-24186, left) run {@code sub_11FEE} — the floor
     * probe under the flag — and negate the push-out before {@code sub.w d1,y_pos}.
     */
    @ParameterizedTest
    @EnumSource(Character.class)
    void invertedGravityIsPushedOffTheFloorWhileMovingSideways(Character character) {
        int floorRest = FLOOR_SURFACE_Y - radiusOf(character);
        Outcome right = rise(character, true, floorRest + 4, -0x0100, 0x0600);
        assertEquals(0, right.yVel, "loc_1212A: move.w #0,y_vel(a0)");
        assertEquals(floorRest, right.centreY, "loc_1211A push-out");

        Outcome left = rise(character, true, floorRest + 4, -0x0100, -0x0600);
        assertEquals(0, left.yVel, "locret_12052: move.w #0,y_vel(a0)");
        assertEquals(floorRest, left.centreY, "Player_HitCeiling push-out");
    }

    private int radiusOf(Character character) {
        HeadlessTestFixture fixture = fixtureFor(character);
        try {
            return fixture.sprite().getYRadius();
        } finally {
            SessionManager.clear();
        }
    }

    private record Outcome(boolean air, int yVel, int centreY) { }

    /** Steps until the player's downward-in-its-own-frame velocity stops being negative. */
    private Outcome rise(Character character, boolean reverseGravity, int startCentreY, int yVel) {
        return rise(character, reverseGravity, startCentreY, yVel, 0);
    }

    private Outcome rise(Character character, boolean reverseGravity, int startCentreY,
                         int yVel, int xVel) {
        return run(character, reverseGravity, startCentreY, yVel, xVel, s -> s.getYSpeed() >= 0);
    }

    /** Steps until the falling player is grounded. */
    private Outcome fall(Character character, boolean reverseGravity, int startCentreY, int yVel) {
        return fall(character, reverseGravity, startCentreY, yVel, 0);
    }

    private Outcome fall(Character character, boolean reverseGravity, int startCentreY,
                         int yVel, int xVel) {
        return run(character, reverseGravity, startCentreY, yVel, xVel, s -> !s.getAir());
    }

    private HeadlessTestFixture fixtureFor(Character character) {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        if (savedMain == null && savedSidekicks == null) {
            savedMain = configuration.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
            savedSidekicks = configuration.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        }
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, character.code);
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
    }

    private Outcome run(Character character, boolean reverseGravity, int startCentreY, int yVel,
                        int xVel,
                        java.util.function.Predicate<AbstractPlayableSprite> done) {
        HeadlessTestFixture fixture = fixtureFor(character);
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(reverseGravity);

            NativePositionOps.writeXPosResetSubpixel(sprite, CORRIDOR_X);
            NativePositionOps.writeYPosResetSubpixel(sprite, startCentreY);
            sprite.setAir(true);
            sprite.setOnObject(false);
            sprite.setXSpeed((short) xVel);
            sprite.setGSpeed((short) 0);
            sprite.setYSpeed((short) yVel);
            assertFalse(sprite.isObjectControlSuppressesMovement(),
                    "the corridor probe must own the player's movement");

            for (int frame = 0; frame < 8 && !done.test(sprite); frame++) {
                fixture.stepIdleFrames(1);
            }
            return new Outcome(sprite.getAir(), sprite.getYSpeed(), sprite.getCentreY());
        } finally {
            SessionManager.clear();
        }
    }
}
