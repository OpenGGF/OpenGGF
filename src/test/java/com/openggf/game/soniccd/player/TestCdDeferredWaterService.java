package com.openggf.game.soniccd.player;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Tests authored BEFORE production; all Java UNRUN. Explicit finite owner controls, not a real
 * TT Frame, source admission, controller trace, scheduler, palette renderer or beam simulation. */
class TestCdDeferredWaterService {
    @Test void lowSplitReturnsThroughFinishBeforeWaterAndAllThreeDeferredOwners(){
        var h=new Harness(0x5F);h.service.runStageTail();
        assertEquals(List.of("split","pending=1","finish","arm","arm=0","split"),h.trace.subList(0,6));
        for(int i=0;i<32;i++)assertEquals(List.of("read:"+i,"select:"+i),h.trace.subList(6+2*i,8+2*i));
        assertEquals(List.of("pending","pending=0","draw","plc","hud"),h.trace.subList(70,75));
        assertEquals(0,h.arm);assertEquals(0,h.pending);h.service.requireClosedCut();
    }
    @Test void highSplitDrawsBeforeFinishAndDoesNotReplayAfterWater(){
        var h=new Harness(0x60);h.service.runStageTail();
        assertEquals(List.of("split","draw","plc","hud","finish","arm","arm=0","split"),h.trace.subList(0,8));
        assertEquals(1,Collections.frequency(h.trace,"draw"));assertEquals(1,Collections.frequency(h.trace,"finish"));h.service.requireClosedCut();
    }
    @Test void everyUnsignedSplitByteUsesTheNative60ThresholdAndDfSuppression(){
        for(int split=0;split<256;split++){
            var h=new Harness(split);h.service.runStageTail();
            assertEquals(split<0x60,h.trace.contains("pending=1"),"unsigned split "+split);
            assertEquals(split==0xDF?0:32,h.selectedCount);assertEquals(1,Collections.frequency(h.trace,"draw"));
            assertEquals(split<0x60,h.trace.indexOf("finish")<h.trace.indexOf("draw"));h.service.requireClosedCut();
        }
    }
    @Test void waterPaletteIsReadCurrentAfterFinishNotCapturedAtStageEntry(){
        var h=new Harness(0x30);h.onFinish=()->{for(int n=0;n<32;n++)h.water[n]=0x12340000+n;};h.service.runStageTail();
        assertArrayEquals(h.water,h.selected);
    }
    @Test void eachPaletteLongIsReadAtItsReachedSiteBeforeItsOwnStore(){
        var h=new Harness(0x30);h.afterSelect=offset->{if(offset==0)h.water[1]=0xDEADBEEF;};h.service.runStageTail();
        assertEquals(0xDEADBEEF,h.selected[1]);assertEquals("read:1",h.trace.get(h.trace.indexOf("select:0")+1));
    }
    @Test void everyPaletteReadFailureRetainsOnlyCompletedLongStoresAndOriginalPending(){
        for(int fail=0;fail<32;fail++){
            var h=new Harness(0x30);var original=new AssertionError("water long "+fail);h.failAt="read:"+fail;h.failure=original;
            assertSame(original,assertThrows(AssertionError.class,h.service::runStageTail));assertEquals(fail,h.selectedCount);
            for(int n=0;n<32;n++)assertEquals(n<fail?h.water[n]:0x55555555,h.selected[n]);
            assertEquals(0,h.arm);assertEquals(1,h.pending);assertFalse(h.trace.contains("pending=0"));assertFalse(h.trace.contains("draw"));
            assertThrows(UnsupportedOperationException.class,h.service::requireClosedCut);assertThrows(IllegalStateException.class,h.service::runStageTail);
        }
    }
    @Test void selectedPaletteStoreFailureRetainsPreviousLongsAndDoesNotClearPending(){
        var h=new Harness(0x30);var original=new IllegalArgumentException("select long");h.failAt="select:7";h.failure=original;
        assertSame(original,assertThrows(IllegalArgumentException.class,h.service::runStageTail));assertEquals(7,h.selectedCount);assertEquals(1,h.pending);assertEquals(0x55555555,h.selected[7]);assertFalse(h.trace.contains("draw"));
    }
    @Test void finishFailureKeepsNativePendingStoreAndSkipsTheWaterBoundary(){
        var h=new Harness(0x30);var original=new IllegalStateException("original Finish");h.failAt="finish";h.failure=original;
        assertSame(original,assertThrows(IllegalStateException.class,h.service::runStageTail));assertEquals(1,h.pending);assertEquals(1,h.arm);assertFalse(h.trace.contains("arm"));assertThrows(UnsupportedOperationException.class,h.service::requireClosedCut);
    }
    @Test void unboundSecondBoredTimerStopsInsideFinishBeforeWaterOrClockInvention(){
        var h=new Harness(0x30);var prefix=new ArrayList<String>();
        h.onFinish=()->{prefix.add("completed primary bored store");throw new CdDeferredWaterService.Unowned(CdDeferredWaterService.Boundary.FINISH_SECOND_BORED_TIMER_OWNER);};
        var fault=assertThrows(CdDeferredWaterService.Unowned.class,h.service::runStageTail);
        assertEquals(CdDeferredWaterService.Boundary.FINISH_SECOND_BORED_TIMER_OWNER,fault.boundary());assertEquals(List.of("completed primary bored store"),prefix);assertEquals(1,h.pending);assertEquals(1,h.arm);assertFalse(h.trace.contains("arm"));
    }
    @Test void directDrawFailureStopsBeforeFinishAndPreservesTheOriginalException(){
        var h=new Harness(0x60);var original=new AssertionError("original direct DrawStage");h.failAt="draw";h.failure=original;
        assertSame(original,assertThrows(AssertionError.class,h.service::runStageTail));assertFalse(h.trace.contains("finish"));assertFalse(h.trace.contains("arm"));
    }
    @Test void deferredTailFailuresKeepPendingClearAndCompletedEarlierCalls(){
        for(String fail:List.of("draw","plc","hud")){
            var h=new Harness(0x30);var original=new AssertionError(fail);h.failAt=fail;h.failure=original;
            assertSame(original,assertThrows(AssertionError.class,h.service::runStageTail));assertEquals(0,h.pending);assertEquals(32,h.selectedCount);
            assertTrue(h.trace.indexOf("pending=0")<h.trace.indexOf(fail));assertEquals(1,Collections.frequency(h.trace,"draw"));
            assertEquals(fail.equals("draw")?0:1,Collections.frequency(h.trace,"plc"));assertThrows(IllegalStateException.class,h.service::runStageTail);
        }
    }
    @Test void zeroArmHasNoSplitPaletteOrPendingReadAndCannotCompleteOriginalLowContinuation(){
        var h=new Harness(0x30);h.arm=0;
        var fault=assertThrows(CdDeferredWaterService.Unowned.class,h.service::runStageTail);
        assertEquals(CdDeferredWaterService.Boundary.UNFINISHED_WATER_SERVICE,fault.boundary());
        assertEquals(List.of("split","pending=1","finish","arm"),h.trace);assertEquals(1,h.pending);assertThrows(UnsupportedOperationException.class,h.service::requireClosedCut);
    }
    @Test void dfAfterFinishClearsArmButPreservesPendingWithoutReadingThePalette(){
        var h=new Harness(0x30);h.onFinish=()->h.split=0xDF;
        assertThrows(CdDeferredWaterService.Unowned.class,h.service::runStageTail);
        assertEquals(List.of("split","pending=1","finish","arm","arm=0","split"),h.trace);assertEquals(1,h.pending);assertEquals(0,h.arm);assertEquals(0,h.selectedCount);
    }
    @Test void highSplitZeroArmAndDfRetainDirtyPendingWithoutInventingAnInvocation(){
        for(boolean df:new boolean[]{false,true}){
            var h=new Harness(df?0xDF:0x60);h.pending=0xA5;if(!df)h.arm=0;h.service.runStageTail();
            assertEquals(0xA5,h.pending);assertFalse(h.trace.contains("pending"));assertEquals(1,Collections.frequency(h.trace,"draw"));h.service.requireClosedCut();
        }
    }
    @Test void dirtyPendingByteCannotAuthorizeASecondDrawAfterADirectStageTail(){
        var h=new Harness(0x60);h.pending=0xA5;
        var fault=assertThrows(CdDeferredWaterService.Unowned.class,h.service::runStageTail);
        assertEquals(CdDeferredWaterService.Boundary.ORIGINAL_DEFERRED_CALLER,fault.boundary());assertEquals(32,h.selectedCount);assertEquals(0xA5,h.pending);assertFalse(h.trace.contains("pending=0"));assertEquals(1,Collections.frequency(h.trace,"draw"));
    }
    @Test void clearingTheRawByteDoesNotCompleteTheOriginalDeferredService(){
        var h=new Harness(0x30);h.onFinish=()->h.pending=0;
        var fault=assertThrows(CdDeferredWaterService.Unowned.class,h.service::runStageTail);
        assertEquals(CdDeferredWaterService.Boundary.LOST_NATIVE_PENDING,fault.boundary());assertEquals(32,h.selectedCount);assertFalse(h.trace.contains("draw"));assertThrows(UnsupportedOperationException.class,h.service::requireClosedCut);
    }
    @Test void captureAndRecursiveEntryRejectionInsideACallbackDoNotPoisonTheHealthyService(){
        var h=new Harness(0x30);h.onFinish=()->{
            assertThrows(UnsupportedOperationException.class,h.service::requireClosedCut);
            assertThrows(IllegalStateException.class,h.service::runStageTail);
        };h.service.runStageTail();h.service.requireClosedCut();assertEquals(1,Collections.frequency(h.trace,"draw"));
    }
    @Test void privatelyIssuedCallRejectsForeignOwnerThreadAndRetainedUse()throws Exception{
        var h=new Harness(0x30);var other=new Harness(0x60);var retained=new AtomicReference<CdDeferredWaterService.Call>();
        h.onFinish=()->{
            var call=h.last;retained.set(call);assertTrue(call.isCurrent());assertThrows(IllegalStateException.class,()->call.requireCurrent(other));
            var fault=new AtomicReference<Throwable>();var t=new Thread(()->{try{call.requireCurrent(h);}catch(Throwable e){fault.set(e);}});t.start();try{t.join(5000);}catch(InterruptedException e){throw new AssertionError(e);}assertFalse(t.isAlive());assertInstanceOf(IllegalStateException.class,fault.get());
        };h.service.runStageTail();assertFalse(retained.get().isCurrent());assertThrows(IllegalStateException.class,()->retained.get().requireCurrent(h));
        assertTrue(Arrays.stream(CdDeferredWaterService.Call.class.getDeclaredConstructors()).allMatch(c->Modifier.isPrivate(c.getModifiers())));
    }
    @Test void originalCallbackFaultSurvivesDistinctGuardFailureWithoutRollback(){
        var h=new Harness(0x30);var original=new AssertionError("original first-long source read");var guard=new IllegalArgumentException("full Frame changed");h.failAt="read:1";h.failure=original;h.guardAfterFailure=guard;
        assertSame(original,assertThrows(AssertionError.class,h.service::runStageTail));assertEquals(1,h.selectedCount);assertEquals(1,h.pending);assertTrue(Arrays.asList(original.getSuppressed()).contains(guard));
    }
    @Test void identicalGuardFailureIsNeverAddedAsItsOwnSuppressedThrowable(){
        var h=new Harness(0x30);var original=new AssertionError("same Throwable");h.failAt="read:0";h.failure=original;h.guardAfterFailure=original;
        assertSame(original,assertThrows(AssertionError.class,h.service::runStageTail));assertEquals(0,original.getSuppressed().length);
    }
    @Test void absentActualFrameAdapterFailsTypedBeforeAnyNativeReadOrWrite(){
        var h=new Harness(0x30);h.unbound=true;
        var fault=assertThrows(CdDeferredWaterService.Unowned.class,h.service::runStageTail);assertEquals(CdDeferredWaterService.Boundary.FRAME_SERVICE_OWNER,fault.boundary());assertTrue(h.trace.isEmpty());assertEquals(1,h.arm);
    }
    private static final class Harness implements CdDeferredWaterService.Owners {
        final CdDeferredWaterService service=new CdDeferredWaterService(this);
        final List<String> trace=new ArrayList<>();final int[] water=new int[32],selected=new int[32];
        int split,arm=1,pending,selectedCount;boolean unbound,failedCallback;String failAt;Throwable failure,guardAfterFailure;
        Runnable onFinish=()->{};java.util.function.IntConsumer afterSelect=offset->{};CdDeferredWaterService.Call last;
        Harness(int split){this.split=split;for(int n=0;n<32;n++)water[n]=0xA0000000+n;Arrays.fill(selected,0x55555555);}
        public void requireCurrent(CdDeferredWaterService.Call c){c.requireCurrent(this);last=c;if(unbound)throw new CdDeferredWaterService.Unowned(CdDeferredWaterService.Boundary.FRAME_SERVICE_OWNER);if(failedCallback&&guardAfterFailure!=null)raise(guardAfterFailure);}
        private void hit(String event){if(event.equals(failAt)){trace.add(event);failedCallback=true;raise(failure);}trace.add(event);}
        public int splitByte(CdDeferredWaterService.Call c){hit("split");return split;}
        public int armWord(CdDeferredWaterService.Call c){hit("arm");return arm;}
        public int deferredByte(CdDeferredWaterService.Call c){hit("pending");return pending;}
        public void clearArm(CdDeferredWaterService.Call c){hit("arm=0");arm=0;}
        public void setDeferred(CdDeferredWaterService.Call c,int value){hit("pending="+value);pending=value;}
        public int waterPaletteLong(CdDeferredWaterService.Call c,int offset){hit("read:"+(offset/4));return water[offset/4];}
        public void selectPaletteLong(CdDeferredWaterService.Call c,int offset,int value){hit("select:"+(offset/4));selected[offset/4]=value;selectedCount++;afterSelect.accept(offset);}
        public void drawStage(CdDeferredWaterService.Call c){hit("draw");}
        public void slowPlc(CdDeferredWaterService.Call c){hit("plc");}
        public void updateHud(CdDeferredWaterService.Call c){hit("hud");}
        public void finishVBlank(CdDeferredWaterService.Call c){hit("finish");onFinish.run();}
        private static void raise(Throwable t){if(t instanceof Error e)throw e;if(t instanceof RuntimeException e)throw e;throw new AssertionError("Explicit runtime failure required",t);}
    }
}
