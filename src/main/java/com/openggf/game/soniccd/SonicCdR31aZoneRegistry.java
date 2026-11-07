package com.openggf.game.soniccd;
import com.openggf.game.ZoneRegistry;
import com.openggf.level.LevelDescriptor;
import java.util.List;
import java.util.Objects;
/** Host descriptor only. Native zone1/act0 preserves its ordinary engine index3;
 * earlier native descriptor identities are explicitly unbound, not PPZ admission. Only the private original handoff loads it. */
final class SonicCdR31aZoneRegistry implements ZoneRegistry {
 private final SonicCdGameModule module;
 private final LevelDescriptor r31=new LevelDescriptor(){
  @Override public int levelIndex(){return 1*3+0;}
  @Override public int startX(){return module.actualR31RegistryAssets().initialCentre().centreX();}
  @Override public int startY(){return module.actualR31RegistryAssets().initialCentre().centreY();}
 };
 SonicCdR31aZoneRegistry(SonicCdGameModule module){this.module=Objects.requireNonNull(module);}
 int loadingIndex(){module.actualR31RegistryAssets();return r31.levelIndex();}
 private void require(int zone,int act){module.actualR31RegistryAssets();if(zone!=1||act!=0)throw SonicCd.unsupported("Only the actual R31 descriptor zone1/act0 is selected");}
 @Override public int getZoneCount(){module.actualR31RegistryAssets();return 2;}
 @Override public int getActCount(int zone){module.actualR31RegistryAssets();if(zone==0)return 3;require(zone,0);return 1;}
 @Override public String getZoneName(int zone){module.actualR31RegistryAssets();if(zone==0)return "PALMTREE PANIC (UNBOUND)";require(zone,0);return "COLLISION CHAOS";}
 @Override public int[] getStartPosition(int zone,int act){require(zone,act);return new int[]{r31.startX(),r31.startY()};}
 private LevelDescriptor unbound(int index){return new LevelDescriptor(){public int levelIndex(){return index;}public int startX(){throw SonicCd.unsupported("Earlier programme descriptor is not R31 load authority");}public int startY(){throw SonicCd.unsupported("Earlier programme descriptor is not R31 load authority");}};}
 @Override public List<LevelDescriptor> getLevelDataForZone(int zone){module.actualR31RegistryAssets();if(zone==0)return List.of(unbound(0),unbound(1),unbound(2));require(zone,0);return List.of(r31);}
 @Override public List<List<LevelDescriptor>> getAllZones(){module.actualR31RegistryAssets();return List.of(getLevelDataForZone(0),getLevelDataForZone(1));}
 @Override public int getMusicId(int zone,int act){require(zone,act);return -1;}
}
