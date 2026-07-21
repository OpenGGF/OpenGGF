package com.openggf.game.sonic2;

import com.openggf.game.sonic2.events.Sonic2WFZEvents;

/** Engine-only cross-package access to zone-event implementation state. */
public final class Sonic2LevelEventInternalAccess {
    private Sonic2LevelEventInternalAccess() {
    }

    public static Sonic2WFZEvents wfzEvents(Sonic2LevelEventManager manager) {
        return manager.getWfzEvents();
    }
}
