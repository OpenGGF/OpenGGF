package com.openggf.level.objects;

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

    private static TouchResponseProfile decoded(TouchCategoryDecodeMode mode, boolean continuous,
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

    public static TouchResponseProfile fromProvider(TouchResponseProvider provider) {
        return fromCanonical(com.openggf.game.profiles.touchresponse.TouchResponseProfile.fromProvider(provider));
    }

    public static TouchResponseProfile standardEnemy() {
        return fromCanonical(com.openggf.game.profiles.touchresponse.TouchResponseProfile.standardEnemy());
    }

    public static TouchResponseProfile fromProvider(TouchResponseProvider provider, boolean multiRegionSource) {
        Objects.requireNonNull(provider, "provider");
        boolean sonic1 = provider.usesSonic1TouchSpecialPropertyResponse();
        boolean sonic2 = provider.usesSonic2TouchSpecialPropertyResponse();
        boolean s3k = provider.usesS3kTouchSpecialPropertyResponse();
        boolean forceEnemy = provider.usesEnemyTouchCategoryOverride();
        if ((sonic1 ? 1 : 0) + (sonic2 ? 1 : 0) + (s3k ? 1 : 0) > 1) {
            throw new IllegalArgumentException(
                    "Touch special-property decode mode must be Sonic 1, Sonic 2, or S3K");
        }
        if (forceEnemy && (sonic1 || sonic2 || s3k)) {
            throw new IllegalArgumentException(
                    "Enemy touch override cannot be combined with special-property decode modes");
        }
        TouchCategoryDecodeMode mode = forceEnemy ? TouchCategoryDecodeMode.FORCE_ENEMY
                : sonic1 ? TouchCategoryDecodeMode.S1_SPECIAL_PROPERTY
                : sonic2 ? TouchCategoryDecodeMode.SONIC2_SPECIAL_PROPERTY
                : s3k ? TouchCategoryDecodeMode.S3K_SPECIAL_PROPERTY : TouchCategoryDecodeMode.NORMAL;
        int shieldFlags = provider.getShieldReactionFlags();
        return decoded(mode, provider.requiresContinuousTouchCallbacks(), provider.requiresRenderFlagForTouch(),
                multiRegionSource, shieldFlags, provider.enablesPostSpecialTouchAirborneSideVelocityPreservation());
    }

    public com.openggf.game.profiles.touchresponse.TouchResponseProfile toCanonical() {
        return new com.openggf.game.profiles.touchresponse.TouchResponseProfile(
                categoryDecodeMode.toCanonical(),
                continuousCallbacks,
                requiresRenderFlagForTouch,
                multiRegionSource,
                shieldDeflectCapability.toCanonical(),
                shieldReactionFlags,
                enablesPostSpecialTouchAirborneSideVelocityPreservation,
                attackBouncePolicy.toCanonical(),
                actorContextPolicy.toCanonical(),
                stopAfterFirstOverlapPolicy.toCanonical());
    }

    public static TouchResponseProfile fromCanonical(
            com.openggf.game.profiles.touchresponse.TouchResponseProfile canonical) {
        Objects.requireNonNull(canonical, "canonical");
        if (canonical.attackBouncePolicy() == com.openggf.game.profiles.touchresponse.TouchAttackBouncePolicy.STANDARD_ENEMY_KILL
                && canonical.actorContextPolicy() == com.openggf.game.profiles.touchresponse.TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY
                && canonical.stopAfterFirstOverlapPolicy() == (canonical.multiRegionSource()
                    ? com.openggf.game.profiles.touchresponse.TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_MAIN_ONLY
                    : com.openggf.game.profiles.touchresponse.TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS)
                && (canonical.shieldReactionFlags() == 0 || canonical.shieldReactionFlags() == 8)
                && canonical.shieldDeflectCapability() == (canonical.shieldReactionFlags() == 8
                    ? com.openggf.game.profiles.touchresponse.TouchShieldDeflectCapability.SHIELD_DEFLECT
                    : com.openggf.game.profiles.touchresponse.TouchShieldDeflectCapability.NONE)) {
            return decoded(TouchCategoryDecodeMode.fromCanonical(canonical.categoryDecodeMode()),
                    canonical.continuousCallbacks(), canonical.requiresRenderFlagForTouch(), canonical.multiRegionSource(),
                    canonical.shieldReactionFlags(), canonical.enablesPostSpecialTouchAirborneSideVelocityPreservation());
        }
        return new TouchResponseProfile(
                TouchCategoryDecodeMode.fromCanonical(canonical.categoryDecodeMode()),
                canonical.continuousCallbacks(),
                canonical.requiresRenderFlagForTouch(),
                canonical.multiRegionSource(),
                TouchShieldDeflectCapability.fromCanonical(canonical.shieldDeflectCapability()),
                canonical.shieldReactionFlags(),
                canonical.enablesPostSpecialTouchAirborneSideVelocityPreservation(),
                TouchAttackBouncePolicy.fromCanonical(canonical.attackBouncePolicy()),
                TouchActorContextPolicy.fromCanonical(canonical.actorContextPolicy()),
                TouchOverlapStopPolicy.fromCanonical(canonical.stopAfterFirstOverlapPolicy()));
    }
}
