package com.openggf.game.soniccd.objects;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;

/** SOURCE ONLY / UNRUN. Constructed component vectors borrow the existing literal native
 * fixtures; they do not create World/Frame admission or manufacture an original receipt. */
class TestCdTimePostOriginalOperations {
    static final class Observe implements CdTimePost.NativeObserver {
        CdTimePost body;final List<CdTimePost.OriginalCall> calls=new ArrayList<>();
        final List<String> order=new ArrayList<>();Runnable before=()->{},after=()->{};
        public void before(CdTimePost.OriginalCall c){body.requireCurrent(c);calls.add(c);order.add(c.site().name());before.run();}
        public void after(CdTimePost.OriginalCall c){body.requireCurrent(c);c.verifyNativePrefix();after.run();}
        public void failed(CdTimePost.OriginalCall c,Throwable original){body.requireCurrent(c);c.verifyNativePrefix();}
    }
    private static CdTimePost bind(TestCdPastTimePost.Harness h,Observe o){
        var body=CdTimePost.withOriginalOperations(h.runtime,TestCdMonitor.assets(),h::direction,h::voice,h::draw,o);o.body=body;return body;
    }
    @Test void rawAttackDeletesBeforeEveryOtherReadAndDirtyRoutineLookup(){
        for(int attack:new int[]{1,128,255})for(int subtype:new int[]{8,9,10,255}){
            var h=new TestCdPastTimePost.Harness();h.seed(255,subtype);h.attack=attack;var o=new Observe();var b=bind(h,o);var saved=h.runtime.capture().savedFlags().copy();
            b.runWithReads(TestCdPastTimePost.WHOLE,h);assertEquals(List.of("ATTACK"),h.reads);assertEquals(List.of("TIME_ATTACK_READ","DELETE"),o.order);assertArrayEquals(new byte[64],h.slot().copyBytes());assertArrayEquals(saved,h.runtime.capture().savedFlags().copy());
        }
    }
    @Test void originalFutureDirectionThenOneSoundAndDrawHasLiteralSourceOrder(){
        var h=new TestCdPastTimePost.Harness();h.seed(2,9);h.edit(h.slot().moveByte(COLLISION_STATUS,255));var o=new Observe();var b=bind(h,o);var before=h.runtime.capture();
        b.runWithReads(TestCdPastTimePost.WHOLE,h);assertEquals(List.of("DIR:0>255","DIR:255>1","VOICE:118","DRAW"),h.order);assertEquals(1,h.direction);assertEquals(1,h.runtime.savedFlag(7,1));assertEquals(4,h.slot().routine());assertEquals(60,h.slot().unsignedByte(TIME_POST_SPIN));
        assertTrue(o.order.indexOf("DIRECTION_STORE")<o.order.indexOf("SUB_CPU"));assertTrue(o.order.indexOf("SUB_CPU")<o.order.indexOf("DRAW"));assertEquals(before.status(),h.runtime.status());TestCdPastMonitor.assertOtherSlots(before.pool(),h.pool(),Set.of(TestCdPastTimePost.WHOLE));
    }
    @Test void originalInitPreservesDirtyBytesAndUnknownRoutineDoesNotWrite(){
        for(int routine:new int[]{1,3,5,7,8,128,255}){
            var h=new TestCdPastTimePost.Harness();h.seed(routine,9);var before=h.runtime.capture();var o=new Observe();var b=bind(h,o);
            var e=assertThrows(CdTimePost.UnsupportedTimePost.class,()->b.runWithReads(TestCdPastTimePost.WHOLE,h));assertEquals(CdTimePost.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary());assertEquals(routine,e.value());assertEquals(before.pool().slots(),h.pool().capture().slots());assertArrayEquals(before.savedFlags().copy(),h.runtime.capture().savedFlags().copy());assertEquals(List.of("TIME_ATTACK_READ"),o.order);
        }
        var h=new TestCdPastTimePost.Harness();h.seed(0,9);var before=h.slot();var o=new Observe();var b=bind(h,o);b.runWithReads(TestCdPastTimePost.WHOLE,h);
        var expected=before.addByte(ROUTINE,2).moveByte(Y_RADIUS,32).moveByte(X_RADIUS,14).moveLong(CdObjectState.LongField.MAPPINGS,TestCdMonitor.MAP).moveWord(CdObjectState.WordField.TILE,0x5A8).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,3).moveByte(WIDTH,15).moveByte(ANIMATION,9).moveByte(MAP_FRAME,12).moveByte(COLLISION_TYPE,0xDF);
        assertArrayEquals(expected.copyBytes(),h.slot().copyBytes());
    }
    @Test void conditionalLayerAndRawEraUseExistingNativeLazyReads(){
        var h=new TestCdPastTimePost.Harness();h.seed(2,9);h.edit(h.slot().moveByte(COLLISION_STATUS,1));h.zone=6;var o=new Observe();var b=bind(h,o);
        assertEquals(CdTimePost.UnsupportedBoundary.MMZ_LAYER_READ,assertThrows(CdTimePost.UnsupportedTimePost.class,()->b.runWithReads(TestCdPastTimePost.WHOLE,h)).boundary());assertEquals(0,h.slot().unsignedByte(COLLISION_STATUS));assertTrue(h.order.isEmpty());assertEquals(List.of("ATTACK","ZONE","LAYER"),h.reads);
        // period7 is a literal linear saved-byte alias, not period1 or a normalized era.
        var k=new TestCdPastTimePost.Harness();k.seed(0,9);k.time=7;k.runtime.bsetSavedFlag(9,1,CdObjectPlacementRuntime.SavedBit.LOADED);var q=new Observe();bind(k,q).runWithReads(TestCdPastTimePost.WHOLE,k);assertEquals(0,k.runtime.savedFlag(9,1)&128);assertFalse(k.reads.contains("DIRECTION"));
        for(int[] vector:new int[][]{{0x81,255,2},{0x81,128,2},{0x81,1,0},{0x81,0,1}}){
            var w=new TestCdPastTimePost.Harness();w.seed(0,9);w.time=vector[0];w.direction=vector[1];w.runtime.bsetSavedFlag(7,vector[2],CdObjectPlacementRuntime.SavedBit.LOADED);bind(w,new Observe()).runWithReads(TestCdPastTimePost.WHOLE,w);assertEquals(0,w.runtime.savedFlag(7,vector[2])&128);
        }
    }
    @Test void expiredCallsAndEqualDirectionClonesNeverAcquireOriginalAuthority(){
        var h=new TestCdPastTimePost.Harness();h.seed(2,9);h.edit(h.slot().moveByte(COLLISION_STATUS,1));var o=new Observe();var b=bind(h,o);
        o.before=()->{var c=o.calls.getLast();if(c.site()==CdTimePost.Site.DIRECTION_STORE){var d=(CdTimePost.DirectionCall)c.argument();var clone=new CdTimePost.DirectionCall(d.identity(),d.expectedRawDirection(),d.rawDirection());assertThrows(IllegalArgumentException.class,()->b.requireCurrentArgument(c.site(),clone));}};
        b.runWithReads(TestCdPastTimePost.WHOLE,h);for(var c:o.calls)assertThrows(IllegalStateException.class,()->b.requireCurrent(c));
    }
    @Test void wrongThreadAndReentryCannotInvokeOrBorrowCurrentOriginalCall()throws Exception{
        var h=new TestCdPastTimePost.Harness();h.seed(2,9);var o=new Observe();var b=bind(h,o);
        o.before=()->{var c=o.calls.getLast();assertThrows(IllegalStateException.class,()->b.runWithReads(TestCdPastTimePost.WHOLE,h));var failure=new AtomicReference<Throwable>();var t=new Thread(()->{try{b.requireCurrent(c);}catch(Throwable e){failure.set(e);}});t.start();try{t.join(5000);}catch(InterruptedException e){throw new AssertionError(e);}assertFalse(t.isAlive());assertInstanceOf(IllegalStateException.class,failure.get());};
        b.runWithReads(TestCdPastTimePost.WHOLE,h);
    }
    @Test void originalReadFaultAndFailedObserverRetainNativePrefixAndDistinctSuppression(){
        var h=new TestCdPastTimePost.Harness();h.seed(0,9);h.failRead="ZONE";var before=h.slot();var o=new Observe();var b=bind(h,o);var verify=new AssertionError("distinct verification");
        o.after=()->{};var e=assertThrows(IllegalStateException.class,()->b.runWithReads(TestCdPastTimePost.WHOLE,h));assertSame(h.sourceFailure,e);assertEquals(before.addByte(ROUTINE,2).moveByte(Y_RADIUS,32).moveByte(X_RADIUS,14).moveLong(CdObjectState.LongField.MAPPINGS,TestCdMonitor.MAP).moveWord(CdObjectState.WordField.TILE,0x5A8).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,3),h.slot());
        var k=new TestCdPastTimePost.Harness();k.seed(2,9);k.failRead="ATTACK";var observer=new CdTimePost.NativeObserver(){public void before(CdTimePost.OriginalCall c){}public void after(CdTimePost.OriginalCall c){}public void failed(CdTimePost.OriginalCall c,Throwable original){if(original instanceof RuntimeException r)throw r;if(original instanceof Error e)throw e;throw verify;}};
        var body=CdTimePost.withOriginalOperations(k.runtime,TestCdMonitor.assets(),k::direction,k::voice,k::draw,observer);assertSame(k.sourceFailure,assertThrows(IllegalStateException.class,()->body.runWithReads(TestCdPastTimePost.WHOLE,k)));assertEquals(0,k.sourceFailure.getSuppressed().length);
    }
    @Test void secondDirectionPreparationFailureRetainsFirstStoreAndActivationBeforeSound(){
        var h=new TestCdPastTimePost.Harness();h.seed(2,9);h.edit(h.slot().moveByte(COLLISION_STATUS,1));var o=new Observe();var b=bind(h,o);var original=new AssertionError("second direction preparation");int[] directions={0};
        o.before=()->{if(o.calls.getLast().site()==CdTimePost.Site.DIRECTION_STORE&&++directions[0]==2)throw original;};
        assertSame(original,assertThrows(AssertionError.class,()->b.runWithReads(TestCdPastTimePost.WHOLE,h)));assertEquals(255,h.direction);assertEquals(List.of("DIR:0>255"),h.order);assertEquals(1,h.runtime.savedFlag(7,1)&1);assertEquals(4,h.slot().routine());assertEquals(60,h.slot().unsignedByte(TIME_POST_SPIN));assertEquals(0,h.slot().unsignedByte(COLLISION_STATUS));
    }
    @Test void distinctFailedVerificationIsSuppressedOnTheOriginalNativeError(){
        var h=new TestCdPastTimePost.Harness();h.seed(0,9);h.failRead="ZONE";var secondary=new AssertionError("verification preparation");
        var observer=new CdTimePost.NativeObserver(){public void before(CdTimePost.OriginalCall c){}public void after(CdTimePost.OriginalCall c){}public void failed(CdTimePost.OriginalCall c,Throwable original){throw secondary;}};
        var b=CdTimePost.withOriginalOperations(h.runtime,TestCdMonitor.assets(),h::direction,h::voice,h::draw,observer);assertSame(h.sourceFailure,assertThrows(IllegalStateException.class,()->b.runWithReads(TestCdPastTimePost.WHOLE,h)));assertTrue(Arrays.asList(h.sourceFailure.getSuppressed()).contains(secondary));assertEquals(2,h.slot().routine());
    }
    @Test void foreignSlotSavedStatusOrSourceCallbackMutationCannotBeBlessedAsAPrefix(){
        var h=new TestCdPastTimePost.Harness();h.seed(2,9);var o=new Observe();var b=bind(h,o);o.after=()->{if(o.calls.getLast().site()==CdTimePost.Site.TIME_ATTACK_READ)h.pool().write(h.pool().state(127).moveByte(ID,7));};
        // Root's observer rejects any such mutation before returning. Kernel rechecks AFTER it.
        assertThrows(IllegalStateException.class,()->b.runWithReads(TestCdPastTimePost.WHOLE,h));assertEquals(7,h.pool().state(127).id());assertTrue(h.order.isEmpty());
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
