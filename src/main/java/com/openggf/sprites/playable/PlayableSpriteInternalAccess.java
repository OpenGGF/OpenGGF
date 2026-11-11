package com.openggf.sprites.playable;

import com.openggf.level.objects.ObjectInstance;

/** Engine-only cross-package access to playable controller internals. */
public final class PlayableSpriteInternalAccess {
    /** Bind ordinary resources before temporary native presentation or rewind reconciliation. */
    public static void bindNormalAnimationProfile(AbstractPlayableSprite player) {
        var controller = player.getSuperStateController();
        if (controller != null) controller.bindNormalAnimationProfile(player.getAnimationProfile());
    }

    /** Structural semantic owner. Null means the ordinary inferred status remains authoritative. */
    public interface SecondaryInvincibility {
        Boolean read();
        void write(boolean value);
        com.openggf.game.PowerUpObject grantShield();
        com.openggf.game.PowerUpObject grantInvincibilityStars();
        void restoreCapturedEffects();
    }

    public static Boolean nativeSecondaryInvincibility(com.openggf.game.PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite actor)) return null;
        var owner = actor.nativeSecondaryInvincibilityOwner();
        return owner != null ? owner.read() : null;
    }

    public static void bindNativeSecondaryInvincibility(AbstractPlayableSprite player, SecondaryInvincibility owner) {
        player.bindNativeSecondaryInvincibility(owner);
    }

    public static void writeNativeSecondaryInvincibility(AbstractPlayableSprite player, boolean value) {
        player.writeNativeSecondaryInvincibility(value);
    }

    /** Fixed-size private ghost state: no gameplay/controller owners or rewind payloads. */
    public record GhostPresentationState(
            short xPixel, short yPixel, short xSubpixel, short ySubpixel,
            com.openggf.physics.Direction direction, com.openggf.game.GroundMode groundMode,
            short xSpeed, short ySpeed, short gSpeed, byte angle,
            boolean air, boolean rolling, boolean movementInputActive,
            boolean nativeSlotPresent, boolean pushing, boolean skidding,
            int mappingFrame, int animationId, int animationFrameIndex, int animationTick,
            boolean renderHFlip, boolean renderVFlip,
            com.openggf.sprites.managers.PlayableAnimationInternalAccess.PresentationState animation) { }

    /** Only the diagnostic renderer's unregistered copies may use these bridges. */
    public static GhostPresentationState captureGhostPresentation(AbstractPlayableSprite ghost) {
        return ghost.captureGhostPresentation();
    }

    public static void restoreGhostPresentation(AbstractPlayableSprite ghost, GhostPresentationState state) {
        ghost.restoreGhostPresentation(state);
    }

    /** Input scalars for private animation, never a player/gameplay restore. */
    public record GhostPresentationInput(short x, short y, int xSub, int ySub,
            short xSpeed, short ySpeed, short gSpeed, byte angle,
            boolean air, boolean rolling, com.openggf.game.GroundMode groundMode,
            com.openggf.physics.Direction direction, boolean movementInputActive) { }

    public static void applyGhostPresentationInput(AbstractPlayableSprite ghost, GhostPresentationInput input) {
        ghost.applyGhostPresentationInput(input);
    }

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

    /** Actor-only primitive; the world owner validates routines and performs the rest of the table. */
    public static void swapNativeTeleportActors(AbstractPlayableSprite first, AbstractPlayableSprite second) {
        var a = first.captureNativeTeleportState();
        var b = second.captureNativeTeleportState();
        var am = first.controller.getMovement().captureRewindState();
        var bm = second.controller.getMovement().captureRewindState();
        first.controller.getMovement().restoreRewindState(withTeleportTilt(am, bm));
        second.controller.getMovement().restoreRewindState(withTeleportTilt(bm, am));
        first.applyNativeTeleportState(b);
        second.applyNativeTeleportState(a);
        // super_shoes (s2.asm:25924-25949) writes the effective bank.
        // Obj01_ChkShoes (s2.asm:36307-36329) restores Sonic's bank at expiry.
        // Obj02_ChkShoes (s2.asm:39043-39056) restores Tails' bank at expiry.
        // fixBugs=0: transfer effective WORDs, never recompute them from the receiving character.
        swapNativeSpeedWords(first, second);
        installTransferredShoes(first, b.shoes(), b.shoeTime());
        installTransferredShoes(second, a.shoes(), a.shoeTime());
    }

    private static com.openggf.sprites.managers.PlayableSpriteMovement.RewindState withTeleportTilt(
            com.openggf.sprites.managers.PlayableSpriteMovement.RewindState own,
            com.openggf.sprites.managers.PlayableSpriteMovement.RewindState incoming) {
        return new com.openggf.sprites.managers.PlayableSpriteMovement.RewindState(
                own.jumpPressed(),
                own.jumpPrevious(),
                own.jumpReleasedSinceJump(),
                own.testKeyPressed(),
                own.inputUp(),
                own.inputDown(),
                own.inputLeft(),
                own.inputRight(),
                own.inputJump(),
                own.inputJumpPress(),
                own.inputRawLeft(),
                own.inputRawRight(),
                own.facingFlipForcesPushClearAfterGroundWall(),
                own.wasCrouching(),
                own.preMoveBalanceEvaluated(),
                own.preMoveBalanceState(),
                own.preMoveBalanceDirection(),
                incoming.latchedNextTilt(),
                incoming.latchedTilt(),
                own.deathElapsedFrames());
    }

    public static void applyNativeTeleportEffects(AbstractPlayableSprite player,
            com.openggf.game.PowerUpObject shield, com.openggf.game.PowerUpObject stars) {
        player.applyNativeTeleportEffects(shield, stars);
    }

    private static void installTransferredShoes(AbstractPlayableSprite player, boolean active, int ticks) {
        String code = "SpeedShoes-" + player.getCode();
        if (!active || ticks == 0) player.currentTimerManager().removeTimerForCode(code);
        else {
            var timer = new com.openggf.timer.timers.SpeedShoesTimer(code, player);
            timer.setTicks(ticks);
            player.currentTimerManager().registerTimer(timer);
        }
    }

    /** Primitive used only within a complete native teleport transaction. */
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

    public static void movementTick(AbstractPlayableSprite sprite) {
        sprite.dispatchMovementTick();
    }

    public static com.openggf.game.mutators.HomingAttackRuntime homingAttack(AbstractPlayableSprite sprite) {
        PlayableMutatorPolicySource source = sprite.mutatorPolicySource;
        var level = sprite.currentLevelManagerIfAvailable();
        return level == null ? (source == null ? null : source.homingAttack())
                : com.openggf.game.session.WorldSessionPolicyAccess.getService(level.getWorldSession(),
                        com.openggf.game.mutators.HomingAttackRuntime.class);
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
