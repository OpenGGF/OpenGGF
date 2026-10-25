package com.openggf.sprites.playable;

import com.openggf.level.objects.ObjectInstance;

/** Engine-only cross-package access to playable controller internals. */
public final class PlayableSpriteInternalAccess {
    private PlayableSpriteInternalAccess() {
    }

    /**
     * Writes a native scripted logical word without rewriting the follower history
     * already recorded by the player slot. Unlike legacy forced-right steering,
     * a logical word can retain both horizontal buttons (Sonic_ChgJumpDir,
     * sonic3k.asm:23129-23158).
     */
    public static void writeScriptedLogicalInput(AbstractPlayableSprite sprite, int heldMask, boolean jumpPress) {
        sprite.setForcedInputMask(heldMask);
        sprite.forceInputRight = false;
        sprite.logicalInputStateForScript(heldMask, jumpPress);
    }

    /** Native primary-routine selection precedes input publication and CPU control. */
    public static boolean hasPrimaryDrowningRoutine(AbstractPlayableSprite sprite) {
        var rules = sprite.getGameRules();
        return sprite.isDrowningPreDeath() && !sprite.isDebugMode()
                && rules != null && rules.playerMovement().drowningUsesPrimaryRoutine();
    }

    public static boolean fixedCountdownOwnsDrowning(AbstractPlayableSprite sprite) {
        return sprite.fixedLevelObjectOwnsDrowningBubbleCadence();
    }

    /** The fixed countdown is the sole lifetime owner for the native player slots. */
    public static void finishFixedDrowning(AbstractPlayableSprite sprite) {
        sprite.drownPreDeathTimer = 0;
        sprite.setDead(true);
    }

    /** S1/S2 fixed drowning slots move with the old velocity, then add $10. */
    public static void moveFixedDrowning(AbstractPlayableSprite sprite) {
        sprite.move(sprite.getXSpeed(), sprite.getYSpeed());
        sprite.setYSpeed((short) (sprite.getYSpeed() + 0x10));
    }

    public static void spawnFixedDrowningBubble(AbstractPlayableSprite sprite, int angle, boolean skipFirstUpdate) {
        if (sprite.getDrowningController() != null)
            sprite.getDrowningController().spawnFixedDrowningBubble(angle, skipFirstUpdate);
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

    /** {@code object_control} bit 1 alone; a player ability's raw frames are not an object owner. */
    public static boolean objectOwnsMappingFrame(AbstractPlayableSprite sprite) {
        return sprite.objectMappingFrameControl;
    }

    public static Short projectedObjectControlledSolidContactXSpeed(
            AbstractPlayableSprite sprite, ObjectInstance candidate) {
        return sprite.getObjectControlledSolidContactProjectedXSpeed(candidate);
    }
}
