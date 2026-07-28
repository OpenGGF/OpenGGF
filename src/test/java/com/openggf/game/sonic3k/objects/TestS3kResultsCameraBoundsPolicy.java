package com.openggf.game.sonic3k.objects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kResultsCameraBoundsPolicy {

    @Test
    void inLevelActOneTitleHandoffsKeepTheirLiveCameraBounds() {
        assertFalse(S3kResultsScreenObjectInstance.shouldRestoreLevelCameraBoundsOnExit(0x00, 0));
        assertFalse(S3kResultsScreenObjectInstance.shouldRestoreLevelCameraBoundsOnExit(0x01, 0));
        assertFalse(S3kResultsScreenObjectInstance.shouldRestoreLevelCameraBoundsOnExit(0x02, 0));
    }

    @Test
    void ordinaryResultsExitStillRestoresLevelCameraBounds() {
        assertTrue(S3kResultsScreenObjectInstance.shouldRestoreLevelCameraBoundsOnExit(0x03, 0));
        assertTrue(S3kResultsScreenObjectInstance.shouldRestoreLevelCameraBoundsOnExit(0x05, 1));
    }

    @Test
    void aResultsOwnerCarriedAcrossASeamlessReloadKeepsOffsetCameraBounds() {
        S3kResultsScreenObjectInstance results = new S3kResultsScreenObjectInstance(true);

        results.onCarriedAcrossSeamlessTransition(-0x4200, 0);

        assertFalse(results.shouldRestoreCameraBoundsOnExit(0x07, 0),
                "Load_Level retained this owner after the background event offset the live camera bounds");
    }

    @Test
    void preloadedNextActKeepsCameraOwnedByInLevelTitleHandoff() {
        assertTrue(S3kResultsScreenObjectInstance.isPreloadedNextActHandoff(0, 1));
        assertFalse(S3kResultsScreenObjectInstance.isPreloadedNextActHandoff(0, 0));
        assertFalse(S3kResultsScreenObjectInstance.isPreloadedNextActHandoff(1, 1));
    }
}
