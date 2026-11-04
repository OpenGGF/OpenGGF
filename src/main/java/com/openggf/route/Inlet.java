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

    /**
     * A Stageplay shot entry: the scene (by its canonical document hash) replayed from frame zero
     * to {@code startFrame}. Only the scene host opens it; it has no native boot witness.
     */
    record Scene(String sceneSha256, int startFrame) implements Inlet {
        public Scene {
            Objects.requireNonNull(sceneSha256, "sceneSha256");
            if (!sceneSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("not a scene SHA-256");
            if (startFrame < 0) throw new IllegalArgumentException("negative shot entry");
        }
    }
}
