package com.openggf.route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An immutable, bounds-checked sequence of {@link PadRow}s. Row {@code i} is the {@code i}-th
 * controller poll of the route's launch, which is the {@code i}-th engine step. There is no
 * clamping: reading past the end is an error, never a silently repeated last row.
 */
public final class InputTape {
    /** Upper bound on one tape; a full S3K complete run is under 500,000 rows. */
    public static final int MAX_ROWS = 2_000_000;

    private final short[] rows;
    private final ContentHash hash;

    private InputTape(short[] rows) {
        if (rows.length > MAX_ROWS) throw new IllegalArgumentException("tape exceeds " + MAX_ROWS + " rows");
        this.rows = rows;
        byte[] bytes = new byte[rows.length * 2];
        for (int i = 0; i < rows.length; i++) {
            bytes[2 * i] = (byte) rows[i];
            bytes[2 * i + 1] = (byte) (rows[i] >>> 8);
        }
        this.hash = ContentHash.of(bytes);
    }

    public static InputTape of(List<PadRow> rows) {
        short[] packed = new short[rows.size()];
        for (int i = 0; i < packed.length; i++) packed[i] = (short) rows.get(i).packed();
        return new InputTape(packed);
    }

    public static InputTape empty() {
        return new InputTape(new short[0]);
    }

    public int size() {
        return rows.length;
    }

    public PadRow row(int index) {
        if (index < 0 || index >= rows.length) {
            throw new IndexOutOfBoundsException("row " + index + " outside tape of " + rows.length);
        }
        return PadRow.unpack(rows[index] & 0xFFFF);
    }

    /** Content identity of the rows (two bytes per row, P1 then P2). */
    public ContentHash hash() {
        return hash;
    }

    public InputTape slice(int from, int to) {
        if (from < 0 || to > rows.length || from > to) {
            throw new IndexOutOfBoundsException("slice " + from + ".." + to + " outside tape of " + rows.length);
        }
        return new InputTape(Arrays.copyOfRange(rows, from, to));
    }

    public InputTape append(InputTape other) {
        short[] joined = Arrays.copyOf(rows, rows.length + other.rows.length);
        System.arraycopy(other.rows, 0, joined, rows.length, other.rows.length);
        return new InputTape(joined);
    }

    public List<PadRow> rows() {
        var list = new ArrayList<PadRow>(rows.length);
        for (short row : rows) list.add(PadRow.unpack(row & 0xFFFF));
        return List.copyOf(list);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof InputTape tape && Arrays.equals(rows, tape.rows);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(rows);
    }

    @Override
    public String toString() {
        return "InputTape[" + rows.length + " rows, " + hash.shortForm() + "]";
    }

    /** Appends rows in order; used by the journal and codecs. */
    public static final class Builder {
        private short[] rows = new short[256];
        private int size;

        public Builder add(PadRow row) {
            if (size == rows.length) rows = Arrays.copyOf(rows, rows.length * 2);
            rows[size++] = (short) row.packed();
            return this;
        }

        public Builder addAll(InputTape tape) {
            for (int i = 0; i < tape.size(); i++) add(tape.row(i));
            return this;
        }

        public int size() {
            return size;
        }

        public InputTape build() {
            return new InputTape(Arrays.copyOf(rows, size));
        }
    }
}
