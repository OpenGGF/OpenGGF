package com.openggf.mods.code;

import com.openggf.game.ModApi;
import com.openggf.game.ModKeySyntax;

import java.util.Objects;
import java.util.Optional;

/** One creator-declared, owner-scoped zone registration. */
@ModApi
public record ModZoneContribution(String localKey, BakedLevelRef level,
                                  String insertAfter, ZoneEventFactory eventFactory,
                                  boolean gameStart) {
    public ModZoneContribution {
        localKey = ModKeySyntax.requireLocalName(localKey);
        Objects.requireNonNull(level, "level");
        if (insertAfter != null) insertAfter = requireAnchor(insertAfter);
    }

    public Optional<ZoneEventFactory> optionalEventFactory() {
        return Optional.ofNullable(eventFactory);
    }

    ModZoneContribution withDefaultAnchor(String fallback) {
        return insertAfter == null
                ? new ModZoneContribution(localKey, level,
                Objects.requireNonNull(fallback, "default insertAfter"), eventFactory, gameStart)
                : this;
    }

    private static String requireAnchor(String value) {
        if (!value.matches("[a-z0-9][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("Invalid stock progression anchor: " + value);
        }
        return value;
    }
}
