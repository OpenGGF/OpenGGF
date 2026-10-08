package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZFallingSpike} (sonic3k.asm:87946-88009, ROM {@code $4284C}).
 *
 * <p>The behaviour worth pinning is the trigger distance. Init writes the subtype into
 * {@code $2F(a0)}, the low byte of the word the waiting routine compares at {@code $2E(a0)}, so
 * with a zeroed slot the trigger is the subtype <em>in pixels</em>. Lava Reef's fifteen placements
 * carry subtypes 1 to 5, and the first test enumerates all five as the layout gives them.
 */
class TestLrzFallingSpikeObjectInstance {

    private static final int OBJECT_ID = Sonic3kObjectIds.LBZ_CUP_ELEVATOR;
    private static final int BASE_X = 0x0280;
    private static final int BASE_Y = 0x0504;

    /** {@code move.b subtype(a0),$2F(a0)} into the word at {@code $2E} (sonic3k.asm:87955). */
    @Test
    void theTriggerDistanceIsTheSubtypeInPixels() {
        for (int subtype : new int[] {1, 2, 3, 4, 5}) {
            assertEquals(subtype, spike(subtype).triggerDistance(),
                    "subtype " + subtype + " is the whole word, not subtype << 8");
        }
    }

    /**
     * {@code cmp.w $2E(a0),d0 / bhs} (:87977-87978): the drop starts only while the separation is
     * strictly smaller than the trigger, so a spike with subtype 4 releases at 3 pixels and not at
     * 4.
     */
    @Test
    void theDropStartsOnlyInsideTheTriggerDistance() {
        for (int[] testCase : new int[][] {{4, 5, 0}, {4, 4, 0}, {4, 3, 1}, {4, 0, 1}}) {
            LrzFallingSpikeObjectInstance spike = spike(testCase[0]);
            spike.setServices(services());
            spike.update(1, playerAt(BASE_X + testCase[1]));
            assertEquals(testCase[2] == 1, spike.isFalling(),
                    "subtype " + testCase[0] + " at separation " + testCase[1]);
        }
    }

    /** {@code neg.w d0} (:87963): the separation is an absolute value, so either side triggers. */
    @Test
    void theTriggerIsSymmetricAboutTheSpike() {
        LrzFallingSpikeObjectInstance left = spike(5);
        left.setServices(services());
        left.update(1, playerAt(BASE_X - 3));
        assertTrue(left.isFalling(), "three pixels to the left");

        LrzFallingSpikeObjectInstance right = spike(5);
        right.setServices(services());
        right.update(1, playerAt(BASE_X + 3));
        assertTrue(right.isFalling(), "three pixels to the right");
    }

    /**
     * {@code move.b #$82,collision_flags(a0)} (:87954) and {@code move.b #0} on landing (:87992):
     * the spike hurts while it hangs and while it falls, and stops once it has landed.
     */
    @Test
    void itIsHarmfulUntilItLandsAndSolidOnlyAfterwards() {
        LrzFallingSpikeObjectInstance spike = spike(5);
        spike.setServices(services());
        assertEquals(0x82, spike.getCollisionFlags(), "waiting");
        assertFalse(spike.isSolidFor(null), "a waiting spike calls no SolidObjectFull");

        spike.update(1, playerAt(BASE_X));
        assertTrue(spike.isFalling());
        assertEquals(0x82, spike.getCollisionFlags(), "falling");
        assertFalse(spike.isSolidFor(null), "a falling spike calls no SolidObjectFull");
    }

    /** {@code move.w #$13,d1 / #$10,d2 / #$11,d3} (sonic3k.asm:88004-88006). */
    @Test
    void solidParamsAreTheLandedRoutineArguments() {
        LrzFallingSpikeObjectInstance spike = spike(5);
        assertEquals(0x13, spike.getSolidParams().halfWidth(), "d1");
        assertEquals(0x10, spike.getSolidParams().airHalfHeight(), "d2");
        assertEquals(0x11, spike.getSolidParams().groundHalfHeight(), "d3");
    }

    /**
     * {@code jsr (MoveSprite)} (:87986) with both velocities starting at zero: the first falling
     * frame moves nothing and leaves {@code y_vel} at one gravity step, exactly as the level-start
     * fall does.
     */
    @Test
    void theFirstFallingFrameOnlyAppliesGravity() {
        LrzFallingSpikeObjectInstance spike = spike(5);
        spike.setServices(services());
        spike.update(1, playerAt(BASE_X));
        assertTrue(spike.isFalling());
        assertEquals(BASE_Y, spike.getCentreY(), "MoveSprite adds y_vel before gravity");

        spike.update(2, playerAt(BASE_X));
        assertEquals(BASE_Y, spike.getCentreY(), "$38 of subpixel is less than one pixel");
    }

    private static LrzFallingSpikeObjectInstance spike(int subtype) {
        return new LrzFallingSpikeObjectInstance(
                new ObjectSpawn(BASE_X, BASE_Y, OBJECT_ID, subtype, 0, false, 0));
    }

    private static TestablePlayableSprite playerAt(int x) {
        TestablePlayableSprite player =
                new TestablePlayableSprite("sonic", (short) x, (short) (BASE_Y + 0x40));
        player.setCentreX((short) x);
        player.setCentreY((short) (BASE_Y + 0x40));
        player.setAirForTest(false);
        return player;
    }

    private static TestObjectServices services() {
        return new TestObjectServices().withIsolatedObjectManager();
    }
}
