package com.openggf.mods.code;

import com.openggf.game.ModApi;
import com.openggf.game.ModKeySyntax;
import com.openggf.game.modzone.ModZoneRuntimeFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One creator-declared tagged zone with ordered authored acts and an optional typed runtime. */
@ModApi
public record ModZoneContribution(String localKey, BakedLevelRef level,
        String insertAfter, ZoneEventFactory eventFactory, boolean gameStart,
        List<BakedLevelRef> additionalActs, ModZoneRuntimeFactory runtimeFactory) {
    public ModZoneContribution {
        localKey = ModKeySyntax.requireLocalName(localKey);
        Objects.requireNonNull(level, "level");
        if (insertAfter != null) insertAfter = requireAnchor(insertAfter);
        additionalActs = List.copyOf(additionalActs);
        if (additionalActs.size() >= com.openggf.io.ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES)
            throw new IllegalArgumentException("Too many authored acts");
    }
    /** A single authored act; use {@link #multiAct} for an ordered campaign. */
    public static ModZoneContribution singleAct(String localKey, BakedLevelRef level,
            String insertAfter, ZoneEventFactory eventFactory, boolean gameStart) {
        return new ModZoneContribution(localKey, level, insertAfter, eventFactory,
                gameStart, List.of(), null);
    }
    public static ModZoneContribution multiAct(String localKey, List<BakedLevelRef> acts,
            String insertAfter, ZoneEventFactory events, boolean gameStart) {
        List<BakedLevelRef> ordered = List.copyOf(acts);
        if (ordered.isEmpty()) throw new IllegalArgumentException("A zone needs at least one act");
        return new ModZoneContribution(localKey, ordered.getFirst(), insertAfter, events,
                gameStart, ordered.subList(1, ordered.size()), null);
    }
    public List<BakedLevelRef> acts() {
        ArrayList<BakedLevelRef> acts = new ArrayList<>(additionalActs.size() + 1);
        acts.add(level);
        acts.addAll(additionalActs);
        return List.copyOf(acts);
    }
    public ModZoneContribution withRuntime(ModZoneRuntimeFactory factory) {
        return new ModZoneContribution(localKey, level, insertAfter, eventFactory,
                gameStart, additionalActs, Objects.requireNonNull(factory, "factory"));
    }
    public Optional<ZoneEventFactory> optionalEventFactory() { return Optional.ofNullable(eventFactory); }
    ModZoneContribution withDefaultAnchor(String fallback) {
        return insertAfter == null ? new ModZoneContribution(localKey, level,
                Objects.requireNonNull(fallback, "default insertAfter"), eventFactory,
                gameStart, additionalActs, runtimeFactory) : this;
    }
    private static String requireAnchor(String value) {
        if (!value.matches("[a-z0-9][a-z0-9-]{0,63}"))
            throw new IllegalArgumentException("Invalid stock progression anchor: " + value);
        return value;
    }
}
