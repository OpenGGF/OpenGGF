package com.openggf.level.objects;

import java.util.Objects;

public record TouchResponseProfile(
        TouchCategoryDecodeMode categoryDecodeMode,
        boolean continuousCallbacks,
        boolean requiresRenderFlagForTouch,
        boolean multiRegionSource,
        TouchShieldDeflectCapability shieldDeflectCapability,
        int shieldReactionFlags,
        TouchAttackBouncePolicy attackBouncePolicy,
        TouchActorContextPolicy actorContextPolicy,
        TouchOverlapStopPolicy stopAfterFirstOverlapPolicy) {

    private static final int SHIELD_REACTION_BOUNCE_BIT = 0x08;

    public TouchResponseProfile {
        Objects.requireNonNull(categoryDecodeMode, "categoryDecodeMode");
        Objects.requireNonNull(shieldDeflectCapability, "shieldDeflectCapability");
        Objects.requireNonNull(attackBouncePolicy, "attackBouncePolicy");
        Objects.requireNonNull(actorContextPolicy, "actorContextPolicy");
        Objects.requireNonNull(stopAfterFirstOverlapPolicy, "stopAfterFirstOverlapPolicy");
    }

    public static TouchResponseProfile fromProvider(TouchResponseProvider provider) {
        return fromCanonical(com.openggf.game.profiles.touchresponse.TouchResponseProfile.fromProvider(provider));
    }

    public static TouchResponseProfile standardEnemy() {
        return fromCanonical(com.openggf.game.profiles.touchresponse.TouchResponseProfile.standardEnemy());
    }

    public static TouchResponseProfile fromProvider(TouchResponseProvider provider, boolean multiRegionSource) {
        Objects.requireNonNull(provider, "provider");
        boolean sonic2 = provider.usesSonic2TouchSpecialPropertyResponse();
        boolean s3k = provider.usesS3kTouchSpecialPropertyResponse();
        if (sonic2 && s3k) {
            throw new IllegalArgumentException(
                    "Touch special-property decode mode must be Sonic 2 or S3K, not both");
        }
        TouchCategoryDecodeMode decodeMode = sonic2
                ? TouchCategoryDecodeMode.SONIC2_SPECIAL_PROPERTY
                : s3k ? TouchCategoryDecodeMode.S3K_SPECIAL_PROPERTY : TouchCategoryDecodeMode.NORMAL;
        int shieldFlags = provider.getShieldReactionFlags();

        return new TouchResponseProfile(
                decodeMode,
                provider.requiresContinuousTouchCallbacks(),
                provider.requiresRenderFlagForTouch(),
                multiRegionSource,
                (shieldFlags & SHIELD_REACTION_BOUNCE_BIT) != 0
                        ? TouchShieldDeflectCapability.SHIELD_DEFLECT
                        : TouchShieldDeflectCapability.NONE,
                shieldFlags,
                TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
                TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
                multiRegionSource
                        ? TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_MAIN_ONLY
                        : TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);
    }

    public com.openggf.game.profiles.touchresponse.TouchResponseProfile toCanonical() {
        return new com.openggf.game.profiles.touchresponse.TouchResponseProfile(
                categoryDecodeMode.toCanonical(),
                continuousCallbacks,
                requiresRenderFlagForTouch,
                multiRegionSource,
                shieldDeflectCapability.toCanonical(),
                shieldReactionFlags,
                attackBouncePolicy.toCanonical(),
                actorContextPolicy.toCanonical(),
                stopAfterFirstOverlapPolicy.toCanonical());
    }

    public static TouchResponseProfile fromCanonical(
            com.openggf.game.profiles.touchresponse.TouchResponseProfile canonical) {
        Objects.requireNonNull(canonical, "canonical");
        return new TouchResponseProfile(
                TouchCategoryDecodeMode.fromCanonical(canonical.categoryDecodeMode()),
                canonical.continuousCallbacks(),
                canonical.requiresRenderFlagForTouch(),
                canonical.multiRegionSource(),
                TouchShieldDeflectCapability.fromCanonical(canonical.shieldDeflectCapability()),
                canonical.shieldReactionFlags(),
                TouchAttackBouncePolicy.fromCanonical(canonical.attackBouncePolicy()),
                TouchActorContextPolicy.fromCanonical(canonical.actorContextPolicy()),
                TouchOverlapStopPolicy.fromCanonical(canonical.stopAfterFirstOverlapPolicy()));
    }
}
