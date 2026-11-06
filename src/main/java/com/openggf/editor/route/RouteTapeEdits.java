package com.openggf.editor.route;

import com.openggf.route.InputTape;
import com.openggf.route.PadRow;
import com.openggf.route.PhysicalButton;
import com.openggf.route.PlayerSlot;

import java.util.function.UnaryOperator;

/**
 * Row edits on an immutable route tape. Every edit returns a new tape and changes only the rows
 * it names; it never normalises opposite directions, Start or neutral rows elsewhere.
 */
public final class RouteTapeEdits {
    private RouteTapeEdits() {
    }

    /** Releases {@code button} on {@code slot} across {@code [from, to)} when every row holds it; presses it otherwise. */
    public static InputTape toggle(InputTape tape, int from, int to, PlayerSlot slot, PhysicalButton button) {
        requireRange(tape, from, to);
        boolean allHeld = true;
        for (int i = from; i < to && allHeld; i++) allHeld = tape.row(i).held(slot, button);
        boolean press = !allHeld;
        return map(tape, from, to, row -> {
            int lane = row.lane(slot);
            return row.with(slot, press ? lane | button.bit() : lane & ~button.bit());
        });
    }

    /** Sets {@code slot}'s whole lane to {@code mask} across {@code [from, to)}. */
    public static InputTape setLane(InputTape tape, int from, int to, PlayerSlot slot, int mask) {
        requireRange(tape, from, to);
        return map(tape, from, to, row -> row.with(slot, mask));
    }

    /** Inserts {@code count} copies of {@code row} before index {@code at}. */
    public static InputTape insert(InputTape tape, int at, int count, PadRow row) {
        if (at < 0 || at > tape.size()) throw new IndexOutOfBoundsException("insert at " + at + " of " + tape.size());
        if (count < 1) throw new IllegalArgumentException("insert needs at least one row: " + count);
        var builder = new InputTape.Builder();
        copy(tape, 0, at, builder);
        for (int i = 0; i < count; i++) builder.add(row);
        copy(tape, at, tape.size(), builder);
        return builder.build();
    }

    /** Removes rows {@code [from, to)}. */
    public static InputTape delete(InputTape tape, int from, int to) {
        requireRange(tape, from, to);
        var builder = new InputTape.Builder();
        copy(tape, 0, from, builder);
        copy(tape, to, tape.size(), builder);
        return builder.build();
    }

    /** Inserts a copy of rows {@code [from, to)} directly after them. */
    public static InputTape duplicate(InputTape tape, int from, int to) {
        requireRange(tape, from, to);
        var builder = new InputTape.Builder();
        copy(tape, 0, to, builder);
        copy(tape, from, to, builder);
        copy(tape, to, tape.size(), builder);
        return builder.build();
    }

    private static InputTape map(InputTape tape, int from, int to, UnaryOperator<PadRow> edit) {
        var builder = new InputTape.Builder();
        copy(tape, 0, from, builder);
        for (int i = from; i < to; i++) builder.add(edit.apply(tape.row(i)));
        copy(tape, to, tape.size(), builder);
        return builder.build();
    }

    private static void copy(InputTape tape, int from, int to, InputTape.Builder builder) {
        for (int i = from; i < to; i++) builder.add(tape.row(i));
    }

    private static void requireRange(InputTape tape, int from, int to) {
        if (from < 0 || to > tape.size() || from >= to) {
            throw new IndexOutOfBoundsException("rows " + from + ".." + to + " outside tape of " + tape.size());
        }
    }
}
