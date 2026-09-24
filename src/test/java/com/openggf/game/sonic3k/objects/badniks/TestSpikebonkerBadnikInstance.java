package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static com.openggf.game.sonic3k.objects.badniks.SpikebonkerBadnikInstance.SpikebonkerMace.angleOffset;
import static com.openggf.game.sonic3k.objects.badniks.SpikebonkerBadnikInstance.SpikebonkerMace.frameForAngle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SKL {@code $A4}, {@code Obj_Spikebonker} (sonic3k.asm:198893-199124): Death Egg's mace robot.
 *
 * <p>Every expected number is a literal read out of the listing — {@code AngleLookup_1}'s own
 * bytes, {@code byte_91C0E}'s own thresholds — never a value computed by calling the object's
 * arithmetic with the test's copy of a constant.
 */
class TestSpikebonkerBadnikInstance {

    private static final int OBJECT_X = 0x0200;
    private static final int OBJECT_Y = 0x0100;

    /**
     * {@code ObjDat_Spikebonker} (:199117-199120) and {@code word_91C26} (:199121-199123). The
     * mace's {@code $9A} has bit 7 set where the body's {@code $1A} does not: the head hurts and
     * cannot be broken, so a player who destroys this badnik has hit the body.
     */
    @Test
    void theBodyIsTheBreakableHitboxAndTheMaceIsTheHurtOne() {
        SpikebonkerBadnikInstance body = activated(0x20, 0);

        assertEquals(0x1A, body.getCollisionFlags(), "ObjDat_Spikebonker collision_flags");
        assertEquals(5, body.getPriorityBucket(), "dc.w $280");
        assertEquals(0x10, body.getOnScreenHalfWidth(), "width_pixels $10");
        assertEquals(0x14, body.getOnScreenHalfHeight(), "height_pixels $14");

        SpikebonkerBadnikInstance.SpikebonkerMace mace = body.maceForTest();
        assertNotNull(mace, "CreateChild1_Normal with ChildObjDat_91C2C (:198920)");
        assertEquals(0x9A, mace.getCollisionFlags(), "word_91C26 collision_flags");
        assertEquals(0x10, mace.getOnScreenHalfWidth(), "the mace's own $10 x $10");
        assertEquals(0x10, mace.getOnScreenHalfHeight());
    }

    /**
     * {@code move.w #-$80,d0 / btst #0,render_flags(a0) / neg.w d0} (:198908-198912), and the
     * two-phase {@code Obj_Wait} countdown {@code $2E = subtype - 1}, {@code $3A = subtype*2 - 1}
     * (:198914-198918). The first leg is half the length of every leg after it, because the
     * badnik starts at one end of its beat.
     */
    @Test
    void theFirstPatrolLegIsHalfTheLengthOfEveryLegAfterIt() {
        SpikebonkerBadnikInstance body = activated(0x20, 0);

        assertEquals(-0x80, body.xVelocityForTest(), "move.w #-$80,d0 with render_flags bit 0 clear");
        assertEquals(0x20 - 1, body.walkTimerForTest(), "$2E = subtype - 1");
        assertEquals(0x20 * 2 - 1, body.walkTimerReloadForTest(), "$3A = subtype * 2 - 1");
        assertTrue(body.isFacingLeftForTest());

        // Obj_Wait decrements once per patrol update and turns on the update it goes negative.
        for (int update = 0; update < 0x20; update++) {
            body.update(10 + update, null);
        }

        assertEquals(0x80, body.xVelocityForTest(), "loc_91AB0 negates x_vel");
        assertFalse(body.isFacingLeftForTest(), "bchg #0,render_flags(a0)");
        assertEquals(0x20 * 2 - 1, body.walkTimerForTest(), "$2E is reloaded from $3A");
    }

    /** The same {@code btst #0,render_flags(a0)} on a flipped placement (:198909-198912). */
    @Test
    void aFlippedPlacementStartsWalkingRight() {
        SpikebonkerBadnikInstance body = activated(0x20, 1);

        assertEquals(0x80, body.xVelocityForTest(), "neg.w d0 for a render_flags bit 0 placement");
        assertFalse(body.isFacingLeftForTest());
    }

    /**
     * {@code move.w #$40,d0 / move.w d0,$3E(a0) / move.w d0,y_vel(a0) / move.w #4,$40(a0) /
     * bclr #0,$38(a0)} (:198926-198930), then {@code Swing_UpAndDown} (:180155-180180). With the
     * direction bit clear the acceleration is negated, so the hover falls by four a frame from
     * its {@code $40} peak.
     */
    @Test
    void theHoverFallsByFourAFrameFromTheFortyPeak() {
        SpikebonkerBadnikInstance body = activated(0x20, 0);

        assertEquals(0x40, body.yVelocityForTest(), "move.w d0,y_vel(a0) with d0 = $40");

        body.update(10, null);
        assertEquals(0x3C, body.yVelocityForTest(), "first swing update: $40 - 4");
        body.update(11, null);
        assertEquals(0x38, body.yVelocityForTest());
        body.update(12, null);
        assertEquals(0x34, body.yVelocityForTest());
    }

    /**
     * {@code cmpi.w #$60,d2 / bhs} and the {@code render_flags} side test (:198936-198944). The
     * slam fires only when the player is inside {@code $60} px <em>and</em> on the side the
     * badnik is walking toward; the other side is ignored at the same distance.
     */
    @Test
    void theSlamNeedsBothTheRangeAndTheSideItIsWalkingToward() {
        assertTrue(bonksAt(-0x5F, 0), "$5F to the left of a left-walking badnik");
        assertFalse(bonksAt(-0x60, 0), "$60 is the exclusive edge");
        assertFalse(bonksAt(0x10, 0), "the right side is ignored while it walks left");
        assertTrue(bonksAt(0x5F, 1), "and a right-walking badnik answers to the right side");
        assertFalse(bonksAt(-0x10, 1), "but not to the left one");
    }

    /**
     * {@code loc_91A9A} (:198952-198956): {@code routine} 4, {@code bset #3,$38(a0)} and
     * {@code sfx_Bouncy}. The walk stops for the whole slam — {@code loc_91AC2} runs nothing
     * until the mace clears the bit (:198969-198973).
     */
    @Test
    void theSlamPlaysBouncyAndFreezesTheWalkUntilTheMaceReleasesIt() {
        ObjectServices services = servicesWithObjectManager();
        SpikebonkerBadnikInstance body = activated(0x20, 0, services);
        PlayableEntity player = playerAt(OBJECT_X - 0x10, OBJECT_Y);

        body.update(10, player);

        assertTrue(body.isBonkingForTest(), "move.b #4,routine(a0)");
        assertTrue(body.isBonkInProgress(), "bset #3,$38(a0)");
        verify(services).playSfx(Sonic3kSfx.BOUNCY.id);

        int frozenX = body.getX();
        int frozenY = body.getY();
        for (int update = 0; update < 8; update++) {
            body.update(11 + update, player);
        }
        assertEquals(frozenX, body.getX(), "loc_91AC2 runs no movement");
        assertEquals(frozenY, body.getY());
        assertTrue(body.isBonkingForTest());

        body.clearBonkFlag();
        body.update(30, player);
        assertFalse(body.isBonkingForTest(), "move.b #2,routine(a0) once bit 3 is clear");
    }

    /**
     * {@code subq.b #8,d0 / move.b d0,$3C(a1)} (:199010-199011): the mace's angle walks down by
     * eight an update and wraps through zero, which is why it orbits continuously rather than
     * waiting for a slam.
     */
    @Test
    void theMaceAngleWalksDownByEightAndWraps() {
        SpikebonkerBadnikInstance body = activated(0x20, 0);
        SpikebonkerBadnikInstance.SpikebonkerMace mace = body.maceForTest();

        assertEquals(0, mace.angleForTest());
        mace.update(0, null);
        assertEquals(0xF8, mace.angleForTest(), "0 - 8 wraps to $F8");
        mace.update(1, null);
        assertEquals(0xF0, mace.angleForTest());
    }

    @Test
    void theCollapsedMaceRetainsThePrecedingDrawnHeadPositionForTouch() {
        SpikebonkerBadnikInstance body = activated(0x20, 0);
        SpikebonkerBadnikInstance.SpikebonkerMace mace = body.maceForTest();

        mace.update(0, null);
        int firstPublishedX = mace.getX();
        int firstPublishedY = mace.getY();
        mace.update(1, null);

        assertEquals(firstPublishedX, mace.getPreUpdateCollisionX(),
                "the player slot reads the preceding drawn-head publication");
        assertEquals(firstPublishedY, mace.getPreUpdateCollisionY());
    }

    /**
     * {@code MoveSprite_AngleXLookupOffset} (:178670-178713) reads {@code AngleLookup_1}
     * (:201847-201850) four different ways depending on the angle's top two bits. Expected
     * values are that table's own first and last bytes: {@code 0} and {@code $C}.
     */
    @Test
    void theMaceOffsetMirrorsTheAngleTableThroughFourQuadrants() {
        assertEquals(0x00, angleOffset(0x00), "AngleLookup_1[0]");
        assertEquals(0x0C, angleOffset(0x3F), "AngleLookup_1[$3F], the table's last byte");
        assertEquals(0x0C, angleOffset(0x40), "loc_84E60 reads $7F - $40 = $3F");
        assertEquals(0x00, angleOffset(0x7F), "loc_84E60 reads $7F - $7F = 0");
        assertEquals(0x00, angleOffset(0x80), "loc_84E6C negates AngleLookup_1[$80 & $3F]");
        assertEquals(-0x0C, angleOffset(0xBF), "and $BF & $3F is $3F");
        assertEquals(-0x0C, angleOffset(0xC0), "loc_84E7C reads $FF - $C0 = $3F");
        assertEquals(0x00, angleOffset(0xFF), "and $FF - $FF = 0");
    }

    /**
     * {@code sub_91BFA} over {@code byte_91C0E} (:199101-199116). The compare is {@code bls},
     * so each byte is an inclusive upper bound and the frame changes on the byte after it.
     */
    @Test
    void theMaceFrameComesFromTheRomThresholdTable() {
        assertEquals(1, frameForAngle(0x00));
        assertEquals(1, frameForAngle(0x30), "$30 is still frame 1");
        assertEquals(2, frameForAngle(0x31), "one past it is frame 2");
        assertEquals(2, frameForAngle(0x50));
        assertEquals(3, frameForAngle(0x51));
        assertEquals(3, frameForAngle(0xB0));
        assertEquals(2, frameForAngle(0xB1));
        assertEquals(2, frameForAngle(0xD0));
        assertEquals(1, frameForAngle(0xD1));
        assertEquals(1, frameForAngle(0xFF));
    }

    /**
     * {@code loc_91B14} → {@code loc_91B3E} → {@code loc_91B68} → {@code loc_91B70} →
     * {@code loc_91B8A} → {@code loc_91B56} (:199019-199077). The head slides out for
     * {@code $1F} counted frames at {@code 4} px each, sweeps its angle down to {@code $80},
     * slides back, and only then clears the body's bit 3.
     */
    @Test
    void theSlamSlidesOutSweepsToEightyAndComesBackBeforeReleasingTheBody() {
        SpikebonkerBadnikInstance body = activated(0x20, 0, servicesWithObjectManager());
        SpikebonkerBadnikInstance.SpikebonkerMace mace = body.maceForTest();
        PlayableEntity player = playerAt(OBJECT_X - 0x10, OBJECT_Y);
        body.update(10, player);
        assertTrue(body.isBonkInProgress(), "precondition: the slam was requested");

        // loc_91AEC only starts the slam from angle 0, which is where the mace begins.
        mace.update(0, null);
        int previous = -1;
        for (int update = 1; update < 200 && mace.slamOffsetForTest() != previous; update++) {
            previous = mace.slamOffsetForTest();
            mace.update(update, null);
        }
        // move.w #$1F,$2E(a0) and Obj_Wait give 32 slides of 4 px before the sweep takes over.
        assertEquals(-4 * 0x20, mace.slamOffsetForTest(), "moveq #-4,d0 over $1F + 1 frames");
        assertTrue(body.isBonkInProgress(), "the body is still held through the sweep");

        for (int update = 0; update < 300 && body.isBonkInProgress(); update++) {
            mace.update(0x200 + update, null);
        }
        assertFalse(body.isBonkInProgress(), "loc_91B56's bclr #3,$38(a1) ends the slam");
        assertEquals(0, mace.slamOffsetForTest(), "and the head is back where it started");
    }

    // --- helpers ---

    private static boolean bonksAt(int dx, int renderFlags) {
        SpikebonkerBadnikInstance body = activated(0x20, renderFlags, servicesWithObjectManager());
        body.update(10, playerAt(OBJECT_X + dx, OBJECT_Y));
        return body.isBonkingForTest();
    }

    private static SpikebonkerBadnikInstance activated(int subtype, int renderFlags) {
        return activated(subtype, renderFlags, servicesWithObjectManager());
    }

    private static SpikebonkerBadnikInstance activated(int subtype, int renderFlags,
                                                       ObjectServices services) {
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 1024, 0);
        SpikebonkerBadnikInstance body = new SpikebonkerBadnikInstance(new ObjectSpawn(
                OBJECT_X, OBJECT_Y, Sonic3kObjectIds.SPARKLE, subtype, renderFlags, false, 0));
        body.setServices(services);
        // Obj_WaitOffscreen (:198894) holds the object until Draw_Sprite has set render_flags
        // bit 7, which the engine reports through refreshPostCameraRenderState.
        body.update(0, null);
        body.refreshPostCameraRenderState();
        body.update(1, null);
        body.update(2, null);
        return body;
    }

    private static PlayableEntity playerAt(int centreX, int centreY) {
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getCentreX()).thenReturn((short) centreX);
        when(player.getCentreY()).thenReturn((short) centreY);
        return player;
    }

    private static ObjectServices servicesWithObjectManager() {
        ObjectServices services = mock(ObjectServices.class);
        when(services.objectManager()).thenReturn(mock(ObjectManager.class));
        return services;
    }
}
