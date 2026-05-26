package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSignpostInstance {

    @Test
    void bumpFromBelowRequiresRomAnimationTwoAndUpwardVelocity() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setAir(true);
        player.setYSpeed((short) -0x100);
        player.setAnimationId(Sonic3kAnimationIds.SPRING);

        assertFalse(S3kSignpostInstance.hasRomBumpPose(player),
                "Obj_EndSign sub_83A70 rejects non-#2 animations before applying the upward hit "
                        + "(docs/skdisasm/sonic3k.asm:176372-176387)");

        player.setAnimationId(Sonic3kAnimationIds.ROLL);

        assertTrue(S3kSignpostInstance.hasRomBumpPose(player),
                "Obj_EndSign sub_83A70 accepts animation #2 with upward y_vel "
                        + "(docs/skdisasm/sonic3k.asm:176372-176387)");

        player.setYSpeed((short) 0);

        assertFalse(S3kSignpostInstance.hasRomBumpPose(player),
                "Obj_EndSign sub_83A70 rejects non-upward y_vel "
                        + "(docs/skdisasm/sonic3k.asm:176376-176377)");
    }

    @Test
    void sameFrameNativeP2BumpCanOverwriteNativeP1Velocity() {
        TestablePlayableSprite sonic = eligibleBumpPlayer("sonic", 0x32D0, 0x045D);
        TestablePlayableSprite tails = eligibleBumpPlayer("tails", 0x329A, 0x045D);
        int signpostX = 0x329F;
        int signpostY = 0x045D;

        int finalVelocity = 0;
        for (TestablePlayableSprite player : List.of(sonic, tails)) {
            assertTrue(S3kSignpostInstance.isRomBumpCandidate(signpostX, signpostY, player));
            finalVelocity = S3kSignpostInstance.romBumpXVelocity(signpostX, player.getCentreX());
        }

        assertEquals(0x0050, finalVelocity,
                "EndSign_CheckPlayerHit calls sub_83A70 for Sonic and then Tails after one cooldown check, "
                        + "so a same-frame native P2 hit overwrites the signpost x_vel "
                        + "(docs/skdisasm/sonic3k.asm:176342-176365, 176372-176387)");
    }

    @Test
    void landedTimerAdvancesOnlyAfterSignedNegativePostDecrement() {
        assertFalse(S3kSignpostInstance.romPostLandTimerExpired(0),
                "Obj_EndSignLanded uses subq.w then bmi.s; timer 1 -> 0 must keep waiting "
                        + "(docs/skdisasm/sonic3k.asm:176198-176208)");
        assertTrue(S3kSignpostInstance.romPostLandTimerExpired(0xFFFF),
                "Obj_EndSignLanded advances only when the post-decrement word is negative "
                        + "(docs/skdisasm/sonic3k.asm:176198-176208)");
    }

    @Test
    void mainEndingPoseDoesNotLockCtrl1LogicalInputHistory() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        player.setControlLocked(false);
        player.setLogicalInputState(false, false, false, false, false);
        player.endOfTick();

        S3kSignpostInstance.applyMainPlayerEndingPose(player);

        assertTrue(player.isObjectControlSuppressesMovement(),
                "Set_PlayerEndingPose writes object_control=$81 to freeze movement "
                        + "(docs/skdisasm/sonic3k.asm:181977-181988)");
        assertFalse(player.isControlLocked(),
                "Set_PlayerEndingPose does not set Ctrl_1_locked; Obj_EndSignLanded only sets Ctrl_2_locked "
                        + "(docs/skdisasm/sonic3k.asm:176198-176218, 181977-181988)");

        player.setLogicalInputState(false, false, false, true, false);
        player.endOfTick();

        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, player.getInputHistory(0),
                "Sonic_RecordPos must keep storing live Ctrl_1_logical for Tails' delayed follow input "
                        + "(docs/skdisasm/sonic3k.asm:21541-21545, 22119-22136)");
    }

    private static TestablePlayableSprite eligibleBumpPlayer(String code, int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0, (short) 0);
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        player.setYSpeed((short) -0x100);
        player.setAnimationId(Sonic3kAnimationIds.ROLL);
        return player;
    }
}
