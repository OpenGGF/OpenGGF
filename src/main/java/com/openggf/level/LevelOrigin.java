package com.openggf.level;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/** Engine-internal helper for resolving the concrete source beneath mutable snapshots. */
public final class LevelOrigin {
    private LevelOrigin() {}

    /** Returns the first non-mutable source, unwrapping nested snapshots defensively. */
    public static Level original(Level level) {
        Objects.requireNonNull(level, "level");
        Set<Level> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Level current = level;
        while (current instanceof MutableLevel mutable) {
            if (!visited.add(current)) {
                throw new IllegalStateException("Mutable level source cycle");
            }
            current = Objects.requireNonNull(mutable.sourceLevelForEngine(),
                    "mutable level source");
        }
        return current;
    }
}
