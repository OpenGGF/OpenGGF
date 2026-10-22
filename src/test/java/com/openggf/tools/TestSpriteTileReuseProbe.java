package com.openggf.tools;

import com.openggf.game.session.EngineServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSpriteTileReuseProbe {
    @Test void currentPreparationsPassAllSevenEqualityAndOwnershipControlsWithoutTiming() {
        var services = EngineServices.current();
        var graphics = services.graphics();
        boolean headless = graphics.isHeadlessMode();
        assertDoesNotThrow(() -> SpriteTileReuseProbe.main(new String[]{"--verify-only"}));
        assertSame(services, EngineServices.current(), "verification must retain the caller's service owner");
        assertSame(graphics, services.graphics());
        assertEquals(headless, graphics.isHeadlessMode(), "verification must use its own graphics host");
    }

    @Test void absentRejectedHintCannotBeReportedAsATimingComparison() {
        var failure = assertThrows(IllegalStateException.class,
                () -> SpriteTileReuseProbe.main(new String[]{"1", "4096", "8192", "6"}));
        assertTrue(failure.getMessage().contains("Rejected reuse hint is absent"));
    }
}
