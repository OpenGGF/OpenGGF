package survivors;

import com.openggf.game.MusicReference;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneProgressionPlan;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.modzone.ModZoneRuntimeContribution;
import com.openggf.level.LevelDescriptor;
import java.util.List;
import java.util.OptionalInt;

/**
 * Delegates the stock Sonic 2 registry, except that every route act starts Sonic in the middle
 * of its arena, just above the highest point of the arena floor (he drops onto it).
 */
final class ArenaZones implements ZoneRegistry {
    private final ZoneRegistry base;

    ArenaZones(ZoneRegistry base) { this.base = base; }

    ZoneRegistry base() { return base; }

    private record ArenaStart(LevelDescriptor stock, Arena arena) implements LevelDescriptor {
        @Override public int levelIndex() { return stock.levelIndex(); }
        @Override public int startX() { return arena.centreX(); }
        @Override public int startY() { return arena.floorTop() - 24; }
    }

    private List<LevelDescriptor> acts(int zone, List<LevelDescriptor> stock) {
        int stage = Stages.stageOfZone(zone);
        if (stage < 0) return stock;
        var acts = new java.util.ArrayList<LevelDescriptor>();
        for (int act = 0; act < stock.size(); act++) {
            acts.add(act < Stages.acts(stage) ? new ArenaStart(stock.get(act), Arena.of(stage, act)) : stock.get(act));
        }
        return List.copyOf(acts);
    }

    @Override public List<LevelDescriptor> getLevelDataForZone(int zoneIndex) {
        return acts(zoneIndex, base.getLevelDataForZone(zoneIndex));
    }
    @Override public List<List<LevelDescriptor>> getAllZones() {
        List<List<LevelDescriptor>> zones = base.getAllZones();
        return java.util.stream.IntStream.range(0, zones.size()).mapToObj(z -> acts(z, zones.get(z))).toList();
    }
    @Override public int[] getStartPosition(int zoneIndex, int actIndex) {
        int stage = Stages.stageOfZone(zoneIndex);
        if (stage < 0 || actIndex >= Stages.acts(stage)) return base.getStartPosition(zoneIndex, actIndex);
        Arena arena = Arena.of(stage, actIndex);
        return new int[]{arena.centreX(), arena.floorTop() - 24};
    }
    @Override public ModZoneRuntimeContribution modZoneRuntimeContribution(int levelIndex) {
        return base.modZoneRuntimeContribution(levelIndex);
    }
    @Override public int getZoneCount() { return base.getZoneCount(); }
    @Override public int getActCount(int zoneIndex) { return base.getActCount(zoneIndex); }
    @Override public String getZoneName(int zoneIndex) { return base.getZoneName(zoneIndex); }
    @Override public int getMusicId(int zoneIndex, int actIndex) { return base.getMusicId(zoneIndex, actIndex); }
    @Override public MusicReference getMusicReference(int zoneIndex, int actIndex) {
        return base.getMusicReference(zoneIndex, actIndex);
    }
    @Override public ZoneKey zoneKey(int zoneIndex) { return base.zoneKey(zoneIndex); }
    @Override public OptionalInt resolveZoneKey(ZoneKey key) { return base.resolveZoneKey(key); }
    @Override public int resolveStockZoneAnchor(String stockKey) { return base.resolveStockZoneAnchor(stockKey); }
    @Override public ZoneProgressionPlan.ZoneTopology progressionTopology() { return base.progressionTopology(); }
    @Override public ZoneProgressionPlan progressionPlan() { return base.progressionPlan(); }
}
