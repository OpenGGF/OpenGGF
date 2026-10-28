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

    @Test
    void escapeToastRestsClearOfEverySimultaneousTopRightSessionPlate() {
        var text = com.openggf.graphics.PixelFontTextRenderer.compactMenu();
        var top = com.openggf.game.presentation.TransportHud.Placement.TOP_RIGHT;
        int touched = 0;
        for (int width : new int[] {320, 400}) {
            var pause = com.openggf.game.presentation.TransportHud.bounds(text, width, Engine.userPausePlacement(false),
                    Engine.USER_PAUSE_PLATE);
            var pauseUnder = com.openggf.game.presentation.TransportHud.bounds(text, width, Engine.userPausePlacement(true),
                    Engine.USER_PAUSE_PLATE);
            var video = com.openggf.game.presentation.TransportHud.bounds(text, width, top, Engine.liveCapturePlate(null));
            var stopped = com.openggf.game.presentation.TransportHud.bounds(text, width, top,
                    Engine.liveCapturePlate(com.openggf.capture.LiveCaptureController.Interruption.WINDOW_RESIZED));
            for (String message : java.util.List.of(EscapeToMasterTitleController.MESSAGE,
                    EscapeToMasterTitleController.LEAVING_MESSAGE)) {
                int toastWidth = com.openggf.game.MenuChrome.holdToastWidth("Esc", message);
                int x = com.openggf.game.MenuChrome.holdToastX(width, toastWidth);
                assertEquals(6, Engine.escapeToastRestY(width, toastWidth, java.util.List.of()), "alone it keeps the top row");
                for (var plates : java.util.List.of(java.util.List.of(pause), java.util.List.of(video, pauseUnder),
                        java.util.List.of(stopped, pauseUnder))) {
                    int y = Engine.escapeToastRestY(width, toastWidth, plates);
                    if (y != 6) touched++;
                    for (var plate : plates) {
                        boolean overlaps = x < plate.right() && plate.x() < x + toastWidth + 1
                                && y < plate.bottom() && plate.y() < y + com.openggf.game.MenuChrome.HOLD_TOAST_H + 1;
                        assertTrue(!overlaps, width + " " + message + ": toast at " + x + "," + y + " overlaps " + plate);
                    }
                    assertTrue(x > Engine.centeredDiagnosticOrigin(width) + com.openggf.game.MenuChrome.GAMEPLAY_HUD_RIGHT,
                            "the toast stays right of the score/time/rings column");
                    assertTrue(y + com.openggf.game.MenuChrome.HOLD_TOAST_H < 64, "the toast stays in the top band");
                }
            }
        }
        // Breaking the comparison on purpose: the 320 frame really does put the toast under the pause plate.
        assertTrue(touched > 0, "at least one simultaneous state needed to move the toast");
        assertEquals(Engine.escapeToastY(1f, 6), Engine.escapeToastY(1f));
        assertTrue(Engine.escapeToastY(0f, 26) + com.openggf.game.MenuChrome.HOLD_TOAST_H < 0);
    }
}
