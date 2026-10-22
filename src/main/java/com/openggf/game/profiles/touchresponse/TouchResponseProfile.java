package com.openggf.game.profiles.touchresponse;

import java.util.Objects;

@com.openggf.game.ModApi
public record TouchResponseProfile(
        TouchCategoryDecodeMode categoryDecodeMode,
        boolean continuousCallbacks,
        boolean requiresRenderFlagForTouch,
        boolean multiRegionSource,
        TouchShieldDeflectCapability shieldDeflectCapability,
        int shieldReactionFlags,
        boolean enablesPostSpecialTouchAirborneSideVelocityPreservation,
        TouchAttackBouncePolicy attackBouncePolicy,
        TouchActorContextPolicy actorContextPolicy,
        TouchOverlapStopPolicy stopAfterFirstOverlapPolicy) {

    private static final int SHIELD_REACTION_BOUNCE_BIT = 0x08;

    // Finite value table, never keyed by provider identity/class. Nonstandard shield
    // words retain their full int value through the uncached path.
    private static final TouchResponseProfile[] STABLE_PROFILES = stableProfiles();

    private static TouchResponseProfile[] stableProfiles() {
        var modes = TouchCategoryDecodeMode.values();
        var profiles = new TouchResponseProfile[modes.length * 32];
        for (var mode : modes) for (int bits = 0; bits < 32; bits++) {
            profiles[mode.ordinal() * 32 + bits] = createDecoded(mode,
                    (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0,
                    (bits & 16) != 0 ? 8 : 0, (bits & 8) != 0);
        }
        return profiles;
    }

    static TouchResponseProfile decoded(TouchCategoryDecodeMode mode, boolean continuous,
            boolean renderFlag, boolean multi, int shieldFlags, boolean preserveVelocity) {
        if (shieldFlags != 0 && shieldFlags != 8)
            return createDecoded(mode, continuous, renderFlag, multi, shieldFlags, preserveVelocity);
        int bits = (continuous ? 1 : 0) | (renderFlag ? 2 : 0) | (multi ? 4 : 0)
                | (preserveVelocity ? 8 : 0) | (shieldFlags == 8 ? 16 : 0);
        return STABLE_PROFILES[mode.ordinal() * 32 + bits];
    }

    private static TouchResponseProfile createDecoded(TouchCategoryDecodeMode mode, boolean continuous,
            boolean renderFlag, boolean multi, int shieldFlags, boolean preserveVelocity) {
        return new TouchResponseProfile(mode, continuous, renderFlag, multi,
                (shieldFlags & SHIELD_REACTION_BOUNCE_BIT) != 0
                        ? TouchShieldDeflectCapability.SHIELD_DEFLECT : TouchShieldDeflectCapability.NONE,
                shieldFlags, preserveVelocity, TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
                TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
                multi ? TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_MAIN_ONLY
                        : TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);
    }

    public TouchResponseProfile {
        Objects.requireNonNull(categoryDecodeMode, "categoryDecodeMode");
        Objects.requireNonNull(shieldDeflectCapability, "shieldDeflectCapability");
        Objects.requireNonNull(attackBouncePolicy, "attackBouncePolicy");
        Objects.requireNonNull(actorContextPolicy, "actorContextPolicy");
        Objects.requireNonNull(stopAfterFirstOverlapPolicy, "stopAfterFirstOverlapPolicy");
    }

    public TouchResponseProfile(
            TouchCategoryDecodeMode categoryDecodeMode,
            boolean continuousCallbacks,
            boolean requiresRenderFlagForTouch,
            boolean multiRegionSource,
            TouchShieldDeflectCapability shieldDeflectCapability,
            int shieldReactionFlags,
            TouchAttackBouncePolicy attackBouncePolicy,
            TouchActorContextPolicy actorContextPolicy,
            TouchOverlapStopPolicy stopAfterFirstOverlapPolicy) {
        this(categoryDecodeMode,
                continuousCallbacks,
                requiresRenderFlagForTouch,
                multiRegionSource,
                shieldDeflectCapability,
                shieldReactionFlags,
                false,
                attackBouncePolicy,
                actorContextPolicy,
                stopAfterFirstOverlapPolicy);
    }

    public static TouchResponseProfile fromProvider(com.openggf.level.objects.TouchResponseProvider provider) {
        Objects.requireNonNull(provider, "provider");
        boolean sonic1 = provider.usesSonic1TouchSpecialPropertyResponse();
        boolean sonic2 = provider.usesSonic2TouchSpecialPropertyResponse();
        boolean s3k = provider.usesS3kTouchSpecialPropertyResponse();
        TouchCategoryDecodeMode decodeMode = TouchResponseProfileMapper.decodeMode(sonic1, sonic2, s3k);
        return TouchResponseProfileMapper.fromDecodedProvider(
                provider, decodeMode, provider.getMultiTouchRegions() != null);
    }

    public static TouchResponseProfile standardEnemy() {
        return standardEnemy(false);
    }

    public static TouchResponseProfile standardEnemy(boolean multiRegionSource) {
        return decoded(TouchCategoryDecodeMode.NORMAL, false, true, multiRegionSource, 0, false);
    }

    public static TouchResponseProfile singleRegionContinuousCallbacks() {
        return decoded(TouchCategoryDecodeMode.NORMAL, true, true, false, 0, false);
    }

    public static TouchResponseProfile singleRegionShieldDeflect() {
        return decoded(TouchCategoryDecodeMode.NORMAL, false, true, false, SHIELD_REACTION_BOUNCE_BIT, false);
    }
}
