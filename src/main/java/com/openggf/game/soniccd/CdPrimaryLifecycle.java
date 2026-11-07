package com.openggf.game.soniccd;

import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import java.util.*;

/** Shared Level_MainLoop death stores and completed primary lifecycle comparison/preparation.
 * R3 PlayerDead/Restart (objects/player.asm:2244-2352) and R1 ObjSonic_Dead/Restart
 * (Level/_Objects/Sonic/Main.asm:2397-2494), N1ecc5f3a. No value here is invocation authority.
 * Original World/Frame callers alone preflight and publish once; never replay effects. */
final class CdPrimaryLifecycle {
 private CdPrimaryLifecycle(){}
 static CdR11aForegroundScroll.State freezeDestination(CdR11aForegroundScroll.State f,int cameraY){
  if((cameraY&~65535)!=0||f.cameraYWord()!=cameraY)throw new IllegalArgumentException("Stale native cameraY WORD");
  if(f.destBottomBound()==cameraY)return f;
  return new CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),cameraY,f.destLeftBound(),f.bottomBoundShift(),f.scrollLock());
 }
 record Plan(CdRingGlobals ring,CdLevelGlobals level,CdObjectPool.Snapshot pool,
             List<SonicCdGameModule.PastSound> sounds,boolean resetSaved,boolean alias){
  Plan{sounds=List.copyOf(sounds);}
 }
 static boolean aliasReached(CdPlayerBody.Result result){
  for(int i=0;i<result.effects().size();i++)if(result.effects().get(i) instanceof CdPlayerBody.UncheckedAliasWrite)return true;
  return false;
 }
 static Plan prepare(CdPlayerFrameRuntime.BodyPublication publication,CdObjectPool.Snapshot pool,
                     CdRingGlobals inputRing,CdLevelGlobals inputLevel){
  var result=publication.result();boolean dead=result.boundary()==CdPlayerBody.Boundary.DEAD_RETURN;
  if(!dead&&result.boundary()!=CdPlayerBody.Boundary.RESTART_RETURN)throw new IllegalArgumentException("Not a lifecycle return");
  CdPlayerBody.DeadCheckGone gone=null;CdPlayerBody.RestartCut restart=null;int resets=0;var aliases=new ArrayList<CdPlayerBody.UncheckedAliasWrite>();var driver=new ArrayList<CdPlayerBody.Z80RamWrite>();
  for(var effect:result.effects()){
   if(effect instanceof CdPlayerBody.DeadCheckGone v){if(gone!=null||restart!=null||!dead)throw new IllegalArgumentException("Repeated/foreign death summary");gone=v;}
   else if(effect instanceof CdPlayerBody.RestartCut v){if(gone!=null||restart!=null||dead)throw new IllegalArgumentException("Repeated/foreign restart summary");restart=v;}
   else if(effect instanceof CdPlayerBody.ResetSavedObjectFlagsRequest)resets++;
   else if(effect instanceof CdPlayerBody.UncheckedAliasWrite v)aliases.add(v);
   else if(effect instanceof CdPlayerBody.Z80RamWrite v)driver.add(v);
   else if(effect instanceof CdPlayerBody.ObjectTouchResult||effect instanceof CdPlayerBody.ObjectTouchResponse||effect instanceof CdPlayerBody.ObjectBossTouchResponse||effect instanceof CdPlayerBody.TimeWarpCall||effect instanceof CdPlayerBody.ModeEffect)throw new IllegalArgumentException("Lifecycle return mixed another primary owner");
  }
  var ring=inputRing;var level=inputLevel;var nextPool=pool;boolean alias=false;
  if(gone!=null){
   if(resets!=0||!driver.isEmpty()||gone.updateHudTime()!=0)throw new IllegalArgumentException("Death summary changed restart nonwrites");
   var found=gone.allocation().map(CdPlayerTouchResponse.FindObjSlot::wholeIndex).orElse(OptionalInt.empty());
   if(gone.allocation().isPresent()){
    OptionalInt first=OptionalInt.empty();for(var slot:pool.slots())if(slot.id()==0){first=OptionalInt.of(slot.identity().wholeIndex());break;}
    if(!first.equals(found))throw new IllegalArgumentException("Death allocation is not the native first-free slot");
   }
   alias=gone.allocation().isPresent()&&found.isEmpty();
   var expectedAliases=alias?List.of(new CdPlayerBody.UncheckedAliasWrite(0xFFF000,0x3B)):List.of();
   if(!aliases.equals(expectedAliases))throw new IllegalArgumentException("Death alias differs from actual exhausted allocation");
   if(gone.gameOverImage().isPresent()){
    var image=gone.gameOverImage().orElseThrow();var expected=pool.slots().get(image.identity().dynamicIndex()).moveByte(CdObjectState.ByteField.ID,0x3B);
    if(!image.equals(expected))throw new IllegalArgumentException("Death allocation changed bytes other than oID");
    nextPool=CdObjectPool.overlay(pool,List.of(image));
   }
   ring=new CdRingGlobals(ring.rings(),gone.lives(),ring.livesFlags(),ring.updateHudRings(),gone.updateHudLives(),ring.timeStopTimer(),ring.ringAnimationTimer(),ring.ringAnimationFrame());
   level=new CdLevelGlobals(level.levelRestart(),gone.updateHudTime(),level.checkpoint(),level.spawnMode(),level.plcLoadFlags(),level.flowerCount(),level.fadeEnableDisplay(),level.powerup(),level.timeOver());
  }else if(restart!=null){
   var next=restart.next();if(!restart.resetSavedObjectFlags()||resets!=1||next.levelRestart()!=1||next.flowerCount()!=0||next.fadeEnableDisplay()!=0||!aliases.isEmpty()||!driver.equals(List.of(new CdPlayerBody.Z80RamWrite(0x1C3E,1))))throw new IllegalArgumentException("Restart omitted its native complete publication");
   level=next;
  }else if(resets!=0||!aliases.isEmpty()||!driver.isEmpty()||result.effects().stream().anyMatch(CdPlayerBody.LevelWrite.class::isInstance))throw new IllegalArgumentException("Lifecycle writes omitted their one summary");
  var sounds=new ArrayList<SonicCdGameModule.PastSound>();for(var sound:publication.sounds())sounds.add(new SonicCdGameModule.PastFrameSound(sound));
  return new Plan(ring,level,nextPool,sounds,restart!=null,alias);
 }
}
