package com.openggf.editor.route;

import com.openggf.route.InputTape;
import com.openggf.route.PhysicalButton;
import com.openggf.route.PlayerSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * Frontend-neutral layout of a two-lane route tape window. Each lane shows its eight physical
 * buttons as tracks of held runs; markers place replay, native and repair facts on the same row
 * axis. Both editors paint from this view; painting never changes the tape.
 */
public final class RouteTapeTimeline {
    /** One button track; P1 tracks come first, then P2, in pad order. */
    public record Track(PlayerSlot slot, PhysicalButton button) {
        public String label() {
            return String.valueOf(button.letter());
        }
    }

    private static final List<PhysicalButton> PAD_ORDER = List.of(PhysicalButton.UP, PhysicalButton.DOWN,
            PhysicalButton.LEFT, PhysicalButton.RIGHT, PhysicalButton.A, PhysicalButton.B, PhysicalButton.C,
            PhysicalButton.START);
    public static final List<Track> TRACKS;
    static {
        var tracks = new ArrayList<Track>();
        for (PlayerSlot slot : PlayerSlot.values()) for (PhysicalButton button : PAD_ORDER) tracks.add(new Track(slot, button));
        TRACKS = List.copyOf(tracks);
    }
    public static final int TRACKS_PER_LANE = PAD_ORDER.size();

    public enum MarkerKind {
        /** The first row an engine replay could not complete. */
        DIVERGED,
        /** A native lag VBlank observed before this row (display only). */
        NATIVE_LAG,
        /** The first row a native run diverged at (display only). */
        NATIVE_DIVERGED,
        /** Output rows a repair searched. */
        SEARCHED,
        /** Output rows a repair kept verbatim from the original. */
        KEPT,
        /** Output rows a repair took verbatim from its reference rather than the original. */
        REFERENCE
    }

    /** Rows {@code [from, to)}; a single-row fact has {@code to == from + 1}. */
    public record Marker(MarkerKind kind, int from, int to) {
        public Marker {
            if (from < 0 || to <= from) throw new IllegalArgumentException("empty marker " + from + ".." + to);
        }
    }

    /** A held run on {@code track}, clipped to the window; flags say whether it continues outside. */
    public record Run(int track, int from, int to, boolean continuesBefore, boolean continuesAfter) { }

    /**
     * @param first     first visible row
     * @param rows      visible row count
     * @param selection {@code [from, to)} or {@code from == to} when nothing is selected
     * @param ticks     ruler rows at a whole step inside the window
     */
    public record View(int first, int rows, int tapeRows, int cursor, int track, int selectionFrom, int selectionTo,
                       List<Run> runs, List<Marker> markers, List<Integer> ticks) {
        public float x(int row, float left, float width) {
            return left + (row - first) * width / rows;
        }

        public float rowWidth(float width) {
            return width / rows;
        }

        /** Row under a horizontal position, clamped to the tape. */
        public int rowAt(float x, float left, float width) {
            int row = first + (int) Math.floor((x - left) * rows / width);
            return Math.max(0, Math.min(Math.max(0, tapeRows - 1), row));
        }
    }

    private RouteTapeTimeline() {
    }

    public static View layout(InputTape tape, int first, int rows, int cursor, int track,
                              int selectionFrom, int selectionTo, List<Marker> markers) {
        if (rows < 1) throw new IllegalArgumentException("a timeline shows at least one row");
        int end = Math.min(tape.size(), first + rows);
        var runs = new ArrayList<Run>();
        for (int t = 0; t < TRACKS.size(); t++) {
            Track lane = TRACKS.get(t);
            int start = -1;
            for (int row = first; row < end; row++) {
                boolean held = tape.row(row).held(lane.slot(), lane.button());
                if (held && start < 0) start = row;
                if (!held && start >= 0) {
                    runs.add(run(tape, t, lane, start, row));
                    start = -1;
                }
            }
            if (start >= 0) runs.add(run(tape, t, lane, start, end));
        }
        var visible = new ArrayList<Marker>();
        for (Marker marker : markers) if (marker.to() > first && marker.from() < first + rows) visible.add(marker);
        return new View(first, rows, tape.size(), cursor, track, selectionFrom, selectionTo,
                List.copyOf(runs), List.copyOf(visible), ticks(first, rows));
    }

    /** First visible row keeping {@code cursor} inside the window, with a quarter-window margin. */
    public static int follow(int first, int rows, int cursor, int size) {
        int margin = Math.max(1, rows / 4);
        int next = first;
        if (cursor < first + margin) next = cursor - margin;
        if (cursor >= first + rows - margin) next = cursor - rows + margin + 1;
        return Math.max(0, Math.min(Math.max(0, size - rows + margin), next));
    }

    private static Run run(InputTape tape, int track, Track lane, int from, int to) {
        boolean before = from > 0 && tape.row(from - 1).held(lane.slot(), lane.button());
        boolean after = to < tape.size() && tape.row(to).held(lane.slot(), lane.button());
        return new Run(track, from, to, before, after);
    }

    private static List<Integer> ticks(int first, int rows) {
        int step = 1;
        for (int candidate : new int[] {1, 5, 10, 30, 60, 120, 300, 600, 1200, 3000, 6000, 12000, 30000, 60000}) {
            step = candidate;
            if (rows / candidate <= 8) break;
        }
        var ticks = new ArrayList<Integer>();
        for (int row = (first + step - 1) / step * step; row < first + rows; row += step) ticks.add(row);
        return List.copyOf(ticks);
    }
}
