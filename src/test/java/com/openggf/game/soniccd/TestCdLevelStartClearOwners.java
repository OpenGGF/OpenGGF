package com.openggf.game.soniccd;

import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.level.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/** Finite native component value writers, not World/loading invocation authority. */
class TestCdLevelStartClearOwners {
 @Test void primary64ClearRetainsHistoryEnvironmentArtClockAndHasOneOwner()throws Exception{
  try(var h=new TestSonicCdR31aObjectPass.Harness();var foreign=new TestSonicCdR31aObjectPass.Harness()){
   h.primary(1,0,0);h.service();h.walk();h.finish();var state=h.frame.state();var env=h.frame.environment();var art=h.frame.artState();var clock=h.frame.vIntClock();var ready=h.frame.prepareLevelStartObjectClear();
   assertSame(state,h.frame.state());assertThrows(IllegalArgumentException.class,()->foreign.frame.applyLevelStartClear(ready));h.frame.applyLevelStartClear(ready);
   assertEquals(new CdPlayerBody.State(new CdPlayerMotionState(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(0,0,0,0,0),new CdPlayerBody.Timers(0,0,0,0),state.history(),0,new CdPlayerBody.ObjectLinks(0,0),0),h.frame.state());assertSame(state.history(),h.frame.state().history());assertSame(env,h.frame.environment());assertSame(art,h.frame.artState());assertSame(clock,h.frame.vIntClock());assertTrue(h.frame.display().isEmpty());assertThrows(IllegalArgumentException.class,()->h.frame.applyLevelStartClear(ready));
  }
 }
 @Test void primaryClearPreparationDoesNotMintAuthorityAcrossAChangedPair()throws Exception{
  try(var h=new TestSonicCdR31aObjectPass.Harness()){var ready=h.frame.prepareLevelStartObjectClear();h.frame.initializeLevelSizeCentrePlayerValues(0x1234,0x5678);var before=h.frame.state();assertThrows(IllegalArgumentException.class,()->h.frame.applyLevelStartClear(ready));assertSame(before,h.frame.state());}
 }
 @Test void primaryClearRejectsPendingNativeInputWithoutConsumingIt()throws Exception{
  try(var h=new TestSonicCdR31aObjectPass.Harness()){h.frame.prepareInput(8);var before=h.frame.state();assertThrows(IllegalStateException.class,h.frame::prepareLevelStartObjectClear);assertSame(before,h.frame.state());}
 }
 @Test void cameraClearPreservesComplete96SavedBankAndActualSource()throws Exception{
  try(var h=new TestSonicCdR31aObjectPass.Harness()){h.placement.spawnObjects(0x100,1);h.placement.pool().write(h.placement.pool().state(127).moveByte(ID,0xA5).moveWord(X_FRACTION,0xCAFE));h.placement.bsetSavedFlag(7,1,CdObjectPlacementRuntime.SavedBit.MEMBER_3);var before=h.placement.capture();h.placement.clearLevelStartCameraValues();var after=h.placement.capture();assertEquals(new CdObjectPlacementRuntime.Status(0,0,0,0,before.savedFlags().unsignedByte(0),before.savedFlags().unsignedByte(1),-1,-1),after.status());assertEquals(before.pool().slots(),after.pool().slots());assertArrayEquals(before.savedFlags().copy(),after.savedFlags().copy());assertTrue(h.placement.ownsR31aAssets(h.assets));}
 }
}
