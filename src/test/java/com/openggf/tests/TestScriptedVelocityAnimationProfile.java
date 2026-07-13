package com.openggf.tests;

import org.junit.jupiter.api.Test;
import com.openggf.game.rules.GameRules;
import com.openggf.physics.Direction;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestScriptedVelocityAnimationProfile {

    @Test
    public void resolvesSlideAnimationWhenSlidingOnGround() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setSliding(true);
        sprite.setAir(false);
        sprite.setGSpeed((short) 0x0800); // would normally choose run

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertEquals(13, animId.intValue());
    }

    @Test
    public void keepsHurtAnimationPriorityOverSlide() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setSliding(true);
        sprite.setAir(false);
        sprite.setHurt(true);

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertEquals(11, animId.intValue());
    }

    @Test
    public void usesRollAnimationWhenSlidingAndAirborne() {
        // ROM: when airborne (e.g. jumping off water slide), the jump/roll mode
        // overwrites obAnim with id_Roll. Slide animation only applies on ground.
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setSliding(true);
        sprite.setRolling(true);
        sprite.setAir(true);
        sprite.setGSpeed((short) 0x0800);

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertEquals(3, animId.intValue()); // rollAnimId, not slideAnimId
    }

    @Test
    public void preservesSlideAnimationWhenTerrainDetachSetsAirAfterSlideDispatch() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setAnimationId(13);
        sprite.setSliding(true);
        sprite.setRolling(false);
        sprite.setJumping(false);
        sprite.setAir(true);

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertNull(animId,
                "AnglePos terrain detach does not overwrite LZWaterSlides' animation byte");
    }

    @Test
    public void rollingJumpPreservesSlideWrittenBeforeJumpDispatch() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setAnimationId(profile.getSlideAnimId());
        sprite.setSliding(true);
        sprite.setRolling(true);
        sprite.setRollingJump(true);
        sprite.setJumping(true);
        sprite.setAir(true);

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertNull(animId,
                "Sonic_Jump .rolljump sets only the Roll-Jump bit and leaves Slide active");
    }

    @Test
    public void preservesAnimationWhenFinalMoveLockTickExpiresAfterMoveDispatch() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setAnimationId(1);
        sprite.setSkidding(true);
        sprite.setMoveLockTimer(0);
        sprite.getAnimationManager().suppressGroundMovementAnimationForFrame();

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertNull(animId,
                "Sonic_Move skipped animation writes while locktime was non-zero at dispatch");
    }

    @Test
    public void preservesObjectAnimationForAirborneExternalReleaseWithRollingStatus() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setAir(true);
        sprite.setRolling(true);
        sprite.setJumping(true);
        sprite.setRollingJump(false);
        sprite.setSliding(false);
        sprite.setAnimationId(20);

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertNull(animId,
                "Obj80 moving-vine release leaves AniIDSonAni_Hang2 active even when Status_Roll remains set");
    }

    @Test
    void groundedRollPreservesAnimationWrittenByLaterObjectDispatch() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setRolling(true);
        sprite.setAir(false);
        sprite.setAnimationId(profile.getWalkAnimId());

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertNull(animId,
                "MdRoll does not overwrite a SolidObject push-release animation write");
    }

    @Test
    void zeroInertiaChoosesWaitEvenWhileOppositeDirectionRemainsHeld() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setMovementInputActive(true);
        sprite.getAnimationManager().captureGroundMovementAnimSpeed((short) 0);

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertEquals(0, animId.intValue(),
                "the enclosing ROM move routine writes WAIT after opposite-direction deceleration reaches zero");
    }

    @Test
    void releasedDirectionPreservesPublishedStopUntilItsScriptSwitches() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setAnimationId(profile.getSkidAnimId());
        sprite.setSkidding(false);
        sprite.setMovementInputActive(false);
        sprite.getAnimationManager().captureGroundMovementAnimSpeed((short) -0x0700);

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertNull(animId,
                "no-input Sonic_Move reaches ResetScr without replacing the existing Stop byte");
    }

    @Test
    void coastingWithoutDirectionPreservesExplicitAnimationOwner() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setAnimationId(profile.getSlideAnimId());
        sprite.setMovementInputActive(false);
        sprite.getAnimationManager().captureGroundMovementAnimSpeed((short) 0x0700);

        assertNull(profile.resolveAnimationId(sprite, 0, 32),
                "no-input Sonic_Move does not replace an explicit animation while inertia remains non-zero");
    }

    @Test
    void effectiveSameDirectionPublishesWalkDespiteOppositeRawInput() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setAnimationId(profile.getSkidAnimId());
        sprite.setMovementInputActive(true);
        sprite.setDirectionalInputPressed(false, false, false, true);
        sprite.setDirection(Direction.RIGHT);
        sprite.getAnimationManager().captureGroundMovementAnimSpeed((short) 0x0080);

        assertEquals(1, profile.resolveAnimationId(sprite, 1, 32).intValue(),
                "animation resolution follows the effective movement path, not raw input hidden by a forced direction");
    }

    @Test
    void releasedDirectionPreservesPublishedSpringWhileCoasting() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setAnimationId(profile.getSpringAnimId());
        sprite.setMovementInputActive(false);
        sprite.getAnimationManager().captureGroundMovementAnimSpeed((short) 0x0230);

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertNull(animId,
                "the friction-only tail does not replace an object-published Spring byte with Walk");
    }

    @Test
    void groundMovementWaitSurvivesAnglePosDetachFrame() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setAnimationId(1);
        sprite.getAnimationManager().captureGroundMovementAnimSpeed((short) 0);
        sprite.setAir(true);

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertEquals(0, animId.intValue(),
                "Move writes Wait before AnglePos sets Status_InAir on a ground-to-air detach frame");
    }

    @Test
    void hurtLandingPublishesWalkForRecoveryFrameBeforeReturningToWait() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setHurt(true);
        sprite.setAirRaw(true);
        sprite.captureOnObjectAtFrameStart();
        sprite.setHurt(false);
        sprite.setAirRaw(false);

        assertEquals(1, profile.resolveAnimationId(sprite, 0, 32).intValue(),
                "ROM hurt-stop writes Walk before running animation with zero inertia");

        sprite.captureOnObjectAtFrameStart();

        assertEquals(0, profile.resolveAnimationId(sprite, 1, 32).intValue(),
                "the following normal-control frame may select Wait at zero inertia");
    }

    @Test
    void ordinaryAirRoutineLandingPreservesItsIncomingAnimationForThatFrame() {
        TestSprite sprite = new TestSprite();
        ScriptedVelocityAnimationProfile profile = createProfile();
        sprite.setAirRaw(true);
        sprite.setAnimationId(profile.getWalkAnimId());
        sprite.captureOnObjectAtFrameStart();

        sprite.setAirRaw(false);

        assertNull(profile.resolveGroundMovementAnimId(sprite),
                "an airborne routine that lands does not run grounded animation selection in the same frame");
    }

    @Test
    public void s3kBalanceUsesSingleFacingSetForAwayStates() {
        ScriptedVelocityAnimationProfile profile = createProfile()
                .setBalanceAnimId(20)
                .setBalance2AnimId(21)
                .setBalance3AnimId(22)
                .setBalance4AnimId(23);
        TestSprite sprite = new TestSprite();
        sprite.useGameRules(GameRules.SONIC_3K);
        sprite.setBalanceState(4);

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertEquals(21, animId.intValue(),
                "S3K dummies out the S2 away-facing object/terrain balance animations and uses Balance2");
    }

    private static ScriptedVelocityAnimationProfile createProfile() {
        return new ScriptedVelocityAnimationProfile()
                .setIdleAnimId(0)
                .setWalkAnimId(1)
                .setRunAnimId(2)
                .setRollAnimId(3)
                .setRoll2AnimId(4)
                .setPushAnimId(5)
                .setDuckAnimId(6)
                .setLookUpAnimId(7)
                .setSpindashAnimId(8)
                .setSpringAnimId(9)
                .setDeathAnimId(10)
                .setHurtAnimId(11)
                .setSkidAnimId(12)
                .setSlideAnimId(13)
                .setAirAnimId(14)
                .setWalkSpeedThreshold(0x40)
                .setRunSpeedThreshold(0x600)
                .setFallbackFrame(0)
                .setAnglePreAdjust(true);
    }

    private static class TestSprite extends AbstractPlayableSprite {
        TestSprite() {
            super("test", (short) 0, (short) 0);
        }

        @Override
        public void draw() {
        }

        @Override
        protected void defineSpeeds() {
            runAccel = 12;
            runDecel = 128;
            friction = 12;
            max = 1536;
            jump = 1664;
        }

        @Override
        protected void createSensorLines() {
        }

        void useGameRules(GameRules fs) {
            super.setGameRulesForTest(fs);
        }

        void setAirRaw(boolean air) {
            this.air = air;
        }
    }
}
