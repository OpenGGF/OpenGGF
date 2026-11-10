package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Tests FIRST; all author Java/build/media/native/GL UNRUN.
 * Actual immutable-USA data and original runR31Body calls over explicitly CONSTRUCTED complete
 * component cuts. Bit7 operands are value-CAS controls, never World/Draw/queue-success proof. */
class TestCdR31PendingPrimaryDisplay {
 private static final Runnable NOTHING=()->{};
 @ParameterizedTest @CsvSource({"0,128","128,0"})
 void setAndClearOnlyBitSevenPrebuildAllStateWithoutChangingAnyFrameOwner(int extra,int bit)throws Exception{
  try(var r=rig(true,extra,0)){
   int word=service(r.frame());var before=before(r.frame());var pool=r.pool().capture();var chunks=r.terrain().chunks().copy();var retained=new AtomicReference<CdPlayerBody.State>();
   var publication=r.frame().runR31Body(word,context(r),call->{
    call.requireOwner(r.frame());var original=call.publication();var ready=original.body().result().state();assertTrue(original.body().result().display().isPresent());assertEquals(extra&0x80,ready.motion().spriteFlags()&0x80);
    int flags=(ready.motion().spriteFlags()&0x7F)|bit;var prepared=call.prepareDisplayFlag(flags);retained.set(prepared);
    assertBefore(r.frame(),before);assertOnlyFlag(ready,prepared,flags);assertSame(original,call.publication());assertSame(ready,call.publication().body().result().state());assertEquals(extra&0x80,call.publication().body().result().display().orElseThrow().spriteFlags()&0x80);
    assertEquals(pool.slots(),r.pool().capture().slots());assertArrayEquals(chunks,r.terrain().chunks().copy());call.requireOwner(r.frame());call.applyNative();assertSame(prepared,r.frame().state());
   });
   assertSame(retained.get(),r.frame().state());assertSame(publication.body().result().environment(),r.frame().environment());assertSame(publication.body().result().display(),r.frame().display());assertSame(before.art(),r.frame().artState());assertSame(before.clock(),r.frame().vIntClock());assertEquals(before.paused(),r.frame().paused());assertFalse(r.frame().closed());assertEquals(pool.slots(),r.pool().capture().slots());
  }
 }
 @Test void originalPublicationRemainsPreDrawAndUnpreparedApplyKeepsItsOldReferenceBehavior()throws Exception{
  try(var r=rig(true,0x80,0)){var p=r.frame().runR31Body(service(r.frame()),context(r),call->call.applyNative());assertSame(p.body().result().state(),r.frame().state());assertSame(p.body().result().environment(),r.frame().environment());assertSame(p.body().result().display(),r.frame().display());}
 }
 @ParameterizedTest @ValueSource(ints={-1,256,257,Integer.MIN_VALUE,Integer.MAX_VALUE})
 void byteOverflowRejectsWithoutTruncationOrConsumingPreparation(int bad)throws Exception{
  try(var r=rig(true,0,0)){int word=service(r.frame());var b=before(r.frame());r.frame().runR31Body(word,context(r),call->{assertThrows(IllegalArgumentException.class,()->call.prepareDisplayFlag(bad));assertBefore(r.frame(),b);var ready=call.publication().body().result().state();var prepared=call.prepareDisplayFlag(ready.motion().spriteFlags()^0x80);assertOnlyFlag(ready,prepared,ready.motion().spriteFlags()^0x80);call.applyNative();});}
 }
 @Test void eachOfTheSevenLowerBitsIsRejectedBeforeAnyPreparedOrFrameStore()throws Exception{
  try(var r=rig(true,0,0)){int word=service(r.frame());var b=before(r.frame());r.frame().runR31Body(word,context(r),call->{int flags=call.publication().body().result().state().motion().spriteFlags();for(int n=0;n<7;n++){int bad=flags^(1<<n);assertThrows(IllegalArgumentException.class,()->call.prepareDisplayFlag(bad));assertBefore(r.frame(),b);}var prepared=call.prepareDisplayFlag(flags);call.applyNative();assertSame(prepared,r.frame().state());});}
 }
 @Test void aSecondPreparationIncludingAnEqualOperandCannotReplaceTheOriginalPrivateState()throws Exception{
  try(var r=rig(true,0,0)){int word=service(r.frame());var b=before(r.frame());r.frame().runR31Body(word,context(r),call->{int flags=call.publication().body().result().state().motion().spriteFlags()^0x80;var prepared=call.prepareDisplayFlag(flags);assertThrows(IllegalStateException.class,()->call.prepareDisplayFlag(flags));assertThrows(IllegalStateException.class,()->call.prepareDisplayFlag(flags^0x80));assertBefore(r.frame(),b);call.applyNative();assertSame(prepared,r.frame().state());assertThrows(IllegalStateException.class,()->call.prepareDisplayFlag(flags));});}
 }
 @Test void actualSlotAbsentBodyHasNoDisplayAndCannotPrepareAnInventedDraw()throws Exception{
  try(var r=rig(false,0,0)){int word=service(r.frame());var b=before(r.frame());var p=r.frame().runR31Body(word,context(r),call->{assertEquals(CdPlayerBody.Boundary.SLOT_ABSENT,call.publication().body().result().boundary());assertTrue(call.publication().body().result().display().isEmpty());assertThrows(IllegalStateException.class,()->call.prepareDisplayFlag(0x84));assertBefore(r.frame(),b);call.applyNative();});assertSame(p.body().result().state(),r.frame().state());assertTrue(r.frame().display().isEmpty());}
 }
 @Test void displayPresenceIsNotAClaimThatARealWorldDrawOrSuccessfulQueueOperationOccurred()throws Exception{
  try(var r=rig(true,0,0x40)){r.frame().runR31Body(service(r.frame()),context(r),call->{var result=call.publication().body().result();assertTrue(result.display().isPresent());assertFalse(result.display().orElseThrow().queued());int flags=result.state().motion().spriteFlags();var prepared=call.prepareDisplayFlag(flags);assertOnlyFlag(result.state(),prepared,flags);call.applyNative();});}
 }
 @Test void foreignOwnerAndActualForeignThreadRejectBeforePreparation()throws Exception{
  try(var r=rig(true,0,0);var other=rig(true,0,0)){int word=service(r.frame());var b=before(r.frame());r.frame().runR31Body(word,context(r),call->{assertThrows(IllegalArgumentException.class,()->call.requireOwner(other.frame()));var bad=new AtomicReference<Throwable>();var thread=new Thread(()->{try{call.prepareDisplayFlag(0x84);bad.set(new AssertionError("foreign thread admitted"));}catch(Throwable original){bad.set(original);}},"constructed-pending-R31-flags");thread.start();try{thread.join(5000);assertFalse(thread.isAlive());}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}assertInstanceOf(IllegalStateException.class,bad.get());assertBefore(r.frame(),b);var ready=call.publication().body().result().state();call.prepareDisplayFlag(ready.motion().spriteFlags());call.applyNative();});assertFalse(other.frame().closed());}
 }
 @Test void retainedDataAndExpiredCallCannotApplyAfterTheOriginalCallbackReturns()throws Exception{
  try(var r=rig(true,0,0)){var kept=new AtomicReference<CdPlayerFrameRuntime.R31BodyCall>();var data=new AtomicReference<CdPlayerBody.State>();r.frame().runR31Body(service(r.frame()),context(r),call->{kept.set(call);data.set(call.prepareDisplayFlag(call.publication().body().result().state().motion().spriteFlags()^0x80));call.applyNative();});var actual=r.frame().state();assertSame(data.get(),actual);assertThrows(IllegalStateException.class,()->kept.get().prepareDisplayFlag(actual.motion().spriteFlags()));assertThrows(IllegalStateException.class,kept.get()::applyNative);assertSame(actual,r.frame().state());
   r.frame().runR31Body(service(r.frame()),context(r),call->{assertNotSame(kept.get(),call);assertThrows(IllegalStateException.class,()->kept.get().prepareDisplayFlag(actual.motion().spriteFlags()));call.prepareDisplayFlag(call.publication().body().result().state().motion().spriteFlags());call.applyNative();});
  }
 }
 @ParameterizedTest @ValueSource(booleans={false,true})
 void preApplyFailureRetainsCompletedInputArtClockPrefixAndNeverAdoptsPreparedData(boolean error)throws Exception{
  try(var r=rig(true,0,0)){int word=service(r.frame());var before=before(r.frame());assertEquals(1,before.clock().deltaSinceAdmittedCut());assertTrue(before.clock().absoluteSeed().isEmpty());var pool=r.pool().capture();var callRef=new AtomicReference<CdPlayerFrameRuntime.R31BodyCall>();var data=new AtomicReference<CdPlayerBody.State>();Throwable original=error?new AssertionError("preapply original"):new IllegalArgumentException("preapply original");assertSame(original,assertThrows(Throwable.class,()->r.frame().runR31Body(word,context(r),call->{callRef.set(call);data.set(call.prepareDisplayFlag(call.publication().body().result().state().motion().spriteFlags()^0x80));if(original instanceof Error e)throw e;throw(RuntimeException)original;})));assertBefore(r.frame(),before);assertNotSame(data.get(),r.frame().state());assertEquals(pool.slots(),r.pool().capture().slots());assertFalse(r.frame().closed());assertThrows(IllegalStateException.class,()->callRef.get().prepareDisplayFlag(data.get().motion().spriteFlags()));assertThrows(IllegalStateException.class,callRef.get()::applyNative);assertThrows(IllegalStateException.class,()->r.frame().runR31Body(word,context(r),call->fail("Consumed entitlement")));r.frame().captureActor();r.frame().captureShared();}
 }
 @ParameterizedTest @ValueSource(booleans={false,true})
 void postApplyFailureKeepsThePreparedNativePrefixAndClosesAdmission(boolean error)throws Exception{
  try(var r=rig(true,0,0)){int word=service(r.frame());var b=before(r.frame());var kept=new AtomicReference<CdPlayerFrameRuntime.R31BodyCall>();var data=new AtomicReference<CdPlayerBody.State>();Throwable original=error?new AssertionError("postapply original"):new IllegalArgumentException("postapply original");assertSame(original,assertThrows(Throwable.class,()->r.frame().runR31Body(word,context(r),call->{kept.set(call);data.set(call.prepareDisplayFlag(call.publication().body().result().state().motion().spriteFlags()^0x80));call.applyNative();if(original instanceof Error e)throw e;throw(RuntimeException)original;})));assertSame(data.get(),r.frame().state());assertSame(kept.get().publication().body().result().environment(),r.frame().environment());assertSame(b.art(),r.frame().artState());assertSame(b.clock(),r.frame().vIntClock());assertTrue(r.frame().closed());assertThrows(IllegalStateException.class,()->kept.get().prepareDisplayFlag(data.get().motion().spriteFlags()));assertThrows(IllegalStateException.class,kept.get()::applyNative);assertThrows(IllegalStateException.class,r.frame()::captureActor);}
 }
 @Test void rejectedFrameReentryDuringPreparationDoesNotChangeThePreparedFullPair()throws Exception{
  try(var r=rig(true,0,0)){int word=service(r.frame());var b=before(r.frame());r.frame().runR31Body(word,context(r),call->{var prepared=call.prepareDisplayFlag(call.publication().body().result().state().motion().spriteFlags()^0x80);assertThrows(IllegalStateException.class,()->r.frame().prepareInput(0));assertThrows(IllegalStateException.class,r.frame()::close);assertThrows(IllegalStateException.class,r.frame()::captureShared);assertThrows(IllegalStateException.class,()->r.frame().runR31Body(word,context(r),ignored->fail("Reentrant Body")));assertBefore(r.frame(),b);call.applyNative();assertSame(prepared,r.frame().state());});}
 }
 @Test void preparationWithoutOriginalApplyIsNotACompletionOrAnAdoption()throws Exception{
  try(var r=rig(true,0,0)){int word=service(r.frame());var before=before(r.frame());var kept=new AtomicReference<CdPlayerFrameRuntime.R31BodyCall>();var data=new AtomicReference<CdPlayerBody.State>();assertThrows(IllegalStateException.class,()->r.frame().runR31Body(word,context(r),call->{kept.set(call);data.set(call.prepareDisplayFlag(call.publication().body().result().state().motion().spriteFlags()^0x80));}));assertBefore(r.frame(),before);assertNotSame(data.get(),r.frame().state());assertFalse(r.frame().closed());assertThrows(IllegalStateException.class,kept.get()::applyNative);assertThrows(IllegalStateException.class,()->kept.get().prepareDisplayFlag(data.get().motion().spriteFlags()));r.frame().captureActor();}
 }
 @Test void onlyOneNestedPublicValuePortIsAddedAndNoQueueOrGenericOwnerParameterAppears()throws Exception{
  assertEquals(List.of("applyNative/0","lastPrefix/0","prepareDisplayFlag/1","publication/0","requireOwner/1"),Arrays.stream(CdPlayerFrameRuntime.R31BodyCall.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList());var m=CdPlayerFrameRuntime.R31BodyCall.class.getMethod("prepareDisplayFlag",int.class);assertEquals(CdPlayerBody.State.class,m.getReturnType());assertTrue(Arrays.stream(CdPlayerFrameRuntime.R31BodyCall.class.getDeclaredConstructors()).allMatch(c->Modifier.isPrivate(c.getModifiers())));assertEquals(8,CdPlayerBody.State.class.getRecordComponents().length);
 }
 private record Rig(CdR31aAssets assets,CdPlayerFrameRuntime frame,CdObjectPool pool,CdStageTerrain terrain)implements AutoCloseable{public void close(){frame.close();}}
 private record Before(CdPlayerBody.State state,CdPlayerBody.Environment environment,Optional<CdPlayerBody.DisplayPublication> display,CdPlayerArtRuntime.State art,CdPlayerFrameRuntime.VIntClock clock,boolean paused){}
 private static Rig rig(boolean present,int spriteExtra,int control)throws Exception{var d=TestCdR31PlayerBodyMainHurt.actual();var state=TestCdR31PlayerBodyMainHurt.state(d,2,2,spriteExtra,control);if(!present){var h=state.header();state=new CdPlayerBody.State(state.motion(),state.animation(),new CdPlayerBody.Header(0,h.mappingAddress(),h.tile(),h.priority(),h.width()),state.timers(),state.history(),state.rotationDistance(),state.objectLinks(),state.rotationAngle());}var frame=CdPlayerFrameRuntime.fromR31aEntry(d.a(),state,TestCdR31PlayerBodyMainHurt.environment(d),art(),new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(),0));return new Rig(d.a(),frame,new CdObjectPool(),d.a().terrain());}
 private static CdPlayerBody.R31Context context(Rig r){return new CdPlayerBody.R31Context(r.frame().stageSource(),r.assets(),r.terrain(),r.pool().capture(),Optional.empty(),CdPlayerObjectTouch.fromR31aAssets(r.assets()),new CdPlayerBody.ResponseContext(CdPlayerTouchResponse.fromR31aAssets(r.assets()),new CdPlayerBody.ContactGlobals(0,0,0),new CdPlayerTouchResponse.WorldGlobals(1,3,0,0,5000,0)),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),0x20,0x300);}
 private static int service(CdPlayerFrameRuntime frame){frame.bindArtPublication((request,current)->{assertEquals(0xF000,request.vramByteAddress());assertSame(current,frame.artState());});var selection=frame.prepareInput(0);frame.serviceVBlank(selection.phase(),NOTHING,NOTHING,NOTHING);return frame.advanceLevelMainFrame();}
 private static CdPlayerArtRuntime.State art(){byte[] cpu=new byte[768],resident=new byte[768];for(int i=0;i<768;i++){cpu[i]=(byte)(i*7+3);resident[i]=(byte)(i*11+5);}return new CdPlayerArtRuntime.State(cpu,resident,255,false);}
 private static Before before(CdPlayerFrameRuntime f){return new Before(f.state(),f.environment(),f.display(),f.artState(),f.vIntClock(),f.paused());}
 private static void assertBefore(CdPlayerFrameRuntime f,Before b){assertSame(b.state(),f.state());assertSame(b.environment(),f.environment());assertSame(b.display(),f.display());assertSame(b.art(),f.artState());assertSame(b.clock(),f.vIntClock());assertEquals(b.paused(),f.paused());}
 private static void assertOnlyFlag(CdPlayerBody.State old,CdPlayerBody.State next,int flags){assertNotSame(old,next);assertEquals(flags,next.motion().spriteFlags());assertEquals(motionWithoutSprite(old.motion()),motionWithoutSprite(next.motion()));assertSame(old.animation(),next.animation());assertSame(old.header(),next.header());assertSame(old.timers(),next.timers());assertSame(old.history(),next.history());assertEquals(old.rotationDistance(),next.rotationDistance());assertSame(old.objectLinks(),next.objectLinks());assertEquals(old.rotationAngle(),next.rotationAngle());}
 private static List<Integer> motionWithoutSprite(CdPlayerMotionState m){return List.of(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
