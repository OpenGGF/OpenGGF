package com.openggf.route;

import java.util.Objects;

/**
 * Explicit clocks and seeds at row 0. {@code vIntStart} is always declared and always written,
 * so a launch never inherits {@code V_int_run_count} from an earlier session in the same JVM.
 */
public record InitialState(int vIntStart, Seeds seeds) {
    public InitialState {
        Objects.requireNonNull(seeds, "seeds");
    }
}
