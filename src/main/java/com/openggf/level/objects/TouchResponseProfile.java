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
        Objects.requireNonNull(provider, "provider");

        TouchCategoryDecodeMode decodeMode = decodeMode(provider);
        boolean multiRegionSource = provider.getMultiTouchRegions() != null;
        int shieldFlags = provider.getShieldReactionFlags();
        TouchShieldDeflectCapability shieldCapability =
                (shieldFlags & SHIELD_REACTION_BOUNCE_BIT) != 0
                        ? TouchShieldDeflectCapability.SHIELD_DEFLECT
                        : TouchShieldDeflectCapability.NONE;

        return new TouchResponseProfile(
                decodeMode,
                provider.requiresContinuousTouchCallbacks(),
                provider.requiresRenderFlagForTouch(),
                multiRegionSource,
                shieldCapability,
                shieldFlags,
                TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
                TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
                multiRegionSource
                        ? TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_MAIN_ONLY
                        : TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);
    }

    private static TouchCategoryDecodeMode decodeMode(TouchResponseProvider provider) {
        boolean sonic2 = provider.usesSonic2TouchSpecialPropertyResponse();
        boolean s3k = provider.usesS3kTouchSpecialPropertyResponse();
        if (sonic2 && s3k) {
            throw new IllegalArgumentException(
                    "Touch special-property decode mode must be Sonic 2 or S3K, not both");
        }
        if (sonic2) {
            return TouchCategoryDecodeMode.SONIC2_SPECIAL_PROPERTY;
        }
        if (s3k) {
            return TouchCategoryDecodeMode.S3K_SPECIAL_PROPERTY;
        }
        return TouchCategoryDecodeMode.NORMAL;
    }
}
