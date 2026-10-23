package com.openggf.game.soniccd;

import com.openggf.game.ZoneRegistry;
import com.openggf.level.LevelDescriptor;

import java.util.List;

/** One admitted static entry. The full 70-payload metadata inventory remains CdProgramCatalogue. */
final class SonicCdZoneRegistry implements ZoneRegistry {
    private CdGameDataSource source;
    private final LevelDescriptor r11a = new LevelDescriptor() {
        @Override public int levelIndex() { return 0; }
        @Override public int startX() { return requireSource().assets().loadPlan().initialCentre().centreX(); }
        @Override public int startY() { return requireSource().assets().loadPlan().initialCentre().centreY(); }
    };
    private final List<List<LevelDescriptor>> zones = List.of(List.of(r11a));

    void bind(CdGameDataSource source) { this.source = source; }
    private CdGameDataSource requireSource() {
        if (source == null) throw new IllegalStateException("Authenticate and bind the CD source before reading start data");
        return source;
    }
    private static void requireEntry(int zone, int act) {
        if (zone != 0 || act != 0) throw SonicCd.unsupported("zone/act " + zone + "/" + act);
    }
    @Override public int getZoneCount() { return 1; }
    @Override public int getActCount(int zoneIndex) { requireEntry(zoneIndex, 0); return 1; }
    @Override public String getZoneName(int zoneIndex) { requireEntry(zoneIndex, 0); return "PALMTREE PANIC"; }
    @Override public int[] getStartPosition(int zoneIndex, int actIndex) {
        requireEntry(zoneIndex, actIndex);
        return new int[]{r11a.startX(), r11a.startY()};
    }
    @Override public List<LevelDescriptor> getLevelDataForZone(int zoneIndex) {
        requireEntry(zoneIndex, 0);
        return zones.getFirst();
    }
    @Override public List<List<LevelDescriptor>> getAllZones() { return zones; }
    @Override public int getMusicId(int zoneIndex, int actIndex) { requireEntry(zoneIndex, actIndex); return -1; }
}
