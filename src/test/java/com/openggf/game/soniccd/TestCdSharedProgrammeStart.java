package com.openggf.game.soniccd;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** SOURCE-ONLY / UNRUN. Declared semantic component port, never a World/receipt authority. */
class TestCdSharedProgrammeStart {
 @Test void controllerClearSkipsMarkerReadAndScratchPrecedesTheReachedVersionRead(){
  var p=new Port(new CdProgramInitialization.State(CdProgramInitialization.KnownBit.CLEAR,CdProgramInitialization.MarkerKnowledge.UNKNOWN,new CdProgramInitialization.VersionCache(0,0)),Optional.empty());
  assertThrows(CdProgramInitialization.UnknownRead.class,()->CdProgramInitialization.enterLevelProgram(p));assertEquals(List.of("scratch"),p.calls);
 }
 @Test void actualGameInitBranchDoesNotReadUnknownExecutionRegion(){
  var p=new Port(new CdProgramInitialization.State(CdProgramInitialization.KnownBit.SET,CdProgramInitialization.MarkerKnowledge.EQUALS_INIT,new CdProgramInitialization.VersionCache(0,0)),Optional.empty());CdProgramInitialization.enterLevelProgram(p);assertEquals(List.of("video","controllers","mode"),p.calls);
 }
 @Test void doInitPreservesNativeOrderAndOriginalFailureIdentity(){
  var p=new Port(new CdProgramInitialization.State(CdProgramInitialization.KnownBit.CLEAR,CdProgramInitialization.MarkerKnowledge.UNKNOWN,new CdProgramInitialization.VersionCache(0,0)),Optional.of(new CdProgramInitialization.ExecutionRegion(0x80)));p.failure=new AssertionError("original video failure");assertSame(p.failure,assertThrows(AssertionError.class,()->CdProgramInitialization.enterLevelProgram(p)));assertEquals(List.of("scratch","version","marker","video"),p.calls);assertEquals(0x80,p.state.versionCache().value());
 }
 static final class Port implements CdProgramInitialization.StartPort {
  CdProgramInitialization.State state;final CdProgramInitialization.ExecutionEnvironment environment;final List<String> calls=new ArrayList<>();AssertionError failure;
  Port(CdProgramInitialization.State s,Optional<CdProgramInitialization.ExecutionRegion> e){state=s;environment=new CdProgramInitialization.ExecutionEnvironment(e);}
  public CdProgramInitialization.State initialization(){return state;}public CdProgramInitialization.ExecutionEnvironment executionEnvironment(){return environment;}
  public void clearScratch(){calls.add("scratch");}public void writeVersion(CdProgramInitialization.ExecutionRegion r){state=CdProgramInitialization.writeVersionCache(state,r);calls.add("version");}public void writeMarker(){state=CdProgramInitialization.writeInitMarker(state);calls.add("marker");}
  public void initializeVideo(){calls.add("video");if(failure!=null)throw failure;}public void initializeControllers(){state=CdProgramInitialization.initializeControllers(state);calls.add("controllers");}public void selectLevelMode(){calls.add("mode");}
 }
}
