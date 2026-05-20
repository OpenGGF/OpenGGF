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
