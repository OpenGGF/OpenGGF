package com.openggf.game.profiles.touchresponse;

import com.openggf.level.objects.TouchResponseProvider;
import java.util.Objects;

/** Canonical provider-to-profile mapping used by compatibility views. */
public final class TouchResponseProfileMapper {

    private TouchResponseProfileMapper() {
    }

    public static TouchResponseProfile fromDecodedProvider(
            TouchResponseProvider provider,
            TouchCategoryDecodeMode decodeMode,
            boolean multiRegionSource) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(decodeMode, "decodeMode");
        int shieldFlags = provider.getShieldReactionFlags();
        return TouchResponseProfile.decoded(decodeMode, provider.requiresContinuousTouchCallbacks(),
                provider.requiresRenderFlagForTouch(), multiRegionSource, shieldFlags,
                provider.enablesPostSpecialTouchAirborneSideVelocityPreservation());
    }

    static TouchCategoryDecodeMode decodeMode(boolean sonic1, boolean sonic2, boolean s3k) {
        if ((sonic1 ? 1 : 0) + (sonic2 ? 1 : 0) + (s3k ? 1 : 0) > 1) {
            throw new IllegalArgumentException(
                    "Touch special-property decode mode must be Sonic 1, Sonic 2, or S3K");
        }
        if (sonic1) {
            return TouchCategoryDecodeMode.S1_SPECIAL_PROPERTY;
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
