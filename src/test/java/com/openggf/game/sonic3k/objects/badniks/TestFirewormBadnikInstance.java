package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_Fireworm} (sonic3k.asm:196192-196500, ROM {@code $8F760}): the placement spawner, the
 * head, the four segments and each segment's flame.
 *
 * <p>Every expectation here is a ROM table or a ROM arithmetic step, not a reading of the Java:
 * {@code ChildObjDat_8FA0E}'s {@code (0,-8)}, {@code word_8F940}'s {@code $B,$16,$21,$2C},
 * {@code ChildObjDat_8FA30}'s {@code (0,-$E)}, {@code ObjSlot_Fireworm}'s
 * {@code dc.b $C,$C,0,$1A}, {@code ObjDat3_8F9FC}'s {@code dc.b 8,8,1,$98},
 * {@code word_8FA08}'s {@code dc.b 8,8,3,$98}, {@code loc_8F7F4}'s {@code $2E = 3} and
 * {@code d4 = -$100}, and {@code loc_8F842}'s {@code $39 = 8 / $3E = y_vel = $80 / $40 = 8}.
 */
class TestFirewormBadnikInstance {

    private static final int OBJECT_ID = Sonic3kObjectIds.HCZ_MINIBOSS;
    private static final int BASE_X = 0x1000;
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

    /** {@code ObjDat3_8F9DE}: {@code dc.b $C,$C,0,0} and no {@code Draw_Sprite} anywhere. */
    @Test
    void thePlacementObjectIsAnInvisibleHarmlessSpawner() {
        FirewormBadnikInstance spawner = spawner();
        assertEquals(0x0C, spawner.getOnScreenHalfWidth(), "width_pixels");
        assertEquals(0x0C, spawner.getOnScreenHalfHeight(), "height_pixels");
        assertFalse(com.openggf.level.objects.TouchResponseProvider.class.isInstance(spawner),
                "collision_flags 0: Obj_Fireworm never publishes a touch region");
    }

    /** {@code loc_8F77A}: {@code cmpi.w #$80,d2} on the nearer player's X distance. */
    @Test
    void theHeadIsNotCreatedUntilAPlayerIsWithin80Pixels() {
        TestObjectServices services = services();
        FirewormBadnikInstance spawner = spawner();
        spawner.setServices(services);

        TestablePlayableSprite far = player(BASE_X + 0x80);
        spawner.update(1, far);            // Obj_WaitOffscreen's release frame
        spawner.update(2, far);
        assertFalse(spawner.spawned(), "$80 exactly is not below $80: blo, not bls");
        assertEquals(0, heads(services).size());

        spawner.update(3, player(BASE_X + 0x7F));
        assertTrue(spawner.spawned(), "routine 4");
        assertEquals(1, heads(services).size(), "ChildObjDat_8FA0E creates exactly one child");
        assertEquals(BASE_Y - 8, heads(services).get(0).getCentreY(), "dc.b 0,-8");
        assertEquals(BASE_X, heads(services).get(0).getCentreX(), "no X offset");
    }

    /** {@code ObjSlot_Fireworm}: {@code dc.b $C,$C,0,$1A}. */
    @Test
    void theHeadCarriesTheSlottedAttributes() {
        FirewormHeadInstance head = head(services());
        assertEquals(0x1A, head.getCollisionFlags(), "collision number $1A");
    }

    /**
     * {@code loc_8F7F4}: {@code Set_VelocityXTrackSonic} with {@code d4 = -$100} reads
     * {@code Find_OtherObject} against PLAYER 1 and negates {@code d4} when Player 1 is to the
     * right, so the head sets off at one pixel a frame toward Player 1.
     */
    @Test
    void theHeadSetsOffTowardPlayerOneAtOnePixelAFrame() {
        FirewormHeadInstance toTheRight = head(services());
        toTheRight.update(1, player(BASE_X + 0x40));
        assertEquals(0x100, toTheRight.xVel(), "neg.w d4 when Player 1 is right");

        FirewormHeadInstance toTheLeft = head(services());
        toTheLeft.update(1, player(BASE_X - 0x40));
        assertEquals(-0x100, toTheLeft.xVel(), "d4 unchanged when Player 1 is left");
    }

    /**
     * {@code move.w #3,$2E(a0)} then {@code loc_8F812}'s {@code MoveSprite2 / Obj_Wait}: four
     * dispatches of straight travel before {@code loc_8F81E} creates the segments.
     */
    @Test
    void theHeadTravelsFourFramesThenCreatesFourSegments() {
        TestObjectServices services = services();
        TestablePlayableSprite p1 = player(BASE_X - 0x40);
        FirewormHeadInstance head = head(services);

        for (int i = 0; i < 4; i++) {
            head.update(i + 1, p1);
            if (i < 3) {
                assertEquals(0, segments(services).size(), "still in routine 4 on frame " + i);
            }
        }
        List<FirewormSegmentInstance> segments = segments(services);
        assertEquals(4, segments.size(), "ChildObjDat_8FA16: dc.w 4-1");
        assertEquals(-4, head.getCentreX() - BASE_X,
                "four MoveSprite2 steps at -$100 each move exactly four pixels");
        assertEquals(1, head.mappingFrame(), "move.b #1,mapping_frame(a0) at loc_8F81E");
        assertEquals(0x80, head.yVel(), "loc_8F842: move.w d0,y_vel(a0) with d0 = $80");
        assertEquals(8, head.swingHalfCycles(), "loc_8F842: move.b #8,$39(a0)");
    }

    /** {@code word_8F940}: {@code dc.w $B,$16,$21,$2C} indexed by the child's own subtype. */
    @Test
    void eachSegmentWaitsElevenFramesLongerThanTheOneInFront() {
        TestObjectServices services = services();
        TestablePlayableSprite p1 = player(BASE_X - 0x40);
        FirewormHeadInstance head = head(services);
        for (int i = 0; i < 4; i++) {
            head.update(i + 1, p1);
        }
        List<FirewormSegmentInstance> segments = segments(services);
        int[] expected = {0x0B, 0x16, 0x21, 0x2C};
        for (int i = 0; i < segments.size(); i++) {
            assertEquals(expected[i] + 1, segments.get(i).waitTimer(),
                    "word_8F940 entry " + i + ", plus the init dispatch");
            assertEquals(head.xVel(), segments.get(i).xVel(),
                    "loc_8F910 copies the parent's x_vel");
        }
        assertEquals(1, segments.get(0).mappingFrame(), "ObjDat3_8F9FC: mapping_frame 1");
        assertEquals(0x98, segments.get(0).getCollisionFlags(), "ObjDat3_8F9FC: collision $98");
    }

    /** {@code loc_8F94E}: the wait's expiry creates {@code ChildObjDat_8FA30} at {@code (0,-$E)}. */
    @Test
    void aSegmentGrowsItsFlameWhenItsOwnWaitExpires() {
        TestObjectServices services = services();
        FirewormSegmentInstance segment = segment(services, 0);
        // $2E = $B, and Obj_Wait's subq-then-bmi fires on the twelfth decrement; the init
        // dispatch that SetUp_ObjAttributes consumed is the thirteenth pass in total.
        for (int i = 0; i < 12; i++) {
            segment.update(i + 1, null);
        }
        assertEquals(0, segment.waitTimer(), "one decrement still to go");
        assertEquals(null, segment.flame(), "and no flame yet");
        segment.update(13, null);
        assertEquals(-1, segment.waitTimer(), "the wait is over");
        FirewormFlameInstance flame = segment.flame();
        assertNotNull(flame, "ChildObjDat_8FA30");
        assertEquals(segment.getCentreY() - 0x0E, flame.getCentreY(), "dc.b 0,-$E");
        assertEquals(segment.getCentreX(), flame.getCentreX(), "no X offset");
        assertEquals(3, flame.mappingFrame(), "word_8FA08: mapping_frame 3");
        assertEquals(0x98, flame.getCollisionFlags(), "word_8FA08: collision $98");
        assertEquals(1 << 4, flame.getShieldReactionFlags(),
                "bset #4,shield_reaction(a0) at loc_8F97C: this is fire");
    }

    /**
     * {@code loc_8F862}: {@code Swing_UpAndDown_Count} with {@code $40 = 8} and {@code $3E = $80},
     * then {@code loc_8F876}'s turn once {@code $39} has counted eight half-cycles down past zero.
     * The turn walks {@code x_vel} by {@code $10} a frame until it reaches the opposite
     * {@code $100} ({@code loc_8F89A}/{@code loc_8F8DE}).
     */
    @Test
    void theHeadSwingsEightHalfCyclesAndThenTurnsAround() {
        TestObjectServices services = services();
        TestablePlayableSprite p1 = player(BASE_X - 0x40);
        FirewormHeadInstance head = head(services);
        for (int i = 0; i < 4; i++) {
            head.update(i + 1, p1);
        }
        assertEquals(-0x100, head.xVel(), "heading left");

        int frames = 0;
        while (!head.turning() && frames < 2000) {
            head.update(frames + 5, p1);
            frames++;
            if (!head.turning()) {
                assertTrue(Math.abs(head.yVel()) <= 0x80,
                        "the swing never exceeds $3E = $80 (frame " + frames + ")");
            }
        }
        assertEquals(-0x100, head.yVel(),
                "loc_8F876: move.w $42(a0),y_vel(a0) kicks the turn off at -$100");
        assertTrue(head.turning(), "eight half-cycles at accel 8 and cap $80 end in a turn");

        int previous = head.xVel();
        while (head.turning() && frames < 4000) {
            head.update(frames + 5, p1);
            frames++;
            if (head.turning()) {
                assertEquals(0x10, head.xVel() - previous, "addi.w #$10,d0 each frame");
                previous = head.xVel();
            }
        }
        // loc_8F8C6 tests the candidate BEFORE storing it: `addi.w #$10,d0 / cmpi.w #$100,d0 /
        // bge.s loc_8F8DE` branches away without the `move.w d0,x_vel(a0)`, so the last value
        // the worm keeps is $F0 and it never actually reaches $100 after a turn.
        assertEquals(0xF0, head.xVel(),
                "the walk stops one step short: loc_8F8DE does not write x_vel");
        assertEquals(0x80, head.yVel(), "loc_8F842 seeds the swing again");
        assertEquals(8, head.swingHalfCycles(), "and reloads $39 = 8");
    }

    /**
     * {@code Child_DrawTouch_Sprite_FlickerMove} (sonic3k.asm:178136-178141) only reaches
     * {@code Add_SpriteToCollisionResponseList} while the parent's {@code status} bit 7 is clear.
     */
    @Test
    void segmentsStopHurtingOnceTheHeadIsGone() {
        TestObjectServices services = services();
        TestablePlayableSprite p1 = player(BASE_X - 0x40);
        FirewormHeadInstance head = head(services);
        for (int i = 0; i < 4; i++) {
            head.update(i + 1, p1);
        }
        FirewormSegmentInstance segment = segments(services).get(0);
        assertEquals(0x98, segment.getCollisionFlags(), "while the head lives");
        head.setDestroyed(true);
        assertEquals(0, segment.getCollisionFlags(), "once status bit 7 is set on the parent");
    }

    /**
     * The other half of the same retirement. Each flame draws through
     * {@code Child_DrawTouch_Sprite} (sonic3k.asm:178053-178058), which runs
     * {@code Go_Delete_Sprite} as soon as its own parent -- the segment -- has {@code status}
     * bit 7 set, and {@code loc_849D8} (:178120-178125) sets that bit on the segment the moment
     * the head is gone. So a killed worm takes its flames with it in the same frame the segments
     * stop hurting; a surviving flame is still a {@code collision_flags $98} hurt region.
     */
    @Test
    void theFlamesGoWithTheChain() {
        TestObjectServices services = services();
        TestablePlayableSprite p1 = player(BASE_X - 0x40);
        FirewormHeadInstance head = head(services);
        for (int frame = 1; frame <= 80; frame++) {
            head.update(frame, p1);
            for (FirewormSegmentInstance segment : segments(services)) {
                segment.update(frame, p1);
            }
        }
        List<FirewormFlameInstance> flames = active(services, FirewormFlameInstance.class);
        assertFalse(flames.isEmpty(), "the segments must have grown their flames by now");
        assertEquals(0x98, flames.getFirst().getCollisionFlags(), "word_8FA08's collision_flags");

        head.setDestroyed(true);
        for (FirewormSegmentInstance segment : segments(services)) {
            segment.update(81, p1);
        }

        for (FirewormFlameInstance flame : flames) {
            assertTrue(flame.isDestroyed(),
                    "Child_DrawTouch_Sprite must Go_Delete_Sprite once the segment's bit 7 is set");
        }
        assertTrue(active(services, FirewormFlameInstance.class).stream()
                        .allMatch(AbstractObjectInstance::isDestroyed),
                "no flame may outlive the worm as a live hurt region; the object manager's own"
                        + " pass is what then takes the destroyed slots out");
    }

    // ===== harness =====

    private static FirewormBadnikInstance spawner() {
        return new FirewormBadnikInstance(
                new ObjectSpawn(BASE_X, BASE_Y, OBJECT_ID, 0, 0, false, 0));
    }

    private static FirewormHeadInstance head(TestObjectServices services) {
        FirewormHeadInstance head = new FirewormHeadInstance(
                new ObjectSpawn(BASE_X, BASE_Y, OBJECT_ID, 0, 0, false, 0));
        head.setServices(services);
        return head;
    }

    private static FirewormSegmentInstance segment(TestObjectServices services, int subtype) {
        FirewormSegmentInstance segment =
                FirewormSegmentInstance.forRewindRecreate(
                        new ObjectSpawn(BASE_X, BASE_Y, OBJECT_ID, subtype, 0, false, 0));
        segment.setServices(services);
        return segment;
    }

    private static List<FirewormHeadInstance> heads(TestObjectServices services) {
        return active(services, FirewormHeadInstance.class);
    }

    private static List<FirewormSegmentInstance> segments(TestObjectServices services) {
        return active(services, FirewormSegmentInstance.class);
    }

    private static <T> List<T> active(TestObjectServices services, Class<T> type) {
        return services.objectManager().getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private static TestablePlayableSprite player(int x) {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic",
                (short) x, (short) BASE_Y);
        player.setCentreX((short) x);
        player.setCentreY((short) BASE_Y);
        player.setAirForTest(false);
        return player;
    }

    private static TestObjectServices services() {
        return new TestObjectServices().withIsolatedObjectManager();
    }
}
