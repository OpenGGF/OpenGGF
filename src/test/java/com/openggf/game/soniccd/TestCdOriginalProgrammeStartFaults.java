package com.openggf.game.soniccd;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Declared source controls. UNRUN. Aggregate observation is never replayed as calls. */
class TestCdOriginalProgrammeStartFaults {
 @Test void sharedStartPreservesExactCallbackFailureAndOrderedPrefix() {
  var failure=new AssertionError("actual callback");
  for(String cut:List.of("scratch","version","marker","video","controllers","mode")) {
   var calls=new ArrayList<String>();
   var port=new CdProgramInitialization.StartPort(){
    private void call(String name){calls.add(name);if(name.equals(cut))throw failure;}
    public CdProgramInitialization.State initialization(){return new CdProgramInitialization.State(CdProgramInitialization.KnownBit.CLEAR,CdProgramInitialization.MarkerKnowledge.UNKNOWN,new CdProgramInitialization.VersionCache(0,0));}
    public CdProgramInitialization.ExecutionEnvironment executionEnvironment(){return new CdProgramInitialization.ExecutionEnvironment(Optional.of(new CdProgramInitialization.ExecutionRegion(0)));}
    public void clearScratch(){call("scratch");}public void writeVersion(CdProgramInitialization.ExecutionRegion r){call("version");}
    public void writeMarker(){call("marker");}public void initializeVideo(){call("video");}
    public void initializeControllers(){call("controllers");}public void selectLevelMode(){call("mode");}
   };
   assertSame(failure,assertThrows(AssertionError.class,()->CdProgramInitialization.enterLevelProgram(port)));
   var order=List.of("scratch","version","marker","video","controllers","mode");
   assertEquals(order.subList(0,order.indexOf(cut)+1),calls);
  }
 }
 @Test void startGameInitNeverReadsRegionOrClearsScratch() {
  var calls=new ArrayList<String>();
  CdProgramInitialization.enterLevelProgram(new CdProgramInitialization.StartPort(){
   public CdProgramInitialization.State initialization(){return new CdProgramInitialization.State(CdProgramInitialization.KnownBit.SET,CdProgramInitialization.MarkerKnowledge.EQUALS_INIT,new CdProgramInitialization.VersionCache(0,0));}
   public CdProgramInitialization.ExecutionEnvironment executionEnvironment(){throw new AssertionError("unreached region");}
   public void clearScratch(){fail("unreached scratch");}public void writeVersion(CdProgramInitialization.ExecutionRegion r){fail("unreached version");}public void writeMarker(){fail("unreached marker");}
   public void initializeVideo(){calls.add("video");}public void initializeControllers(){calls.add("controllers");}public void selectLevelMode(){calls.add("mode");}
  });
  assertEquals(List.of("video","controllers","mode"),calls);
 }
}
