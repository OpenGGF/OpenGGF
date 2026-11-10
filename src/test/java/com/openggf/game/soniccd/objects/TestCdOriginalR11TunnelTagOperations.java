package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
/** Written before implementation. Constructed component controls; all Java UNRUN. */
class TestCdOriginalR11TunnelTagOperations {
 @Test void actualOrderedStoresPreserveFractionsAndRawRightWins(){
  var h=new H(3);h.run();assertEquals(List.of("DEBUG_MODE","RAW_HELD","FLAGS","X_VELOCITY","GROUND_VELOCITY","FLAGS","Y_RADIUS","X_RADIUS","CENTRE_Y_WORD","ANIMATION","DEBUG_CHEAT"),h.events);
  assertEquals(0xD00,h.motion.xVelocity());assertEquals(0xD00,h.motion.groundVelocity());assertEquals(0x2005BEEF,h.motion.centreY16_16());assertEquals(0x10001234,h.motion.centreX16_16());assertFalse(h.drawn);
 }
 @Test void subtypeZeroReadsZoneButNeverHeldAndAlreadyRollStillWritesFlags(){
  var h=new H(0);h.motion=motion(4,0);h.run();assertEquals(List.of("DEBUG_MODE","ZONE","X_VELOCITY","GROUND_VELOCITY","FLAGS","DEBUG_CHEAT"),h.events);assertEquals(0xA00,h.motion.xVelocity());
 }
 @Test void boredSkipsAllConditionalReadsAndDebugZeroNeverDraws(){
  var h=new H(0);h.motion=motion(0,0x2B);h.run();assertEquals(List.of("DEBUG_CHEAT"),h.events);assertEquals(2,h.slot().routine());assertFalse(h.drawn);
 }
 @Test void refusedFirstStoreRetainsInitAndIdenticalFaultWithoutLaterReads(){
  var h=new H(0);var fault=new AssertionError("first player store");h.fault=fault;
  assertSame(fault,assertThrows(AssertionError.class,h::run));assertEquals(2,h.slot().routine());assertEquals(0,h.motion.xVelocity());assertEquals(List.of("DEBUG_MODE","ZONE"),h.events);assertFalse(h.body.isCurrentCall(h.last));
 }
 @Test void identityCopiesAndReentryNeverAuthorizeAnOperation(){
  var h=new H(4);h.check=()->{assertTrue(h.body.isCurrentCall(h.last));assertThrows(IllegalStateException.class,h::run);};h.run();assertFalse(h.body.isCurrentCall(h.last));assertFalse(h.body.isCurrentCall(new Object()));
 }
 @Test void subtypeFourWritesNegativeYRegardlessOfOldYSign(){var h=new H(4);h.held=1;h.run();assertEquals(-0xA00,h.motion.yVelocity());assertEquals(-0xA00,h.motion.groundVelocity());}
 @Test void invalidRoutineStopsBeforeInitReadsAndPlayerWrites(){var h=new H(0);h.p.pool().write(h.slot().moveByte(ROUTINE,1));var before=h.p.capture();assertThrows(CdSpinTunnelTag.UnsupportedSpinTunnelTag.class,h::run);assertEquals(before.pool().slots(),h.p.capture().pool().slots());assertTrue(h.events.isEmpty());}
 static CdPlayerMotionState motion(int flags,int anim){return new CdPlayerMotionState(0x10001234,0x2000BEEF,0,0,0,2,0,flags,4,9,19,anim,0,1,0,0,0,0,0,0);}
 static final class H {
  final CdObjectPlacementRuntime p=new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(new byte[]{0,12,0,20,-1,-1,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,-1,-1,0,0,0,0}));
  CdPlayerMotionState motion=motion(0,0);final List<String> events=new ArrayList<>();int held=12;boolean drawn;Error fault;Runnable check=()->{};Object last;final CdSpinTunnelTag body;
  H(int subtype){p.pool().write(p.pool().state(43).moveByte(ID,0x34).moveWord(X,0x1000).moveWord(Y,0x2000).moveByte(SUBTYPE,subtype));
   body=CdSpinTunnelTag.withOriginalOperations(p,new CdSpinTunnelTag.Assets(0x23EE9C),new CdSpinTunnelTag.OriginalPorts(){
    public CdPlayerMotionState motion(CdSpinTunnelTag.OriginalCall c){last=c;check.run();return motion;}
    public int read(CdSpinTunnelTag.OriginalCall c,CdSpinTunnelTag.Read field){last=c;check.run();events.add(field.name());return field==CdSpinTunnelTag.Read.RAW_HELD?held:0;}
    public void player(CdSpinTunnelTag.OriginalCall c,CdSpinTunnelTag.PlayerResult value){last=c;check.run();if(fault!=null)throw fault;assertEquals(motion,value.expectedMotion());events.add(value.executedWrites().iterator().next().name());motion=value.finalMotion();}
    public void draw(CdSpinTunnelTag.OriginalCall c){last=c;check.run();drawn=true;}
   },new CdSpinTunnelTag.OriginalObserver(){public void before(CdSpinTunnelTag.OriginalCall c){last=c;assertTrue(c.source()==slot());}public void after(CdSpinTunnelTag.OriginalCall c){}public void failed(CdSpinTunnelTag.OriginalCall c,Throwable t){}});}
  CdObjectState slot(){return p.pool().state(43);}void run(){body.runOriginal(43,new CdSpinTunnelTag.DespawnGlobals(1,0,0x1000));}
 }
}
