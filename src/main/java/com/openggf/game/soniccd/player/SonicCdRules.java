package com.openggf.game.soniccd.player;

import com.openggf.game.rules.CameraRules;
import com.openggf.game.rules.CrossGameRuleComposer;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.PlayerAirMovementRules;
import com.openggf.game.rules.PlayerAnimationRules;
import com.openggf.game.rules.PlayerCapabilityRules;
import com.openggf.game.rules.PlayerLevelBoundaryRules;
import com.openggf.game.rules.PlayerLandingRules;
import com.openggf.game.rules.PlayerMovementRules;

/**
 * Sonic CD's player and camera rules: Sonic 1's groups with each routine-level
 * difference found by comparing scddisasm {@code src/Level/_Objects/Sonic/Main.asm}
 * (pin 1ecc5f3a, shared by every zone except Wacky Workbench) with
 * {@code docs/s1disasm/_incObj/01 Sonic.asm}. Every value not changed here is Sonic 1's
 * because the two routines are the same instruction for instruction. The shipped
 * CD build has no {@code FixBugs} switch; its routines are modelled as assembled.
 */
public final class SonicCdRules {
    private SonicCdRules() { }

    public static final GameRules RULES = build();

    private static GameRules build() {
        GameRules s1 = GameRules.SONIC_1;
        PlayerMovementRules m = s1.playerMovement();
        PlayerAirMovementRules air = m.air();
        PlayerLevelBoundaryRules b = m.levelBoundary();
        PlayerMovementRules movement = new PlayerMovementRules(
                m.fixedAnglePosThreshold(),
                // ObjSonic_MoveGndLeft/.Accelerate and MoveGndRight/.Accelerate test top
                // speed BEFORE adding acceleration and keep a faster speed unchanged
                // (Main.asm:1247-1254, 1301-1306); Sonic 1 adds then clamps
                // ("01 Sonic.asm" Sonic_MoveLeft/Sonic_MoveRight). Same outcome as S2.
                false,
                m.angleDiffCardinalSnap(),
                m.groundPose(),
                // ObjSonic_MoveAir has no roll-jump steering lock (scddisasm/src/Level/_Objects/Sonic/Main.asm:1559-1603).
                new PlayerAirMovementRules(air.airSuperspeedPreserved(),
                        air.glideRestoresStandingRadiiAfterCollision(), air.idleWallClimbChecksFloor(),
                        air.airAbilityAcceptsOverlappingJumpPress(), air.glideFloorContactPrecedesWallGrab(), true,
                        // ObjMoveGrv caps the fall at $1000 (Object Functions.asm:68-73).
                        0x1000,
                        // ObjSonic_CheckJump's .RollJump keeps the roll radii (scddisasm/src/Level/_Objects/Sonic/Main.asm:1837-1838, 1858-1860).
                        true),
                m.slopeResistStartsFromRest(),
                m.slopeRepelChecksOnObject(),
                m.slopeRepelUsesS3kSlipKick(),
                // Player_LvlColAir_Up's steep-ceiling landing calls Player_ResetOnSteepSlope,
                // which keeps the roll state (Main.asm:2229-2232, 2339-2345).
                new PlayerLandingRules(m.landing().pinballLandingPreservesRoll(),
                        m.landing().pinballLandingPreservesPinballMode(),
                        m.landing().landingRollClearUsesCurrentYRadiusDelta(),
                        m.landing().objectSolidHurtLandingRetainsRoutine(),
                        m.landing().hurtLandingClearsObjectControl(),
                        m.landing().landingWalkWriteSkippedWhileSpindashing(), true),
                // ObjSonic_LevelBound: rightBound + 320-16, plus 56 outside a boss fight
                // (Main.asm:1686-1690); Sonic 1 uses +$128 and +$40.
                // ObjSonic_DeadChkGone measures from bottomBound + 256 (scddisasm/src/Level/_Objects/Sonic/Main.asm:2410-2414), but
                // while the player's routine is 6 or more Level_MainLoop writes cameraY into
                // bottomBound and destBottomBound and skips LevelScroll (src/Level/Main.asm:411-415),
                // so the restart row is the frozen camera Y + 256, not the boundary in force.
                new PlayerLevelBoundaryRules(b.rightStrict(), b.usesCentreY(), b.lockUsesScreenLockFlag(),
                        b.usesPreEasedMaxXDuringBossLock(), false,
                        b.deathFallRestartHandoffCancelsGravity(), b.hurtStopBottomKillUnsigned(), 16, 56),
                m.rollingJumpPinballGateRequiresSpindashFlag(),
                m.rollStopsBelowMinimumSpeed(),
                m.rollControlledDecelUsesEffectiveDecelQuarter(),
                m.controlLockLatchesLogicalInput(),
                m.hurtRoutineLatchesLogicalInput(),
                m.waterExitBoostSkipsFastUpwardVelocity(),
                m.slopeResistAppliesAtZeroInertia(),
                m.tailsRollSpeedUsesEffectiveDecelQuarter(),
                m.waterVelocityChangeGatedByObjectControl(),
                m.poweredRollFriction());
        CameraRules c = s1.camera();
        CameraRules camera = new CameraRules(
                c.lookScrollDelay(),
                // The water split is a palette swap from the water_y scanline with no
                // shimmer (r4/main.asm:1316-1358); Sonic 1's Labyrinth shimmer is off.
                false,
                c.fastScrollCap(),
                // MoveScreenHoriz_CamBehind caps the leftward move at -16
                // (Scroll (Act 1 Present).asm:597-600); Sonic 1 FixBugs=0 does not.
                false,
                c.useScreenYWrapValueForVisibility(),
                c.playerControlAppliesVerticalWrapMask(),
                c.groundedRollRecentersCameraBias(),
                true,
                true);
        PlayerCapabilityRules cap = s1.playerCapability();
        // Spin dash and super peel-out use the timed oPlayerCharge model, not S2's
        // rev counter, so spindashEnabled stays false (Main.asm:69-86, 946-1121, 1366-1427).
        // ObjSonic_StartRoll raises a non-negative speed below $200 to $200, also when
        // already rolling (Main.asm:1744-1777).
        PlayerCapabilityRules capability = new PlayerCapabilityRules(cap.spindashEnabled(),
                cap.spindashSpeedTable(), cap.elementalShieldsEnabled(), cap.instaShieldEnabled(),
                cap.tailsFlightEnabled(), cap.jumpRepressClearsRollJumpBeforeAbility(),
                cap.lightningShieldEnabled(), cap.superSpindashSpeedTable(), cap.glideAttacksEnabled(),
                true, true);
        PlayerAnimationRules a = s1.playerAnimation();
        // ObjSonic_MdAir resets a falling, un-rolled player to Walk (scddisasm/src/Level/_Objects/Sonic/Main.asm:713-721).
        PlayerAnimationRules animation = new PlayerAnimationRules(a.extendedEdgeBalance(),
                a.singleFacingBalanceAnimationSet(), a.animationChangeClearsPush(),
                a.walkRunDelayLatchesRenderOrientation(), a.angledLandingPublishesWalk(),
                a.airLandingPublishesTiltAngles(), a.tailsTailPushDetection(), true,
                // .BalanceRight/.BalanceLeft keep the facing and pick the forwards or
                // backwards animation (Main.asm:916-935).
                true);
        return CrossGameRuleComposer.withPlayerAndCameraRules(s1, movement, capability, animation, camera);
    }
}
