package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_Iwamodoki} (sonic3k.asm:188040-188135, ROM {@code $8FAF0}) and its fragments.
 *
 * <p>Expectations are the routine's own tables and comparisons: the {@code $40}-pixel fuse range
 * from {@code cmpi.w #$40,d2}, the {@code byte_8FC30} script, and the {@code ChildObjDat_8FBD6}
 * offset and velocity pairs. Nothing here is taken from a trace.
 */
class TestIwamodokiBadnikInstance {

    private static final int OBJECT_ID = Sonic3kObjectIds.HCZ_END_BOSS;
    private static final int BASE_X = 0x1400;
    private static final int BASE_Y = 0x0800;

    @BeforeEach
    void initBounds() {
        AbstractObjectInstance.updateCameraBounds(BASE_X - 160, BASE_Y - 112,
                BASE_X + 160, BASE_Y + 112, 0);
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    /** {@code ObjDat_Iwamodoki}: {@code dc.b $C,$C,0,0} and {@code dc.w $280} (:188105-188106). */
    @Test
    void itIsASolidBlockWithNoTouchCollisionAtAll() {
        IwamodokiBadnikInstance bomb = bomb();
        assertEquals(0x0C, bomb.getOnScreenHalfWidth(), "width_pixels");
        assertEquals(0x0C, bomb.getOnScreenHalfHeight(), "height_pixels");
        assertEquals(0x17, bomb.getSolidParams().halfWidth(), "moveq #$17,d1");
        assertEquals(0x0C, bomb.getSolidParams().airHalfHeight(), "moveq #$C,d2");
        assertEquals(0x0B, bomb.getSolidParams().groundHalfHeight(),
                "moveq #$B,d3 -- one LESS than d2, not one more");
        assertFalse(bomb.isHighPriority(), "make_art_tile(ArtTile_Iwamodoki,0,0)");
    }

    /** {@code Obj_WaitOffscreen} (:180271-180302) gates the whole routine on having been drawn. */
    @Test
    void anOffscreenBombDoesNothingAtAll() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);  // the bomb is far outside
        IwamodokiBadnikInstance bomb = bomb();
        bomb.setServices(services());
        TestablePlayableSprite player = player(BASE_X);

        for (int frame = 1; frame <= 200; frame++) {
            bomb.update(frame, player);
        }
        assertFalse(bomb.awake(), "render_flags bit 7 never set");
        assertEquals(0, bomb.routine(), "the routine is never even initialised");
        assertFalse(bomb.detonated());
    }

    /** {@code cmpi.w #$40,d2 / bhs} (:188076): the range is exclusive at {@code $40}. */
    @Test
    void theFuseLightsOnlyInsideFortyHexPixelsHorizontally() {
        IwamodokiBadnikInstance far = bomb();
        far.setServices(services());
        far.update(1, player(BASE_X + 0x40));
        far.update(2, player(BASE_X + 0x40));
        assertEquals(2, far.routine(), "$40 away is exactly outside");

        IwamodokiBadnikInstance near = bomb();
        near.setServices(services());
        near.update(1, player(BASE_X + 0x3F));
        near.update(2, player(BASE_X + 0x3F));
        assertEquals(4, near.routine(), "$3F away lights it");

        IwamodokiBadnikInstance left = bomb();
        left.setServices(services());
        left.update(1, player(BASE_X - 0x3F));
        left.update(2, player(BASE_X - 0x3F));
        assertEquals(4, left.routine(), "Find_SonicTails returns an ABSOLUTE distance");
    }

    /**
     * {@code Animate_RawMultiDelay} adds two to {@code anim_frame} BEFORE reading
     * (sonic3k.asm:177563-177566) and {@code anim_frame_timer} starts at zero, so the first step
     * lands on {@code byte_8FC30}'s SECOND pair: frame 1 for seven frames, not frame 0.
     */
    @Test
    void theFuseScriptSkipsItsFirstPairAndThenWalksTheRomTable() {
        IwamodokiBadnikInstance bomb = lit();

        bomb.update(10, null);
        assertEquals(1, bomb.mappingFrame(), "byte_8FC30 pair 1, not pair 0");
        assertEquals(7, bomb.animTimer());
        for (int frame = 0; frame < 7; frame++) {
            bomb.update(11 + frame, null);
            assertEquals(1, bomb.mappingFrame(), "held for its seven frames");
        }
        bomb.update(18, null);
        assertEquals(2, bomb.mappingFrame(), "pair 2");
        // Seven updates spend the delay and the eighth steps, exactly as for pair 1 above.
        for (int frame = 0; frame < 8; frame++) {
            bomb.update(19 + frame, null);
        }
        assertEquals(3, bomb.mappingFrame(), "pair 3");
        assertEquals(0x2F, bomb.animTimer(), "and its $2F hold");
    }

    /**
     * {@code loc_8FB76} (:188086-188094). {@code FixBugs} is 0, so the ROM's own comment applies:
     * the 100 points is never awarded. Four fragments arrive at {@code ChildObjDat_8FBD6}'s
     * offsets and velocities and the bomb stops being solid.
     */
    @Test
    void detonationThrowsTheFourRomFragmentsAndEndsTheSolid() {
        TestObjectServices services = services();
        IwamodokiBadnikInstance bomb = lit(services);

        for (int frame = 10; frame < 400 && !bomb.detonated(); frame++) {
            bomb.update(frame, null);
        }
        assertTrue(bomb.detonated(), "the $F4 command called $34(a0)");
        assertTrue(bomb.isDestroyed(), "the slot became Obj_Explosion");

        List<IwamodokiShrapnelInstance> fragments = services.objectManager().getActiveObjects()
                .stream()
                .filter(o -> o instanceof IwamodokiShrapnelInstance)
                .map(IwamodokiShrapnelInstance.class::cast)
                .toList();
        assertEquals(4, fragments.size(), "CreateChild2_Complex makes four");

        int[][] expected = {
                {BASE_X - 4, BASE_Y + 4, -0x400, -0x200, 6},
                {BASE_X + 4, BASE_Y + 4, 0x400, -0x200, 6},
                {BASE_X - 8, BASE_Y - 8, -0x200, -0x400, 7},
                {BASE_X + 8, BASE_Y - 8, 0x200, -0x400, 7}};
        for (int i = 0; i < expected.length; i++) {
            IwamodokiShrapnelInstance fragment = fragments.get(i);
            assertEquals(expected[i][0], fragment.getCentreX(), "fragment " + i + " x");
            assertEquals(expected[i][1], fragment.getCentreY(), "fragment " + i + " y");
            assertEquals(expected[i][2], fragment.xVel(), "fragment " + i + " x_vel");
            assertEquals(expected[i][3], fragment.yVel(), "fragment " + i + " y_vel");
            assertEquals(expected[i][4], fragment.mappingFrame(),
                    "fragment " + i + " (subtype >> 2) + 6");
        }
    }

    /** {@code loc_8FBB8} is {@code MoveSprite}, so the fragments fall under the {@code $38} gravity. */
    @Test
    void theFragmentsFallUnderGravity() {
        IwamodokiShrapnelInstance fragment =
                new IwamodokiShrapnelInstance(BASE_X, BASE_Y, 0, -0x400, -0x200);
        fragment.setServices(services());

        fragment.update(1, null);
        assertEquals(BASE_X - 4, fragment.getCentreX(), "-$400 >> 8 = -4 pixels");
        assertEquals(BASE_Y - 2, fragment.getCentreY(), "the OLD y_vel moves it: -$200 >> 8 = -2");
        assertEquals(-0x200 + 0x38, fragment.yVel(), "addi.w #$38,y_vel(a0)");
        assertEquals(0x98, fragment.getCollisionFlags(), "word_8FBD0's collision number");
        assertEquals(8, fragment.getOnScreenHalfWidth());
        assertEquals(4, fragment.getOnScreenHalfHeight());
    }

    // ----- harness ------------------------------------------------------------------------------

    /** A bomb that has been on screen and whose fuse the player has already lit. */
    private static IwamodokiBadnikInstance lit() {
        return lit(services());
    }

    private static IwamodokiBadnikInstance lit(TestObjectServices services) {
        IwamodokiBadnikInstance bomb = bomb();
        bomb.setServices(services);
        TestablePlayableSprite player = player(BASE_X);
        bomb.update(1, player);
        bomb.update(2, player);
        assertEquals(4, bomb.routine(), "precondition: burning");
        return bomb;
    }

    private static IwamodokiBadnikInstance bomb() {
        return new IwamodokiBadnikInstance(
                new ObjectSpawn(BASE_X, BASE_Y, OBJECT_ID, 0, 0, false, 0));
    }

    private static TestablePlayableSprite player(int x) {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) x, (short) BASE_Y);
        player.setCentreX((short) x);
        player.setCentreY((short) BASE_Y);
        return player;
    }

    private static TestObjectServices services() {
        return new TestObjectServices().withIsolatedObjectManager();
    }
}
