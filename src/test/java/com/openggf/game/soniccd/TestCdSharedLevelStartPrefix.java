package com.openggf.game.soniccd;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** SOURCE / UNRUN. Semantic-port order only; genuine ingress is tested separately. */
class TestCdSharedLevelStartPrefix {
 @Test void nonAllStonesDoesNotReadTimeAttackBeforeEntryStores(){var p=new Port();p.stones=0;CdLevelStart.enter(p);assertEquals(List.of("demo","stones","entry","plc","prefix","saved","suffix","mode","clear"),p.calls);}
 @Test void allStonesInTimeAttackDoesNotForceGoodFuture(){var p=new Port();p.stones=127;p.attack=1;CdLevelStart.enter(p);assertEquals(List.of("demo","stones","attack","entry","plc","prefix","saved","suffix","mode","clear"),p.calls);}
 @Test void allStonesOutsideTimeAttackForcesGoodFutureBeforeEntryStores(){var p=new Port();p.stones=127;CdLevelStart.enter(p);assertEquals(List.of("demo","stones","attack","future","entry","plc","prefix","saved","suffix","mode","clear"),p.calls);}
 @Test void alreadyLoadedPreservesCheckpointAndFreshResetFields(){var p=new Port();p.loaded=true;CdLevelStart.enter(p);assertEquals(List.of("demo","stones","entry","plc","mode","clear"),p.calls);}
 @Test void failedSavedResetKeepsTheReachedPrefixAndDoesNotRunSuffix(){var p=new Port();var fault=new AssertionError("original saved owner");p.failure=fault;assertSame(fault,assertThrows(AssertionError.class,()->CdLevelStart.enter(p)));assertEquals(List.of("demo","stones","entry","plc","prefix","saved"),p.calls);}
 private static final class Port implements CdLevelStart.Port {
  final List<String> calls=new ArrayList<>();int stones,attack;boolean loaded;AssertionError failure;
  public void clearDemo(){calls.add("demo");}public int timeStones(){calls.add("stones");return stones;}
  public int timeAttack(){calls.add("attack");return attack;}public void forceGoodFuture(){calls.add("future");}
  public void entryStores(){calls.add("entry");}public boolean setPlcLoaded(){calls.add("plc");return loaded;}
  public void freshPrefix(){calls.add("prefix");}public void resetSavedFlags(){calls.add("saved");if(failure!=null)throw failure;}
  public void freshSuffix(){calls.add("suffix");}public void markInitializing(){calls.add("mode");}public void clearPlcs(){calls.add("clear");}
 }
}
