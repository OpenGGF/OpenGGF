package com.openggf.game.dataselect;

import com.openggf.game.sonic3k.dataselect.S3kDataSelectManager;

/**
 * Centralizes cross-game data-select presentation donation.
 */
public final class CrossGameDataSelectPresentations {
    private CrossGameDataSelectPresentations() {
    }

    public static DataSelectPresentationProvider s3kDonor(DataSelectHostProfile hostProfile) {
        return new DataSelectPresentationProvider(S3kDataSelectManager::new,
                new DataSelectSessionController(hostProfile));
    }
}
