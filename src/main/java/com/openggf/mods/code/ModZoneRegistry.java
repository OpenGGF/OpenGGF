package com.openggf.mods.code;

import com.openggf.game.MusicReference;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneProgressionPlan;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.modzone.ModZoneRuntimeContribution;
import com.openggf.level.LevelDescriptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/** Immutable aggregate zone registry rebuilt from stock plus all effective contributions. */
public final class ModZoneRegistry implements ZoneRegistry {
    private final ZoneRegistry stock;
    private final List<PreparedModZone> contributions;
    private final List<List<LevelDescriptor>> zones;
    private final Map<ZoneKey.Mod, Integer> indices;
    private final Map<Integer, PreparedModZone> byLevelIndex;
    private final ZoneProgressionPlan.ZoneTopology topology;
    private final ZoneProgressionPlan progression;

    private ModZoneRegistry(ZoneRegistry stock, List<PreparedModZone> contributions) {
        this.stock = stock;
        this.contributions = List.copyOf(contributions);
        ArrayList<List<LevelDescriptor>> assembled = new ArrayList<>(stock.getAllZones());
        LinkedHashMap<ZoneKey.Mod, Integer> keys = new LinkedHashMap<>();
        LinkedHashMap<Integer, PreparedModZone> levels = new LinkedHashMap<>();
        java.util.HashSet<Integer> authoredZones = new java.util.HashSet<>();
        for (PreparedModZone contribution : contributions) {
            ZoneKey.Mod key = new ZoneKey.Mod(contribution.ownerModId(), contribution.localKey());
            int syntheticIndex = assembled.size();
            if (keys.putIfAbsent(key, syntheticIndex) != null) {
                throw new IllegalArgumentException("Duplicate mod zone identity: " + key);
            }
            if (levels.putIfAbsent(contribution.levelIndex(), contribution) != null) {
                throw new IllegalArgumentException("Duplicate mod level index: " + contribution.levelIndex());
            }
            if (!authoredZones.add(contribution.authoredZoneIndex())) {
                throw new IllegalArgumentException("Duplicate authored mod zone index: " + contribution.authoredZoneIndex());
            }
            assembled.add(List.of(contribution.descriptor()));
        }
        this.zones = List.copyOf(assembled);
        this.indices = Map.copyOf(keys);
        this.byLevelIndex = Map.copyOf(levels);

        ArrayList<ZoneProgressionPlan.ZoneMetadata> metadata =
                new ArrayList<>(stock.progressionTopology().zones());
        contributions.forEach(ignored -> metadata.add(new ZoneProgressionPlan.ZoneMetadata(
                1, ZoneProgressionPlan.Completion.RESULTS_DRIVEN)));
        this.topology = ZoneProgressionPlan.ZoneTopology.of(metadata);
        ZoneProgressionPlan.Builder builder = ZoneProgressionPlan.builder(topology);
        for (int i = 0; i < contributions.size(); i++) {
            PreparedModZone contribution = contributions.get(i);
            if (contribution.insertAfter() == null) continue;
            int anchor = stock.resolveStockZoneAnchor(contribution.insertAfter());
            if (anchor < 0 || anchor >= stock.getZoneCount()) {
                throw new IllegalArgumentException("Stock progression anchor is outside stock registry");
            }
            builder.insertAfter(anchor, stock.getZoneCount() + i);
        }
        this.progression = builder.build();
    }

    public static ZoneRegistry decorate(ZoneRegistry inherited, List<PreparedModZone> added) {
        if (added.isEmpty()) return inherited;
        if (inherited instanceof ModZoneRegistry prior) {
            ArrayList<PreparedModZone> all = new ArrayList<>(prior.contributions);
            all.addAll(added);
            return new ModZoneRegistry(prior.stock, all);
        }
        return new ModZoneRegistry(inherited, added);
    }

    public List<PreparedModZone> contributions() { return contributions; }
    List<PreparedModZone> gameStartContributions() {
        return contributions.stream().filter(PreparedModZone::gameStart).toList();
    }
    public PreparedModZone levelContribution(int levelIndex) { return byLevelIndex.get(levelIndex); }

    @Override
    public ModZoneRuntimeContribution modZoneRuntimeContribution(int levelIndex) {
        PreparedModZone zone = byLevelIndex.get(levelIndex);
        return zone == null ? null : zone.runtimeContribution();
    }
    public int getZoneCount() { return zones.size(); }
    public int getActCount(int zoneIndex) { return getLevelDataForZone(zoneIndex).size(); }
    public String getZoneName(int zoneIndex) {
        return zoneIndex < stock.getZoneCount() ? stock.getZoneName(zoneIndex)
                : contributions.get(zoneIndex - stock.getZoneCount()).zoneName();
    }
    public int[] getStartPosition(int zoneIndex, int actIndex) {
        if (zoneIndex < stock.getZoneCount()) return stock.getStartPosition(zoneIndex, actIndex);
        PreparedModZone zone = contributions.get(zoneIndex - stock.getZoneCount());
        if (actIndex != 0) throw new IllegalArgumentException("Mod zones currently contain one act");
        return new int[]{zone.startX(), zone.startY()};
    }
    public List<LevelDescriptor> getLevelDataForZone(int zoneIndex) {
        return zoneIndex < 0 || zoneIndex >= zones.size() ? List.of() : zones.get(zoneIndex);
    }
    public List<List<LevelDescriptor>> getAllZones() { return zones; }
    public int getMusicId(int zoneIndex, int actIndex) {
        MusicReference reference = getMusicReference(zoneIndex, actIndex);
        return reference instanceof MusicReference.Stock stockMusic ? stockMusic.musicId() : -1;
    }
    public MusicReference getMusicReference(int zoneIndex, int actIndex) {
        if (zoneIndex < stock.getZoneCount()) return stock.getMusicReference(zoneIndex, actIndex);
        if (actIndex != 0) throw new IllegalArgumentException("Mod zones currently contain one act");
        return contributions.get(zoneIndex - stock.getZoneCount()).musicReference();
    }
    public ZoneKey zoneKey(int zoneIndex) {
        if (zoneIndex < stock.getZoneCount()) return stock.zoneKey(zoneIndex);
        PreparedModZone zone = contributions.get(zoneIndex - stock.getZoneCount());
        return ZoneKey.mod(zone.ownerModId(), zone.localKey());
    }
    public OptionalInt resolveZoneKey(ZoneKey key) {
        if (key instanceof ZoneKey.Mod mod) {
            Integer index = indices.get(mod);
            return index == null ? OptionalInt.empty() : OptionalInt.of(index);
        }
        return stock.resolveZoneKey(key);
    }
    public int resolveStockZoneAnchor(String stockKey) { return stock.resolveStockZoneAnchor(stockKey); }
    public ZoneProgressionPlan.ZoneTopology progressionTopology() { return topology; }
    public ZoneProgressionPlan progressionPlan() { return progression; }
}
