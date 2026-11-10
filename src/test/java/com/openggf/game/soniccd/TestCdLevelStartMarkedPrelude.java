package com.openggf.game.soniccd;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Source-written branch/fault requirements. These constructed callbacks grant no live authority. */
class TestCdLevelStartMarkedPrelude {
 private static final class P implements CdLevelStart.InitialFade,CdLevelStart.WhiteReturn {
  int special,period=0x80,flags=0xA0,restart=0x0107,act,direction=-1;boolean markerCleared;
  String fault="";final RuntimeException failure=new RuntimeException("original callback");final List<String> calls=new ArrayList<>();
  void call(String s){calls.add(s);if(s.equals(fault))throw failure;}
  public int specialStage(){call("special");return special;}
  public int rawPeriod(){call("period");return period;}
  public boolean setPaletteFadeFlag(){boolean old=(flags&1)!=0;flags|=1;call("bset");return old;}
  public void beginFade(boolean white){call(white?"fadeWhite":"fadeBlack");}
  public void skipped(boolean white){call(white?"skipWhite":"skipBlack");}
  public void clearPaletteFadeFlag(){flags&=~1;call("clearFlag");}
  public void clearDirection(){direction=0;call("direction");}
  public int restartWord(){call("restartRead");return restart;}
  public void clearRestartWord(){restart=0;call("restartClear");}
  public int actByte(){call("act");return act;}
  public void clearPeriodMarker(){period&=127;markerCleared=true;call("marker");}
  public void returned(){call("rts");}
 }
 @Test void markedOldClearBitSkipsWhiteAndKeepsEveryOtherFlag(){var p=new P();CdLevelStart.initialFade(p);assertEquals(List.of("special","period","bset","skipWhite"),p.calls);assertEquals(0xA1,p.flags);}
 @Test void markedOldSetBitChoosesWhiteWithoutEarlyFlagClear(){var p=new P();p.flags=0xA1;CdLevelStart.initialFade(p);assertEquals(List.of("special","period","bset","fadeWhite"),p.calls);assertEquals(0xA1,p.flags);}
 @Test void ordinaryOldClearBitSkipsBlack(){var p=new P();p.period=1;CdLevelStart.initialFade(p);assertEquals(List.of("special","period","bset","skipBlack"),p.calls);}
 @Test void ordinaryOldSetBitChoosesBlack(){var p=new P();p.period=1;p.flags=0xA1;CdLevelStart.initialFade(p);assertEquals(List.of("special","period","bset","fadeBlack"),p.calls);}
 @Test void specialStageGoesDirectlyWhiteWithoutPeriodReadOrBset(){var p=new P();p.special=1;p.fault="period";CdLevelStart.initialFade(p);assertEquals(List.of("special","fadeWhite"),p.calls);assertEquals(0xA0,p.flags);}
 @Test void specialStageReadFailureDoesNotReadOrStoreAnythingLater(){var p=new P();p.fault="special";assertSame(p.failure,assertThrows(RuntimeException.class,()->CdLevelStart.initialFade(p)));assertEquals(List.of("special"),p.calls);assertEquals(0xA0,p.flags);}
 @Test void periodReadFailurePrecedesThePaletteStore(){var p=new P();p.fault="period";assertSame(p.failure,assertThrows(RuntimeException.class,()->CdLevelStart.initialFade(p)));assertEquals(List.of("special","period"),p.calls);assertEquals(0xA0,p.flags);}
 @Test void landedBsetFailureRetainsThatPrefixAndDoesNotChooseFade(){var p=new P();p.fault="bset";assertSame(p.failure,assertThrows(RuntimeException.class,()->CdLevelStart.initialFade(p)));assertEquals(0xA1,p.flags);assertEquals(List.of("special","period","bset"),p.calls);}
 @Test void fadeCallbackFailureRetainsBsetAndItsOriginalThrowable(){var p=new P();p.flags=0xA1;p.fault="fadeWhite";assertSame(p.failure,assertThrows(RuntimeException.class,()->CdLevelStart.initialFade(p)));assertEquals(0xA1,p.flags);assertEquals(0,p.failure.getSuppressed().length);}
 @Test void skipCallbackFailureDoesNotClearItsNewFlag(){var p=new P();p.fault="skipWhite";assertSame(p.failure,assertThrows(RuntimeException.class,()->CdLevelStart.initialFade(p)));assertEquals(0xA1,p.flags);}
 @Test void completedWhiteClearsOnlyFlagThenDirectionThenWholeRestartBeforeRts(){var p=new P();p.flags=0xA1;assertTrue(CdLevelStart.afterWhite(true,p));assertEquals(List.of("clearFlag","direction","restartRead","restartClear","act","rts"),p.calls);assertEquals(0xA0,p.flags);assertEquals(0,p.direction);assertEquals(0,p.restart);assertEquals(0x80,p.period);}
 @Test void skippedWhiteDoesNotExecuteTheUnreachedFlagClear(){var p=new P();p.flags=0xA1;assertTrue(CdLevelStart.afterWhite(false,p));assertEquals(List.of("direction","restartRead","restartClear","act","rts"),p.calls);assertEquals(0xA1,p.flags);}
 @Test void zeroRestartContinuesNormalLoadWithoutActReadOrRts(){var p=new P();p.restart=0;p.fault="act";assertFalse(CdLevelStart.afterWhite(false,p));assertEquals(List.of("direction","restartRead"),p.calls);assertEquals(0x80,p.period);}
 @Test void actThreeClearsMarkerOnlyAfterWholeRestartAndBeforeRts(){var p=new P();p.act=2;p.period=0x82;assertTrue(CdLevelStart.afterWhite(false,p));assertEquals(List.of("direction","restartRead","restartClear","act","marker","rts"),p.calls);assertEquals(2,p.period);assertTrue(p.markerCleared);}
 @Test void otherActsDoNotClearTheirMarker(){for(int act:new int[]{0,1,3}){var p=new P();p.act=act;assertTrue(CdLevelStart.afterWhite(false,p));assertFalse(p.markerCleared);assertEquals(0x80,p.period);}}
 @Test void flagClearFaultPreventsDirectionAndRetainsItsLandedPrefix(){var p=new P();p.flags=0xA1;p.fault="clearFlag";assertSame(p.failure,assertThrows(RuntimeException.class,()->CdLevelStart.afterWhite(true,p)));assertEquals(List.of("clearFlag"),p.calls);assertEquals(0xA0,p.flags);assertEquals(-1,p.direction);assertEquals(0x0107,p.restart);}
 @Test void directionFaultRetainsFlagClearButDoesNotReadRestart(){var p=new P();p.flags=0xA1;p.fault="direction";assertSame(p.failure,assertThrows(RuntimeException.class,()->CdLevelStart.afterWhite(true,p)));assertEquals(List.of("clearFlag","direction"),p.calls);assertEquals(0,p.direction);assertEquals(0x0107,p.restart);}
 @Test void restartReadFaultRetainsDirectionAndDoesNotClearTheWord(){var p=new P();p.fault="restartRead";assertSame(p.failure,assertThrows(RuntimeException.class,()->CdLevelStart.afterWhite(false,p)));assertEquals(List.of("direction","restartRead"),p.calls);assertEquals(0,p.direction);assertEquals(0x0107,p.restart);}
 @Test void restartClearFaultRetainsTheWholeWordStoreWithoutReadingAct(){var p=new P();p.fault="restartClear";assertSame(p.failure,assertThrows(RuntimeException.class,()->CdLevelStart.afterWhite(false,p)));assertEquals(0,p.restart);assertEquals(List.of("direction","restartRead","restartClear"),p.calls);}
 @Test void actReadFaultCannotUndoDirectionAndRestartStores(){var p=new P();p.fault="act";assertSame(p.failure,assertThrows(RuntimeException.class,()->CdLevelStart.afterWhite(false,p)));assertEquals(0,p.restart);assertEquals(0,p.direction);assertEquals(0x80,p.period);assertFalse(p.calls.contains("rts"));}
 @Test void markerStoreFaultPrecedesRtsAndRetainsTheMarkerClear(){var p=new P();p.act=2;p.fault="marker";assertSame(p.failure,assertThrows(RuntimeException.class,()->CdLevelStart.afterWhite(false,p)));assertEquals(0,p.period);assertEquals(0,p.restart);assertFalse(p.calls.contains("rts"));}
 @Test void returnedCallbackFailureDoesNotUndoEarlierStores(){var p=new P();p.fault="rts";assertSame(p.failure,assertThrows(RuntimeException.class,()->CdLevelStart.afterWhite(false,p)));assertEquals(0,p.direction);assertEquals(0,p.restart);assertEquals(0x80,p.period);assertEquals(0,p.failure.getSuppressed().length);}
 @Test void nullPortsAreRejectedBeforeAnyCallback(){assertThrows(NullPointerException.class,()->CdLevelStart.initialFade(null));assertThrows(NullPointerException.class,()->CdLevelStart.afterWhite(true,null));}
}
