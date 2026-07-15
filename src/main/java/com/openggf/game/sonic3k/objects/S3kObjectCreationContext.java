package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.S3kZoneSet;

import java.util.Objects;
import java.util.OptionalInt;

/** Zone-set identity and optional real ROM zone identity used by S3K object factories. */
record S3kObjectCreationContext(S3kZoneSet zoneSet, OptionalInt stockRomZoneId) {
    S3kObjectCreationContext {
        Objects.requireNonNull(zoneSet, "zoneSet");
        Objects.requireNonNull(stockRomZoneId, "stockRomZoneId");
    }

    static S3kObjectCreationContext custom(S3kZoneSet zoneSet) {
        return new S3kObjectCreationContext(zoneSet, OptionalInt.empty());
    }

    static S3kObjectCreationContext stock(S3kZoneSet zoneSet, int romZoneId) {
        if (romZoneId < 0) {
            throw new IllegalArgumentException("Stock ROM zone id must not be negative");
        }
        return new S3kObjectCreationContext(zoneSet, OptionalInt.of(romZoneId));
    }
}
