package com.openggf.sprites.playable;

import com.openggf.level.objects.ObjectInstance;

/** Engine-only cross-package access to playable controller internals. */
public final class PlayableSpriteInternalAccess {
    /** Raw native 2/4 dispatch only; leave animation, hurt effects and frame ownership attached. */
    public static void applyCapturedControlRoutineByte(AbstractPlayableSprite player, int raw) {
        if (raw != 2 && raw != 4) throw new IllegalArgumentException("Native control routine " + raw);
        player.hurt = raw == 4;
        player.setObjectRoutineOverride(null);
    }
    /** Native non-recording routines still use the canonical end-of-tick cleanup. */
    public static void markFollowerHistoryHandledWithoutWrite(AbstractPlayableSprite player) {
        player.markFollowerHistoryHandledWithoutWrite();
    }
    private PlayableSpriteInternalAccess() {
    }

    public static void bindMutatorPolicies(AbstractPlayableSprite sprite, PlayableMutatorPolicySource source) {
        sprite.mutatorPolicySource = source;
    }

    public static PlayableMutatorPolicy mutatorPolicy(AbstractPlayableSprite sprite) {
        PlayableMutatorPolicySource source = sprite.mutatorPolicySource;
        return source == null ? PlayableMutatorPolicy.STOCK
                : java.util.Objects.requireNonNull(source.policyFor(sprite), "effective playable policy");
    }

    public static boolean activateScriptedSuperForm(SuperStateController controller) {
        return controller.activateFromScript();
    }

    /** Runs the native display expiry before Sonic_Super can install a fresh timer. */
    public static void tickNativeDisplayInvincibility(AbstractPlayableSprite sprite) {
        sprite.tickInvincibilityDisplayTimer();
    }

    /**
     * Publishes the fluid bank selected by a native speed-constant writer.
     * Status_Underwater, gravity, jump force and water-transition history are
     * independent: a routine can overwrite the speed words while submerged.
     * The existing playable snapshot captures this latch.
     */
    public static void publishNativeSpeedConstantsWaterState(
            AbstractPlayableSprite sprite, boolean underwater) {
        sprite.waterPhysicsActive = underwater;
    }

    /** Internal palette-context construction for native scene controllers. */
    public static SuperStateController.PaletteTarget nativePaletteTarget(com.openggf.level.Palette palette,int line) {
        return new SuperStateController.PaletteTarget(palette,line);
    }

    /** Retires the persistent auxiliary slot when a native owner clears object RAM. */
    public static void retirePersistentInstaShield(AbstractPlayableSprite sprite) {
        var shield = sprite.getInstaShieldObject();
        if (shield != null && !shield.isDestroyed()) shield.destroy();
        sprite.setInstaShieldObject(null);
        sprite.markInstaShieldForReregistration();
    }

    /** Direct clr.b status for script owners; do not synthesize landing or resize collision radii. */
    public static void clearScriptedNativeStatus(AbstractPlayableSprite sprite) {
        sprite.setDirection(com.openggf.physics.Direction.RIGHT);
        sprite.clearAirForNativeControlRestore();
        sprite.setRollingFlagPreserveRadii(false);
        sprite.setOnObject(false);
        sprite.setRollingJump(false);
        sprite.pushing = false;
        sprite.clearUnderwaterStatusPreserveWaterPhysics();
        sprite.setPreventTailsRespawn(false);
    }

    /** Native slide get-up floor reset, preserving the turn byte before landing callbacks. */
    public static void landForNativeFloorReset(AbstractPlayableSprite sprite) {
        sprite.landForNativeFloorReset();
    }

    /** {@code object_control} bit 1 alone; a player ability's raw frames are not an object owner. */
    public static boolean objectOwnsMappingFrame(AbstractPlayableSprite sprite) {
        return sprite.objectMappingFrameControl;
    }

    public static Short projectedObjectControlledSolidContactXSpeed(
            AbstractPlayableSprite sprite, ObjectInstance candidate) {
        return sprite.getObjectControlledSolidContactProjectedXSpeed(candidate);
    }
}
