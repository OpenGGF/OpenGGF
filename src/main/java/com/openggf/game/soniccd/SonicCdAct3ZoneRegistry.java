package com.openggf.game.soniccd;

import com.openggf.game.ZoneRegistry;
import com.openggf.level.LevelDescriptor;
import java.util.List;
import java.util.Objects;

/** Host descriptors for the retained PPZ programmes; these values grant no load or gameplay admission. */
final class SonicCdAct3ZoneRegistry implements ZoneRegistry {
    private final SonicCdGameModule module;
    SonicCdAct3ZoneRegistry(SonicCdGameModule module) { this.module=Objects.requireNonNull(module); }
    private void zone(int zone) { module.actualAct3RegistryAssets();if(zone!=0)throw SonicCd.unsupported("R13 host zone"); }
    private LevelDescriptor descriptor(int act) {
        if(act<0||act>2)throw SonicCd.unsupported("R13 host act");
        return new LevelDescriptor() {
            @Override public int levelIndex() { return act; }
            @Override public int startX() {
                var assets=module.actualAct3RegistryAssets();
                var source=module.actualAct3RegistrySource();
                return act==0?source.assets().loadPlan().initialCentre().centreX()
                        :act==1?source.act2PastAssets().orElseThrow().terrain().loadPlan().initialCentre().centreX()
                        :assets.initialCentre().centreX();
            }
            @Override public int startY() {
                var assets=module.actualAct3RegistryAssets();
                var source=module.actualAct3RegistrySource();
                return act==0?source.assets().loadPlan().initialCentre().centreY()
                        :act==1?source.act2PastAssets().orElseThrow().terrain().loadPlan().initialCentre().centreY()
                        :assets.initialCentre().centreY();
            }
        };
    }
    @Override public int getZoneCount() { zone(0);return 1; }
    @Override public int getActCount(int zone) { zone(zone);return 3; }
    @Override public String getZoneName(int zone) { zone(zone);return "PALMTREE PANIC"; }
    @Override public int[] getStartPosition(int zone,int act) { zone(zone);var d=descriptor(act);return new int[]{d.startX(),d.startY()}; }
    @Override public List<LevelDescriptor> getLevelDataForZone(int zone) { zone(zone);return List.of(descriptor(0),descriptor(1),descriptor(2)); }
    @Override public List<List<LevelDescriptor>> getAllZones() { return List.of(getLevelDataForZone(0)); }
    @Override public int getMusicId(int zone,int act) { zone(zone);descriptor(act);return -1; }
}
