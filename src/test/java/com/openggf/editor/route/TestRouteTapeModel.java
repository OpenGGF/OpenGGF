package com.openggf.editor.route;

import com.openggf.route.InputTape;
import com.openggf.route.PadRow;
import com.openggf.route.PhysicalButton;
import com.openggf.route.PlayerSlot;
import com.openggf.route.RouteFiles;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure route tape edits, timeline layout and path geometry; no engine or files. */
class TestRouteTapeModel {
    private static InputTape tape(String script) {
        return RouteFiles.tapeFromScript(script);
    }

    @Test
    void editsChangeOnlyTheNamedRowsAndPreserveEveryOtherPhysicalBit() {
        InputTape original = tape("2 R\n1 R+A+S / L\n2 - / C\n");
        InputTape pressed = RouteTapeEdits.toggle(original, 0, 3, PlayerSlot.P1, PhysicalButton.B);
        assertTrue(pressed.row(0).held(PlayerSlot.P1, PhysicalButton.B));
        assertTrue(pressed.row(2).held(PlayerSlot.P1, PhysicalButton.START), "Start survives a toggle of B");
        assertTrue(pressed.row(2).held(PlayerSlot.P2, PhysicalButton.LEFT), "P2 survives a P1 toggle");
        assertEquals(original.row(3), pressed.row(3), "Rows outside the range are untouched");
        assertEquals(original, RouteTapeEdits.toggle(pressed, 0, 3, PlayerSlot.P1, PhysicalButton.B),
                "Toggling a fully held range releases it");
        InputTape mixed = RouteTapeEdits.toggle(original, 1, 4, PlayerSlot.P1, PhysicalButton.A);
        assertTrue(mixed.row(3).held(PlayerSlot.P1, PhysicalButton.A), "A partly held range is pressed throughout");

        InputTape inserted = RouteTapeEdits.insert(original, 1, 2, PadRow.NEUTRAL);
        assertEquals(7, inserted.size());
        assertTrue(inserted.row(1).isNeutral() && inserted.row(2).isNeutral());
        assertEquals(original.row(1), inserted.row(3));
        assertEquals(original, RouteTapeEdits.delete(inserted, 1, 3));
        InputTape duplicated = RouteTapeEdits.duplicate(original, 2, 3);
        assertEquals(original.row(2), duplicated.row(3));
        assertEquals(original.row(3), duplicated.row(4));
        InputTape cleared = RouteTapeEdits.setLane(original, 2, 5, PlayerSlot.P2, 0);
        assertFalse(cleared.row(2).held(PlayerSlot.P2, PhysicalButton.LEFT));
        assertTrue(cleared.row(2).held(PlayerSlot.P1, PhysicalButton.START));
        assertThrows(IndexOutOfBoundsException.class, () -> RouteTapeEdits.delete(original, 3, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> RouteTapeEdits.toggle(original, 0, 6, PlayerSlot.P1,
                PhysicalButton.A));
    }

    @Test
    void timelineReportsClippedHeldRunsMarkersAndRulerTicks() {
        InputTape tape = tape("10 R\n5 R+A\n20 -\n5 - / C\n");
        var markers = List.of(new RouteTapeTimeline.Marker(RouteTapeTimeline.MarkerKind.DIVERGED, 12, 13),
                new RouteTapeTimeline.Marker(RouteTapeTimeline.MarkerKind.NATIVE_LAG, 100, 101));
        var view = RouteTapeTimeline.layout(tape, 5, 20, 12, 3, 12, 12, markers);
        int right = RouteTapeTimeline.TRACKS.indexOf(new RouteTapeTimeline.Track(PlayerSlot.P1, PhysicalButton.RIGHT));
        int a = RouteTapeTimeline.TRACKS.indexOf(new RouteTapeTimeline.Track(PlayerSlot.P1, PhysicalButton.A));
        var rightRun = view.runs().stream().filter(run -> run.track() == right).findFirst().orElseThrow();
        assertEquals(5, rightRun.from());
        assertEquals(15, rightRun.to());
        assertTrue(rightRun.continuesBefore(), "The held run started before the window");
        assertFalse(rightRun.continuesAfter());
        var aRun = view.runs().stream().filter(run -> run.track() == a).findFirst().orElseThrow();
        assertEquals(10, aRun.from());
        assertEquals(15, aRun.to());
        assertTrue(view.runs().stream().noneMatch(run -> RouteTapeTimeline.TRACKS.get(run.track()).slot() == PlayerSlot.P2),
                "P2's C starts after this window");
        assertEquals(List.of(markers.getFirst()), view.markers(), "Markers outside the window are not drawn");
        assertEquals(List.of(5, 10, 15, 20), view.ticks());
        assertEquals(16, RouteTapeTimeline.TRACKS.size());
        assertEquals(12, view.rowAt(view.x(12, 0, 200) + 1, 0, 200));
        assertEquals(0, RouteTapeTimeline.follow(0, 40, 5, 1000));
        assertEquals(61, RouteTapeTimeline.follow(0, 40, 90, 1000), "The cursor stays a quarter-window from the edge");
    }

    @Test
    void pathBreaksAtGapsAndEpochsAndFindsTheCursorObservation() {
        var p1 = List.of(new RouteTapeOverlay.Point(0, 0, 100, 200, false), new RouteTapeOverlay.Point(1, 0, 102, 200, false),
                new RouteTapeOverlay.Point(3, 0, 106, 198, true), new RouteTapeOverlay.Point(4, 1, 20, 40, false),
                new RouteTapeOverlay.Point(5, 1, 22, 40, false));
        var overlay = new RouteTapeOverlay(p1, List.of(), 1, -1);
        var lines = new ArrayList<float[]>();
        overlay.paint((x1, y1, x2, y2, rgb, width) -> lines.add(new float[] {x1, y1, x2, y2, rgb}),
                new RouteTapeOverlay.Camera(10, 20, 100, 200, 2), 4);
        long pathSegments = lines.stream().filter(line -> line[4] == RouteTapeOverlay.GROUND && line[1] == line[3]
                && line[0] != line[2] && Math.abs(line[0] - line[2]) == 4).count();
        assertEquals(2, pathSegments, "Rows 0-1 and 4-5 join; the row gap and the epoch change break the line");
        assertEquals(new RouteTapeOverlay.Point(1, 0, 102, 200, false), overlay.at(2));
        assertNull(overlay.at(-1));
        var sparse = new RouteTapeOverlay(List.of(new RouteTapeOverlay.Point(0, 0, 0, 0, false),
                new RouteTapeOverlay.Point(4, 0, 20, 0, false)), List.of(), 4, -1);
        var sparseLines = new ArrayList<float[]>();
        sparse.paint((x1, y1, x2, y2, rgb, width) -> sparseLines.add(new float[] {x1, x2, rgb}),
                new RouteTapeOverlay.Camera(0, 0, 0, 0, 1), -1);
        assertTrue(sparseLines.stream().anyMatch(line -> line[2] == RouteTapeOverlay.GROUND && line[1] - line[0] == 20),
                "Observations one stride apart are joined");
    }
}
