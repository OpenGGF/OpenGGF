package com.openggf.game.soniccd;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Shared semantic controls only. No native execution, destination handoff or capture claim. UNRUN. */
class TestCdRetainedProgrammeReturn {
 @Test void returnStopBlankHaveNativeOrderAndKnownSemanticByte(){
  var order=new ArrayList<String>();CdRetainedProgrammeReturn.enter(new CdRetainedProgrammeReturn.Port(){public void returnByte(int v){assertEquals(255,v);order.add("return");}public void stopFm(){order.add("fm");}public void blank(){order.add("blank");}});assertEquals(List.of("return","fm","blank"),order);
 }
 @Test void stopFailureRetainsReturnPrefixAndDoesNotBlankOrRetry(){
  var order=new ArrayList<String>();var original=new IllegalStateException("fm");assertSame(original,assertThrows(IllegalStateException.class,()->CdRetainedProgrammeReturn.enter(new CdRetainedProgrammeReturn.Port(){public void returnByte(int v){order.add("return");}public void stopFm(){order.add("fm");throw original;}public void blank(){order.add("blank");}})));assertEquals(List.of("return","fm"),order);assertEquals(0,original.getSuppressed().length);
 }
 @Test void runLevelReadsStopAtActualFirstGate(){
  var reads=new ArrayList<String>();assertEquals(CdIpxProgress.LevelReturn.GAME_OVER,CdIpxProgress.levelReturned(new CdIpxProgress.LevelReturnReads(){public int lives(){reads.add("lives");return 0;}public int rawPeriod(){fail("unreached");return 0;}public int specialStage(){fail("unreached");return 0;}}));assertEquals(List.of("lives","lives"),reads);
  assertEquals(CdIpxProgress.LevelReturn.TIME_WARP,CdIpxProgress.levelReturned(new CdIpxProgress.LevelReturnReads(){public int lives(){return 1;}public int rawPeriod(){return 0x81;}public int specialStage(){fail("unreached");return 0;}}));
 }
 @Test void runLevelUsesFullSpecialByteAndNoStageIndexSwitch(){
  assertEquals(CdIpxProgress.LevelReturn.SPECIAL_STAGE,decision(0x80));assertEquals(CdIpxProgress.LevelReturn.RETURN_TO_ACT_CALLER,decision(0));
 }
 @Test void levelOverReadsLivesAgainBeforeSpecialStage(){
  var order=new ArrayList<String>();int[] calls={0};
  assertEquals(CdIpxProgress.LevelReturn.GAME_OVER,CdIpxProgress.levelReturned(new CdIpxProgress.LevelReturnReads(){public int lives(){order.add("lives");return calls[0]++==0?3:0;}public int rawPeriod(){order.add("period");return 1;}public int specialStage(){fail("second lives zero branches before specialStage");return 0;}}));
  assertEquals(List.of("lives","period","lives"),order);
 }
 @Test void firstLivesZeroSkipsPeriodButStillReachesTheSecondRead(){
  var order=new ArrayList<String>();int[] calls={0};
  assertEquals(CdIpxProgress.LevelReturn.SPECIAL_STAGE,CdIpxProgress.levelReturned(new CdIpxProgress.LevelReturnReads(){public int lives(){order.add("lives");return calls[0]++==0?0:3;}public int rawPeriod(){fail("first zero skips period");return 0;}public int specialStage(){order.add("special");return 0x80;}}));
  assertEquals(List.of("lives","lives","special"),order);
 }
 @Test void secondLivesReadFailureStopsBeforeSpecialStageWithoutReplacingTheFault(){
  var order=new ArrayList<String>();var original=new IllegalStateException("second actual lives read");int[] calls={0};
  assertSame(original,assertThrows(IllegalStateException.class,()->CdIpxProgress.levelReturned(new CdIpxProgress.LevelReturnReads(){public int lives(){order.add("lives");if(calls[0]++!=0)throw original;return 3;}public int rawPeriod(){order.add("period");return 1;}public int specialStage(){fail("after fault");return 0;}})));
  assertEquals(List.of("lives","period","lives"),order);
 }
 private static CdIpxProgress.LevelReturn decision(int special){return CdIpxProgress.levelReturned(new CdIpxProgress.LevelReturnReads(){public int lives(){return 3;}public int rawPeriod(){return 1;}public int specialStage(){return special;}});}
}
