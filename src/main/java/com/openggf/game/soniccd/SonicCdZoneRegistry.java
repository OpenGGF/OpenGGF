package com.openggf.game.soniccd;

import com.openggf.game.ZoneRegistry;
import com.openggf.level.LevelDescriptor;

import java.util.List;

/** One admitted static entry. The full 70-payload metadata inventory remains CdProgramCatalogue. */
final class SonicCdZoneRegistry implements ZoneRegistry {
    private CdGameDataSource source;
    private com.openggf.game.soniccd.level.CdLevelLoadingSource loadingSource;
    private final LevelDescriptor r11a = new LevelDescriptor() {
        @Override public int levelIndex() { return loadingSource==null?0:SonicCd.sourceLevelIndex(loadingSource); }
        @Override public int startX() { return loadingSource==null?requireSource().assets().loadPlan().initialCentre().centreX():loadingSource.initialCentre().centreX(); }
        @Override public int startY() { return loadingSource==null?requireSource().assets().loadPlan().initialCentre().centreY():loadingSource.initialCentre().centreY(); }
    };
    private List<List<LevelDescriptor>> zones = List.of(List.of(r11a));
    private int admittedZone;

    void bind(CdGameDataSource source) { this.source = source;this.loadingSource=null;admittedZone=0;zones=List.of(List.of(r11a)); }
    void bind(CdGameDataSource source,com.openggf.game.soniccd.level.CdLevelLoadingSource loading) {
        SonicCd.requireLoadingSource(source,loading);int zone=loading.nativeZoneActWord()>>>8;
        if((zone!=0&&zone!=2)||(loading.nativeZoneActWord()&255)!=0)throw SonicCd.unsupported("unadmitted selected source registry entry");
        var actual=new java.util.ArrayList<List<LevelDescriptor>>();
        for(int i=0;i<zone;i++)actual.add(unboundZone(i)); // Explicit earlier index metadata; starts/loads remain unadmitted.
        actual.add(List.of(r11a));this.source=source;this.loadingSource=loading;admittedZone=zone;zones=List.copyOf(actual);
    }
    private LevelDescriptor unbound(int index) {return new LevelDescriptor(){
        public int levelIndex(){return index;}
        public int startX(){throw SonicCd.unsupported("Earlier descriptor is not selected programme load authority");}
        public int startY(){throw SonicCd.unsupported("Earlier descriptor is not selected programme load authority");}
    };}
    private List<LevelDescriptor> unboundZone(int zone){return List.of(unbound(zone*3),unbound(zone*3+1),unbound(zone*3+2));}
    private CdGameDataSource requireSource() {
        if (source == null) throw new IllegalStateException("Authenticate and bind the CD source before reading start data");
        return source;
    }
    private void requireEntry(int zone, int act) {
        if (zone != admittedZone || act != 0) throw SonicCd.unsupported("zone/act " + zone + "/" + act);
    }
    @Override public int getZoneCount() { return zones.size(); }
    @Override public int getActCount(int zoneIndex) { if(zoneIndex>=0&&zoneIndex<admittedZone)return 3;requireEntry(zoneIndex, 0); return 1; }
    @Override public String getZoneName(int zoneIndex) { if(zoneIndex>=0&&zoneIndex<admittedZone)return zoneIndex==0?"PALMTREE PANIC (UNBOUND)":"COLLISION CHAOS (UNBOUND)";requireEntry(zoneIndex, 0); return loadingSource==null?"PALMTREE PANIC":SonicCd.sourceTitle(loadingSource); }
    @Override public int[] getStartPosition(int zoneIndex, int actIndex) {
        requireEntry(zoneIndex, actIndex);
        return new int[]{r11a.startX(), r11a.startY()};
    }
    @Override public List<LevelDescriptor> getLevelDataForZone(int zoneIndex) {
        if(zoneIndex>=0&&zoneIndex<admittedZone)return zones.get(zoneIndex);
        requireEntry(zoneIndex, 0);
        return zones.get(admittedZone);
    }
    @Override public List<List<LevelDescriptor>> getAllZones() { return zones; }
    @Override public int getMusicId(int zoneIndex, int actIndex) { requireEntry(zoneIndex, actIndex); return -1; }
}
