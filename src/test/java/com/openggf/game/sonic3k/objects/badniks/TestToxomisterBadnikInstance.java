package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.ShieldType;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_Toxomister} (sonic3k.asm, ROM {@code $8FD48}), its cloud ({@code loc_8FDBA}) and the
 * seven puffs ({@code loc_8FE8E}).
 *
 * <p>Expectations are the ROM's own tables and arithmetic: {@code ChildObjDat_90040}'s
 * {@code (-$C,8)} cloud offset, the {@code $6F}/{@code $7F} timers, {@code sub_8FF8C}'s two
 * refusals, {@code sub_8FFE0}'s arithmetic shift, {@code Check_LRControllerShake}'s five
 * reversals, and the {@code $C - index} inversion the puffs apply to their own subtype.
 */
class TestToxomisterBadnikInstance {

    private static final int OBJECT_ID = Sonic3kObjectIds.BUBBLES_BADNIK;
    private static final int BASE_X = 0x1A00;
    private static final int BASE_Y = 0x0900;

    @BeforeEach
    void initBounds() {
        AbstractObjectInstance.updateCameraBounds(BASE_X - 160, BASE_Y - 112,
                BASE_X + 160, BASE_Y + 112, 0);
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    /** {@code ObjDat_Toxomister}: {@code dc.b 8,8,1,$18} and the set priority bit (:the init). */
    @Test
    void renderAndCollisionStateAreTheInitWrites() {
        ToxomisterBadnikInstance body = body(0);
        assertEquals(8, body.getOnScreenHalfWidth(), "width_pixels");
        assertEquals(8, body.getOnScreenHalfHeight(), "height_pixels");
        assertEquals(0x18, body.getCollisionFlags(), "ObjDat collision number");
        assertTrue(body.isHighPriority(), "make_art_tile(ArtTile_Toxomister,1,1)");
    }

    /**
     * The init's {@code moveq #$18,d1 / btst #1,render_flags(a0) / neg.w d1}: the body's own
     * second sprite sits {@code $18} below, or above on a Y-flipped placement.
     */
    @Test
    void theSecondSpriteFollowsThePlacementsYFlip() {
        assertEquals(BASE_Y + 0x18, body(0).childSpriteY(), "no flip");
        assertEquals(BASE_Y - 0x18, body(0x2).childSpriteY(), "render_flags bit 1 negates d1");
    }

    /** {@code Obj_WaitOffscreen} gates the whole routine, cloud included. */
    @Test
    void anOffscreenBodyNeverBreathes() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        ToxomisterBadnikInstance body = body(0);
        body.setServices(services());
        for (int frame = 1; frame <= 120; frame++) {
            body.update(frame, null);
        }
        assertFalse(body.awake());
        assertEquals(null, body.cloud(), "sub_8FF72 never ran");
    }

    /** {@code sub_8FF72} + {@code ChildObjDat_90040}: one cloud at {@code (-$C, 8)}. */
    @Test
    void theBodyBreathesOneCloudAtTheRomOffset() {
        ToxomisterBadnikInstance body = body(0);
        body.setServices(services());
        body.update(1, null);

        ToxomisterCloudInstance cloud = body.cloud();
        assertNotNull(cloud, "$44(a0)");
        assertEquals(BASE_X - 0x0C, cloud.getCentreX(), "child_dx");
        assertEquals(BASE_Y + 8, cloud.getCentreY(), "child_dy");
        assertEquals(2, cloud.routine(), "SetUp_ObjAttributes3's addq.b #2,routine");
        assertEquals(0xD8, cloud.getCollisionFlags(), "move.b #$D8,collision_flags(a0)");
    }

    /** {@code ChildObjDat_90048} makes seven, and {@code loc_8FE8E} inverts each index. */
    @Test
    void theCloudOpensSevenPuffsWithInvertedIndicesAndStaggeredDelays() {
        TestObjectServices services = services();
        ToxomisterBadnikInstance body = body(0);
        body.setServices(services);
        body.update(1, null);

        List<ToxomisterPuffInstance> puffs = services.objectManager().getActiveObjects().stream()
                .filter(o -> o instanceof ToxomisterPuffInstance)
                .map(ToxomisterPuffInstance.class::cast)
                .toList();
        assertEquals(7, puffs.size());

        int[] expectedIndex = {0x0C, 0x0A, 8, 6, 4, 2, 0};
        for (int i = 0; i < puffs.size(); i++) {
            assertEquals(expectedIndex[i], puffs.get(i).invertedIndex(),
                    "puff " + i + ": subi.b #$C / neg.b");
            assertEquals((expectedIndex[i] << 2) & 0xFF, puffs.get(i).openDelay(),
                    "puff " + i + ": lsl.b #2 into $2F(a0)");
        }
    }

    /**
     * {@code sub_8FF8C}: a rolling player ({@code anim == 2}) and a bubble shield both pass
     * straight through; anything else latches with routine 8 and a 59-frame ring timer.
     */
    @Test
    void theCloudRefusesARollingPlayerAndABubbleShieldAndLatchesEverythingElse() {
        ToxomisterCloudInstance rolling = cloud();
        TestablePlayableSprite roller = player();
        roller.setAnimationId(Sonic3kAnimationIds.ROLL.id());
        rolling.onTouchResponse(roller, null, 1);
        assertEquals(2, rolling.routine(), "cmpi.b #2,anim(a2) / beq");

        ToxomisterCloudInstance shielded = cloud();
        TestablePlayableSprite bubbled = player();
        bubbled.setShieldStateForTest(true, ShieldType.BUBBLE);
        shielded.onTouchResponse(bubbled, null, 1);
        assertEquals(2, shielded.routine(), "btst #Status_BublShield / bne");

        ToxomisterCloudInstance latched = cloud();
        latched.onTouchResponse(player(), null, 1);
        assertEquals(8, latched.routine(), "move.b #8,routine(a0)");
        assertEquals(59, latched.timer(), "move.w #60-1,$2E(a0)");
        assertEquals(1, latched.attachedPlayerSlot(), "$44(a0)");
        assertEquals(0, latched.getCollisionFlags(), "an attached cloud stops being touchable");
    }

    /**
     * {@code sub_8FFE0}: an eighth off {@code x_vel} every frame, and off {@code ground_vel} when
     * grounded. {@code asr.w #3} is an ARITHMETIC shift, so a negative speed decays toward zero
     * from below rather than reversing.
     */
    @Test
    void anAttachedCloudTakesAnEighthOfTheSpeedEveryFrame() {
        ToxomisterCloudInstance cloud = cloud();
        TestablePlayableSprite player = player();
        player.setAirForTest(false);
        cloud.onTouchResponse(player, null, 1);

        player.setXSpeed((short) 0x0800);
        player.setGSpeed((short) 0x0800);
        cloud.update(2, player);
        assertEquals(0x0800 - 0x0100, player.getXSpeed(), "x_vel - (x_vel asr 3)");
        assertEquals(0x0800 - 0x0100, player.getGSpeed(), "ground_vel while grounded");

        player.setXSpeed((short) -0x0800);
        cloud.update(3, player);
        assertEquals(-0x0800 + 0x0100, player.getXSpeed(),
                "an arithmetic shift decays a negative speed toward zero, it does not reverse it");

        player.setAirForTest(true);
        player.setYSpeed((short) 0x0400);
        short groundBefore = player.getGSpeed();
        cloud.update(4, player);
        assertEquals(0x0400 - 0x0080, player.getYSpeed(), "y_vel while airborne");
        assertEquals(groundBefore, player.getGSpeed(), "and ground_vel is left alone");
    }

    /** {@code cmpi.b #9,anim(a1)}: a spindash frees the player and scatters the puffs. */
    @Test
    void aSpindashFreesThePlayerAndRaisesTheScatterFlag() {
        ToxomisterCloudInstance cloud = cloud();
        TestablePlayableSprite player = player();
        cloud.onTouchResponse(player, null, 1);

        player.setAnimationId(Sonic3kAnimationIds.SPINDASH.id());
        cloud.update(2, player);

        assertTrue(cloud.escapedBySpindash(), "bset #2,$38(a0)");
        assertTrue(cloud.puffsDispersing(), "status bit 7");
        assertTrue(cloud.isDestroyed(), "Go_Delete_Sprite");
    }

    /**
     * {@code Check_LRControllerShake} (sonic3k.asm:179881-179900). {@code $3C(a0)} is loaded with
     * {@code 5}, but the escape is {@code subq.b #1,$3C(a0) / bmi}, so it is the reversal that
     * takes the counter BELOW zero that frees the player -- the sixth, not the fifth. Reading the
     * loaded constant as the count would be off by one.
     */
    @Test
    void theSixthLeftRightReversalShakesTheCloudOff() {
        ToxomisterCloudInstance cloud = cloud();
        TestablePlayableSprite player = player();
        cloud.onTouchResponse(player, null, 1);
        assertEquals(5, cloud.shakeReversalsLeft(), "move.b #5,$3C(a0)");

        boolean left = true;
        for (int reversal = 1; reversal <= 6; reversal++) {
            player.setDirectionalInputPressed(false, false, left, !left);
            left = !left;
            cloud.update(1 + reversal, player);
            if (reversal < 6) {
                assertFalse(cloud.isDestroyed(), "still held after " + reversal + " reversals");
            }
        }
        assertTrue(cloud.isDestroyed(), "the sixth reversal takes $3C(a0) below zero");
        assertFalse(cloud.escapedBySpindash(),
                "the shake escape does NOT raise $38 bit 2, so the puffs only rise");
    }

    /** Holding one direction is not a reversal: {@code eor.w d1,d0 / beq} keeps the player. */
    @Test
    void holdingOneDirectionNeverShakesItOff() {
        ToxomisterCloudInstance cloud = cloud();
        TestablePlayableSprite player = player();
        player.setDirectionalInputPressed(false, false, false, true);
        cloud.onTouchResponse(player, null, 1);

        for (int frame = 2; frame <= 40; frame++) {
            cloud.update(frame, player);
        }
        assertFalse(cloud.isDestroyed(), "a held direction is not a change");
        assertEquals(5, cloud.shakeReversalsLeft(), "$3C(a0) is untouched");
    }

    // ----- harness ------------------------------------------------------------------------------

    private static ToxomisterCloudInstance cloud() {
        ToxomisterBadnikInstance body = body(0);
        body.setServices(services());
        body.update(1, null);
        return body.cloud();
    }

    private static ToxomisterBadnikInstance body(int renderFlags) {
        return new ToxomisterBadnikInstance(
                new ObjectSpawn(BASE_X, BASE_Y, OBJECT_ID, 0, renderFlags, false, 0));
    }

    private static TestablePlayableSprite player() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic",
                (short) BASE_X, (short) BASE_Y);
        player.setCentreX((short) BASE_X);
        player.setCentreY((short) BASE_Y);
        player.setAirForTest(false);
        return player;
    }

    private static TestObjectServices services() {
        return new TestObjectServices().withIsolatedObjectManager();
    }
}
