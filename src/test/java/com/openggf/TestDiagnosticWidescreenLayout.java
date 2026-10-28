package com.openggf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

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

    @Test
    void escapeToastNeverCrossesASessionPlateWhileSlidingInOrOut() {
        var text = com.openggf.graphics.PixelFontTextRenderer.compactMenu();
        var top = com.openggf.game.presentation.TransportHud.Placement.TOP_RIGHT;
        int sideways = 0;
        for (int width : new int[] {320, 400, 528}) {
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
                for (var plates : java.util.List.of(java.util.List.<com.openggf.game.presentation.TransportHud.Bounds>of(),
                        java.util.List.of(pause), java.util.List.of(video, pauseUnder), java.util.List.of(stopped, pauseUnder))) {
                    int[] rest = Engine.escapeToastOrigin(width, toastWidth, plates, 1f);
                    assertEquals(Engine.escapeToastRestY(width, toastWidth, plates), rest[1]);
                    assertEquals(com.openggf.game.MenuChrome.holdToastX(width, toastWidth), rest[0]);
                    int[] hidden = Engine.escapeToastOrigin(width, toastWidth, plates, 0f);
                    assertTrue(hidden[0] >= width || hidden[1] + com.openggf.game.MenuChrome.HOLD_TOAST_H + 1 <= 0,
                            "a fully hidden toast is off screen");
                    if (hidden[1] == rest[1]) sideways++;
                    // Every presence the controller can produce, in and out, including eased steps.
                    for (int step = 0; step <= 100; step++) {
                        int[] at = Engine.escapeToastOrigin(width, toastWidth, plates, step / 100f);
                        for (var plate : plates) {
                            boolean overlaps = at[0] < plate.right() && plate.x() < at[0] + toastWidth + 1
                                    && at[1] < plate.bottom() && plate.y() < at[1] + com.openggf.game.MenuChrome.HOLD_TOAST_H + 1;
                            assertTrue(!overlaps, width + " " + message + " at presence " + step + "%: toast at "
                                    + at[0] + "," + at[1] + " crosses " + plate);
                        }
                    }
                }
            }
        }
        // The old vertical slide crossed the plates; at least one combination must take the side path.
        assertTrue(sideways > 0, "a displaced toast slides in along its resting row");
    }


    /**
     * A plate that comes or goes mid-hold: every frame of every change between plate sets keeps the
     * toast clear of the plates on screen that frame. A plate that appears under the toast moves it
     * at once; a row change whose path is clear (the pause plate going away) eases over six frames.
     */
    @Test
    void toastRowChangesNeverCrossAPlateAndEaseWhenThePathIsClear() {
        var text = com.openggf.graphics.PixelFontTextRenderer.compactMenu();
        var top = com.openggf.game.presentation.TransportHud.Placement.TOP_RIGHT;
        int eased = 0, jumped = 0;
        for (int width : new int[] {320, 400}) {
            var pause = com.openggf.game.presentation.TransportHud.bounds(text, width, Engine.userPausePlacement(false),
                    Engine.USER_PAUSE_PLATE);
            var pauseUnder = com.openggf.game.presentation.TransportHud.bounds(text, width, Engine.userPausePlacement(true),
                    Engine.USER_PAUSE_PLATE);
            var video = com.openggf.game.presentation.TransportHud.bounds(text, width, top, Engine.liveCapturePlate(null));
            List<List<com.openggf.game.presentation.TransportHud.Bounds>> sets = List.of(List.of(), List.of(pause),
                    List.of(video), List.of(video, pauseUnder));
            for (String message : List.of(EscapeToMasterTitleController.MESSAGE, EscapeToMasterTitleController.LEAVING_MESSAGE)) {
                int toastWidth = com.openggf.game.MenuChrome.holdToastWidth("Esc", message);
                int left = com.openggf.game.MenuChrome.holdToastX(width, toastWidth);
                for (var before : sets) for (var after : sets) {
                    if (before == after) continue;
                    var motion = new EscapeToastMotion();
                    int frame = 100;
                    motion.restY(Engine.escapeToastRestY(width, toastWidth, before), frame, left, width, before);
                    int target = Engine.escapeToastRestY(width, toastWidth, after);
                    List<Integer> rows = new java.util.ArrayList<>();
                    for (int f = 1; f <= EscapeToastMotion.EASE_FRAMES + 2; f++) {
                        int y = motion.restY(target, frame + f, left, width, after);
                        rows.add(y);
                        int[] origin = Engine.escapeToastOrigin(width, toastWidth, y, 1f);
                        for (var plate : after) {
                            boolean overlaps = origin[0] < plate.right() && plate.x() < origin[0] + toastWidth + 1
                                    && origin[1] < plate.bottom() && plate.y() < origin[1] + com.openggf.game.MenuChrome.HOLD_TOAST_H + 1;
                            assertFalse(overlaps, width + " " + before + " -> " + after + " frame " + f + " y " + y);
                        }
                    }
                    assertEquals(target, rows.getLast(), "settles on the new row");
                    int from = Engine.escapeToastRestY(width, toastWidth, before);
                    if (from == target) continue;
                    if (rows.getFirst() == target) jumped++;
                    else {
                        eased++;
                        assertEquals(target, (int) rows.get(EscapeToastMotion.EASE_FRAMES), "eases over six frames after the change");
                        assertTrue(rows.get(EscapeToastMotion.EASE_FRAMES - 1) != target, "not a jump: " + rows);
                        for (int f = 1; f < rows.size(); f++)
                            assertTrue(Integer.signum(target - from) * (rows.get(f) - rows.get(f - 1)) >= 0, "moves one way: " + rows);
                    }
                }
            }
        }
        assertTrue(eased > 0, "a plate going away eases the toast up");
        assertTrue(jumped > 0, "a plate arriving under the toast moves it at once");
    }
}
