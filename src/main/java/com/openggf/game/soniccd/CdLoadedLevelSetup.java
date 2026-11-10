package com.openggf.game.soniccd;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;
import java.util.*;
/** One internal semantic owner of Main261-329 after the real second PLC wait.
 * Real callers provide their existing complete stores and source art adapter. No public
 * caller sequences native cuts; this driver owns their order and final fade completion.
 * No retained state, World, Host, scheduler, nullable programme union or invocation token. */
final class CdLoadedLevelSetup {
 enum Event { STORE,LOAD_PLAYER,HUD,LIFE_ICON,TIME_ICON,TITLE,BEGIN_MAIN,ANIMATED_TILES,ENABLE_FADE,FROM_WHITE,BEFORE_INPUT_TIMERS,SECONDARY_TIMER }
 record Values<A>(A art,CdLevelGlobals level,CdAssetBytes reserved,CdProgramInitialization.State initialization,CdAssetBytes normal,CdAssetBytes fade){
  Values{Objects.requireNonNull(art);Objects.requireNonNull(level);Objects.requireNonNull(initialization);if(reserved.length()!=1984||normal.length()!=128||fade.length()!=128)throw new IllegalArgumentException("Complete post-PLC values");}
  Values<A> level(CdLevelGlobals l){return new Values<>(art,l,reserved,initialization,normal,fade);}
  Values<A> reserved(CdAssetBytes b){return new Values<>(art,level,b,initialization,normal,fade);}
  Values<A> art(A a){return new Values<>(a,level,reserved,initialization,normal,fade);}
  Values<A> initialization(CdProgramInitialization.State i){return new Values<>(art,level,reserved,i,normal,fade);}
  Values<A> normal(CdAssetBytes n){return new Values<>(art,level,reserved,initialization,n,fade);}
 }
 record Ready(int gameModeByte,int levelStartedByte,SonicCdR31aLoadingVisit.Phase phase){}
 interface Programme<A> {
  CdPlayerFrameRuntime frame();Values<A> values();void store(Values<A> ready,Event nativeEvent);
  void lifeIcon(int period);A clearSectionHighByte(A actual);void unavailablePowerup();void unavailablePeriod();
  int gameMode();void completed(Ready ready);
 }
 static <A> SonicCdR31aLoadingVisit.Phase afterSecondPlc(Programme<A> p){
  var f=p.frame();var v=p.values();if(v.level().spawnMode()==0)f.applyLevelStartNormalPlayer();else f.applyLevelStartWarpPlayer();p.store(v,Event.LOAD_PLAYER);
  v=p.values();p.store(v.reserved(CdReservedLevelStart.hud(v.reserved())),Event.HUD);
  p.lifeIcon(f.environment().movement().gates().timeZone());p.store(p.values(),Event.LIFE_ICON);
  v=p.values();p.store(v.reserved(CdReservedLevelStart.timeIcon(v.reserved())),Event.TIME_ICON);
  v=p.values();var l=v.level();boolean title=(l.plcLoadFlags()&2)==0;
  p.store(v.level(new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags()|2,l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver())),Event.STORE);
  if(title){v=p.values();p.store(v.reserved(CdReservedLevelStart.title(v.reserved())),Event.TITLE);if(f.environment().controls().controlLocked()!=1)throw new IllegalStateException("Actual title control-lock store missing");v=p.values();p.store(v.art(p.clearSectionHighByte(v.art())),Event.STORE);}
  p.store(p.values(),Event.BEFORE_INPUT_TIMERS);f.clearLevelStartControlsAndBoredTimer();p.store(p.values(),Event.SECONDARY_TIMER);f.clearLevelStartPlayerGlobals();v=p.values();l=v.level();
  p.store(v.level(new CdLevelGlobals(0,1,l.checkpoint(),0,l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),0)),Event.BEGIN_MAIN);
  if(CdPowerupArtRuntime.gate(p.values().level().powerup()).copyRequired())p.unavailablePowerup();p.store(p.values(),Event.ANIMATED_TILES);
  v=p.values();l=v.level();p.store(v.level(new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),1,l.powerup(),l.timeOver())),Event.STORE);
  boolean marked=f.clearLevelStartTimeZoneMarker();if(f.environment().movement().gates().timeZone()!=f.stageSource().ordinaryPeriod())p.unavailablePeriod();
  boolean white=marked;
  if(!marked){v=p.values();white=CdProgramInitialization.requirePalClearFlagBit0(v.initialization());var i=v.initialization();if(white)p.store(v.initialization(new CdProgramInitialization.State(i.controllerSetupBit(),i.initMarker(),i.versionCache(),CdProgramInitialization.KnownBit.CLEAR)),Event.STORE);}
  p.store(p.values(),Event.ENABLE_FADE);if(white)p.store(p.values(),Event.FROM_WHITE);
  v=p.values();byte[] normal=v.normal().copy();if(white)normal=CdLevelStart.normalLoadPalette(true).copy();else Arrays.fill(normal,32,128,(byte)0);p.store(v.normal(CdAssetBytes.copyOf(normal)),Event.STORE);return white?SonicCdR31aLoadingVisit.Phase.FROM_WHITE:SonicCdR31aLoadingVisit.Phase.FROM_BLACK;
 }
 /** Only the real returned 22nd fade invokes this; original private callers retain authority. */
 static boolean returnedFade(int completedSteps){if(completedSteps<0||completedSteps>22)throw new IllegalArgumentException("Native fade progress");return completedSteps==22;}
 static void completedFade(int completedSteps,Programme<?> p){if(!returnedFade(completedSteps))throw new IllegalStateException("Native fade has not returned");p.completed(new Ready(p.gameMode()&127,1,SonicCdR31aLoadingVisit.Phase.READY));}
 /** One source-neutral script continuation, native r4/stage_gfx_animate_1a.asm:56-95.
  * The current private Module call owns all reads/stores and failures. No action/result replay,
  * hardware transfers, source selection, implicit CPU-buffer allocation or default timers. */
 interface LoadingAnimation {
  int timer(int ordinal);void timer(int ordinal,int value);int frame(int ordinal);void frame(int ordinal,int value);
  long script(int ordinal);int sourceByte(long address);int sourceLong(long address);
  void cpuLong(int byteOffset,int bits);void publishCpu(int destination,int bytes);
 }
 static void animateStage(LoadingAnimation p,int ordinal,int destination,int bytes){
  int timer=(p.timer(ordinal)-1)&255;p.timer(ordinal,timer);if((timer&128)==0)return;
  int frame=(p.frame(ordinal)+1)&255;long script=p.script(ordinal);
  if(frame>=p.sourceByte(script))frame=0;p.frame(ordinal,frame);
  p.timer(ordinal,p.sourceByte(script+2+frame*2L));
  int sourceIndex=(byte)p.sourceByte(script+3+frame*2L);
  int count=p.sourceByte(script);
  long address=Integer.toUnsignedLong(p.sourceLong(script+2+count*2L+sourceIndex*4L));
  if(bytes<=0||(bytes&3)!=0)throw new IllegalArgumentException("Complete native script LONG count");
  for(int at=0;at<bytes;at+=4){int bits=p.sourceLong(address+at);p.cpuLong(at,bits);}
  p.publishCpu(destination,bytes);
 }
 private CdLoadedLevelSetup(){}
}
