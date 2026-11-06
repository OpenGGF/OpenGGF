package com.openggf.game.soniccd;

import com.openggf.game.ZoneRegistry;
import com.openggf.level.LevelDescriptor;
import java.util.List;

/** Derived host descriptors available only while Module owns the genuine Act2 programme selection. */
final class SonicCdAct2ZoneRegistry implements ZoneRegistry {
    private final SonicCdGameModule module;
    SonicCdAct2ZoneRegistry(SonicCdGameModule module) { this.module = module; }
    private void zone(int zone) { module.actualAct2RegistrySource(); if (zone != 0) throw SonicCd.unsupported("Act2 host zone"); }
    private LevelDescriptor descriptor(int act) {
        if (act != 0 && act != 1) throw SonicCd.unsupported("Act2 host act");
        return new LevelDescriptor() {
            @Override public int levelIndex() { return act; }
            @Override public int startX() { var source = module.actualAct2RegistrySource(); return act == 0
                    ? source.assets().loadPlan().initialCentre().centreX()
                    : module.act2PastRegistrySelected() ? source.act2PastAssets().orElseThrow().terrain().loadPlan().initialCentre().centreX()
                    : source.act2Assets().orElseThrow().terrain().loadPlan().initialCentre().centreX(); }
            @Override public int startY() { var source = module.actualAct2RegistrySource(); return act == 0
                    ? source.assets().loadPlan().initialCentre().centreY()
                    : module.act2PastRegistrySelected() ? source.act2PastAssets().orElseThrow().terrain().loadPlan().initialCentre().centreY()
                    : source.act2Assets().orElseThrow().terrain().loadPlan().initialCentre().centreY(); }
        };
    }
    @Override public int getZoneCount() { zone(0); return 1; }
    @Override public int getActCount(int zone) { zone(zone); return 2; }
    @Override public String getZoneName(int zone) { zone(zone); return "PALMTREE PANIC"; }
    @Override public int[] getStartPosition(int zone, int act) { zone(zone); var d = descriptor(act); return new int[]{d.startX(),d.startY()}; }
    @Override public List<LevelDescriptor> getLevelDataForZone(int zone) { zone(zone); return List.of(descriptor(0),descriptor(1)); }
    @Override public List<List<LevelDescriptor>> getAllZones() { return List.of(getLevelDataForZone(0)); }
    @Override public int getMusicId(int zone,int act) { getStartPosition(zone,act); return -1; }
}
