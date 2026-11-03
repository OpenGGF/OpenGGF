package com.openggf.route;

import java.util.Objects;

/** A level by the game module's own zone index and zero-based act. No shared code switches on it. */
public record LevelId(String game, int zone, int act) {
    public LevelId {
        Objects.requireNonNull(game, "game");
        if (zone < 0 || act < 0) throw new IllegalArgumentException("negative level index " + zone + ":" + act);
    }
}
