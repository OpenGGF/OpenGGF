package com.openggf.game.soniccd.player;

import com.openggf.game.CharacterKey;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.PhysicsModifiers;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.internal.NativePlayerSpeedPolicy;
import com.openggf.game.internal.PlayerStageRoutines;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.SuperStateController;

import java.util.Objects;

/**
 * Sonic CD's speed-word writers and stage routines. Ground movement reads the words
 * directly (ObjSonic_MoveGround, Level/_Objects/Sonic/Main.asm:837-840).
 * The shared player stores them separately from its power-up flags for rewind.
 */
public final class SonicCdPhysicsProvider implements PhysicsProvider, PlayerStageRoutines, NativePlayerSpeedPolicy {
    private final CdPlayerStage stage;
    // Binding scratch, as in the cartridge provider: modifiers are copied into each player.
    private boolean bindingKnuckles;

    public SonicCdPhysicsProvider(CdPlayerStage stage) {
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    /** The stage routines this provider offers. */
    public CdPlayerStage stage() {
        return stage;
    }

    @Override
    public PhysicsProfile getProfile(String characterType) {
        bindingKnuckles = "knuckles".equalsIgnoreCase(characterType);
        return switch (characterType.toLowerCase(java.util.Locale.ROOT)) {
            case "tails" -> PhysicsProfile.SONIC_2_TAILS;
            case "knuckles" -> PhysicsProfile.SONIC_3K_KNUCKLES;
            default -> PhysicsProfile.SONIC_2_SONIC;
        };
    }

    @Override
    public PhysicsModifiers getModifiers() {
        return bindingKnuckles ? PhysicsModifiers.KNUCKLES : PhysicsModifiers.STANDARD;
    }


    @Override
    public boolean supports(AbstractPlayableSprite player, WorldSession ownerWorld) {
        return ownerWorld != null
                && ownerWorld.rootGameModule().getClass() == SonicCdGameModule.class
                && ownerWorld.getGameModule().getClass() == SonicCdGameModule.class
                && !WorldSessionPolicyAccess.hasPolicies(ownerWorld)
                && !CrossGameFeatureProvider.isActive()
                && player.getClass() == Sonic.class
                && CharacterKey.SONIC.equals(player.characterKey())
                && player.getPhysicsProfile() == PhysicsProfile.SONIC_2_SONIC;
    }

    @Override
    public void initialize(AbstractPlayableSprite player) {
        // ObjSonic_Init (Level/_Objects/Sonic/Main.asm:152-154).
        PlayableSpriteInternalAccess.publishNativeSpeedWords(player, 0x600, 0xC, 0x80);
    }

    @Override
    public void waterEdge(AbstractPlayableSprite player, boolean underwater) {
        // ObjSonic_Water (Level/_Objects/Sonic/Main.asm:488-490,509-511).
        PlayableSpriteInternalAccess.publishNativeSpeedWords(player,
                underwater ? 0x300 : 0x600, underwater ? 6 : 0xC, underwater ? 0x40 : 0x80);
    }

    @Override
    public void giveSpeedShoes(AbstractPlayableSprite player) {
        // ObjMonitorItem_Main .GiveSpeedShoes (Level/_Objects/Monitor and Time Post/Main.asm:570-580).
        PlayableSpriteInternalAccess.publishNativeSpeedWords(player, 0xC00, 0x18, 0x80);
    }

    @Override
    public void expireSpeedShoes(AbstractPlayableSprite player) {
        // ObjSonic_Display (Level/_Objects/Sonic/Main.asm:413-423).
        PlayableSpriteInternalAccess.publishNativeSpeedWords(player, 0x600, 0xC, 0x80);
    }

    @Override
    public short neutralGroundFriction(AbstractPlayableSprite player, short acceleration) {
        // ObjSonic_MoveGround .Settle (Level/_Objects/Sonic/Main.asm:837-839)
        // subtracts or adds the same d5 acceleration operand.
        return acceleration;
    }

    @Override
    public boolean ownsProfileInstallation(SuperStateController controller) {
        return false;
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
    public int lostRingTouchLayer(AbstractPlayableSprite player) {
        return stage.lostRingTouchLayer(player);
    }

    @Override
    public boolean deathKeepsXSpeed(AbstractPlayableSprite player) {
        return stage.deathKeepsXSpeed(player);
    }
}
