package com.openggf.game.modzone;

import com.openggf.game.ModApi;

import java.util.Objects;

/** Neutral host metadata carried by a strict additive-zone definition. */
@ModApi
public record ModZoneHostMetadata(ModObjectZoneSet objectZoneSet) {
    public ModZoneHostMetadata {
        Objects.requireNonNull(objectZoneSet, "objectZoneSet");
    }
}
