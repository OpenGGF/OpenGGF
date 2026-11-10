package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** SOURCE tests FIRST. Constructed component pools are not original World birth/capture authority. */
class TestCdOriginalTtTouchSource {
 private static CdProgrammeObjectSource source,other;
 @BeforeAll static void actualSource()throws Exception {
  var root=System.getProperty("soniccd.media.root","");var cue=Path.of(root,"USA","Sonic CD (US).cue");
  assumeTrue(!root.isBlank()&&Files.isRegularFile(cue),"Actual USA source");
  try(var disc=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
   assertEquals(CdR4DataLayout.USA_DISC_SHA256,disc.identity().normalizedSha256());
   source=CdProgrammeData.open(disc,CdR4DataLayout.Programme.R41A).objectSource();
   other=CdProgrammeData.open(disc,CdR4DataLayout.Programme.R41A).objectSource();
  }
 }
 @Test void actualOperandsBorrowExactTouchAndPointsTablesFromTheSameReader() {
  var dims=source.view(CdProgrammeObjectSource.Table.TOUCH_DIMENSIONS);
  assertEquals(0x207502,dims.nativeAddress());assertEquals(126,dims.bytes().length());
  assertEquals("8794c61b2aa394d30f7e7fa44916529e4e1c0aca52166091fcbf20d01b3c1644",sha(dims.bytes().copy()));
  var points=source.view(CdProgrammeObjectSource.Table.ENEMY_POINTS);
  assertEquals(0x2072F0,points.nativeAddress());assertArrayEquals(new byte[]{0,10,0,20,0,50,0,100},points.bytes().copy());
  assertSame(dims,source.view(CdProgrammeObjectSource.Table.TOUCH_DIMENSIONS));
 }
 @Test void scannerCacheRetainsTheExactSourceAndCannotBorrowEqualOtherReader() {
  assertSame(source.touchScanner(),source.touchScanner());assertTrue(source.touchScanner().ownsProgrammeSource(source));
  assertFalse(source.touchScanner().ownsProgrammeSource(other));assertFalse(source.touchScanner().ownsProgrammeSource(null));
 }
 @Test void fullNinetySixScanReturnsNoTouchWithoutAnyOwnerWrite() {
  var pool=new CdObjectPool();var before=pool.capture();assertSame(CdPlayerObjectTouch.NoTouch.INSTANCE,scan(pool,0));
  assertEquals(96,before.slots().size());assertEquals(before.slots(),pool.capture().slots());
 }
 @Test void actualGenericItemChangesOnlyItsNativeRoutineByteWithByteWrap() {
  var pool=new CdObjectPool();var old=row(pool,127,0x41).moveByte(ROUTINE,0xFE).moveByte(SUBTYPE,0x7D).moveWord(X_FRACTION,0xCAFE);pool.write(old);
  var before=pool.capture();var result=assertInstanceOf(CdPlayerObjectTouch.Item.class,scan(pool,0));
  assertSame(old,result.selectedSlot().orElseThrow());var changed=result.changedSlot().orElseThrow();var expected=old.copyBytes();expected[0x24]=0;
  assertArrayEquals(expected,changed.copyBytes());assertEquals(before.slots(),pool.capture().slots());
 }
 @Test void hurtNinetySuppressesItemStoreAndStillReturnsBeforeLaterHazard() {
  var pool=new CdObjectPool();pool.write(row(pool,32,0x41));pool.write(row(pool,33,0x81));
  var selected=assertInstanceOf(CdPlayerObjectTouch.Item.class,scan(pool,90));assertEquals(32,selected.selectedSlot().orElseThrow().identity().wholeIndex());assertTrue(selected.changedSlot().isEmpty());
  assertTrue(assertInstanceOf(CdPlayerObjectTouch.Item.class,scan(pool,89)).changedSlot().isPresent());
 }
 @Test void physicalSelectionDoesNotAddAnIdGateForDirtyIdZeroSlot() {
  var pool=new CdObjectPool();pool.write(row(pool,32,0x41).moveByte(ID,0));pool.write(row(pool,33,1));
  var item=assertInstanceOf(CdPlayerObjectTouch.Item.class,scan(pool,0));assertEquals(32,item.selectedSlot().orElseThrow().identity().wholeIndex());
 }
 @Test void selectedZeroSizeFaultPrecedesGeometryAndLeavesAllSourceImagesUnwritten() {
  var pool=new CdObjectPool();pool.write(row(pool,32,0x40).moveWord(X,0x7000));var old=pool.capture();
  assertThrows(IllegalArgumentException.class,()->scan(pool,0));assertEquals(old.slots(),pool.capture().slots());
 }
 @Test void responseKindsKeepExactTtSourceAndRemainBoundariesWithoutPpzAdmission() {
  for(int type:new int[]{1,0x81,0x46,0xDF}) {
   var pool=new CdObjectPool();pool.write(row(pool,32,type));var old=pool.capture();
   var selected=assertInstanceOf(CdPlayerObjectTouch.HandlerBoundary.class,scan(pool,0));
   assertSame(source,selected.programmeSource());assertTrue(selected.changedSlot().isEmpty());assertEquals(old.slots(),pool.capture().slots());
  }
 }
 private static CdPlayerObjectTouch.Result scan(CdObjectPool pool,int hurt) {
  return source.touchScanner().scan(new CdPlayerObjectTouch.PlayerCut(100,100,19,0,hurt),pool.capture());
 }
 private static CdObjectState row(CdObjectPool p,int w,int type) {
  return p.state(w).moveByte(ID,0x10).moveByte(SPRITE_FLAGS,0x80).moveByte(COLLISION_TYPE,type).moveWord(X,100).moveWord(Y,100);
 }
 private static String sha(byte[] bytes){
  try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
  catch(java.security.NoSuchAlgorithmException failure){throw new AssertionError(failure);}
 }
}
