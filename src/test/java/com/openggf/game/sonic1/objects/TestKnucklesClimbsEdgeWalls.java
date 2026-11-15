package com.openggf.game.sonic1.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ClimbableWall;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Direction;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Green Hill's cliff faces are edge-wall objects, not terrain, and Knuckles' grab and climb read terrain
 * alone. The engine lets those probes see the walls: its own rule, with no original routine behind it.
 */
@RequiresRom(SonicGame.SONIC_1)
class TestKnucklesClimbsEdgeWalls {
    private static final int EDGE_WALL = 0x44;
    /** Green Hill 3: edge walls at x 2120 from y 720 down, over terrain that is solid only below y 768. */
    private static final int CLIFF_WALL_X = 2120;
    private static final int CLIFF_TOP_Y = 720 - 40;
    private static final int TERRAIN_TOP_Y = 768;
    /** EdgeWall_SolidWall's d1: where the solid strip holds a player's centre. */
    private static final int HELD_AT = 38 / 2;

    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    private static HeadlessTestFixture bootGreenHillWithKnuckles(int act) {
        var donorRom = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(donorRom != null, "Sonic 3 & Knuckles donor ROM is not available in this environment");
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.SONIC_3K_ROM, donorRom.getAbsolutePath());
        return HeadlessTestFixture.builder().withCrossGameDonation("s3k").withZoneAndAct(0, act).build();
    }

    /** Knux_Test_For_Glide's writes, from a point in the air: the state a jump and a second press leave. */
    private static void glideFrom(HeadlessTestFixture fixture, int x, int y, boolean right) {
        var knuckles = fixture.sprite();
        knuckles.setCentreX((short) x);
        knuckles.setCentreY((short) y);
        knuckles.setAir(true);
        knuckles.setXSpeed((short) 0);
        knuckles.setYSpeed((short) 0);
        knuckles.setGSpeed((short) 0);
        fixture.camera().updatePosition(true);
        fixture.stepIdleFrames(3);

        knuckles.setDirection(right ? Direction.RIGHT : Direction.LEFT);
        knuckles.setAir(true);
        knuckles.setJumping(true);
        knuckles.setRolling(false);
        knuckles.applyCustomRadii(10, 10);
        knuckles.setCentreX((short) x);
        knuckles.setCentreY((short) y);
        knuckles.setXSpeed((short) (right ? 0x400 : -0x400));
        knuckles.setGSpeed((short) 0x400);
        knuckles.setYSpeed((short) 0);
        knuckles.setAngle((byte) 0);
        knuckles.setDoubleJumpProperty((byte) (right ? 0 : -128));
        knuckles.setDoubleJumpFlag(1);
    }

    private static boolean glideUntilGrab(HeadlessTestFixture fixture, boolean right) {
        for (int frame = 0; frame < 90 && fixture.sprite().getDoubleJumpFlag() == 1; frame++) {
            fixture.stepFrame(false, false, !right, right, true);
        }
        return fixture.sprite().getDoubleJumpFlag() == 4;
    }

    private static boolean climbUntilStanding(HeadlessTestFixture fixture) {
        for (int frame = 0; frame < 600 && fixture.sprite().getAir(); frame++) {
            int state = fixture.sprite().getDoubleJumpFlag();
            if (state != 4 && state != 5) return false;
            fixture.stepFrame(true, false, false, false, false);
        }
        return !fixture.sprite().getAir();
    }

    @Test
    void knucklesGrabsTheCliffAboveItsTerrainClimbsItAndStandsOnTop() {
        var fixture = bootGreenHillWithKnuckles(2);
        var knuckles = fixture.sprite();
        assertEquals(SecondaryAbility.GLIDE, knuckles.getSecondaryAbility());

        glideFrom(fixture, CLIFF_WALL_X - 80, 730, true);
        assertTrue(glideUntilGrab(fixture, true), "the glide ends holding the cliff");

        assertEquals(CLIFF_WALL_X - HELD_AT, knuckles.getCentreX(), "held where the solid strip holds a player");
        assertTrue(knuckles.getCentreY() < TERRAIN_TOP_Y - 11, "above the terrain, at y " + knuckles.getCentreY());
        TerrainCheckResult terrain = ObjectTerrainUtils.checkRightWallDist(
                knuckles.getCentreX() + 10, knuckles.getCentreY());
        assertTrue(terrain == null || terrain.distance() >= 4, "the terrain alone has no wall there to hold");

        assertStandsOnTopAfterClimbing(fixture, CLIFF_TOP_Y, true);
    }

    @Test
    void aWallFacingRightIsGrabbedFromItsOtherSide() {
        // Green Hill 1: one solid wall at x 3080, y 760, with open air to its right.
        var fixture = bootGreenHillWithKnuckles(0);
        glideFrom(fixture, 3080 + HELD_AT + 24, 750, false);
        assertTrue(glideUntilGrab(fixture, false), "the glide ends holding the wall");
        assertEquals(3080 + HELD_AT, fixture.sprite().getCentreX(), "held where the solid strip holds a player");
        assertStandsOnTopAfterClimbing(fixture, 760 - 40, false);
    }

    @Test
    void aTallStackOfWallsIsClimbedAsOneFace() {
        // Green Hill 1: walls at x 6152, y 448 and 496: their boxes overlap into one face from 408 to 535.
        var fixture = bootGreenHillWithKnuckles(0);
        glideFrom(fixture, 6152 - HELD_AT - 24, 500, true);
        assertTrue(glideUntilGrab(fixture, true), "the glide ends holding the lower wall");
        assertTrue(fixture.sprite().getCentreY() > 448 + 39, "below the upper wall's box, y " + fixture.sprite().getCentreY());
        assertStandsOnTopAfterClimbing(fixture, 448 - 40, true);
    }

    private static void assertStandsOnTopAfterClimbing(HeadlessTestFixture fixture, int wallTop, boolean facingRight) {
        var knuckles = fixture.sprite();
        int heldAt = knuckles.getCentreX();
        assertTrue(climbUntilStanding(fixture), "the climb ends on the ground, not in a fall; stopped at "
                + knuckles.getCentreX() + "," + knuckles.getCentreY() + " in state " + knuckles.getDoubleJumpFlag());
        int topX = knuckles.getCentreX();
        int topY = knuckles.getCentreY();
        assertTrue(facingRight ? topX > heldAt : topX < heldAt, "pulled up onto the ledge, x " + topX);
        assertTrue(topY < wallTop, "standing above the wall's top, y " + topY);
        fixture.stepIdleFrames(30);
        assertFalse(knuckles.getAir(), "still standing half a second later");
        assertEquals(topX, knuckles.getCentreX());
        assertTrue(Math.abs(knuckles.getCentreY() - topY) <= 2, "settled at y " + knuckles.getCentreY());
    }

    @Test
    void theWallLetsGoOfNobodyElse() {
        var wall = new Sonic1EdgeWallObjectInstance(new ObjectSpawn(CLIFF_WALL_X, 720, EDGE_WALL, 0, 0, false, 0));
        var sonic = mock(AbstractPlayableSprite.class);
        when(sonic.getSecondaryAbility()).thenReturn(SecondaryAbility.INSTA_SHIELD);
        when(sonic.getDoubleJumpFlag()).thenReturn(4);
        assertTrue(wall.isSolidFor(sonic), "a number in another character's ability flag is not a climb");

        var knuckles = mock(AbstractPlayableSprite.class);
        when(knuckles.getSecondaryAbility()).thenReturn(SecondaryAbility.GLIDE);
        for (int state = 0; state <= 5; state++) {
            when(knuckles.getDoubleJumpFlag()).thenReturn(state);
            // 1 glide, 4 climb, 5 pull-up: his own probes hold him. 2 falling and 3 sliding are pushed like anyone.
            assertEquals(state != 1 && state < 4, wall.isSolidFor(knuckles), "solid to Knuckles in state " + state);
        }
    }

    @Test
    void theClimbableStripIsAsWideAsDrawnAndAsTallAsItsSolidBox() {
        var solid = new Sonic1EdgeWallObjectInstance(new ObjectSpawn(CLIFF_WALL_X, 720, EDGE_WALL, 1, 0, false, 0));
        assertEquals(new ClimbableWall.Box(CLIFF_WALL_X - 8, 680, CLIFF_WALL_X + 7, 759), solid.climbableWall());

        var displayOnly = new Sonic1EdgeWallObjectInstance(
                new ObjectSpawn(CLIFF_WALL_X, 720, EDGE_WALL, 0x11, 0, false, 0));
        assertNull(displayOnly.climbableWall(), "bit 4 of the subtype draws a wall that stops nobody");
    }

    @Test
    void aProbeReadsTheBoxAsItWouldReadTerrain() {
        var box = new ClimbableWall.Box(100, 200, 115, 279);
        // Zero on the last free pixel, negative by the depth inside, from each side.
        assertEquals(0, box.distance(Direction.RIGHT, 99, 240));
        assertEquals(-1, box.distance(Direction.RIGHT, 100, 240));
        assertEquals(3, box.distance(Direction.RIGHT, 96, 240));
        assertEquals(0, box.distance(Direction.LEFT, 116, 240));
        assertEquals(-16, box.distance(Direction.LEFT, 100, 240));
        assertEquals(0, box.distance(Direction.DOWN, 107, 199));
        assertEquals(-1, box.distance(Direction.DOWN, 107, 200));
        assertEquals(0, box.distance(Direction.UP, 107, 280));
        assertEquals(-80, box.distance(Direction.UP, 107, 200));

        int none = TerrainCheckResult.NO_COLLISION;
        assertEquals(none, box.distance(Direction.RIGHT, 99, 199), "a row above the box");
        assertEquals(none, box.distance(Direction.RIGHT, 99, 280), "a row below the box");
        assertEquals(none, box.distance(Direction.DOWN, 99, 199), "a column beside the box");
        assertEquals(none, box.distance(Direction.RIGHT, 116, 240), "the box is behind the probe");
        assertEquals(none, box.distance(Direction.LEFT, 99, 240), "the box is behind the probe");
        assertEquals(31, box.distance(Direction.RIGHT, 68, 240), "the last pixel of a two-tile reach");
        assertEquals(none, box.distance(Direction.RIGHT, 67, 240), "beyond a terrain probe's reach");
    }
}
