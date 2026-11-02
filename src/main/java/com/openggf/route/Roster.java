package com.openggf.route;

import java.util.Objects;

/** The bound P1 character and optional CPU/P2 sidekick, as the capture host names them. */
public record Roster(String main, String sidekick) {
    public Roster {
        Objects.requireNonNull(main, "main");
        if (main.isBlank()) throw new IllegalArgumentException("roster needs a main character");
        sidekick = sidekick == null ? "" : sidekick;
    }

    public boolean solo() {
        return sidekick.isEmpty();
    }
}
