package com.openggf.route;

import java.util.Objects;

/** How the world a route's row 0 plays into is reached. */
public sealed interface Inlet {
    /** A direct boot into a level, as the capture host performs it; no native power-on prefix. */
    record ColdLevel(LevelId level, boolean showTitleCard) implements Inlet {
        public ColdLevel {
            Objects.requireNonNull(level, "level");
        }
    }
}
