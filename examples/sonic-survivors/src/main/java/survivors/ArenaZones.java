package survivors;

import com.openggf.game.ZoneRegistry;
import com.openggf.game.DelegatingZoneRegistry;
import com.openggf.level.LevelDescriptor;
import java.util.List;

/**
 * Delegates the stock Sonic 2 registry, except that every route act starts Sonic in the middle
 * of its arena, just above the highest point of the arena floor (he drops onto it).
 */
final class ArenaZones extends DelegatingZoneRegistry {

    ArenaZones(ZoneRegistry base) { super(base); }

    ZoneRegistry base() { return source; }

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
        return acts(zoneIndex, source.getLevelDataForZone(zoneIndex));
    }
    @Override public List<List<LevelDescriptor>> getAllZones() {
        List<List<LevelDescriptor>> zones = source.getAllZones();
        return java.util.stream.IntStream.range(0, zones.size()).mapToObj(z -> acts(z, zones.get(z))).toList();
    }
    @Override public int[] getStartPosition(int zoneIndex, int actIndex) {
        int stage = Stages.stageOfZone(zoneIndex);
        if (stage < 0 || actIndex >= Stages.acts(stage)) return source.getStartPosition(zoneIndex, actIndex);
        Arena arena = Arena.of(stage, actIndex);
        return new int[]{arena.centreX(), arena.floorTop() - 24};
    }
}
