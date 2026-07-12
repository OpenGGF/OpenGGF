package com.openggf.game.session;

import java.util.Objects;

/** Internal cross-package bridge to pattern-window state owned by a WorldSession. */
public final class PatternWindowSessionState {
    private PatternWindowSessionState() { }

    public static PatternWindowState of(WorldSession session) {
        return Objects.requireNonNull(session, "session").patternWindowState;
    }

    public static void install(WorldSession session, PatternWindowState state) {
        Objects.requireNonNull(session, "session").patternWindowState =
                PatternWindowState.copyOf(state);
    }
}
