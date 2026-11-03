package com.openggf.route;

import java.util.Objects;

/** One player held by one object this tick, as the owner (or the solid ride) reports it. */
public record InteractionView(InstanceKey owner, PlayerSlot participant, InteractionKind kind, int anchorX, int anchorY,
                              boolean releasing) {
    public InteractionView {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(participant, "participant");
        Objects.requireNonNull(kind, "kind");
    }
}
