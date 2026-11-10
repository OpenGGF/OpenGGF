package com.openggf.game.soniccd;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import java.util.*;
/** Actual R41A DATA/scene relation for the same ordinary World. No authority, scheduler or R11 fallback. */
final class CdTtOriginalWorldBindings implements CdOriginalWorldBindings<CdR11aSectionArt.State> {
 private final CdLevelLoadingSource loading;private final CdStagePlayerSource player;private final CdProgrammeObjectSource objects;private final CdR11aSectionArt art;
 CdTtOriginalWorldBindings(CdStagePlayerSource player,CdLevelLoadingSource loading,CdR11aSectionArt art,CdObjectPlacementRuntime placement)throws java.io.IOException{
  this.loading=Objects.requireNonNull(loading);this.player=Objects.requireNonNull(player);this.art=Objects.requireNonNull(art);loading.requirePlayerSource(player);
  if(loading.file()!=CdNativeFile.R41A||!art.ownsLoadingSource(loading)||placement.placements()!=loading.placements())throw new IllegalArgumentException("Actual original TT source/scene/placement required");
  objects=loading.objectSource();objects.requirePlacement(placement);
 }
 CdProgrammeObjectSource objectSource(){return objects;}
 public CdStagePlayerSource playerSource(){return player;}public CdObjectPlacements placements(){return loading.placements();}public int nativeZoneActWord(){return loading.nativeZoneActWord();}
 public ScrollResult scroll(SonicCdR31aLoadingVisit.Scroll s,CdR11aForegroundScroll.Input i,int period,int vx,int x,int control){throw new IllegalStateException("TT scroll requires its actual Module water/global source cut");}
 public PaletteResult paletteCycle(PaletteAccess access){throw new IllegalStateException("TT palette requires its actual current source camera/water aliases");}
 public UnsupportedOperationException paletteFailure(CdLevelPaletteCycle.Fault f){return new UnsupportedOperationException("Actual TT palette source: "+f);}
 CdR4PaletteComposition.Result paletteAt(PaletteState before,CdAssetBytes water,CdAssetBytes fade,CdAssetBytes waterFade,int x,int y,int stop){
  var standard=new ArrayList<CdLevelPaletteCycle.Script>();var upper=new ArrayList<CdLevelPaletteCycle.Script>();
  for(var c:loading.cycles()){var s=new CdLevelPaletteCycle.Script(c.script(),c.colors());if(c.kind()==CdLevelLoadingSource.CycleKind.STANDARD)standard.add(s);else if(c.kind()==CdLevelLoadingSource.CycleKind.UPPER)upper.add(s);else throw new UnsupportedOperationException("Unadmitted TT cycle format");}
  var palette4=loading.loadTables().palettes().stream().filter(p->p.nativeId()==4).findFirst().orElseThrow().bytes();
  return CdR4PaletteComposition.step(CdR4PaletteComposition.Mode.PRESENT,new CdR4PaletteComposition.State(before.palette(),water,fade,waterFade,before.timers7(),before.steps7()),loading.nativeZoneActWord()&255,x,y,stop,new CdR4PaletteComposition.Tables(standard,upper,palette4));
 }
 Hud<CdR11aSectionArt.State> hud(){var cursor=new CdLevelHud.OriginalR11(loading,objects.font(),art);return(before,inputs,access)->{
  var s=new CdLevelHud.OriginalR11State(before.section(),before.ring(),before.level(),before.score(),before.liveTime(),before.bonus());
  try{access.retain(hud(cursor.run(s,inputs).state()));}
  catch(CdLevelHud.UnsupportedOriginalR11Hud first){try{access.retain(hud(first.state()));}catch(RuntimeException|Error retention){if(retention!=first)first.addSuppressed(retention);}throw first;}
  catch(CdLevelHud.OriginalR11ArtFailure fault){var first=fault.original();try{access.retain(hud(fault.state()));}catch(RuntimeException|Error retention){if(retention!=first)first.addSuppressed(retention);}throw first;}
 };}
 private static HudState<CdR11aSectionArt.State> hud(CdLevelHud.OriginalR11State s){return new HudState<>(s.section(),s.ring(),s.level(),s.score(),s.liveTime(),s.bonus());}
}
