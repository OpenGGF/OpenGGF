package com.openggf.game.internal;

import com.openggf.game.GameServices;

/** Internal readiness bridge: native spatial entry owners settle before a director holds the world. */
public final class DirectedEntryPresentation {
    private DirectedEntryPresentation() { }

    /** A zone may need native event dispatches after an authored camera/actor entry. */
    public interface Owner {
        boolean readyForDirectedHold();
    }

    /** This query performs no event, clock, palette, or scroll mutation. */
    public static boolean readyForHold() {
        var registry = GameServices.zoneRuntimeRegistryOrNull();
        var runtime = registry == null ? null : registry.current();
        return !(runtime instanceof Owner owner) || owner.readyForDirectedHold();
    }
}
