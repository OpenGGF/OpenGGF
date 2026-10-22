package com.openggf.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSpriteTileReuseProbe {
    @Test void currentPreparationsPassAllSevenEqualityAndOwnershipControlsWithoutTiming() {
        assertDoesNotThrow(() -> SpriteTileReuseProbe.main(new String[]{"--verify-only"}));
    }

    @Test void absentRejectedHintCannotBeReportedAsATimingComparison() {
        var failure = assertThrows(IllegalStateException.class,
                () -> SpriteTileReuseProbe.main(new String[]{"1", "4096", "8192", "6"}));
        assertTrue(failure.getMessage().contains("Rejected reuse hint is absent"));
    }
}
