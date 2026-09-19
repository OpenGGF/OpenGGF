package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SKL {@code $A5}, {@code Obj_Chainspike} (sonic3k.asm:199132-199420).
 *
 * <p>Every expected number is a literal from the ROM listing, never a call back into the
 * object's own arithmetic.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestChainspikeBadnikInstance {

    /** {@code DEZ2_Sprites} record 7: {@code $0480,$05B0}, subtype {@code $00}, no flips. */
    private static final int OBJECT_X = 0x0480;
    private static final int OBJECT_Y = 0x05B0;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /**
     * {@code loc_91C62} :199172-199179. {@code -$1200} and {@code $180}, both negated for a
     * {@code render_flags} bit 0 placement.
     */
    @Test
    void theLaunchVelocityAndRampAreMirroredByTheFlipBit() {
        HeadlessTestFixture fixture = fixture();
        try {
            ChainspikeBadnikInstance right = started(0, fixture.sprite());
            assertEquals(-0x1200, right.launchVelocityForTest(), "unflipped charges towards -X");
            ChainspikeBadnikInstance left = started(1, fixture.sprite());
            assertEquals(0x1200, left.launchVelocityForTest(), "bit 0 negates both words");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code $2E(a0)} is zero out of the RAM wipe and {@code SetUp_ObjAttributes}
     * (:41043-41052) never writes it, so {@code Obj_Wait}'s first {@code subq.w #1} already
     * goes negative: the badnik charges on its first update in routine 2, with no rest first.
     */
    @Test
    void theFirstChargeStartsImmediately() {
        HeadlessTestFixture fixture = fixture();
        try {
            ChainspikeBadnikInstance body = started(0, fixture.sprite());
            AbstractPlayableSprite sprite = farAwayPlayer(fixture);
            assertEquals("WAITING", body.routineForTest(), "precondition: routine 2");
            body.update(0, sprite);
            assertEquals("CHARGING", body.routineForTest(), "loc_91CA6 on the first Obj_Wait");
            assertEquals(-0x1200, body.xVelocityForTest(), "x_vel comes from $3E(a0)");
            assertEquals(0x180, body.rampForTest(), "$40(a0) comes from $3C(a0)");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_91CC2} :199191-199204. The ramp steps {@code $C} towards zero every update and
     * the new value is added to {@code x_vel}, so the corrections run
     * {@code $174, $168, $15C, ...} — largest first.
     */
    @Test
    void theDecelerationRampRunsDownByTwelveAnUpdate() {
        HeadlessTestFixture fixture = fixture();
        try {
            ChainspikeBadnikInstance body = started(0, fixture.sprite());
            AbstractPlayableSprite sprite = farAwayPlayer(fixture);
            body.update(0, sprite);
            int[] expectedRamp = { 0x174, 0x168, 0x15C, 0x150 };
            int velocity = -0x1200;
            for (int update = 0; update < expectedRamp.length; update++) {
                body.update(update + 1, sprite);
                velocity += expectedRamp[update];
                assertEquals(expectedRamp[update], body.rampForTest(), "ramp " + update);
                assertEquals(velocity, body.xVelocityForTest(), "x_vel " + update);
            }
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code smi d2 / tst.w $3E(a0) / bpl / not.b d2 / bne loc_91CF6} (:199197-199211). The
     * charge ends on the update the velocity would cross zero; the rest is {@code (2*60)-1}
     * updates and both stored words are negated so the next charge goes the other way.
     *
     * <p>The correction after {@code n} updates sums to {@code n*$180 - $C*n*(n+1)/2}. That
     * first exceeds {@code $1200} at {@code n = 17} ({@code $1980 - $726 = $125A}, against
     * {@code $1200 - $660 = $11A0} at sixteen), so the charge is seventeen updates long.
     */
    @Test
    void theChargeEndsWhenTheVelocityWouldCrossZeroAndThenReverses() {
        HeadlessTestFixture fixture = fixture();
        try {
            ChainspikeBadnikInstance body = started(0, fixture.sprite());
            AbstractPlayableSprite sprite = farAwayPlayer(fixture);
            body.update(0, sprite);
            int updates = 0;
            while ("CHARGING".equals(body.routineForTest()) && updates < 200) {
                body.update(updates + 1, sprite);
                updates++;
            }
            assertEquals("WAITING", body.routineForTest(), "loc_91CF6 installs routine 2");
            assertEquals(17, updates, "n*$180 - $C*n*(n+1)/2 first exceeds $1200 at n = 17");
            assertEquals(119, body.waitTimerForTest(), "move.w #(2*60)-1,$2E(a0)");
            assertEquals(0x1200, body.launchVelocityForTest(), "neg.w $3E(a0)");
        } finally {
            SessionManager.clear();
        }
    }

    /** {@code ChildObjDat_91EEC} :199412-199420: four children at the listed offsets. */
    @Test
    void theFourChildrenSitAtTheRomsOffsets() {
        HeadlessTestFixture fixture = fixture();
        try {
            ChainspikeBadnikInstance body = started(0, fixture.sprite());
            List<ChainspikeBadnikInstance.ChainspikeChild> children = body.childrenForTest();
            assertEquals(4, children.size(), "dc.w 4-1 (:199413)");
            int[][] offsets = { {0, 0x14}, {0, -0x14}, {0x14, 0}, {-0x14, 0} };
            for (int index = 0; index < children.size(); index++) {
                var child = children.get(index);
                // Refresh_ChildPosition (:199288) runs in the child's own update, so the
                // offsets are only on the live spawn once each child has run once.
                child.update(0, fixture.sprite());
                assertEquals(index, child.indexForTest(), "child order");
                assertEquals(OBJECT_X + offsets[index][0], child.getX(), "child " + index + " x");
                assertEquals(OBJECT_Y + offsets[index][1], child.getY(), "child " + index + " y");
            }
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code word_91EE6} (:199410-199411) gives the spikes {@code $98}, and {@code loc_91E5A}
     * (:199336) gives the fixed pair the same: every child is a hurt box the player cannot
     * break. Only the body carries {@code ObjDat_Chainspike}'s destructible {@code $1A}.
     */
    @Test
    void everyChildIsAHurtBoxAndOnlyTheBodyIsDestructible() {
        HeadlessTestFixture fixture = fixture();
        try {
            ChainspikeBadnikInstance body = started(0, fixture.sprite());
            for (var child : body.childrenForTest()) {
                assertEquals(0x98, child.collisionFlagsForTest(),
                        "child " + child.indexForTest() + " collision_flags");
            }
            assertEquals(0x1A, body.getCollisionFlags() & 0x3F,
                    "ObjDat_Chainspike collision flags $1A (:199409)");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_91D8C} :199289-199302. The parent's Y flip picks which spike extends — the
     * lower one when it is unflipped — and the other never does. The extension is {@code 8} px
     * an update for {@code $17} updates.
     */
    @Test
    void onlyOneSpikeExtendsAndItMovesEightPixelsAnUpdate() {
        HeadlessTestFixture fixture = fixture();
        try {
            ChainspikeBadnikInstance body = started(0, fixture.sprite());
            AbstractPlayableSprite sprite = farAwayPlayer(fixture);
            triggerExtend(body, sprite);
            assertTrue(body.extendSignalForTest(), "loc_91D12 sets bit 1 of $38(a0)");

            var lower = body.childrenForTest().get(0);
            var upper = body.childrenForTest().get(1);
            lower.update(0, sprite);
            upper.update(0, sprite);
            assertTrue(lower.isExtendingForTest(), "the unflipped parent extends the lower spike");
            assertFalse(upper.isExtendingForTest(), "and never the upper one");
            assertEquals(8, lower.extendVelocityForTest(), "moveq #8,d0 (:199296)");

            lower.update(1, sprite);
            assertEquals(8, lower.extendOffsetForTest(), "add.w y_vel(a0),d0 (:199308-199309)");
            // word_91EE6's $80 is height_pixels, while ObjCheckFloorDist reads the untouched
            // y_radius byte (zero), so the first two steps do not invent a 128px-ahead hit.
            lower.update(2, sprite);
            assertEquals(8, lower.extendVelocityForTest(),
                    "the signed $80 probe does not manufacture an immediate floor hit");
            assertEquals(16, lower.extendOffsetForTest());
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code sub_91EB0} :199383-199401 over {@code RawAni_91ED4} ({@code 7,6,5,4,2}) in
     * {@code moveq #$18,d3} steps, compared with {@code bls}.
     */
    @Test
    void theExtensionFrameIsTheDistanceInTwentyFourPixelSteps() {
        assertEquals(7, ChainspikeBadnikInstance.ChainspikeChild.frameForExtension(0x00));
        assertEquals(7, ChainspikeBadnikInstance.ChainspikeChild.frameForExtension(0x18));
        assertEquals(6, ChainspikeBadnikInstance.ChainspikeChild.frameForExtension(0x19));
        assertEquals(6, ChainspikeBadnikInstance.ChainspikeChild.frameForExtension(0x30));
        assertEquals(5, ChainspikeBadnikInstance.ChainspikeChild.frameForExtension(0x48));
        assertEquals(4, ChainspikeBadnikInstance.ChainspikeChild.frameForExtension(0x60));
        assertEquals(7, ChainspikeBadnikInstance.ChainspikeChild.frameForExtension(0x400),
                "moveq #0,d4 on a dbf fall-through (:199396) goes back to the first entry");
    }

    /**
     * {@code sub_91E7E} :199355-199356: {@code cmpi.w #$10,d2 / bhs} — a player has to be
     * within sixteen pixels horizontally, which is far closer than the Spikebonker's
     * {@code $60}.
     */
    @Test
    void onlyAPlayerWithinSixteenPixelsInterruptsTheCharge() {
        HeadlessTestFixture fixture = fixture();
        try {
            ChainspikeBadnikInstance body = started(0, fixture.sprite());
            AbstractPlayableSprite sprite = fixture.sprite();
            moveTo(sprite, OBJECT_X + 0x10, OBJECT_Y);
            body.update(0, sprite);
            assertEquals("CHARGING", body.routineForTest(), "$10 away is bhs, so it charges");

            ChainspikeBadnikInstance close = started(0, fixture.sprite());
            moveTo(sprite, OBJECT_X + 0x0F, OBJECT_Y);
            close.update(0, sprite);
            assertEquals("REACTING", close.routineForTest(), "$F away is not");
        } finally {
            SessionManager.clear();
        }
    }

    // --- helpers ---

    /** Drives the body to the update on which {@code loc_91D12} sets the extend signal. */
    private void triggerExtend(ChainspikeBadnikInstance body, AbstractPlayableSprite sprite) {
        moveTo(sprite, OBJECT_X, OBJECT_Y);
        for (int update = 0; update < 400 && !body.extendSignalForTest(); update++) {
            body.update(update, sprite);
        }
    }

    private AbstractPlayableSprite farAwayPlayer(HeadlessTestFixture fixture) {
        AbstractPlayableSprite sprite = fixture.sprite();
        moveTo(sprite, OBJECT_X + 0x800, OBJECT_Y);
        return sprite;
    }

    private ChainspikeBadnikInstance started(int renderFlags, AbstractPlayableSprite sprite) {
        ObjectSpawn spawn = new ObjectSpawn(OBJECT_X, OBJECT_Y, 0xA5, 0, renderFlags,
                false, OBJECT_Y, -1);
        ChainspikeBadnikInstance body = new ChainspikeBadnikInstance(spawn);
        GameServices.level().getObjectManager().addDynamicObject(body);
        body.forceOnscreenForTest();
        // The INIT routine, which SetUp_ObjAttributes reaches through addq.b #2,routine(a0).
        body.update(0, sprite);
        return body;
    }

    private void moveTo(AbstractPlayableSprite sprite, int centreX, int centreY) {
        NativePositionOps.writeXPosResetSubpixel(sprite, centreX);
        NativePositionOps.writeYPosResetSubpixel(sprite, centreY);
    }

    private HeadlessTestFixture fixture() {
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
    }
}
