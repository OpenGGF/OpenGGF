package com.openggf.game.soniccd.player;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Tests FIRST; Author Java was UNRUN; Root qualification is recorded in the implementation plan. Actual immutable original-USA assets plus explicitly CONSTRUCTED
 * complete component actor/environment/current-image/pool/clock cuts. No native boot, World,
 * ordinary route, source epoch, current-art/pixels/audio or real invocation authority is supplied. */
class TestCdR31OrdinaryFrameBridge {
    private static final Runnable NOTHING=()->{};
    private static final CdPlayerFrameRuntime.R31PublicationPort APPLY=call->call.applyNative();

    @Test void mainRunsRealChargeModeHelperTailAnimationHistoryAndTouchOnce() throws Exception {
        try(var r=rig(2,2,null,null)){
            int word=service(r.frame(),8);var art=r.frame().artState();var clock=r.frame().vIntClock();
            var p=r.frame().runR31Body(word,context(r),APPLY);var result=p.body().result();var calls=TestCdR31PlayerBodyMainHurt.calls(result);
            assertEquals(CdPlayerBody.Boundary.MAIN_RETURN,result.boundary());assertTrue(result.modeBoundary().isPresent());
            for(var c:List.of(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH,CdPlayerBody.NativeCall.MODE,CdPlayerBody.NativeCall.R3_SPECIAL_BLOCK_COLLISION,CdPlayerBody.NativeCall.DISPLAY,CdPlayerBody.NativeCall.RECORD_POSITION,CdPlayerBody.NativeCall.ANIMATE))assertEquals(1,Collections.frequency(calls,c));
            assertEquals(1,result.effects().stream().filter(CdPlayerBody.TouchScanCompleted.class::isInstance).count());
            assertEquals(4,result.state().history().index());assertEquals(word,result.environment().globals().levelFrames());assertAdopted(r.frame(),p);
            assertSame(art,r.frame().artState());assertSame(clock,r.frame().vIntClock());assertEquals(8,r.frame().environment().controls().rawHeld());
            assertThrows(IllegalStateException.class,()->r.frame().runR31Body(word,context(r),APPLY));
        }
    }

    @Test void hurtUsesBlockBeforeRecoveryAndReturnsItsChangedRoutineWithUnconditionalDraw() throws Exception {
        try(var r=rig(4,2,0,null)){
            var p=r.frame().runR31Body(service(r.frame(),0),context(r),APPLY);var b=p.body().result();var calls=TestCdR31PlayerBodyMainHurt.calls(b);
            assertEquals(CdPlayerBody.Boundary.HURT_RETURN,b.boundary());assertTrue(b.modeBoundary().isEmpty());assertTrue(b.display().orElseThrow().queued());
            assertNotEquals(4,b.state().motion().routine(),"Original helper/recovery changes outgoing routine; dispatch remains Hurt");
            assertTrue(calls.indexOf(CdPlayerBody.NativeCall.R3_SPECIAL_BLOCK_COLLISION)<calls.indexOf(CdPlayerBody.NativeCall.HURT_CHECK_LAND));
            assertFalse(calls.contains(CdPlayerBody.NativeCall.MODE));assertFalse(calls.contains(CdPlayerBody.NativeCall.DISPLAY));assertFalse(b.effects().stream().anyMatch(CdPlayerBody.TouchScanCompleted.class::isInstance));assertAdopted(r.frame(),p);
        }
    }

    @Test void entireFramePairAndDisplayStayOriginalUntilTheOriginalApply() throws Exception {
        try(var r=rig(2,2,null,null)){
            int word=service(r.frame(),0);var before=before(r.frame());var p=r.frame().runR31Body(0x10000|word,context(r),call->{
                call.requireOwner(r.frame());assertBefore(r.frame(),before);assertEquals(word,call.publication().body().result().environment().globals().levelFrames());
                assertNotSame(before.state(),call.publication().body().result().state());call.applyNative();assertAdopted(r.frame(),call.publication());
            });assertAdopted(r.frame(),p);assertSame(before.art(),r.frame().artState());assertSame(before.clock(),r.frame().vIntClock());
        }
    }

    @Test void actualFinalFull96AndTerrainArePrebuiltThenAdoptedAsWholeValues() throws Exception {
        try(var r=rig(4,2,0,null)){
            r.pool().write(r.pool().state(32).moveLong(CdObjectState.LongField.MAPPINGS,0x123456).moveWord(CdObjectState.WordField.X_FRACTION,0xA5A5));
            var original=r.pool().capture();byte[] chunks=r.terrain().chunks().copy();var nextTerrain=new AtomicReference<>(r.terrain());
            var p=r.frame().runR31Body(service(r.frame(),0),context(r),call->{var ready=call.publication().nativeValues();
                assertEquals(original.slots(),r.pool().capture().slots());assertArrayEquals(chunks,r.terrain().chunks().copy());r.pool().validateRestore(ready.snapshot());
                call.requireOwner(r.frame());call.applyNative();r.pool().restore(ready.snapshot());nextTerrain.set(ready.terrain());
            });assertEquals(p.nativeValues().snapshot().slots(),r.pool().capture().slots());assertSame(p.nativeValues().terrain(),nextTerrain.get());
            assertEquals(0x29,r.pool().state(32).id());assertEquals(0x123456,r.pool().state(32).longBits(CdObjectState.LongField.MAPPINGS));assertEquals(0xA5A5,r.pool().state(32).word(CdObjectState.WordField.X_FRACTION));
            assertArrayEquals(chunks,r.terrain().chunks().copy());assertNotSame(r.terrain(),nextTerrain.get());assertEquals(96,p.nativeValues().snapshot().slots().size());
        }
    }

    @Test void laterHurtCollisionUsesTheEarlierPrivateSharedChunkImage() throws Exception {
        try(var r=rig(4,2,0,null)){
            var p=r.frame().runR31Body(service(r.frame(),0),context(r),APPLY);var air=p.body().result().effects().stream().filter(CdPlayerBody.R31AirCollisionImage.class::isInstance).map(CdPlayerBody.R31AirCollisionImage.class::cast).findFirst().orElseThrow();
            assertSame(p.nativeValues().terrain(),air.terrain());assertNotSame(r.terrain(),air.terrain());
            for(var e:p.body().result().effects())if(e instanceof CdPlayerBody.R31PlaceBlock place&&place.chunkByteOffset().isPresent())assertEquals(0,air.terrain().chunks().unsignedWord(place.chunkByteOffset().orElseThrow()));
            var block=p.body().result().effects().stream().filter(CdPlayerBody.R31BlockCall.class::isInstance).map(CdPlayerBody.R31BlockCall.class::cast).findFirst().orElseThrow();
            assertEquals(block.result().state().actor().motion(),air.before());assertAdopted(r.frame(),p);
        }
    }

    @Test void completeAnglesHistoryFractionsRotationAndSupportValuesComeOnlyFromBody() throws Exception {
        try(var r=rig(4,2,null,null)){
            var original=r.frame().state();var pool=r.pool().capture();var c=context(r);var support=new CdPlayerMovement.ObjectContext(pool,original.objectLinks().standObject(),original.header().slotId(),original.header().width());
            c=new CdPlayerBody.R31Context(c.source(),c.assets(),c.currentTerrain(),pool,Optional.of(support),c.scanner(),c.response(),c.noBumperByte(),c.bossStartedByte(),c.goodFutureByte(),c.cameraXWord(),c.cameraYWord());
            var p=r.frame().runR31Body(service(r.frame(),0),c,APPLY);var s=p.body().result().state();assertEquals(original.rotationAngle(),s.rotationAngle());assertEquals(original.rotationDistance(),s.rotationDistance());
            assertEquals(original.objectLinks(),s.objectLinks());assertEquals(original.header(),s.header());assertEquals(original.timers().reset(),s.timers().reset());assertEquals(original.motion().centreX16_16()&65535,s.motion().centreX16_16()&65535);assertEquals(original.motion().centreY16_16()&65535,s.motion().centreY16_16()&65535);
            assertEquals(p.body().result().environment(),r.frame().environment());assertEquals(pool.slots(),r.pool().capture().slots());assertEquals(4,s.history().index());
        }
    }

    @Test void regularBumperProducesItsOriginalFiniteFmRequestWithoutBlockLogDuplication() throws Exception {
        for(int index:new int[]{5,2})try(var r=rig(4,2,index,null)){
            var p=r.frame().runR31Body(service(r.frame(),0),context(r),APPLY);var fm=p.body().sounds().stream().filter(CdPlayerFrameRuntime.R31NativeSound.class::isInstance).map(CdPlayerFrameRuntime.R31NativeSound.class::cast).toList();
            assertEquals(1,fm.size());assertEquals(new CdPlayerBody.R31FmRequest(index==5?0xB5:0xBE),fm.getFirst().request());
            assertEquals(1,p.body().result().effects().stream().filter(CdPlayerBody.R31FmRequest.class::isInstance).count());assertFalse(p.body().result().effects().stream().anyMatch(CdPlayerBody.R31PlaceBlock.class::isInstance));
        }
    }

    @Test void destructionKeepsNativeChildFourPlaceRequestsAndSubCpuOrder() throws Exception {
        try(var r=rig(4,2,0,null)){
            var pool=r.pool().capture();var p=r.frame().runR31Body(service(r.frame(),0),context(r),APPLY);var effects=p.body().result().effects();var places=effects.stream().filter(CdPlayerBody.R31PlaceBlock.class::isInstance).map(CdPlayerBody.R31PlaceBlock.class::cast).toList();
            assertEquals(4,places.size());assertEquals(List.of(0x60,0x50,0x50,0x60),places.stream().map(CdPlayerBody.R31PlaceBlock::xWord).toList());assertEquals(List.of(0x400,0x400,0x3F0,0x3F0),places.stream().map(CdPlayerBody.R31PlaceBlock::yWord).toList());
            int last=effects.indexOf(places.getLast());int sub=-1;for(int i=0;i<effects.size();i++)if(effects.get(i) instanceof CdPlayerBody.R31SubCpuRequest)sub=i;assertTrue(sub>last);
            assertEquals(List.of(new CdPlayerFrameRuntime.R31NativeSound(new CdPlayerBody.R31SubCpuRequest(0x92))),p.body().sounds());assertEquals(pool.slots(),r.pool().capture().slots());
            for(var place:places)if(place.onscreen())assertArrayEquals(r.terrain().blocks().slice(0,8).copy(),place.planeWords().orElseThrow().copy());
        }
    }

    @Test void powerupExpiryPublishesUnselectedR31MusicAtItsOriginalEffectPosition() throws Exception {
        var f=data();var original=TestCdR31PlayerBodyMainHurt.state(f,2,2,0,0);var s=new CdPlayerBody.State(original.motion(),original.animation(),original.header(),new CdPlayerBody.Timers(0,1,0,0xBEEF),original.history(),original.rotationDistance(),original.objectLinks(),original.rotationAngle());
        var env=TestCdR31PlayerBodyMainHurt.environment(f);var g=env.globals();env=new CdPlayerBody.Environment(env.movement(),env.controls(),new CdPlayerBody.Globals(g.levelFrames(),g.cameraXCentre(),g.timeAttackMode(),g.debugMode(),g.debugCheat(),g.timeWarpTimer(),1,0),env.dynamicSlots());
        try(var r=rig(2,2,5,new Pair(s,env))){var p=r.frame().runR31Body(service(r.frame(),0),context(r),APPLY);var request=new CdPlayerBody.R31MusicRequest(0x100,1,0);
            assertEquals(List.of(new CdPlayerFrameRuntime.R31NativeSound(new CdPlayerBody.R31FmRequest(0xB5)),new CdPlayerFrameRuntime.R31NativeSound(request)),p.body().sounds());assertEquals(0,r.frame().environment().globals().invincible());
            var e=p.body().result().effects();assertTrue(e.indexOf(request)<index(e,CdPlayerBody.PositionHistoryWrite.class));
            assertFalse(p.body().sounds().stream().anyMatch(CdPlayerFrameRuntime.BodySound.class::isInstance));assertTrue(context(r).goodFutureByte().isEmpty());
        }
    }

    @Test void fullPoolExhaustionDoesNotInventAChildOrOmitFourNativePlacements() throws Exception {
        try(var r=rig(4,2,0,null)){
            for(int w=32;w<128;w++)r.pool().write(r.pool().state(w).moveByte(CdObjectState.ByteField.ID,0x19).moveLong(CdObjectState.LongField.MAPPINGS,0x100000+w));var before=r.pool().capture();
            var p=r.frame().runR31Body(service(r.frame(),0),context(r),APPLY);assertEquals(before.slots(),p.nativeValues().snapshot().slots());assertEquals(before.slots(),r.pool().capture().slots());assertEquals(4,p.body().result().effects().stream().filter(CdPlayerBody.R31PlaceBlock.class::isInstance).count());assertEquals(1,p.body().sounds().size());
        }
    }

    @Test void equalDataForeignSourceAndForeignOriginalFrameCannotSupplyTheCall() throws Exception {
        try(var r=rig(2,2,null,null);var foreign=rig(2,2,null,null)){
            var c=context(r);var other=CdStagePlayerSource.fromR31aAssets(r.assets());var wrong=new CdPlayerBody.R31Context(other,c.assets(),c.currentTerrain(),c.snapshot(),c.objects(),c.scanner(),c.response(),c.noBumperByte(),c.bossStartedByte(),c.goodFutureByte(),c.cameraXWord(),c.cameraYWord());
            var before=r.frame().state();assertThrows(IllegalArgumentException.class,()->r.frame().runR31Body(service(r.frame(),0),wrong,call->fail("Foreign source reached port")));assertSame(before,r.frame().state());
            var p=r.frame().runR31Body(service(r.frame(),0),context(r),call->{assertThrows(IllegalArgumentException.class,()->call.requireOwner(foreign.frame()));call.requireOwner(r.frame());call.applyNative();});assertAdopted(r.frame(),p);
        }
    }

    @Test void unknownOptionalFactsRemainLazyAndRawOuterTouchGatesStayNative() throws Exception {
        var f=data();var s=TestCdR31PlayerBodyMainHurt.state(f,2,2,0,0x40);
        try(var r=rig(2,2,5,new Pair(s,TestCdR31PlayerBodyMainHurt.environment(f)))){
            r.pool().write(r.pool().state(32).moveByte(CdObjectState.ByteField.ID,0x22).moveByte(CdObjectState.ByteField.SPRITE_FLAGS,0x80).moveByte(CdObjectState.ByteField.COLLISION_TYPE,0xFF).moveWord(CdObjectState.WordField.X,s.motion().centreX16_16()>>>16).moveWord(CdObjectState.WordField.Y,s.motion().centreY16_16()>>>16));
            var c=context(r);assertTrue(c.noBumperByte().isEmpty());assertTrue(c.bossStartedByte().isEmpty());assertTrue(c.goodFutureByte().isEmpty());var p=r.frame().runR31Body(service(r.frame(),0),c,APPLY);
            assertEquals(OptionalInt.of(0),p.nativeValues().noBumperByte(),"Actual matched helper establishes zero from unknown before its test");assertTrue(p.body().result().effects().stream().anyMatch(e->e instanceof CdPlayerBody.TouchScanCompleted t&&t.slotsVisited()==0));assertEquals(c.snapshot().slots(),p.nativeValues().snapshot().slots());
        }
    }

    @Test void oldR31RunBodyStillStopsAfterGenuineInitWhileTheNewEntryIsDistinct() throws Exception {
        try(var r=rig(0,0,null,null)){
            var init=r.frame().runBody(service(r.frame(),0));assertEquals(CdPlayerBody.Boundary.INIT_RETURN,init.result().boundary());var before=r.frame().state();
            assertEquals(CdPlayerBody.UnsupportedBoundary.R3_CONTINUATION_OWNER,assertThrows(CdPlayerBody.UnsupportedBody.class,()->r.frame().runBody(service(r.frame(),0))).boundary());assertSame(before,r.frame().state());
            var p=r.frame().runR31Body(service(r.frame(),0),context(r),APPLY);assertEquals(CdPlayerBody.Boundary.MAIN_RETURN,p.body().result().boundary());assertAdopted(r.frame(),p);
        }
    }

    @Test void unservicedPauseAndLagNeverIssueAPrimaryPublication() throws Exception {
        try(var r=rig(2,2,null,null)){
            assertThrows(IllegalStateException.class,()->r.frame().runR31Body(1,context(r),call->fail("Unserviced")));
            r.frame().bindArtPublication((a,b)->{});var sample=r.frame().prepareInput(0);r.frame().serviceVBlank(PlcLifecyclePhase.LAG,NOTHING,NOTHING,NOTHING);assertThrows(IllegalStateException.class,()->r.frame().runR31Body(1,context(r),call->fail("Lag")));
            sample=r.frame().prepareInput(0x80);assertEquals(PlcLifecyclePhase.NORMAL_PAUSE,sample.phase());r.frame().serviceVBlank(sample.phase(),NOTHING,NOTHING,NOTHING);assertTrue(r.frame().paused());assertThrows(IllegalStateException.class,()->r.frame().runR31Body(1,context(r),call->fail("Pause")));
        }
    }

    @Test void originalReceiptRejectsDuplicateLateStaleForeignAndReentrantUse() throws Exception {
        try(var r=rig(2,2,null,null)){
            var kept=new AtomicReference<CdPlayerFrameRuntime.R31BodyCall>();r.frame().runR31Body(service(r.frame(),0),context(r),call->{kept.set(call);var wrongThread=new AtomicReference<Throwable>();var thread=new Thread(()->{try{call.applyNative();}catch(Throwable failure){wrongThread.set(failure);}},"constructed-R31-foreign-thread");thread.start();try{thread.join(5000);assertFalse(thread.isAlive(),"Finite foreign-thread call must finish");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}assertInstanceOf(IllegalStateException.class,wrongThread.get());assertThrows(IllegalStateException.class,()->r.frame().runBody(1));assertThrows(IllegalStateException.class,()->r.frame().runR31Body(1,context(r),APPLY));call.applyNative();assertThrows(IllegalStateException.class,call::applyNative);});
            assertThrows(IllegalStateException.class,()->kept.get().requireOwner(r.frame()));assertThrows(IllegalStateException.class,kept.get()::applyNative);
            r.frame().runR31Body(service(r.frame(),0),context(r),call->{assertNotSame(kept.get(),call);assertThrows(IllegalStateException.class,kept.get()::applyNative);call.applyNative();});
        }
    }

    @Test void missingOriginalAcknowledgementConsumesEntitlementWithoutAdoption() throws Exception {
        try(var r=rig(2,2,null,null)){
            int word=service(r.frame(),0);var before=before(r.frame());var kept=new AtomicReference<CdPlayerFrameRuntime.R31BodyCall>();assertThrows(IllegalStateException.class,()->r.frame().runR31Body(word,context(r),kept::set));assertBefore(r.frame(),before);assertFalse(r.frame().closed());assertThrows(IllegalStateException.class,kept.get()::applyNative);assertThrows(IllegalStateException.class,()->r.frame().runR31Body(word,context(r),APPLY));r.frame().captureActor();r.frame().captureShared();
        }
    }

    @Test void originalPortRuntimeExceptionKeepsTheCompletedVintPrefixAndNoBodyWrites() throws Exception {
        try(var r=rig(4,2,0,null)){
            int word=service(r.frame(),8);var before=before(r.frame());var pool=r.pool().capture();byte[] chunks=r.terrain().chunks().copy();var original=new IllegalArgumentException("original preflight fault");var kept=new AtomicReference<CdPlayerFrameRuntime.R31BodyCall>();
            assertSame(original,assertThrows(IllegalArgumentException.class,()->r.frame().runR31Body(word,context(r),call->{kept.set(call);throw original;})));assertBefore(r.frame(),before);assertEquals(8,r.frame().environment().controls().rawHeld());assertEquals(1,r.frame().vIntClock().deltaSinceAdmittedCut());assertEquals(pool.slots(),r.pool().capture().slots());assertArrayEquals(chunks,r.terrain().chunks().copy());assertThrows(IllegalStateException.class,kept.get()::applyNative);
        }
    }

    @Test void originalPortErrorAlsoRetiresItsScopeWithoutInventingRollback() throws Exception {
        try(var r=rig(4,2,0,null)){
            int word=service(r.frame(),0);var before=before(r.frame());var original=new AssertionError("original preflight Error");var kept=new AtomicReference<CdPlayerFrameRuntime.R31BodyCall>();
            assertSame(original,assertThrows(AssertionError.class,()->r.frame().runR31Body(word,context(r),call->{kept.set(call);throw original;})));assertBefore(r.frame(),before);assertThrows(IllegalStateException.class,()->kept.get().requireOwner(r.frame()));assertFalse(r.frame().closed());r.frame().captureActor();
        }
    }

    @Test void lateHistoryFailureLeaksNoStagedFramePoolChunkOrOriginalPort() throws Exception {
        var f=data();var old=TestCdR31PlayerBodyMainHurt.state(f,4,2,0,0);var bad=new CdPlayerBody.State(old.motion(),old.animation(),old.header(),old.timers(),new CdPlayerBody.History(old.history().bytes(),0xFE),old.rotationDistance(),old.objectLinks(),old.rotationAngle());
        try(var r=rig(4,2,0,new Pair(bad,TestCdR31PlayerBodyMainHurt.environment(f)))){
            int word=service(r.frame(),0);var before=before(r.frame());var pool=r.pool().capture();byte[] chunks=r.terrain().chunks().copy();assertEquals(CdPlayerBody.UnsupportedBoundary.HISTORY_ADDRESS,assertThrows(CdPlayerBody.UnsupportedBody.class,()->r.frame().runR31Body(word,context(r),call->fail("Stopped Body exposed a call"))).boundary());assertBefore(r.frame(),before);assertEquals(pool.slots(),r.pool().capture().slots());assertArrayEquals(chunks,r.terrain().chunks().copy());assertThrows(IllegalStateException.class,()->r.frame().runR31Body(word,context(r),APPLY));
        }
    }

    @Test void preciseControlledWarpMiniatureLifecycleAndMissingSupportCutsRemain() throws Exception {
        var f=data();for(int control:new int[]{1}){var s=TestCdR31PlayerBodyMainHurt.state(f,2,2,0,control);try(var r=rig(2,2,null,new Pair(s,TestCdR31PlayerBodyMainHurt.environment(f)))){assertEquals(CdPlayerBody.UnsupportedBoundary.R3_TIME_WARP_OWNER,assertThrows(CdPlayerBody.UnsupportedBody.class,()->r.frame().runR31Body(service(r.frame(),0),context(r),call->fail("Warp"))).boundary());}}
        for(int routine:new int[]{0,6,8})try(var r=rig(routine,2,null,null)){assertEquals(CdPlayerBody.UnsupportedBoundary.R3_CONTINUATION_OWNER,assertThrows(CdPlayerBody.UnsupportedBody.class,()->r.frame().runR31Body(service(r.frame(),0),context(r),call->fail("Lifecycle"))).boundary());}
        var e=TestCdR31PlayerBodyMainHurt.environment(f);var m=e.movement();var g=m.gates();var mini=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(),m.bounds(),new CdPlayerMovement.Gates(g.zoneAct(),g.timeZone(),1,g.waterSlide(),g.windTunnel(),g.speedShoes(),g.timeWarpDirection()),m.globals()),e.controls(),e.globals(),e.dynamicSlots());try(var r=rig(2,2,null,new Pair(TestCdR31PlayerBodyMainHurt.state(f,2,2,0,0),mini))){assertEquals(CdPlayerBody.UnsupportedBoundary.R3_MINIATURE_OWNER,assertThrows(CdPlayerBody.UnsupportedBody.class,()->r.frame().runR31Body(service(r.frame(),0),context(r),call->fail("Mini"))).boundary());}
        try(var r=rig(4,10,null,null)){assertEquals(CdPlayerBody.UnsupportedBoundary.STANDING_OBJECT_MOVE,assertThrows(CdPlayerBody.UnsupportedBody.class,()->r.frame().runR31Body(service(r.frame(),0),context(r),call->fail("Support"))).boundary());}
        var normal=TestCdR31PlayerBodyMainHurt.state(f,4,2,0,0);var fm=normal.motion();var far=new CdPlayerBody.State(new CdPlayerMotionState(fm.centreX16_16(),((e.movement().bounds().bottom()+225)&65535)<<16,fm.xVelocity(),fm.yVelocity(),fm.groundVelocity(),fm.routine(),fm.angle(),fm.flags(),fm.spriteFlags(),fm.xRadius(),fm.yRadius(),fm.animation(),fm.previousAnimation(),fm.playerJump(),fm.charge(),fm.moveLock(),fm.stick(),fm.playerControl(),fm.previousPrimaryAngle(),fm.previousSecondaryAngle()),normal.animation(),normal.header(),normal.timers(),normal.history(),normal.rotationDistance(),normal.objectLinks(),normal.rotationAngle());
        try(var r=rig(4,2,null,new Pair(far,e))){assertEquals(CdPlayerBody.UnsupportedBoundary.R3_KILL_OWNER,assertThrows(CdPlayerBody.UnsupportedBody.class,()->r.frame().runR31Body(service(r.frame(),0),context(r),call->fail("Kill"))).boundary());}
    }

    @Test void ownClosedComponentCapturesRestoreAndReplayWithoutStoredInvocationAuthority() throws Exception {
        try(var r=rig(4,2,0,null)){
            var actor=r.frame().captureActor();var shared=r.frame().captureShared();var pool=r.pool().capture();var first=r.frame().runR31Body(service(r.frame(),0),context(r),APPLY);var completeActor=r.frame().captureActor();var completeShared=r.frame().captureShared();
            r.frame().restoreActor(actor);r.frame().restoreShared(shared);assertThrows(IllegalStateException.class,()->r.frame().runR31Body(1,context(r),APPLY));var again=r.frame().runR31Body(service(r.frame(),0),context(r),APPLY);
            assertReplay(first,again);assertEquals(completeActor,r.frame().captureActor());assertShared(completeShared,r.frame().captureShared());assertEquals(pool.slots(),r.pool().capture().slots());
        }
    }

    @Test void earlierRestoreNeverRevivesTheRetiredOriginalReceipt() throws Exception {
        try(var r=rig(2,2,null,null)){
            var a=r.frame().captureActor();var s=r.frame().captureShared();var kept=new AtomicReference<CdPlayerFrameRuntime.R31BodyCall>();r.frame().runR31Body(service(r.frame(),0),context(r),call->{kept.set(call);call.applyNative();});r.frame().restoreActor(a);r.frame().restoreShared(s);assertThrows(IllegalStateException.class,kept.get()::applyNative);
            var b=before(r.frame());assertThrows(IllegalStateException.class,()->r.frame().runR31Body(1,context(r),APPLY));assertBefore(r.frame(),b);
        }
    }

    @Test void postApplyFaultPreservesCompletedBodyAndPermanentlyClosesAdmission() throws Exception {
        var r=rig(2,2,null,null);var kept=new AtomicReference<CdPlayerFrameRuntime.R31Publication>();var original=new AssertionError("fault after original native apply");try{
            assertSame(original,assertThrows(AssertionError.class,()->r.frame().runR31Body(service(r.frame(),0),context(r),call->{kept.set(call.publication());call.applyNative();throw original;})));assertAdopted(r.frame(),kept.get());assertTrue(r.frame().closed());assertThrows(IllegalStateException.class,()->r.frame().prepareInput(0));assertThrows(IllegalStateException.class,()->r.frame().captureActor());
        }finally{r.close();}
    }

    @Test void exactApiHasNoRawRestoreEscapeAndScopeCannotCaptureMutateCloseOrCancel() throws Exception {
        assertEquals(CdPlayerFrameRuntime.R31Publication.class,CdPlayerFrameRuntime.class.getMethod("runR31Body",int.class,CdPlayerBody.R31Context.class,CdPlayerFrameRuntime.R31PublicationPort.class).getReturnType());assertEquals(1,Arrays.stream(CdPlayerFrameRuntime.class.getDeclaredMethods()).filter(v->v.getName().equals("runR31Body")).count());
        assertEquals(List.of("publish"),Arrays.stream(CdPlayerFrameRuntime.R31PublicationPort.class.getDeclaredMethods()).map(v->v.getName()).toList());assertEquals(2,CdPlayerFrameRuntime.R31Publication.class.getRecordComponents().length);assertEquals(3,CdPlayerFrameRuntime.ActorState.class.getRecordComponents().length);assertEquals(5,CdPlayerFrameRuntime.SharedState.class.getRecordComponents().length);assertEquals(8,CdPlayerBody.State.class.getRecordComponents().length);
        assertTrue(Arrays.stream(CdPlayerFrameRuntime.R31BodyCall.class.getDeclaredConstructors()).allMatch(c->Modifier.isPrivate(c.getModifiers())));assertEquals(List.of("applyNative/0","prepareDisplayFlag/1","publication/0","requireOwner/1"),Arrays.stream(CdPlayerFrameRuntime.R31BodyCall.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList());assertEquals(7,CdPlayerFrameRuntime.Sound.class.getPermittedSubclasses().length);
        assertThrows(IllegalArgumentException.class,()->new CdPlayerFrameRuntime.R31NativeSound(new CdPlayerBody.R31PlaceBlock(0,0,OptionalInt.empty(),OptionalInt.empty(),false,0,0,Optional.empty())));assertThrows(NullPointerException.class,()->new CdPlayerFrameRuntime.R31NativeSound(null));
        try(var r=rig(2,2,null,null)){var a=r.frame().captureActor();var s=r.frame().captureShared();r.frame().runR31Body(service(r.frame(),0),context(r),call->{assertThrows(NullPointerException.class,()->call.requireOwner(null));assertThrows(IllegalStateException.class,()->r.frame().captureActor());assertThrows(IllegalStateException.class,()->r.frame().captureShared());assertThrows(IllegalStateException.class,()->r.frame().restoreActor(a));assertThrows(IllegalStateException.class,()->r.frame().restoreShared(s));assertThrows(IllegalStateException.class,()->r.frame().clearPendingInput());assertThrows(IllegalStateException.class,()->r.frame().close());call.applyNative();});assertTrue(r.frame().captureShared().vIntClock().absoluteValue().isEmpty());
            int word=service(r.frame(),0);var b=before(r.frame());assertThrows(NullPointerException.class,()->r.frame().runR31Body(word,context(r),null));assertBefore(r.frame(),b);assertThrows(IllegalStateException.class,()->r.frame().runR31Body(word,context(r),APPLY));
            int next=service(r.frame(),0);var beforeNull=before(r.frame());assertThrows(NullPointerException.class,()->r.frame().runR31Body(next,null,call->fail("Null context")));assertBefore(r.frame(),beforeNull);
        }
    }

    @Test void genuineR3EnemyResponseCarriesCompleteScoreLivesChainPoolAndOldSoundOrder() throws Exception {
        var d=data();var e=TestCdR31PlayerBodyMainHurt.environment(d);var g=e.globals();e=new CdPlayerBody.Environment(e.movement(),e.controls(),new CdPlayerBody.Globals(g.levelFrames(),g.cameraXCentre(),g.timeAttackMode(),g.debugMode(),g.debugCheat(),g.timeWarpTimer(),1,0),e.dynamicSlots());
        try(var r=rig(2,2,null,new Pair(TestCdR31PlayerBodyMainHurt.state(d,2,2,0,0),e))){var start=r.assets().initialCentre();r.pool().write(r.pool().state(32).moveByte(CdObjectState.ByteField.ID,0x27).moveByte(CdObjectState.ByteField.SPRITE_FLAGS,0x80).moveByte(CdObjectState.ByteField.COLLISION_TYPE,7).moveWord(CdObjectState.WordField.X,start.centreX()).moveWord(CdObjectState.WordField.Y,start.centreY()).moveLong(CdObjectState.LongField.MAPPINGS,0x123456));
            var before=r.pool().capture();var c=context(r);var world=new CdPlayerTouchResponse.WorldGlobals(1,3,0,4995,5000,0);var globals=new AtomicReference<>(world);var changed=new CdPlayerBody.R31Context(c.source(),c.assets(),c.currentTerrain(),before,c.objects(),c.scanner(),new CdPlayerBody.ResponseContext(c.response().response(),c.response().contact(),world),c.noBumperByte(),c.bossStartedByte(),c.goodFutureByte(),c.cameraXWord(),c.cameraYWord());
            var p=r.frame().runR31Body(service(r.frame(),0),changed,call->{var next=call.publication().nativeValues();assertSame(world,globals.get());assertEquals(before.slots(),r.pool().capture().slots());r.pool().validateRestore(next.snapshot());call.applyNative();r.pool().restore(next.snapshot());globals.set(next.world());});
            assertEquals(new CdPlayerTouchResponse.WorldGlobals(1,4,1,5005,10000,1),globals.get());assertEquals(2,r.frame().environment().movement().globals().scoreChain());assertEquals(0x18,r.pool().state(32).id());assertEquals(0x123456,r.pool().state(32).longBits(CdObjectState.LongField.MAPPINGS));
            for(int n=1;n<96;n++)assertEquals(before.slots().get(n),r.pool().capture().slots().get(n));assertEquals(List.of(new CdPlayerFrameRuntime.ContactSubCpuCommand(CdPlayerTouchResponse.SubCpuCommand.SCMD_YESSFX),new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_DESTROY)),p.body().sounds());assertEquals(1,p.body().result().effects().stream().filter(CdPlayerBody.ObjectTouchResponse.class::isInstance).count());
        }
    }

    @Test void reachedSpecialWithoutActualBossByteStopsBeforeAnyOriginalPublication() throws Exception {
        try(var r=rig(2,2,null,null)){var start=r.assets().initialCentre();r.pool().write(r.pool().state(32).moveByte(CdObjectState.ByteField.ID,0x22).moveByte(CdObjectState.ByteField.SPRITE_FLAGS,0x80).moveByte(CdObjectState.ByteField.COLLISION_TYPE,0xFF).moveWord(CdObjectState.WordField.X,start.centreX()).moveWord(CdObjectState.WordField.Y,start.centreY()));var pool=r.pool().capture();int word=service(r.frame(),0);var old=before(r.frame());
            assertEquals(CdPlayerTouchResponse.UnsupportedBoundary.R3_BOSS_STARTED_OWNER,assertThrows(CdPlayerTouchResponse.UnsupportedResponse.class,()->r.frame().runR31Body(word,context(r),call->fail("Unowned SPECIAL published Body"))).boundary());assertBefore(r.frame(),old);assertEquals(pool.slots(),r.pool().capture().slots());assertThrows(IllegalStateException.class,()->r.frame().runR31Body(word,context(r),APPLY));
        }
    }

    @ParameterizedTest(name="raw{0}/{1} never widens R31 ingress")
    @CsvSource({"0,1","1,1","256,0","256,2","256,129"})
    void badRawSourcesCannotAcquireTheNewBridge(int zoneAct,int period) throws Exception {
        var f=data();var e=withGates(TestCdR31PlayerBodyMainHurt.environment(f),zoneAct,period);assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.SOURCE_FAMILY,assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,()->CdPlayerFrameRuntime.fromR31aEntry(f.a(),TestCdR31PlayerBodyMainHurt.state(f,2,2,0,0),e,art(),clock())).boundary());
    }

    @ParameterizedTest(name="{0} genuine old entry/Init stays exact")
    @ValueSource(strings={"R11A","R11B","R12A","R12B","R13C","R13D"})
    void everyProtectedFamilyStillExecutesItsOriginalServicedInit(String family) throws Exception {
        try(var f=oldFrame(family)){
            var state=f.state();int n=service(f,0);var p=f.runBody(n);assertEquals(CdPlayerBody.Boundary.INIT_RETURN,p.result().boundary());assertEquals(2,f.state().motion().routine());assertEquals(f.stageSource().mappingAddress(),f.state().header().mappingAddress());assertEquals(0x780,f.state().header().tile());assertEquals(state.motion().centreX16_16(),f.state().motion().centreX16_16());assertTrue(p.sounds().isEmpty());assertTrue(f.display().isEmpty());
            int next=service(f,0);var before=before(f);var actualR31=data();var r31=new CdPlayerBody.R31Context(CdStagePlayerSource.fromR31aAssets(actualR31.a()),actualR31.a(),actualR31.a().terrain(),new CdObjectPool().capture(),Optional.empty(),CdPlayerObjectTouch.fromR31aAssets(actualR31.a()),new CdPlayerBody.ResponseContext(CdPlayerTouchResponse.fromR31aAssets(actualR31.a()),new CdPlayerBody.ContactGlobals(0,0,0),new CdPlayerTouchResponse.WorldGlobals(1,3,0,0,5000,0)),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),0x20,0x300);
            assertThrows(IllegalArgumentException.class,()->f.runR31Body(next,r31,call->fail("Protected family used R3 port")));assertBefore(f,before);
        }
    }

    private record Pair(CdPlayerBody.State state,CdPlayerBody.Environment environment){}
    private record Rig(CdR31aAssets assets,CdPlayerFrameRuntime frame,CdObjectPool pool,CdStageTerrain terrain)implements AutoCloseable{public void close(){frame.close();}}
    private record Before(CdPlayerBody.State state,CdPlayerBody.Environment environment,Optional<CdPlayerBody.DisplayPublication> display,CdPlayerArtRuntime.State art,CdPlayerFrameRuntime.VIntClock clock,boolean paused){}
    private static TestCdR31PlayerBodyMainHurt.Fixture data()throws Exception{return TestCdR31PlayerBodyMainHurt.actual();}
    private static Rig rig(int routine,int flags,Integer blocks,Pair pair)throws Exception{var d=data();var s=pair==null?TestCdR31PlayerBodyMainHurt.state(d,routine,flags,0,0):pair.state();var e=pair==null?TestCdR31PlayerBodyMainHurt.environment(d):pair.environment();var f=CdPlayerFrameRuntime.fromR31aEntry(d.a(),s,e,art(),clock());return new Rig(d.a(),f,new CdObjectPool(),blocks==null?d.a().terrain():TestCdR31PlayerBodyMainHurt.constructedCurrent(d,blocks));}
    private static CdPlayerBody.R31Context context(Rig r){return new CdPlayerBody.R31Context(r.frame().stageSource(),r.assets(),r.terrain(),r.pool().capture(),Optional.empty(),CdPlayerObjectTouch.fromR31aAssets(r.assets()),new CdPlayerBody.ResponseContext(CdPlayerTouchResponse.fromR31aAssets(r.assets()),new CdPlayerBody.ContactGlobals(0,0,0),new CdPlayerTouchResponse.WorldGlobals(1,3,0,0,5000,0)),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),0x20,0x300);}
    private static int service(CdPlayerFrameRuntime f,int held){f.bindArtPublication((request,current)->{assertEquals(0xF000,request.vramByteAddress());assertSame(current,f.artState());});var selection=f.prepareInput(held);f.serviceVBlank(selection.phase(),NOTHING,NOTHING,NOTHING);return f.advanceLevelMainFrame();}
    private static CdPlayerArtRuntime.State art(){byte[] cpu=new byte[768],resident=new byte[768];for(int i=0;i<768;i++){cpu[i]=(byte)(i*7+3);resident[i]=(byte)(i*11+5);}return new CdPlayerArtRuntime.State(cpu,resident,255,false);}
    private static CdPlayerFrameRuntime.VIntClock clock(){return new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(),0);}
    private static Before before(CdPlayerFrameRuntime f){return new Before(f.state(),f.environment(),f.display(),f.artState(),f.vIntClock(),f.paused());}
    private static void assertBefore(CdPlayerFrameRuntime f,Before b){assertSame(b.state(),f.state());assertSame(b.environment(),f.environment());assertSame(b.display(),f.display());assertSame(b.art(),f.artState());assertSame(b.clock(),f.vIntClock());assertEquals(b.paused(),f.paused());}
    private static void assertAdopted(CdPlayerFrameRuntime f,CdPlayerFrameRuntime.R31Publication p){assertSame(p.body().result().state(),f.state());assertSame(p.body().result().environment(),f.environment());assertSame(p.body().result().display(),f.display());}
    private static void assertShared(CdPlayerFrameRuntime.SharedState a,CdPlayerFrameRuntime.SharedState b){assertSame(a.owner(),b.owner());assertEquals(a.environment(),b.environment());assertEquals(a.paused(),b.paused());assertEquals(a.vIntClock(),b.vIntClock());assertArrayEquals(a.artState().cpuBuffer().copy(),b.artState().cpuBuffer().copy());assertArrayEquals(a.artState().residentBank().copy(),b.artState().residentBank().copy());assertEquals(a.artState().lastFrame(),b.artState().lastFrame());assertEquals(a.artState().dirty(),b.artState().dirty());}
    /** Full value replay comparison: current terrain/plane bytes are immutable source values,
     * intentionally not equal by Java reference after a separate genuine staged decode. */
    private static void assertReplay(CdPlayerFrameRuntime.R31Publication a,CdPlayerFrameRuntime.R31Publication b){
        var x=a.body().result();var y=b.body().result();assertEquals(x.state(),y.state());assertEquals(x.environment(),y.environment());assertEquals(x.display(),y.display());assertEquals(x.boundary(),y.boundary());assertEquals(x.modeBoundary(),y.modeBoundary());assertEquals(a.body().sounds(),b.body().sounds());assertEquals(x.effects().size(),y.effects().size());
        for(int i=0;i<x.effects().size();i++){var first=x.effects().get(i);var second=y.effects().get(i);assertEquals(first.getClass(),second.getClass());
            if(first instanceof CdPlayerBody.R31AirCollisionImage aa){var bb=(CdPlayerBody.R31AirCollisionImage)second;assertTerrain(aa.terrain(),bb.terrain());assertEquals(aa.before(),bb.before());assertEquals(aa.environment(),bb.environment());assertEquals(aa.result(),bb.result());}
            else if(first instanceof CdPlayerBody.R31PlaceBlock aa){var bb=(CdPlayerBody.R31PlaceBlock)second;assertEquals(aa.xWord(),bb.xWord());assertEquals(aa.yWord(),bb.yWord());assertEquals(aa.chunkByteOffset(),bb.chunkByteOffset());assertEquals(aa.previousWord(),bb.previousWord());assertEquals(aa.onscreen(),bb.onscreen());assertEquals(aa.planeColumn(),bb.planeColumn());assertEquals(aa.planeRow(),bb.planeRow());assertEquals(aa.planeWords().isPresent(),bb.planeWords().isPresent());if(aa.planeWords().isPresent())assertArrayEquals(aa.planeWords().orElseThrow().copy(),bb.planeWords().orElseThrow().copy());}
            else assertEquals(first,second);
        }
        assertTerrain(a.nativeValues().terrain(),b.nativeValues().terrain());assertEquals(a.nativeValues().snapshot().slots(),b.nativeValues().snapshot().slots());assertEquals(a.nativeValues().noBumperByte(),b.nativeValues().noBumperByte());assertEquals(a.nativeValues().contact(),b.nativeValues().contact());assertEquals(a.nativeValues().world(),b.nativeValues().world());
    }
    private static void assertTerrain(CdStageTerrain a,CdStageTerrain b){assertEquals(a.shape(),b.shape());assertArrayEquals(a.blocks().copy(),b.blocks().copy());assertArrayEquals(a.patterns().copy(),b.patterns().copy());assertArrayEquals(a.chunks().copy(),b.chunks().copy());assertArrayEquals(a.layout().ram().copy(),b.layout().ram().copy());assertArrayEquals(a.collision().ids().copy(),b.collision().ids().copy());assertArrayEquals(a.collision().angles().copy(),b.collision().angles().copy());assertArrayEquals(a.collision().heights().copy(),b.collision().heights().copy());assertArrayEquals(a.collision().widths().copy(),b.collision().widths().copy());}
    private static int index(List<CdPlayerBody.Effect> effects,Class<?> kind){for(int i=0;i<effects.size();i++)if(kind.isInstance(effects.get(i)))return i;return -1;}
    private static CdPlayerBody.Environment withGates(CdPlayerBody.Environment e,int word,int period){var m=e.movement();var g=m.gates();return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(),m.bounds(),new CdPlayerMovement.Gates(word,period,g.miniSonic(),g.waterSlide(),g.windTunnel(),g.speedShoes(),g.timeWarpDirection()),m.globals()),e.controls(),e.globals(),e.dynamicSlots());}
    private record Old(CdR11aAssets a,CdR11bAssets b,CdR12aTerrainAssets at,CdR12aPlayerAssets ap,CdR12bTerrainAssets bt,CdR12bPlayerAssets bp,CdR13FutureAssets c,CdR13FutureAssets d){}
    private static Old old;private static String oldRoot;
    private static synchronized Old oldAssets()throws Exception{String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional original USA CD source absent");if(old!=null&&root.equals(oldRoot))return old;var usa=Path.of(root).resolve("USA");assertTrue(Files.isDirectory(usa));List<Path> cues;try(var files=Files.list(usa)){cues=files.filter(q->q.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();}assertEquals(1,cues.size());try(var p=CdProgramAssets.open(cues.getFirst(),Optional.of(CdReleaseVariant.USA),Optional.empty())){old=new Old(CdR11aAssetLoader.load(p,CdReleaseVariant.USA,CdStagePayload.R11A.stage()),CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(p)),CdR12aTerrainAssets.fromPrograms(p),CdR12aPlayerAssets.fromPrograms(p),CdR12bTerrainAssets.fromPrograms(p),CdR12bPlayerAssets.fromPrograms(p),CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13C.stage()),CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13D.stage()));}oldRoot=root;return old;}
    private static CdPlayerFrameRuntime oldFrame(String family)throws Exception{var a=oldAssets();var d=data();var s=TestCdR31PlayerBodyMainHurt.state(d,0,0,0,0);var e=TestCdR31PlayerBodyMainHurt.environment(d);return switch(family){
        // R11A's original DEV component factory is an old-family control only, never R31 boot admission.
        case "R11A"->CdPlayerFrameRuntime.terrainOnlyDevelopment(a.a());case "R11B"->CdPlayerFrameRuntime.fromPastEntry(a.b(),s,withGates(e,0,0),art(),clock());case "R12A"->CdPlayerFrameRuntime.fromAct2Entry(a.at(),a.ap(),s,withGates(e,1,1),art(),clock());case "R12B"->CdPlayerFrameRuntime.fromAct2PastEntry(a.bt(),a.bp(),s,withGates(e,1,0),art(),clock());case "R13C"->CdPlayerFrameRuntime.fromAct3FutureEntry(a.c(),s,withGates(e,2,2),art(),clock());case "R13D"->CdPlayerFrameRuntime.fromAct3FutureEntry(a.d(),s,withGates(e,2,2),art(),clock());default->throw new AssertionError(family);};}
}
