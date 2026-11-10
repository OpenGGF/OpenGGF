package com.openggf.game.soniccd.objects;
import org.junit.jupiter.api.Test;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import java.util.*;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;
/** Scope/fault controls, separate from compulsory public-controller route obligations. UNRUN. */
class TestCdOriginalEnemyPrimitives {
 @Test void primitiveKeepsOriginalAndDistinctSuppressedFaultsAndExpiresCall(){
  var placement=emptyPlacement();
  var first=new IllegalStateException("first");var second=new AssertionError("after");
  var calls=new ArrayList<CdEnemyPrimitiveScope.Call>();
  var scope=new CdEnemyPrimitiveScope(placement,new CdEnemyPrimitiveScope.Observer(){
   public void before(CdEnemyPrimitiveScope.Call c){calls.add(c);throw first;}
   public void after(CdEnemyPrimitiveScope.Call c){throw second;}
  });
  var thrown=assertThrows(IllegalStateException.class,()->scope.enter(32,0x3E));assertSame(first,thrown);assertArrayEquals(new Throwable[]{second},thrown.getSuppressed());assertFalse(scope.isCurrent(calls.getFirst()));scope.close();
 }
 @Test void primitiveNeverSelfSuppressesOrWritesAfterRejectedBefore(){
  var placement=emptyPlacement();var first=new AssertionError("same");var before=placement.capture();
  var scope=new CdEnemyPrimitiveScope(placement,new CdEnemyPrimitiveScope.Observer(){public void before(CdEnemyPrimitiveScope.Call c){throw first;}public void after(CdEnemyPrimitiveScope.Call c){throw first;}});
  assertSame(first,assertThrows(AssertionError.class,()->scope.enter(32,0x3E)));assertEquals(0,first.getSuppressed().length);assertEquals(before.pool().slots(),placement.capture().pool().slots());scope.close();
 }
 private static CdObjectPlacementRuntime emptyPlacement(){
  // Same physical header/backward8/forward8/Null6 form as TestCdObjectPlacementRuntime.layout().
  var bytes=ByteBuffer.allocate(26);
  bytes.putShort((short)12).putShort((short)20).putShort((short)0xFFFF).putInt(0).putShort((short)0);
  bytes.putShort((short)0xFFFF).putInt(0).putShort((short)0);
  bytes.putShort((short)0xFFFF).putInt(0);
  return new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(bytes.array()));
 }

}
