package com.openggf.trace.timing;

import java.util.Arrays;

/**
 * The recorded rows on which a VBlank produced no V-INT entry, distinct from absence of the stream.
 *
 * <p>{@link #notObserved()} is a recording made without V-INT entry observation: it admits nothing and says
 * nothing about lost V-INTs. An observed schedule with no rows is a recording whose every VBlank was serviced.
 * The schedule holds row numbers only: no counter, handler, decoder or gameplay value.
 */
public final class VIntServiceTimingSchedule {
    private static final VIntServiceTimingSchedule NOT_OBSERVED = new VIntServiceTimingSchedule(false, new int[0]);
    private final boolean observed;
    private final int[] unservicedRows;

    private VIntServiceTimingSchedule(boolean observed, int[] unservicedRows) {
        this.observed = observed;
        this.unservicedRows = unservicedRows;
    }

    public static VIntServiceTimingSchedule notObserved() {
        return NOT_OBSERVED;
    }

    /** {@code unservicedRows}: strictly increasing, non-negative trace rows. */
    public static VIntServiceTimingSchedule observed(int[] unservicedRows) {
        int[] rows = unservicedRows.clone();
        for (int i = 0; i < rows.length; i++) {
            if (rows[i] < 0 || (i > 0 && rows[i] <= rows[i - 1])) {
                throw new IllegalArgumentException("vint_not_serviced rows must be non-negative and strictly increasing");
            }
        }
        return new VIntServiceTimingSchedule(true, rows);
    }

    /** Whether the recorder observed V-INT entry (the stream is present, possibly empty). */
    public boolean observed() {
        return observed;
    }

    public int[] unservicedRows() {
        return unservicedRows.clone();
    }

    @Override
    public String toString() {
        return observed ? "observed " + Arrays.toString(unservicedRows) : "not observed";
    }
}
