package com.openggf.sprites.managers;

import com.openggf.sprites.playable.PlayableMutatorPolicySource;

/** Engine-only binding, deliberately outside the creator-facing Mod API. */
public final class SpriteManagerInternalAccess {
    private SpriteManagerInternalAccess() { }

    /**
     * Register a native script's temporary actor independently of configured donor participants.
     * Its body, palette and auxiliary art come from the host ROM. The recreation
     * factory must use this same binding; retired same-world membership retains it.
     */
    public static void addNativeScriptedTemporarySidekick(SpriteManager manager,
            com.openggf.sprites.playable.AbstractPlayableSprite sprite, String characterName,
            java.util.function.Supplier<com.openggf.sprites.playable.AbstractPlayableSprite> recreator) {
        manager.addNativeScriptedTemporarySidekick(sprite, characterName, recreator);
    }

    public static boolean usesNativeScriptedArt(SpriteManager manager,
            com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
        return manager.usesNativeScriptedArt(sprite);
    }

    /** Literal script mappings borrow only presentation resources, never gameplay RAM. */
    public static void borrowNativePlayerArt(SpriteManager manager, com.openggf.sprites.playable.AbstractPlayableSprite player) {
        manager.nativePlayerArtBindings.borrow(player);
    }

    public static void releaseNativePlayerArt(SpriteManager manager, com.openggf.sprites.playable.AbstractPlayableSprite player) {
        manager.nativePlayerArtBindings.release(player);
    }

    public static boolean hasBorrowedNativePlayerArt(SpriteManager manager, com.openggf.sprites.playable.AbstractPlayableSprite player) {
        return manager.nativePlayerArtBindings.isBorrowed(player);
    }

    public static void forgetNativePlayerArt(SpriteManager manager, com.openggf.sprites.playable.AbstractPlayableSprite player) {
        manager.forgetNativePlayerArt(player);
    }

    /** Registers only engine-owned fixed-Dust state; the creator-facing snapshots stay unchanged. */
    public static void registerDustEffectsRewind(SpriteManager manager,
            com.openggf.game.rewind.RewindRegistry registry) {
        requireEngineCaller();
        var owner = new PlayableDustRewind(manager);
        registry.deregister(owner.key());
        registry.deregisterPostRestoreCallback(owner.key());
        registry.register(owner);
        registry.registerPostRestoreCallback(owner.key(), owner::afterRestore);
    }

    public static com.openggf.game.rewind.RewindSnapshottable<?> nativePlayerArtAdapter(SpriteManager manager) {
        return manager.nativePlayerArtBindings.adapter();
    }

    public static void restoreNativePlayerArtAfterRoster(SpriteManager manager) {
        manager.nativePlayerArtBindings.afterRestore();
    }

    /** Retire a scene's follower roster while retaining reversible same-world membership. */
    public static void retireAllSidekicksForRewind(SpriteManager manager) {
        manager.retireAllSidekicksForRewind();
    }

    /**
     * Retire one follower whose object a script deleted, retaining reversible
     * same-world membership and its roster position.
     */
    public static void retireSidekickForRewind(SpriteManager manager,
            com.openggf.sprites.playable.AbstractPlayableSprite sidekick) {
        manager.retireSidekickForRewind(sidekick);
    }

    /** Re-admit retired followers on an ordinary return whose destination load recreates them. */
    public static void restoreRetiredSidekicksForReturn(SpriteManager manager) {
        manager.restoreRetiredSidekicksForReturn();
    }

    /**
     * Bind after gameplay-context creation. New/recreated roster members inherit
     * this source; a replacement gameplay context must be bound independently.
     */
    public static void bindMutatorPolicies(SpriteManager manager, PlayableMutatorPolicySource source) {
        manager.bindMutatorPolicies(source);
    }

    public static void bindMotionMutators(SpriteManager manager, com.openggf.game.mutators.MotionMutatorRuntime runtime) {
        requireEngineCaller();
        manager.bindMotionMutators(runtime);
    }

    public static com.openggf.game.mutators.MotionMutatorRuntime motionMutators(
            com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
        requireEngineCaller();
        return sprite.getMovementManager() instanceof PlayableSpriteMovement movement ? movement.motionMutators : null;
    }

    public static void prepareMotionInputForNativeTick(SpriteManager manager) {
        requireEngineCaller();
        manager.prepareMotionInputForNativeTick();
    }

    public static void clearPreparedMotionInput(SpriteManager manager) {
        requireEngineCaller();
        manager.clearPreparedMotionInput();
    }

    private static void requireEngineCaller() {
        if (com.openggf.util.EngineCallerAccess.callerOutside(SpriteManagerInternalAccess.class).getClassLoader()
                != SpriteManagerInternalAccess.class.getClassLoader()) throw new SecurityException("Engine motion access only");
    }

    public static com.openggf.control.LogicalInputSnapshot publishedInput(SpriteManager manager) {
        return manager.publishedLogicalInput();
    }

    /** S2's locked pre-main-loop player slots, without advancing the level clock. */
    public static void runNativeCompetitionTitlePlayablePass(SpriteManager manager) {
        manager.runNativeCompetitionTitlePlayablePass();
    }
}
