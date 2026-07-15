package com.openggf.game.dataselect;

import java.util.Objects;

/** Host-neutral diagnostic emitted while resolving a tagged mod-zone save. */
public record ModZoneSaveFinding(String ownerModId, String code, String detail) {
    public ModZoneSaveFinding {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
    }
}
