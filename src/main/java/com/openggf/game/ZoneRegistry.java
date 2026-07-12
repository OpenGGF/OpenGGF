package com.openggf.game;

import com.openggf.level.LevelDescriptor;

import java.util.List;
import java.util.OptionalInt;

/**
 * Interface for game-specific zone/level metadata.
 * Each game module provides its own implementation with zone names,
 * act counts, start positions, and other level-specific data.
 *
 * <p>The zone registry is queried by LevelManager to determine
 * what levels are available and how to load them.
 */
@com.openggf.game.ModApi
public interface ZoneRegistry {
    /**
     * Returns the total number of zones in this game.
     *
     * @return the zone count
     */
    int getZoneCount();

    /**
     * Returns the number of acts in a given zone.
     *
     * @param zoneIndex the zone index (0-based)
     * @return the act count for the zone
     */
    int getActCount(int zoneIndex);

    /**
     * Returns the display name for a zone.
     *
     * @param zoneIndex the zone index (0-based)
     * @return the zone name (e.g., "EMERALD HILL")
     */
    String getZoneName(int zoneIndex);

    /**
     * Returns the start position for a level.
     *
     * @param zoneIndex the zone index (0-based)
     * @param actIndex the act index (0-based)
     * @return array of [x, y] start coordinates
     */
    int[] getStartPosition(int zoneIndex, int actIndex);

    /**
     * Returns the level descriptors for all acts in a zone.
     *
     * @param zoneIndex the zone index (0-based)
     * @return list of level descriptors for each act
     */
    List<LevelDescriptor> getLevelDataForZone(int zoneIndex);

    /**
     * Returns all zones as a list of lists of level descriptors.
     * Outer list is zones, inner list is acts.
     *
     * @return the complete zone/act structure
     */
    List<List<LevelDescriptor>> getAllZones();

    /**
     * Returns the music ID for a given level.
     *
     * @param zoneIndex the zone index (0-based)
     * @param actIndex the act index (0-based)
     * @return the music ID, or -1 if no music is defined
     */
    int getMusicId(int zoneIndex, int actIndex);

    /** Returns a tagged stock or namespaced music reference for this level. */
    default MusicReference getMusicReference(int zoneIndex, int actIndex) {
        return MusicReference.stock(getMusicId(zoneIndex, actIndex));
    }

    /** Returns the stable identity represented by one current runtime index. */
    default ZoneKey zoneKey(int zoneIndex) {
        if (zoneIndex < 0 || zoneIndex >= getZoneCount()) {
            throw new IllegalArgumentException("Zone index is outside this registry: " + zoneIndex);
        }
        return ZoneKey.stock(zoneIndex);
    }

    /** Resolves a persisted identity without interpreting unknown numeric values as mod zones. */
    default OptionalInt resolveZoneKey(ZoneKey key) {
        if (key instanceof ZoneKey.Stock stock
                && stock.zoneIndex() >= 0 && stock.zoneIndex() < getZoneCount()) {
            return OptionalInt.of(stock.zoneIndex());
        }
        return OptionalInt.empty();
    }

    /** Resolves a game-owned, stable lower-case stock progression anchor. */
    default int resolveStockZoneAnchor(String stockKey) {
        throw new IllegalArgumentException("Unknown stock zone anchor: " + stockKey);
    }

    /** Immutable completion metadata for this exact registry snapshot. */
    default ZoneProgressionPlan.ZoneTopology progressionTopology() {
        int[] acts = new int[getZoneCount()];
        for (int zone = 0; zone < acts.length; zone++) acts[zone] = getActCount(zone);
        return ZoneProgressionPlan.ZoneTopology.linear(acts);
    }

    /** Progression graph built for {@link #progressionTopology()}. */
    default ZoneProgressionPlan progressionPlan() {
        return ZoneProgressionPlan.builder(progressionTopology()).build();
    }
}
