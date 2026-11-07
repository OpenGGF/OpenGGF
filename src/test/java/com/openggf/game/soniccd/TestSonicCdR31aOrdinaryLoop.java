package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Tests FIRST; author Java was UNRUN; Root qualification is recorded in the implementation plan. Actual original-USA R31 assets/Frame/Body/controller/VBlank,
 * with explicitly CONSTRUCTED complete component ingress and private Ports controls only.
 * Returning from these component ports is NOT a full physical walk, HUD/art/plane service,
 * World, boot, lifecycle, current/pixel/audio or actual Module invocation completion. */
class TestSonicCdR31aOrdinaryLoop {
    private static final List<String> ORDER=List.of("BEFORE","PUBLISH","ART","ANIM","PLANES","SLOW","HUD","MUSIC","SETTLE","SPAWN","BODY","CAMERA","DRAW","TIME","PALETTE","SECTION","PREPARE","GLOBAL");

    @Test void firstRunUsesActualFrameInitFinishAndTheCompleteNativeSchedule() throws Exception {
        try(var h=new Harness()){
            var old=h.queue.cpuSat();h.loop.run(8);assertEquals(ORDER,h.order);assertEquals(Arrays.asList(SonicCdR31aOrdinaryLoop.Cut.values()),h.cuts);
            assertEquals(1,h.frame.environment().globals().levelFrames());assertEquals(1,h.frame.vIntClock().deltaSinceAdmittedCut());assertTrue(h.frame.vIntClock().absoluteValue().isEmpty());
            assertEquals(CdPlayerBody.Boundary.INIT_RETURN,h.body.result().boundary());assertEquals(2,h.frame.state().motion().routine());assertEquals(h.assets.range(CdR31aAssets.Asset.MAP_SPR_SONIC).nativeAddress(),h.frame.state().header().mappingAddress());
            assertSame(old,h.published.orElseThrow());assertSame(h.published,h.settled);assertNotSame(old,h.queue.cpuSat());assertEquals(1,h.beforeReads);assertEquals(8,h.frame.environment().controls().rawHeld());assertEquals(8,h.frame.environment().controls().rawTap());
        }
    }

    @Test void healthySecondRunUsesTheGenuineScopedR31BodyBridgeRatherThanAFakeWalkResult() throws Exception {
        try(var h=new Harness()){h.loop.run(0);h.order.clear();h.cuts.clear();h.loop.run(8);assertEquals(CdPlayerBody.Boundary.MAIN_RETURN,h.body.result().boundary());assertNotNull(h.r31);assertSame(h.r31.body(),h.body);assertEquals(2,h.frame.environment().globals().levelFrames());assertEquals(2,h.frame.vIntClock().deltaSinceAdmittedCut());assertEquals(1,Collections.frequency(h.order,"BODY"));assertEquals(1,h.body.result().effects().stream().filter(e->e instanceof CdPlayerBody.CallReturned r&&r.call()==CdPlayerBody.NativeCall.MODE).count());assertTrue(h.order.indexOf("BODY")<h.order.indexOf("CAMERA"));}
    }

    @Test void pauseHoldReleaseAndUnpauseKeepOriginalRawEdgesAndSkipEveryMainTail() throws Exception {
        try(var h=new Harness()){
            for(int held:new int[]{128,128,0}){h.order.clear();h.cuts.clear();h.loop.run(held);assertTrue(h.frame.paused());assertNull(h.body);assertEquals(0,h.frame.environment().globals().levelFrames());assertFalse(h.order.contains("SPAWN"));assertFalse(h.order.contains("TIME"));assertEquals(SonicCdR31aOrdinaryLoop.Cut.RETURN,h.cuts.getLast());}
            h.order.clear();h.loop.run(128);assertFalse(h.frame.paused());assertEquals(128,h.frame.environment().controls().rawTap());assertEquals(CdPlayerBody.Boundary.INIT_RETURN,h.body.result().boundary());assertEquals(1,h.frame.environment().globals().levelFrames());assertEquals(4,h.frame.vIntClock().deltaSinceAdmittedCut());assertEquals(1,Collections.frequency(h.order,"BODY"));
        }
    }

    @Test void invalidHeldBytesArePreflightErrorsAndDoNotRetireTheHealthyKernel() throws Exception {
        try(var h=new Harness()){var actor=h.frame.captureActor();var shared=h.frame.captureShared();for(int held:new int[]{-1,256,65535,Integer.MIN_VALUE})assertThrows(IllegalArgumentException.class,()->h.loop.run(held));assertTrue(h.order.isEmpty());assertTrue(h.cuts.isEmpty());assertEquals(actor,h.frame.captureActor());assertShared(shared,h.frame.captureShared());h.loop.run(8);assertEquals(ORDER,h.order);}
    }

    @Test void caughtReentryAtEveryCutIncludingInvalidNestedInputCannotPoisonTheOuterRun() throws Exception {
        try(var h=new Harness()){h.nested=true;h.loop.run(8);assertEquals(ORDER,h.order);assertEquals(2*SonicCdR31aOrdinaryLoop.Cut.values().length,h.nestedRejected);assertEquals(1,h.beforeReads);assertEquals(1,h.frame.vIntClock().deltaSinceAdmittedCut());}
    }

    @Test void everyPortRuntimeExceptionAndErrorPreservesItsExactPrefixAndRetires() throws Exception {
        for(boolean error:new boolean[]{false,true})for(String stop:ORDER)try(var h=new Harness()){
            h.stop=stop;h.original=error?new AssertionError("original "+stop):new IllegalArgumentException("original "+stop);var thrown=assertThrows(Throwable.class,()->h.loop.run(8));assertSame(h.original,thrown);assertEquals(ORDER.subList(0,ORDER.indexOf(stop)+1),h.order);
            int at=ORDER.indexOf(stop);assertEquals(at>=ORDER.indexOf("MUSIC")?1:0,h.frame.vIntClock().deltaSinceAdmittedCut(),"Only the original completed Finish advances the clock");assertEquals(at>=ORDER.indexOf("SPAWN")?1:0,h.frame.environment().globals().levelFrames());assertEquals(at>=ORDER.indexOf("PUBLISH")?8:0,h.frame.environment().controls().rawHeld());assertEquals(at>=ORDER.indexOf("PUBLISH")&&at<=ORDER.indexOf("MUSIC"),h.frame.closed(),"Original Frame service fault retirement is preserved");
            var before=List.copyOf(h.order);assertThrows(IllegalStateException.class,()->h.loop.run(0));assertEquals(before,h.order);if(at>ORDER.indexOf("BODY"))assertEquals(2,h.frame.state().motion().routine());
        }
    }

    @Test void everyAuthorityCutFaultStopsBeforeItsOperationAndCannotBeRetried() throws Exception {
        for(var cut:SonicCdR31aOrdinaryLoop.Cut.values())try(var h=new Harness()){
            h.cutStop=cut;h.original=new AssertionError("stale original authority "+cut);assertSame(h.original,assertThrows(AssertionError.class,()->h.loop.run(8)));assertEquals(cut,h.cuts.getLast());var old=List.copyOf(h.cuts);assertThrows(IllegalStateException.class,()->h.loop.run(8));assertEquals(old,h.cuts);
        }
    }

    @Test void actualPortCustodyMayExpireAfterBodyAndItsOriginalFaultIsNotNormalized() throws Exception {
        try(var h=new Harness()){h.cutStop=SonicCdR31aOrdinaryLoop.Cut.POST_OBJECT_CAMERA;h.original=new UnsupportedOperationException("constructed actual source/epoch owner expired");assertSame(h.original,assertThrows(UnsupportedOperationException.class,()->h.loop.run(0)));assertEquals(2,h.frame.state().motion().routine());assertFalse(h.order.contains("CAMERA"));assertFalse(h.order.contains("DRAW"));assertFalse(h.order.contains("SECTION"));}
    }

    @Test void selectedOldCpuIsBorrowedOnceAndNeverReplacedByTheNowCpu() throws Exception {
        try(var h=new Harness()){var old=h.queue.cpuSat();h.replaceNowAtPublication=true;h.loop.run(8);assertNotSame(old,h.replacement);assertNotSame(h.replacement,h.queue.cpuSat());assertSame(old,h.published.orElseThrow());assertSame(old,h.settled.orElseThrow());assertSame(h.published,h.settled);assertEquals(1,h.beforeReads);assertEquals(0,old.spriteCount());assertEquals(640,old.bytes().length);}
    }

    @Test void absentOldCpuRemainsAbsentAcrossBothPublicationAndSettlement() throws Exception {
        try(var h=new Harness()){h.selectAbsent=true;h.loop.run(0);assertTrue(h.published.isEmpty());assertSame(h.published,h.settled);assertEquals(1,h.beforeReads);assertNotNull(h.queue.cpuSat());}
    }

    @Test void nativeWordFrameCounterAndDeclaredComponentClockWrapThroughFrameOnly() throws Exception {
        try(var h=new Harness(65535,0,0,new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xFFFF_FFFFL),0))){h.loop.run(0);assertEquals(0,h.body.result().environment().globals().levelFrames());assertEquals(0,h.actualBodyWord);assertEquals(0,h.frame.vIntClock().absoluteValue().orElseThrow());assertEquals(1,h.frame.vIntClock().deltaSinceAdmittedCut());}
    }

    @Test void fullHeldByteAndNativeTapAreServicedWithoutKernelInputReconstruction() throws Exception {
        try(var h=new Harness(0,0x20,1,clock())){h.loop.run(0xA0);assertFalse(h.frame.paused());assertEquals(0xA0,h.frame.environment().controls().rawHeld());assertEquals(0x80,h.frame.environment().controls().rawTap());assertEquals(CdPlayerBody.Boundary.INIT_RETURN,h.body.result().boundary());}
    }

    @Test void nullForeignSameImageAndClosedFrameRejectBeforeAnyCallerPort() throws Exception {
        try(var h=new Harness()){
            assertThrows(NullPointerException.class,()->new SonicCdR31aOrdinaryLoop(null,h.assets,h));assertThrows(NullPointerException.class,()->new SonicCdR31aOrdinaryLoop(h.frame,null,h));assertThrows(NullPointerException.class,()->new SonicCdR31aOrdinaryLoop(h.frame,h.assets,null));assertThrows(IllegalArgumentException.class,()->new SonicCdR31aOrdinaryLoop(h.frame,actual().get(1),h));assertTrue(h.cuts.isEmpty());h.frame.close();assertThrows(IllegalStateException.class,()->new SonicCdR31aOrdinaryLoop(h.frame,h.assets,h));assertThrows(IllegalStateException.class,()->h.loop.run(0));assertTrue(h.order.isEmpty());
        }
    }

    @Test void healthyFrameAndCpuValueRestoreReplaysWithoutACapturedLoopParticipant() throws Exception {
        try(var h=new Harness()){var actor=h.frame.captureActor();var shared=h.frame.captureShared();var cpu=h.queue.captureClosedCut();h.loop.run(8);var body=h.body;var endActor=h.frame.captureActor();var endShared=h.frame.captureShared();var order=List.copyOf(h.order);h.frame.restoreActor(actor);h.frame.restoreShared(shared);h.queue.restore(cpu);h.order.clear();h.cuts.clear();h.loop.run(8);assertEquals(order,h.order);assertEquals(body,h.body);assertEquals(endActor,h.frame.captureActor());assertShared(endShared,h.frame.captureShared());assertEquals(1,h.frame.vIntClock().deltaSinceAdmittedCut());}
    }

    @Test void restoringFrameValuesDoesNotReviveAFailedSchedulingKernel() throws Exception {
        try(var h=new Harness()){var actor=h.frame.captureActor();var shared=h.frame.captureShared();h.stop="GLOBAL";h.original=new IllegalStateException("original terminal component port");assertSame(h.original,assertThrows(IllegalStateException.class,()->h.loop.run(8)));h.frame.restoreActor(actor);h.frame.restoreShared(shared);h.stop=null;var before=List.copyOf(h.order);assertThrows(IllegalStateException.class,()->h.loop.run(0));assertEquals(before,h.order);}
    }

    @Test void reachedNativeLevelStartTransferIsAnHonestPortFenceBeforeTheOrdinaryTail() throws Exception {
        try(var h=new Harness()){h.transfer=true;var original=h.transferStop;assertSame(original,assertThrows(NativeTransferUnowned.class,()->h.loop.run(8)));assertEquals(ORDER.subList(0,ORDER.indexOf("CAMERA")+1),h.order);assertEquals(2,h.frame.state().motion().routine());assertFalse(h.order.contains("DRAW"));assertFalse(h.order.contains("TIME"));assertThrows(IllegalStateException.class,()->h.loop.run(0));}
    }

    @Test void nullBorrowedOptionalIsNotConvertedToAnEmptySelectedPicture() throws Exception {
        try(var h=new Harness()){h.nullBorrow=true;var actor=h.frame.captureActor();var shared=h.frame.captureShared();assertThrows(NullPointerException.class,()->h.loop.run(8));assertEquals(List.of("BEFORE"),h.order);assertEquals(actor,h.frame.captureActor());assertShared(shared,h.frame.captureShared());assertThrows(IllegalStateException.class,()->h.loop.run(0));}
    }

    @Test void invalidNativeTimeStopWidthFailsAfterItsActualReadWithoutTailDefaults() throws Exception {
        for(int raw:new int[]{-1,65536})try(var h=new Harness()){h.timeStop=raw;assertThrows(IllegalArgumentException.class,()->h.loop.run(0));assertEquals("TIME",h.order.getLast());assertFalse(h.order.contains("PALETTE"));assertFalse(h.order.contains("SECTION"));assertEquals(1,h.frame.environment().globals().levelFrames());}
    }

    @Test void staleRawSourceCannotBorrowAnOrdinaryServiceFromItsRetainedDataRelation() throws Exception {
        try(var h=new Harness()){var s=h.frame.captureShared();var e=s.environment();var m=e.movement();var g=m.gates();var bad=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(),m.bounds(),new CdPlayerMovement.Gates(0x100,0x81,g.miniSonic(),g.waterSlide(),g.windTunnel(),g.speedShoes(),g.timeWarpDirection()),m.globals()),e.controls(),e.globals(),e.dynamicSlots());h.frame.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(),bad,s.artState(),s.paused(),s.vIntClock()));assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.SOURCE_FAMILY,assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,()->h.loop.run(8)).boundary());assertEquals(List.of("BEFORE"),h.order);assertEquals(0,h.frame.environment().controls().rawHeld());assertEquals(0,h.frame.vIntClock().deltaSinceAdmittedCut());assertThrows(IllegalStateException.class,()->h.loop.run(0));}
    }

    @Test void kernelAddsOnlySchedulingReferencesAndFlagsWithoutValueOrCaptureState() throws Exception {
        assertFalse(Modifier.isPublic(SonicCdR31aOrdinaryLoop.class.getModifiers()));assertEquals(List.of(3),Arrays.stream(SonicCdR31aOrdinaryLoop.class.getDeclaredConstructors()).map(c->c.getParameterCount()).toList());assertTrue(Arrays.stream(SonicCdR31aOrdinaryLoop.class.getDeclaredMethods()).noneMatch(m->Modifier.isPublic(m.getModifiers())));assertEquals(20,SonicCdR31aOrdinaryLoop.Cut.values().length);assertEquals(18,SonicCdR31aOrdinaryLoop.Ports.class.getDeclaredMethods().length);
        var types=Arrays.stream(SonicCdR31aOrdinaryLoop.class.getDeclaredFields()).map(f->f.getType().getSimpleName()).sorted().toList();assertEquals(List.of("CdPlayerFrameRuntime","CdR31aAssets","Ports","boolean","boolean"),types);assertTrue(Arrays.stream(SonicCdR31aOrdinaryLoop.Ports.class.getDeclaredMethods()).noneMatch(m->m.isDefault()));
    }

    @ParameterizedTest(name="native timeStop WORD {0}")
    @ValueSource(ints={0,1,256,32768,65535})
    void completeWordTimeStopGatesOnlyCyclesAndKeepsSectionPrepareAndAnimations(int word) throws Exception {
        try(var h=new Harness()){h.timeStop=word;h.loop.run(0);assertEquals(word==0,h.order.contains("PALETTE"));assertEquals(1,Collections.frequency(h.order,"TIME"));assertTrue(h.order.indexOf("TIME")<h.order.indexOf("SECTION"));assertTrue(h.order.indexOf("SECTION")<h.order.indexOf("PREPARE"));assertTrue(h.order.indexOf("PREPARE")<h.order.indexOf("GLOBAL"));if(word!=0)assertFalse(h.cuts.contains(SonicCdR31aOrdinaryLoop.Cut.PALETTE_CYCLE));}
    }

    private static final class NativeTransferUnowned extends UnsupportedOperationException {private NativeTransferUnowned(){super("Constructed actual Main405/408 LevelStart owner unavailable");}}
    private static final class Harness implements SonicCdR31aOrdinaryLoop.Ports,AutoCloseable {
        final CdR31aAssets assets;final CdPlayerFrameRuntime frame;final SonicCdR31aOrdinaryLoop loop;final CdObjectDrawQueue queue;final CdObjectPool pool=new CdObjectPool();
        CdStageTerrain terrain;final List<String> order=new ArrayList<>();final List<SonicCdR31aOrdinaryLoop.Cut> cuts=new ArrayList<>();Optional<CdObjectDrawQueue.CpuSat> borrowed,published,settled;CdObjectDrawQueue.CpuSat replacement;
        String stop;Throwable original;SonicCdR31aOrdinaryLoop.Cut cutStop;boolean nested,replaceNowAtPublication,selectAbsent,nullBorrow,transfer;int nestedRejected,beforeReads,timeStop,actualBodyWord=-1;long beforeVintDelta;CdPlayerFrameRuntime.BodyPublication body;CdPlayerFrameRuntime.R31Publication r31;final NativeTransferUnowned transferStop=new NativeTransferUnowned();
        Harness()throws Exception{this(0,0,0,clock());}
        Harness(int frames,int raw,int locked,CdPlayerFrameRuntime.VIntClock clock)throws Exception{
            assets=actual().getFirst();terrain=assets.terrain();var start=assets.initialCentre();var b=assets.bounds();
            var actor=new CdPlayerBody.State(new CdPlayerMotionState((start.centreX()<<16)|0x1234,(start.centreY()<<16)|0x5678,0,0,0,0,0,0,0,9,19,0,0,0,0,0,0,0,0,0),new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(1,0,0,0,0),new CdPlayerBody.Timers(0,0,0,0),new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]),0),0xA5,new CdPlayerBody.ObjectLinks(0,0),0x5A);
            var env=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0,0,0),new CdPlayerMovement.Bounds(b.left(),b.right(),b.bottom(),0),new CdPlayerMovement.Gates(0x100,1,0,0,0,0,0),new CdPlayerMovement.Globals(0,b.cameraCentreY(),0,0,0,0,0,0,0,0)),new CdPlayerBody.Controls(raw,0,0,0,locked),new CdPlayerBody.Globals(frames,160,0,0,0,0,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
            byte[] cpu=new byte[768],resident=new byte[768];for(int i=0;i<768;i++){cpu[i]=(byte)(i*7+3);resident[i]=(byte)(i*11+5);}frame=CdPlayerFrameRuntime.fromR31aEntry(assets,actor,env,new CdPlayerArtRuntime.State(cpu,resident,0,true),clock);frame.bindArtPublication((request,value)->{assertEquals(0xF000,request.vramByteAddress());assertSame(value,frame.artState());record("ART");});
            var mapping=CdObjectMappings.fromR31aAssets(assets,CdR31aObjectAssets.fromAssets(assets));byte[] sat=new byte[640];for(int i=0;i<640;i++)sat[i]=(byte)(i*3+7);
            queue=new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots(){public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef r){throw new AssertionError("Empty component queue must not dereference slots");}public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef r,boolean v){throw new AssertionError("Empty queue must not write slot bits");}},mapping,sat);finishEmpty();loop=new SonicCdR31aOrdinaryLoop(frame,assets,this);
        }
        void record(String s){order.add(s);if(s.equals(stop))raise(original);}
        public void requireCurrent(SonicCdR31aOrdinaryLoop.Cut c,CdPlayerFrameRuntime f,CdR31aAssets a){assertSame(frame,f);assertSame(assets,a);cuts.add(c);if(c==cutStop)raise(original);if(nested){assertThrows(IllegalStateException.class,()->loop.run(0));assertThrows(IllegalStateException.class,()->loop.run(256));nestedRejected+=2;}}
        public Optional<CdObjectDrawQueue.CpuSat> beforeSelector8(){record("BEFORE");beforeReads++;beforeVintDelta=frame.vIntClock().deltaSinceAdmittedCut();borrowed=selectAbsent?Optional.empty():Optional.of(queue.cpuSat());return nullBorrow?null:borrowed;}
        public void publishLevel(Optional<CdObjectDrawQueue.CpuSat> cpu){record("PUBLISH");assertSame(borrowed,cpu);published=cpu;assertEquals(beforeVintDelta,frame.vIntClock().deltaSinceAdmittedCut(),"Actual publication precedes this service Finish");if(replaceNowAtPublication)replacement=finishEmpty();}
        public void updateAnimTiles(){record("ANIM");}public void copyCameraAndDrawLevel(){record("PLANES");}public void serviceSlowPlc(){record("SLOW");}public void updateHud(){record("HUD");}public void mainMusic(){record("MUSIC");}
        public void settleSelectedCpu(Optional<CdObjectDrawQueue.CpuSat> cpu){record("SETTLE");assertSame(borrowed,cpu);assertSame(published,cpu);settled=cpu;}
        public void spawnObjects(){record("SPAWN");}
        public void runObjects(int word){record("BODY");actualBodyWord=word;if(frame.state().motion().routine()==0)body=frame.runBody(word);else{
            var context=new CdPlayerBody.R31Context(frame.stageSource(),assets,terrain,pool.capture(),Optional.empty(),CdPlayerObjectTouch.fromR31aAssets(assets),new CdPlayerBody.ResponseContext(CdPlayerTouchResponse.fromR31aAssets(assets),new CdPlayerBody.ContactGlobals(0,0,0),new CdPlayerTouchResponse.WorldGlobals(1,3,0,0,5000,0)),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),0x20,0x300);
            r31=frame.runR31Body(word,context,call->{var next=call.publication().nativeValues();pool.validateRestore(next.snapshot());call.applyNative();pool.restore(next.snapshot());terrain=next.terrain();});body=r31.body();
        }}
        public void postObjectCamera(){record("CAMERA");if(transfer)throw transferStop;}public void drawObjects(){record("DRAW");finishEmpty();}public int timeStopWord(){record("TIME");return timeStop;}public void paletteCycle(){record("PALETTE");}public void updateSectionArt(){record("SECTION");}public void preparePlc(){record("PREPARE");}public void updateGlobalAnimations(){record("GLOBAL");}
        CdObjectDrawQueue.CpuSat finishEmpty(){queue.beginPass();var camera=new CdObjectDrawQueue.CameraWords(0,0);return queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera,camera,camera));}
        public void close(){frame.close();queue.close();}
    }
    private static void raise(Throwable original){if(original instanceof RuntimeException r)throw r;if(original instanceof Error e)throw e;throw new AssertionError("Constructed unchecked original required");}
    private static CdPlayerFrameRuntime.VIntClock clock(){return new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(),0);}
    private static void assertShared(CdPlayerFrameRuntime.SharedState a,CdPlayerFrameRuntime.SharedState b){assertSame(a.owner(),b.owner());assertEquals(a.environment(),b.environment());assertEquals(a.paused(),b.paused());assertEquals(a.vIntClock(),b.vIntClock());assertArrayEquals(a.artState().cpuBuffer().copy(),b.artState().cpuBuffer().copy());assertArrayEquals(a.artState().residentBank().copy(),b.artState().residentBank().copy());assertEquals(a.artState().lastFrame(),b.artState().lastFrame());assertEquals(a.artState().dirty(),b.artState().dirty());}
    private static List<CdR31aAssets> cached;private static String cachedRoot;
    private static synchronized List<CdR31aAssets> actual()throws Exception{String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional original-USA media absent");if(cached!=null&&root.equals(cachedRoot))return cached;var usa=Path.of(root).resolve("USA");assertTrue(Files.isDirectory(usa),"Configured source must exist");List<Path> cues;try(var files=Files.list(usa)){cues=files.filter(f->f.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();}assertEquals(1,cues.size());try(var programs=CdProgramAssets.open(cues.getFirst(),Optional.of(CdReleaseVariant.USA),Optional.empty())){cached=List.of(CdR31aAssets.fromPrograms(programs,CdStagePayload.R31A.stage()),CdR31aAssets.fromPrograms(programs,CdStagePayload.R31A.stage()));}cachedRoot=root;return cached;}
}
