package com.openggf.game.soniccd.objects.r4;

import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native vectors over a constructed component cut. Never an actual World/Frame/route claim. */
class TestCdR4FallSpikesConnectedControls {
 private static final class Fixture extends TestCdR4EcologyComponents.Fixture {
  Fixture(){globals.clear();}
  @Override public int read(Read field){effect(Kind.READ);var value=globals.get(field);if(value==null)throw new UnsupportedOperationException("Unowned test scalar "+field);return value;}
  @Override public int pointer(Data field){effect(Kind.POINTER);if(field!=Data.FALL_SPIKES_MAP)throw new UnsupportedOperationException("Unowned test DATA");return 0x20EB08;}
  void rawWord(int n,int offset,int value){word(n,Arrays.stream(CdObjectState.WordField.values()).filter(f->f.byteOffset()==offset).findFirst().orElseThrow(),value);}
  void parent(int routine){seed(40,0x32,routine,0);bytes(40,SPRITE_FLAGS,128);word(40,X,0x200);word(40,Y,0x100);}
 }
 @Test void originalInitAllocatesAfterCurrentWithoutClearingDirtyChildStorage() {
  var f=new Fixture();f.fullExcept(32,81);f.seed(80,0x32,0,0);f.word(80,X,0x123);f.word(80,Y,0x300);
  f.bytes(81,ROUTINE,6);f.word(81,X_FRACTION,0xBEEF);f.longAt(80,0x2a,0x1234);f.globals.put(Read.STATIC_WATER_Y,0x300);
  var before=f.placement.capture();CdR4FallSpikes.run(f.c,80);
  assertEquals(0,f.s(32).id());assertEquals(0x32,f.s(81).id());assertEquals(255,f.s(81).subtype());assertEquals(6,f.s(81).routine());
  assertEquals(0xBEEF,f.s(81).word(X_FRACTION));assertEquals(0x330,f.s(81).centreY());assertEquals(0x3000,f.l(80,0x34));assertEquals(0x1234,f.l(80,0x2a));
  assertEquals(f.c.raw(80),f.w(81,0x3e));assertEquals(f.c.raw(81),f.w(80,0x3c));assertEquals(1,f.s(81).unsignedByte(MAP_FRAME));
  f.unchangedOthers(before,80,81);assertTrue(f.operations.stream().noneMatch(f.c::isCurrent));
 }
 @Test void failedAfterCurrentAllocationKeepsEarlierInitAndRawLink() {
  var f=new Fixture();f.fullExcept(32);f.seed(127,0x32,0,0);f.word(127,Y,0x301);f.word(127,BOSS_SAVED_X,0xD800);f.globals.put(Read.STATIC_WATER_Y,0x300);
  CdR4FallSpikes.run(f.c,127);assertEquals(2,f.s(127).routine());assertEquals(0x1000,f.l(127,0x34));assertEquals(0xD800,f.w(127,0x3c));assertEquals(0,f.s(32).id());
 }
 @Test void triggerReadsYFirstAndNeverReadsUnknownXOutsideItsVerticalBand() {
  var f=new Fixture();f.parent(2);f.globals.put(Read.PLAYER_Y,0x13f);CdR4FallSpikes.run(f.c,40);assertEquals(2,f.s(40).routine());
  f.globals.put(Read.PLAYER_Y,0x140);f.globals.put(Read.PLAYER_X,0x1F0);CdR4FallSpikes.run(f.c,40);
  assertEquals(4,f.s(40).routine());assertEquals(0x100,f.w(40,0x12));assertEquals(0x14,f.w(40,0x32));
 }
 @Test void warmupMovesBothActualRawLinkedRowsBeforeItsTimerTransition() {
  var f=new Fixture();f.parent(4);f.seed(41,0x32,0,255);f.word(40,BOSS_SAVED_X,f.c.raw(41));f.word(41,Y,0x130);f.word(41,Y_FRACTION,0x1234);f.longAt(40,0x2a,0x18000);f.rawWord(40,0x32,0);
  CdR4FallSpikes.run(f.c,40);assertEquals(0x1018000,f.l(40,12));assertEquals(0x1319234,f.l(41,12));assertEquals(6,f.s(40).routine());assertEquals(0,f.l(40,0x2a));assertEquals(0x1e,f.w(40,0x32));
 }
 @Test void pauseTransitionDoesNotSpeculativelyReadFloor() {
  var f=new Fixture();f.parent(6);f.rawWord(40,0x32,0);CdR4FallSpikes.run(f.c,40);assertEquals(8,f.s(40).routine());assertFalse(f.calls.contains(Kind.FLOOR));
 }
 @Test void floorLandingDeletesTheLinkedChildThenSettlesThroughRoutineC() {
  var f=new Fixture();f.parent(8);f.seed(41,0x32,0,255);f.word(40,BOSS_SAVED_X,f.c.raw(41));f.word(41,Y,0x130);f.longAt(40,0x2a,0x10000);f.longAt(40,0x34,0x3000);f.floor=-1;
  CdR4FallSpikes.run(f.c,40);assertEquals(10,f.s(40).routine());assertEquals(0x100,f.s(40).centreY());assertEquals(0x13000,f.l(40,0x2a));assertEquals(0x200000,f.l(40,0x2e));assertArrayEquals(new byte[64],f.s(41).copyBytes());
  f.longAt(40,0x2e,0);CdR4FallSpikes.run(f.c,40);assertEquals(12,f.s(40).routine());f.calls.clear();CdR4FallSpikes.run(f.c,40);assertFalse(f.calls.contains(Kind.FLOOR));assertTrue(f.calls.contains(Kind.SOLID));
 }
 @Test void childUsesParentIdThenDrawOrDeleteWithoutGlobals() {
  var f=new Fixture();f.seed(41,0x32,255,255);f.rawWord(41,0x3e,f.c.raw(40));f.seed(40,0x32,0,0);
  CdR4FallSpikes.run(f.c,41);assertEquals(List.of(Kind.DRAW),f.calls);f.seed(40,0,0,0);f.calls.clear();CdR4FallSpikes.run(f.c,41);assertArrayEquals(new byte[64],f.s(41).copyBytes());assertTrue(f.calls.isEmpty());
 }
 @Test void prePrimitiveRejectionDoesNotPublishAndKeepsOriginalThrowable() {
  var f=new Fixture();f.parent(0);var before=f.placement.capture();var original=new IllegalStateException("refused STORE");
  f.before=op->{if(op.kind()==Kind.STORE)throw original;};assertSame(original,assertThrows(IllegalStateException.class,()->CdR4FallSpikes.run(f.c,40)));
  assertEquals(before.pool().slots(),f.placement.capture().pool().slots());assertTrue(f.operations.stream().noneMatch(f.c::isCurrent));
 }
 @Test void landedStoreFailureRetainsExactlyCompletedPrefixAndExpiresReceipt() {
  var f=new Fixture();f.parent(0);var initial=f.s(40);var original=new IllegalStateException("landed second STORE");int[] stores={0};
  f.after=op->{if(op.kind()==Kind.STORE&&++stores[0]==2)throw original;};assertSame(original,assertThrows(IllegalStateException.class,()->CdR4FallSpikes.run(f.c,40)));
  assertEquals(initial.moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,initial.spriteFlags()|4),f.s(40));assertEquals(2,stores[0]);
  var ended=f.operations.get(f.operations.size()-1);assertFalse(f.c.isCurrent(ended));assertThrows(IllegalStateException.class,()->f.c.draw(40));
 }
 @Test void floorFailureKeepsPairMovementAndAccelerationWithoutDeletingChild() {
  var f=new Fixture();f.parent(8);f.seed(41,0x32,0,255);f.word(40,BOSS_SAVED_X,f.c.raw(41));f.word(41,Y,0x130);f.longAt(40,0x2a,0x10000);f.longAt(40,0x34,0x3000);
  f.failAt=Kind.FLOOR;f.failure=new IllegalStateException("unknown current floor");assertSame(f.failure,assertThrows(IllegalStateException.class,()->CdR4FallSpikes.run(f.c,40)));
  assertEquals(0x101,f.s(40).centreY());assertEquals(0x131,f.s(41).centreY());assertEquals(0x13000,f.l(40,0x2a));assertEquals(8,f.s(40).routine());assertFalse(f.calls.contains(Kind.DRAW));
 }
}
