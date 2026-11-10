package com.openggf.game.soniccd.player;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.lang.reflect.InvocationTargetException;
import static org.junit.jupiter.api.Assertions.*;

/** Tests FIRST, all Java UNRUN. Scalar/source-owner controls, never TT Frame admission or clocks.
 * Reflection below exercises the actual existing pure Frame Finish value function, not a new Frame. */
class TestCdBoredTimers {
    @Test void literalActiveWordCasesKeepZeroUnsignedLimitsAndWrap(){
        int[][] vectors={{0,0},{1,2},{0x7FFF,0x8000},{0x8000,0x8001},{0xFFFE,0xFFFF},{0xFFFF,0}};
        for(var v:vectors)assertEquals(v[1],CdBoredTimers.advanceActiveWord(v[0]));
    }
    @Test void everyLegacyPrimaryWordKeepsTheDisplacedFramesExactValueFunction(){
        for(int n=0;n<=0xFFFF;n++)assertEquals(n==0?0:(n+1)&0xFFFF,CdBoredTimers.advanceActiveWord(n));
    }
    @Test void wordOverflowAndNegativeValuesNeverSilentlyTruncate(){
        for(int n:new int[]{-1,0x10000,Integer.MIN_VALUE,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->CdBoredTimers.advanceActiveWord(n));
    }
    @Test void zeroPairReadsBothWordsButWritesNeither(){
        var h=new Pair(0,0);CdBoredTimers.updatePair(h);assertEquals(List.of("primary-read","secondary-read"),h.trace);assertEquals(0,h.primary);assertEquals(0,h.secondary);
    }
    @Test void eachActiveTimerWritesOnlyItsOwnWord(){
        for(int[] v:new int[][]{{1,0},{0,0xFFFF},{0xFFFF,0x7FFF}}){
            var h=new Pair(v[0],v[1]);CdBoredTimers.updatePair(h);
            assertEquals(v[0]==0?0:(v[0]+1)&0xFFFF,h.primary);assertEquals(v[1]==0?0:(v[1]+1)&0xFFFF,h.secondary);
            assertEquals(v[0]!=0,h.trace.contains("primary-store"));assertEquals(v[1]!=0,h.trace.contains("secondary-store"));
        }
    }
    @Test void completedPrimaryStoreAlwaysPrecedesTheSecondaryRead(){
        var h=new Pair(0xFFFF,1);CdBoredTimers.updatePair(h);assertEquals(List.of("primary-read","primary-store","secondary-read","secondary-store"),h.trace);
    }
    @Test void secondaryWordIsReadLateAfterTheActualPrimaryStore(){
        var h=new Pair(1,9);h.afterPrimary=()->h.secondary=0xFFFF;CdBoredTimers.updatePair(h);assertEquals(2,h.primary);assertEquals(0,h.secondary);
    }
    @Test void primaryReadFaultSkipsEveryStoreAndSecondaryRead(){
        var h=new Pair(1,2);var original=new AssertionError("primary read");h.failAt="primary-read";h.failure=original;
        assertSame(original,assertThrows(AssertionError.class,()->CdBoredTimers.updatePair(h)));assertEquals(List.of("primary-read"),h.trace);assertEquals(1,h.primary);assertEquals(2,h.secondary);
    }
    @Test void primaryStoreFaultKeepsItsReachedWriteAndSkipsSecondary(){
        var h=new Pair(1,2);var original=new IllegalStateException("primary post-store");h.failAfterPrimary=original;
        assertSame(original,assertThrows(IllegalStateException.class,()->CdBoredTimers.updatePair(h)));assertEquals(2,h.primary);assertEquals(2,h.secondary);assertEquals(List.of("primary-read","primary-store"),h.trace);
    }
    @Test void originalSecondaryReadFaultRetainsCompletedPrimaryAndPreventsLaterFinishCalls(){
        for(RuntimeException original:List.of(new IllegalArgumentException("read secondary"),new CdBoredTimers.UnownedSecondaryTimer())){
            var h=new Pair(0xFFFF,7);h.failAt="secondary-read";h.failure=original;
            assertSame(original,assertThrows(RuntimeException.class,()->finish(h)));assertEquals(0,h.primary);assertEquals(7,h.secondary);
            assertEquals(List.of("primary-read","primary-store","secondary-read"),h.trace);assertFalse(h.trace.contains("warp"));assertFalse(h.trace.contains("count"));
        }
    }
    @Test void secondaryErrorAlsoSurvivesByIdentityWithoutRollbackOrSyntheticSuppression(){
        var h=new Pair(1,2);var original=new AssertionError("secondary original Error");h.failAt="secondary-read";h.failure=original;
        assertSame(original,assertThrows(AssertionError.class,()->finish(h)));assertEquals(2,h.primary);assertEquals(0,original.getSuppressed().length);assertFalse(h.trace.contains("warp"));
    }
    @Test void secondaryStoreFailureRetainsBothAlreadyReachedWordWrites(){
        var h=new Pair(1,0xFFFF);var original=new AssertionError("secondary post-store");h.failAfterSecondary=original;
        assertSame(original,assertThrows(AssertionError.class,()->finish(h)));assertEquals(2,h.primary);assertEquals(0,h.secondary);assertFalse(h.trace.contains("warp"));assertFalse(h.trace.contains("count"));
    }
    @Test void invalidSecondaryWordStopsAfterPrimaryAndBeforeSecondaryWrite(){
        var h=new Pair(1,0x10000);assertThrows(IllegalArgumentException.class,()->CdBoredTimers.updatePair(h));assertEquals(2,h.primary);assertEquals(List.of("primary-read","primary-store","secondary-read"),h.trace);
    }
    @Test void invalidPrimaryWordSkipsSecondaryEntirely(){
        var h=new Pair(-1,2);assertThrows(IllegalArgumentException.class,()->CdBoredTimers.updatePair(h));assertEquals(List.of("primary-read"),h.trace);
    }
    @Test void missingPairIsRejectedWithoutInventingAZeroSecondary(){
        assertThrows(NullPointerException.class,()->CdBoredTimers.updatePair(null));
        var h=new Pair(0,7);var original=new CdBoredTimers.UnownedSecondaryTimer();h.failAt="secondary-read";h.failure=original;
        assertSame(original,assertThrows(CdBoredTimers.UnownedSecondaryTimer.class,()->CdBoredTimers.updatePair(h)));assertEquals(7,h.secondary);assertEquals(List.of("primary-read","secondary-read"),h.trace);
    }
    @Test void actualFramePrimaryFinishKeepsPausedChargeAndHeldInputSemantics()throws Throwable{
        for(int held:new int[]{0,1,0x80,0xFF})for(boolean paused:new boolean[]{false,true})for(int charge:new int[]{0,1,0xFF})for(int timer:new int[]{0,1,0xFFFF}){
            var old=environment(timer,0xFFFF,held);var actual=frameFinish(old,paused,charge);
            if(paused){assertSame(old,actual);continue;}
            assertNotSame(old,actual);assertEquals(timer==0?0:(timer+1)&0xFFFF,actual.movement().globals().boredTimer());
            assertEquals(charge==0?0:0xFFFF,actual.globals().timeWarpTimer());assertSame(old.controls(),actual.controls());assertSame(old.dynamicSlots(),actual.dynamicSlots());
            assertSame(old.movement().physics(),actual.movement().physics());assertSame(old.movement().bounds(),actual.movement().bounds());assertSame(old.movement().gates(),actual.movement().gates());
            var a=actual.movement().globals();var b=old.movement().globals();assertEquals(List.of(b.lookMode(),b.cameraYCentre(),b.primaryAngle(),b.secondaryAngle(),b.scoreChain(),b.debugAngle(),b.debugAngleShift(),b.debugQuadrant(),b.debugFloorDistance()),List.of(a.lookMode(),a.cameraYCentre(),a.primaryAngle(),a.secondaryAngle(),a.scoreChain(),a.debugAngle(),a.debugAngleShift(),a.debugQuadrant(),a.debugFloorDistance()));
        }
    }
    private static void finish(Pair h){CdBoredTimers.updatePair(h);h.trace.add("warp");h.trace.add("count");}
    private static CdPlayerBody.Environment frameFinish(CdPlayerBody.Environment e,boolean paused,int charge)throws Throwable{
        var m=CdPlayerFrameRuntime.class.getDeclaredMethod("finishTimers",CdPlayerBody.Environment.class,boolean.class,int.class);m.setAccessible(true);
        try{return(CdPlayerBody.Environment)m.invoke(null,e,paused,charge);}catch(InvocationTargetException original){throw original.getCause();}
    }
    private static CdPlayerBody.Environment environment(int bored,int warp,int held){
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(100,6,8),new CdPlayerMovement.Bounds(1,2,3,4),new CdPlayerMovement.Gates(0,1,0,0,0,0,0),new CdPlayerMovement.Globals(1,2,3,4,bored,6,7,8,9,10)),new CdPlayerBody.Controls(held,0,held,0,0),new CdPlayerBody.Globals(12,13,0,0,0,warp,0,0),CdPlayerBody.DynamicSlots.EMPTY_DEVELOPMENT_96);
    }
    private static final class Pair implements CdBoredTimers.TimerPair {
        int primary,secondary;final List<String> trace=new ArrayList<>();String failAt;Throwable failure,failAfterPrimary,failAfterSecondary;Runnable afterPrimary=()->{};
        Pair(int primary,int secondary){this.primary=primary;this.secondary=secondary;}
        private void read(String name){trace.add(name);if(name.equals(failAt))raise(failure);}
        public int readPrimary(){read("primary-read");return primary;}
        public void writePrimary(int value){trace.add("primary-store");primary=value;afterPrimary.run();if(failAfterPrimary!=null)raise(failAfterPrimary);}
        public int readSecondary(){read("secondary-read");return secondary;}
        public void writeSecondary(int value){trace.add("secondary-store");secondary=value;if(failAfterSecondary!=null)raise(failAfterSecondary);}
        private static void raise(Throwable t){if(t instanceof Error e)throw e;if(t instanceof RuntimeException e)throw e;throw new AssertionError(t);}
    }
}
