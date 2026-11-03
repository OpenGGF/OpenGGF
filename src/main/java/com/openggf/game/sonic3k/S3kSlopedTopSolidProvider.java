package com.openggf.game.sonic3k;

import com.openggf.level.objects.SlopedSolidProvider;

/**
 * Raw-surface form of the native S3K top-solid helper.
 *
 * <p>SolidObjCheckSloped2 / SolidObjCheckSloped (sonic3k.asm:42116-42165)
 * sample objectY - slopeByte and branch to loc_1E45A. The custom curled-vine
 * entry loc_3EA1E (sonic3k.asm:83021-83037) supplies segmentY - 8 instead.
 * These are already surface positions: do not classify them as a full solid
 * using an object's body height or accept a deep side/bottom overlap.
 */
public interface S3kSlopedTopSolidProvider extends S3kTopSolidProvider, SlopedSolidProvider {
    @Override
    default int getSlopeBaseline() {
        // Native samples already describe objectY - surfaceY.
        return 0;
    }

    @Override
    default Integer getDirectTopLandingOverlapLimit() {
        return S3kTopSolidProvider.super.getDirectTopLandingOverlapLimit();
    }
}
