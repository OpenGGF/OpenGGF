package com.openggf.game.sonic2.kis2;

import com.openggf.game.rules.CrossGameRuleComposer;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.ObjectInteractionRules;
import com.openggf.game.rules.PlayerLandingRules;
import com.openggf.game.rules.PlayerMovementRules;

/**
 * Sonic 2 rules with the shipped KiS2 changes the engine already has a
 * semantic seam for ({@code docs/kis2/BRANCH_DIFFS.md}). These are KiS2
 * behaviour, never {@code fixBugs} toggles: the lock-on ROM is built with
 * {@code fixBugs = 0} and the changes live in {@code gameRevision=3} blocks.
 */
public final class Kis2Rules {

    private Kis2Rules() {
    }

    /**
     * KiS2 {@code Touch_Rings}, {@code Check_CNZ_bumpers} and
     * {@code TouchResponse} test {@code cmpi.b #$9C,mapping_frame} (Knuckles'
     * duck frame) instead of {@code $4D}. {@code Touch_Boss} still tests
     * {@code $4D}; the engine has one field, so bosses use {@code $9C} too.
     */
    public static final int DUCK_TOUCH_BOX_MAPPING_FRAME = 0x9C;

    public static final GameRules RULES = build();

    private static GameRules build() {
        GameRules base = GameRules.SONIC_2;
        PlayerMovementRules movement = base.playerMovement();
        PlayerLandingRules landing = movement.landing();
        // KiS2 Sonic_ResetOnFloor_Part2: y_pos += y_radius - 19 after resetting
        // radii (S3K Player_TouchFloor form) replaces stock S2's subq.w #5,y_pos.
        PlayerLandingRules kis2Landing = new PlayerLandingRules(
                landing.pinballLandingPreservesRoll(),
                landing.pinballLandingPreservesPinballMode(),
                true,
                landing.objectSolidHurtLandingRetainsRoutine());
        PlayerMovementRules kis2Movement = new PlayerMovementRules(
                movement.fixedAnglePosThreshold(),
                movement.inputAlwaysCapsGroundSpeed(),
                movement.angleDiffCardinalSnap(),
                movement.movingCrouchThreshold(),
                movement.airSuperspeedPreserved(),
                movement.slopeResistStartsFromRest(),
                movement.slopeRepelChecksOnObject(),
                movement.slopeRepelUsesS3kSlipKick(),
                kis2Landing,
                movement.levelBoundary(),
                movement.rollingJumpPinballGateRequiresSpindashFlag(),
                movement.rollStopsBelowMinimumSpeed(),
                movement.rollControlledDecelUsesEffectiveDecelQuarter(),
                movement.controlLockLatchesLogicalInput(),
                movement.hurtRoutineLatchesLogicalInput(),
                movement.waterExitBoostSkipsFastUpwardVelocity(),
                movement.slopeResistAppliesAtZeroInertia(),
                movement.tailsRollSpeedUsesEffectiveDecelQuarter(),
                movement.waterVelocityChangeGatedByObjectControl(),
                movement.landingWalkWriteSkippedWhileSpindashing());
        ObjectInteractionRules interaction = base.objectInteraction();
        ObjectInteractionRules kis2Interaction = new ObjectInteractionRules(
                interaction.bossHitNegatesGroundSpeed(),
                interaction.bossHitHalvesBounceVelocity(),
                interaction.sidekickDespawnUsesObjectIdMismatch(),
                interaction.sidekickNormalDespawnDelaysFreshRenderEntry(),
                interaction.sidekickDespawnUsesRidingInstanceLoss(),
                interaction.sidekickDespawnUsesInteractCodeWordChange(),
                interaction.sidekickNormalCpuSkipsHurtRoutine(),
                interaction.permanentRespawnTableLatch(),
                interaction.objectsExecuteAfterPlayerPhysics(),
                interaction.touchResponseUsesRenderFlagYGate(),
                interaction.touchResponseUsesPreviousCollisionResponseList(),
                interaction.animalObjectPreservesObjectMoveXSubpixel(),
                interaction.animalObjectUsesRenderFlagDeleteBounds(),
                interaction.solidPushReleaseWritesWalkRunAnimationWord(),
                interaction.solidPushReleaseSkipsWalkRunWhenRolling(),
                interaction.solidPushReleaseSkipsWalkRunWhenSpindashing(),
                DUCK_TOUCH_BOX_MAPPING_FRAME);
        return CrossGameRuleComposer.withPlayerRules(base, kis2Movement, kis2Interaction);
    }
}
