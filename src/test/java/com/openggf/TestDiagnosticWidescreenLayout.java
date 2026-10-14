package com.openggf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDiagnosticWidescreenLayout {

    @Test
    void centeredDiagnosticOriginUsesTheNativeCompositionAtLiveWidths() {
        assertEquals(0, Engine.centeredDiagnosticOrigin(320));
        assertEquals(16, Engine.centeredDiagnosticOrigin(352));
        assertEquals(40, Engine.centeredDiagnosticOrigin(400));
        assertEquals(4, Engine.centeredDiagnosticX(320, 4));
        assertEquals(20, Engine.centeredDiagnosticX(352, 4));
        assertEquals(44, Engine.centeredDiagnosticX(400, 4));
    }

    @Test
    void escapeToastSlidesFromAboveTheScreenToItsRestingRow() {
        assertTrue(Engine.escapeToastY(0f) + com.openggf.game.MenuChrome.HOLD_TOAST_H < 0,
                "a hidden toast sits entirely above the top edge");
        assertEquals(6, Engine.escapeToastY(1f));
    }
}
