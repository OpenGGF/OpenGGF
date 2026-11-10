package com.openggf.game.soniccd.objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
/** Source-derived value controls for the extracted existing helper. No fake Tag assets. */
class TestCdSharedObjectDespawnStatic {
 @Test void actualPriorOnscreenBitSuppressesDistanceButNeverTheWarpTest() {
  var pool=new CdObjectPool();var row=pool.state(32).moveByte(SPRITE_FLAGS,0x80).moveWord(X,0x7000);var before=pool.capture();
  assertFalse(check(row,row.centreX(),1,0,0).deleteObject());assertTrue(check(row,row.centreX(),0x81,0,0).deleteObject());assertEquals(before.slots(),pool.capture().slots());
 }
 @Test void unsignedChunkDistanceKeepsEqualityAndDeletesTheNextChunk() {
  var p=new CdObjectPool();var s=p.state(32);assertFalse(check(s,0x200,1,0,0).deleteObject());assertTrue(check(s,0x280,1,0,0).deleteObject());
 }
 @Test void reloadedPostDrawBitClearedImageChangesOnlyTheHelperDecision() {
  var p=new CdObjectPool();var s=p.state(32).moveByte(SPRITE_FLAGS,0x80).moveWord(X,0x7000);assertFalse(check(s,s.centreX(),1,0,0).deleteObject());
  assertTrue(check(s.moveByte(SPRITE_FLAGS,0),s.centreX(),1,0,0).deleteObject());assertEquals(0,p.state(32).id());
 }
 @Test void savedZeroRequiresNoSavedBankClearEvenOnTheDeleteBranch() {
  var p=new CdObjectPool();var result=check(p.state(32).moveByte(SAVED_FLAGS_ID,0),0,0x81,0,0);assertTrue(result.deleteObject());assertTrue(result.loadedClear().isEmpty());
 }
 @Test void genuineSignedWarpDirectionSelectsAndClampsTheExistingSavedPeriod() {
  var p=new CdObjectPool();var row=p.state(32).moveByte(SAVED_FLAGS_ID,7);var result=check(row,0,0x81,0xFF,0);var clear=result.loadedClear().orElseThrow();
  assertEquals(7,clear.savedId());assertEquals(2,clear.period());assertEquals(25,clear.index());assertEquals(0,check(row,0,0x80,1,0).loadedClear().orElseThrow().period());
 }
 @Test void invalidExplicitXWordRejectsWithoutAnyPoolMutation() {
  var p=new CdObjectPool();var old=p.capture();assertThrows(IllegalArgumentException.class,()->check(p.state(32),0x10000,1,0,0));assertEquals(old.slots(),p.capture().slots());
 }
 private static CdSpinTunnelTag.DespawnResult check(CdObjectState row,int x,int time,int direction,int camera) {
  return CdSpinTunnelTag.checkObjectDespawnAtX(row,x,new CdSpinTunnelTag.DespawnGlobals(time,direction,camera));
 }
}
