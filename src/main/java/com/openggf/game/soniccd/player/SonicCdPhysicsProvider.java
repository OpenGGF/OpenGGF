package com.openggf.game.soniccd.player;

import com.openggf.game.PhysicsModifiers;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.internal.PlayerStageRoutines;
import com.openggf.game.rules.GameRules;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Objects;

/**
 * Sonic CD's physics provider. ObjSonic_Init writes the same top speed, acceleration
 * and deceleration as Sonic 1 ($600/$C/$80, scddisasm src/Level/_Objects/Sonic/
 * Main.asm:153-155), so the profile is Sonic 1's; the rule differences are
 * {@link SonicCdRules}. The player's stage routines (3D ramps, special chunks and the
 * per-zone call sites) are {@link CdPlayerStage}'s, offered to the shared movement
 * owner as {@link PlayerStageRoutines}.
 */
public final class SonicCdPhysicsProvider implements PhysicsProvider, PlayerStageRoutines {
    private final CdPlayerStage stage;

    public SonicCdPhysicsProvider(CdPlayerStage stage) {
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    /** The stage routines this provider offers. */
    public CdPlayerStage stage() {
        return stage;
    }

    @Override
    public PhysicsProfile getProfile(String characterType) {
        return PhysicsProfile.SONIC_2_SONIC;
    }

    @Override
    public PhysicsModifiers getModifiers() {
        return PhysicsModifiers.STANDARD;
    }

    @Override
    public GameRules getRules() {
        return SonicCdRules.RULES;
    }

    @Override
    public boolean updatePseudo3dRamp(AbstractPlayableSprite player, boolean wasOnRamp) {
        return stage.updatePseudo3dRamp(player, wasOnRamp);
    }

    @Override
    public void afterPseudo3dRampCheck(AbstractPlayableSprite player) {
        stage.afterPseudo3dRampCheck(player);
    }

    @Override
    public boolean freezesAirMovement(AbstractPlayableSprite player) {
        return stage.freezesAirMovement(player);
    }

    @Override
    public boolean runsSpeedPannedFocus(AbstractPlayableSprite player) {
        return stage.runsSpeedPannedFocus(player);
    }

    @Override
    public boolean pseudo3dRampCentresFocus(AbstractPlayableSprite player) {
        return stage.pseudo3dRampCentresFocus(player);
    }

    @Override
    public void afterMovementMode(AbstractPlayableSprite player) {
        stage.afterMovementMode(player);
    }

    @Override
    public boolean objectCollisionEnabled(AbstractPlayableSprite player) {
        return stage.objectCollisionEnabled(player);
    }

    @Override
    public boolean touchesAsInvincible(AbstractPlayableSprite player) {
        return stage.touchesAsInvincible(player);
    }

    @Override
    public boolean runsStageAirState(AbstractPlayableSprite player) {
        return stage.runsStageAirState(player);
    }

    @Override
    public boolean holdsFallingAirAnimation(AbstractPlayableSprite player) {
        return stage.holdsFallingAirAnimation(player);
    }

    @Override
    public boolean capsUpwardVelocity(AbstractPlayableSprite player) {
        return stage.capsUpwardVelocity(player);
    }

    @Override
    public boolean rollEntrySpeedFloor(AbstractPlayableSprite player) {
        return stage.rollEntrySpeedFloor(player);
    }

    @Override
    public boolean balanceCancelsTimedCharge(AbstractPlayableSprite player) {
        return stage.balanceCancelsTimedCharge(player);
    }

    @Override
    public int timedSpindashStep(AbstractPlayableSprite player, int standardStep) {
        return stage.timedSpindashStep(player, standardStep);
    }

    @Override
    public void beforeHurt(AbstractPlayableSprite player) {
        stage.beforeHurt(player);
    }

    @Override
    public void beforePlayerRoutine(AbstractPlayableSprite player) {
        stage.beforePlayerRoutine(player);
    }

    @Override
    public void whileObjectControlled(AbstractPlayableSprite player) {
        stage.whileObjectControlled(player);
    }

    @Override
    public void afterHurtGravity(AbstractPlayableSprite player) {
        stage.afterHurtGravity(player);
    }

    @Override
    public boolean jumpLaunchedByStandingObject(AbstractPlayableSprite player) {
        return stage.jumpLaunchedByStandingObject(player);
    }

    @Override
    public int standingObjectMoveXBias(AbstractPlayableSprite player) {
        return stage.standingObjectMoveXBias(player);
    }

    @Override
    public boolean standingObjectHoldsWalk(AbstractPlayableSprite player) {
        return stage.standingObjectHoldsWalk(player);
    }

    @Override
    public boolean runsWaterCheck(AbstractPlayableSprite player) {
        return stage.runsWaterCheck(player);
    }

    @Override
    public boolean waterExitWaitsWhileFalling(AbstractPlayableSprite player) {
        return stage.waterExitWaitsWhileFalling(player);
    }

    @Override
    public void onWaterEntry(AbstractPlayableSprite player) {
        stage.onWaterEntry(player);
    }

    @Override
    public boolean spawnsWaterSplash(AbstractPlayableSprite player) {
        return stage.spawnsWaterSplash(player);
    }

    @Override
    public boolean ownsDrowningCountdown(AbstractPlayableSprite player) {
        return stage.ownsDrowningCountdown(player);
    }

    @Override
    public GroundMode stageGroundMode(AbstractPlayableSprite player) {
        return stage.stageGroundMode(player);
    }

    @Override
    public boolean admitsSpecialTouch(AbstractPlayableSprite player, com.openggf.level.objects.ObjectInstance object) {
        return stage.admitsSpecialTouch(player, object);
    }

    @Override
    public boolean deathKeepsXSpeed(AbstractPlayableSprite player) {
        return stage.deathKeepsXSpeed(player);
    }
}
