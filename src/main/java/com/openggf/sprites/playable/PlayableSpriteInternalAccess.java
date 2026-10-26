package com.openggf.sprites.playable;

import com.openggf.level.objects.ObjectInstance;

/** Engine-only cross-package access to playable controller internals. */
public final class PlayableSpriteInternalAccess {
    /** Unknown is -1, outside the complete unsigned WORD domain (including zero). */
    public static int nativeMaximumSpeedWord(AbstractPlayableSprite player) {
        return player.nativeSpeedPolicy() != null ? player.nativeSpeedWords.maximum() : -1;
    }

    public static int nativeAccelerationWord(AbstractPlayableSprite player) {
        return player.nativeSpeedPolicy() != null ? player.nativeSpeedWords.acceleration() : -1;
    }

    public static int nativeDecelerationWord(AbstractPlayableSprite player) {
        return player.nativeSpeedPolicy() != null ? player.nativeSpeedWords.deceleration() : -1;
    }

    public static void publishNativeSpeedWords(AbstractPlayableSprite player, int maximum, int acceleration, int deceleration) {
        if (player.nativeSpeedPolicy() != null) player.nativeSpeedWords.publish(maximum, acceleration, deceleration);
        else player.nativeSpeedWords.forget();
    }

    public static void invalidateNativeSpeedWords(AbstractPlayableSprite player) {
        player.nativeSpeedWords.forget();
    }

    /** Primitive only: the currently unavailable native teleport transaction must own its eventual call. */
    public static void swapNativeSpeedWords(AbstractPlayableSprite first, AbstractPlayableSprite second) {
        var firstWords = first.nativeSpeedPolicy() != null ? first.nativeSpeedWords.capture() : null;
        var secondWords = second.nativeSpeedPolicy() != null ? second.nativeSpeedWords.capture() : null;
        first.nativeSpeedWords.restore(first.nativeSpeedPolicy() != null ? secondWords : null);
        second.nativeSpeedWords.restore(second.nativeSpeedPolicy() != null ? firstWords : null);
    }

    /** Structural authority only; binding never publishes effective speed words. */
    public static void bindNativeSpeedOwner(AbstractPlayableSprite player,
                                            com.openggf.game.session.WorldSession world) {
        player.nativeSpeedWords.bindOwner(world);
    }

    public static void initializeNativeSpeedWords(AbstractPlayableSprite player,
                                                  com.openggf.game.session.WorldSession world) {
        bindNativeSpeedOwner(player, world);
        initializeNativeSpeedWords(player);
    }

    public static void initializeNativeSpeedWords(AbstractPlayableSprite player) {
        var policy = player.nativeSpeedPolicy();
        if (policy != null) policy.initialize(player);
        else player.nativeSpeedWords.forget();
    }

    static void nativeSpeedWaterEdge(AbstractPlayableSprite player, boolean underwater) {
        var policy = player.nativeSpeedPolicy();
        if (policy != null) policy.waterEdge(player, underwater);
        else player.nativeSpeedWords.forget();
    }

    static void giveNativeSpeedShoes(AbstractPlayableSprite player) {
        var policy = player.nativeSpeedPolicy();
        if (policy != null) policy.giveSpeedShoes(player);
        else player.nativeSpeedWords.forget();
    }

    static void expireNativeSpeedShoes(AbstractPlayableSprite player) {
        var policy = player.nativeSpeedPolicy();
        if (policy != null) policy.expireSpeedShoes(player);
        else player.nativeSpeedWords.forget();
    }

    /** The native controller's profile is descriptive; it must not erase or replay a WORD write. */
    public static void installSuperPhysicsProfile(AbstractPlayableSprite player, SuperStateController controller,
                                                  com.openggf.game.PhysicsProfile profile) {
        var policy = player.nativeSpeedPolicy();
        if (policy != null && policy.ownsProfileInstallation(controller)) player.applyNativePhysicsProfile(profile);
        else player.applyExternalPhysicsProfile(profile);
    }

    /** Select before traction, so the modifier is applied exactly once to the chosen native operand. */
    public static short neutralGroundFrictionBase(AbstractPlayableSprite player) {
        var policy = player.nativeSpeedPolicy();
        if (policy != null) {
            int acceleration = PlayablePhysicsValueResolver.nativeAcceleration(player);
            return policy.neutralGroundFriction(player,
                    acceleration >= 0 ? (short) acceleration : player.getFriction());
        }
        return player.isSuperSonic() ? (short) 0xC : player.getFriction();
    }

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

    /**
     * Refreshes the physical sample when a primary routine skips ordinary input
     * publication. Do not merge forced/logical input or advance its edge latch:
     * late objects read Ctrl_*, while history still records Ctrl_*_logical.
     */
    public static void refreshRawControllerSample(AbstractPlayableSprite sprite, int heldMask, int pressedMask) {
        sprite.jumpInputPressed = (heldMask & AbstractPlayableSprite.INPUT_JUMP) != 0;
        sprite.jumpInputJustPressed = (pressedMask & AbstractPlayableSprite.INPUT_JUMP) != 0;
        sprite.setDirectionalInputPressed(
                (heldMask & AbstractPlayableSprite.INPUT_UP) != 0,
                (heldMask & AbstractPlayableSprite.INPUT_DOWN) != 0,
                (heldMask & AbstractPlayableSprite.INPUT_LEFT) != 0,
                (heldMask & AbstractPlayableSprite.INPUT_RIGHT) != 0);
    }

    /** Native primary-routine selection precedes input publication and CPU control. */
    public static boolean hasPrimaryDrowningRoutine(AbstractPlayableSprite sprite) {
        var rules = sprite.getGameRules();
        return sprite.isDrowningPreDeath() && !sprite.getDead() && !sprite.isDebugMode()
                && rules != null && rules.drowningBubble() != null
                && rules.drowningBubble().drownInstallsDedicatedPlayerRoutine();
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

    /** Native bset/bclr Status_Roll, without animation, box, radii or skid side effects. */
    public static void writeNativeRollingStatus(AbstractPlayableSprite sprite, boolean rolling) {
        sprite.rolling = rolling;
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
