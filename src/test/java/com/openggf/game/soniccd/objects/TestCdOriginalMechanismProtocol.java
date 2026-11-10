package com.openggf.game.soniccd.objects;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import com.openggf.game.soniccd.level.CdObjectPlacements;
/** Source fault controls written first; no Java execution or discovery claimed. */
class TestCdOriginalMechanismProtocol {
 @Test void occurrenceIsIdentityBoundRetiresAndRejectsReentry(){
  var p=placement();var calls=new ArrayList<CdOriginalMechanismProtocol.Call>();
  var g=new CdOriginalMechanismProtocol(p,new CdOriginalMechanismProtocol.Observer(){
   public void before(CdOriginalMechanismProtocol.Call c){calls.add(c);assertFalse(c.completed());}
   public void after(CdOriginalMechanismProtocol.Call c){assertTrue(c.completed());}
   public void failed(CdOriginalMechanismProtocol.Call c,Throwable t){fail(t);}
  });g.begin(43);var next=p.pool().state(43).moveByte(ROUTINE,2);
  g.store(next,()->{assertTrue(g.current(calls.getFirst()));assertThrows(IllegalStateException.class,()->g.begin(43));p.pool().write(next);});
  assertFalse(g.current(calls.getFirst()));assertEquals(2,p.pool().state(43).routine());g.end();
 }
 @Test void originalThrowableAndDistinctSuppressedFaultSurviveCompletedStore(){
  var p=placement();var first=new AssertionError("native failure");var verification=new IllegalStateException("verification");
  var g=new CdOriginalMechanismProtocol(p,new CdOriginalMechanismProtocol.Observer(){public void before(CdOriginalMechanismProtocol.Call c){}public void after(CdOriginalMechanismProtocol.Call c){}public void failed(CdOriginalMechanismProtocol.Call c,Throwable t){assertSame(first,t);throw verification;}});
  g.begin(43);assertSame(first,assertThrows(AssertionError.class,()->g.callback("FIRST",(java.util.function.Supplier<Object>)()->{p.pool().write(p.pool().state(43).moveByte(ROUTINE,2));throw first;})));assertEquals(2,p.pool().state(43).routine());assertArrayEquals(new Throwable[]{verification},first.getSuppressed());g.end();
 }
 @Test void selfSuppressionIsNeverAttempted(){
  var p=placement();var first=new AssertionError("native");var g=new CdOriginalMechanismProtocol(p,new CdOriginalMechanismProtocol.Observer(){public void before(CdOriginalMechanismProtocol.Call c){}public void after(CdOriginalMechanismProtocol.Call c){}public void failed(CdOriginalMechanismProtocol.Call c,Throwable t){throw first;}});g.begin(43);assertSame(first,assertThrows(AssertionError.class,()->g.callback("READ",(java.util.function.Supplier<Object>)()->{throw first;})));assertEquals(0,first.getSuppressed().length);g.end();
 }
 private static CdObjectPlacementRuntime placement(){var p=new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(new byte[]{0,12,0,20,-1,-1,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,-1,-1,0,0,0,0}));p.pool().write(p.pool().state(43).moveByte(ID,9));return p;}
}
