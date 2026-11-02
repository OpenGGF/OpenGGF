package com.openggf.game.sonic3k;

import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.TopSolidLandingWindowProvider;

/**
 * Native S3K SolidObjectTop-family contract, selected by the object's ROM helper.
 *
 * <p>SolidObjectTop_1P (sonic3k.asm:41838-41867) uses the exact d1 horizontal
 * window. New contacts use d3 as the sole vertical radius, then loc_1E45A
 * (sonic3k.asm:42038-42063) compares surface minus (playerY + radius + 4).
 * Its unsigned cmpi.w #-$10 / blo accepts only [-16,-1], including no zero.
 * The upright write is playerY += d0 + 3 before RideObject_SetRide. The flat
 * reverse entry loc_1E4D6 (sonic3k.asm:42093-42112) has its own placement.
 * This contract selects detection; existing owner-specific placement modes
 * remain in force.
 * Custom callers such as loc_3EA1E (sonic3k.asm:83021-83037), and equivalent
 * inline gates such as loc_2AC82 (sonic3k.asm:59198-59229), share this window.
 *
 * <p>This belongs to the native object helper, not the playable character's
 * game/donor rules. Full solids and S1/S2 helpers retain their own contracts.
 * A helper with different semantics must declare and cite that exception at
 * its owner. Sloped callers use {@link S3kSlopedTopSolidProvider} instead.
 */
public interface S3kTopSolidProvider extends SolidObjectProvider, TopSolidLandingWindowProvider {
    @Override
    default Integer getDirectTopLandingOverlapLimit() {
        // loc_1E45A (sonic3k.asm:42044-42055): overlap 1..16, exclusive limit 17.
        return 0x11;
    }

    @Override
    default boolean isTopSolidOnly() {
        return true;
    }

    @Override
    default boolean rejectsZeroDistanceTopSolidLanding() {
        return true;
    }

    @Override
    default boolean usesGroundHalfHeightForTopSolidContact() {
        // SolidObjectTop uses d3 for both new detection and continued riding.
        return true;
    }

    @Override
    default boolean usesStickyContactBuffer() {
        return false;
    }

    @Override
    default boolean usesCollisionHalfWidthForTopLanding() {
        return true;
    }
}
