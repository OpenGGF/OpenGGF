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
    private final List<List<PreparedModZone>> modZones;
    private final Map<ZoneKey.Mod, Integer> indices;
    private final Map<Integer, PreparedModZone> byLevelIndex;
    private final ZoneProgressionPlan.ZoneTopology topology;
    private final ZoneProgressionPlan progression;

    private ModZoneRegistry(ZoneRegistry stock, List<PreparedModZone> contributions) {
        this.stock = stock;
        ArrayList<List<LevelDescriptor>> assembled = new ArrayList<>(stock.getAllZones());
        LinkedHashMap<ZoneKey.Mod, Integer> keys = new LinkedHashMap<>();
        LinkedHashMap<Integer, PreparedModZone> levels = new LinkedHashMap<>();
        java.util.HashSet<Integer> usedLevels = new java.util.HashSet<>();
        assembled.forEach(zone -> zone.forEach(level -> usedLevels.add(level.levelIndex())));
        LinkedHashMap<ZoneKey.Mod, List<PreparedModZone>> grouped = new LinkedHashMap<>();
        for (PreparedModZone authored : contributions) {
            ZoneKey.Mod key = new ZoneKey.Mod(authored.ownerModId(), authored.localKey());
            List<PreparedModZone> acts = grouped.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (authored.actIndex() != acts.size())
                throw new IllegalArgumentException("Duplicate or unordered mod act identity: " + key + "/" + authored.actIndex());
            acts.add(authored);
        }
        ArrayList<PreparedModZone> remapped = new ArrayList<>();
        ArrayList<List<PreparedModZone>> effectiveZones = new ArrayList<>();
        int nextLevel = 0x400;
        int nextRomZone = 0x40;
        for (var entry : grouped.entrySet()) {
            ArrayList<PreparedModZone> acts = new ArrayList<>();
            int runtimeRomZone = nextRomZone++;
            for (PreparedModZone authored : entry.getValue()) {
                while (usedLevels.contains(nextLevel)) nextLevel++;
                PreparedModZone contribution = authored.withRuntimeIndices(nextLevel++, runtimeRomZone);
                usedLevels.add(contribution.levelIndex());
                acts.add(contribution);
                remapped.add(contribution);
                levels.put(contribution.levelIndex(), contribution);
            }
            keys.put(entry.getKey(), assembled.size());
            assembled.add(acts.stream().map(PreparedModZone::descriptor).toList());
            effectiveZones.add(List.copyOf(acts));
        }
        this.contributions = List.copyOf(remapped);
        this.modZones = List.copyOf(effectiveZones);
        this.zones = List.copyOf(assembled);
        this.indices = Map.copyOf(keys);
        this.byLevelIndex = Map.copyOf(levels);

        ArrayList<ZoneProgressionPlan.ZoneMetadata> metadata =
                new ArrayList<>(stock.progressionTopology().zones());
        modZones.forEach(acts -> metadata.add(new ZoneProgressionPlan.ZoneMetadata(
                acts.size(), ZoneProgressionPlan.Completion.RESULTS_DRIVEN)));
        this.topology = ZoneProgressionPlan.ZoneTopology.of(metadata);
        ZoneProgressionPlan.Builder builder = ZoneProgressionPlan.builder(topology);
        for (int i = 0; i < modZones.size(); i++) {
            PreparedModZone contribution = modZones.get(i).getFirst();
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
                : modZones.get(zoneIndex - stock.getZoneCount()).getFirst().zoneName();
    }
    public int[] getStartPosition(int zoneIndex, int actIndex) {
        if (zoneIndex < stock.getZoneCount()) return stock.getStartPosition(zoneIndex, actIndex);
        PreparedModZone zone = modZones.get(zoneIndex - stock.getZoneCount()).get(actIndex);
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
        return modZones.get(zoneIndex - stock.getZoneCount()).get(actIndex).musicReference();
    }
    public ZoneKey zoneKey(int zoneIndex) {
        if (zoneIndex < stock.getZoneCount()) return stock.zoneKey(zoneIndex);
        PreparedModZone zone = modZones.get(zoneIndex - stock.getZoneCount()).getFirst();
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
