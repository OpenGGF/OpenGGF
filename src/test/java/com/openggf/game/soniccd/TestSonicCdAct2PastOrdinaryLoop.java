package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;
import static com.openggf.game.soniccd.SonicCdAct2PastOrdinaryLoop.Cut.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests FIRST, Java UNRUN by author. Genuine source-bound Frame and its actual service/primary Init.
 * Ports/CPU images/native ingress are explicit constructed component controls, never live Core/Host/
 * World/epoch/physical-walk or current-art proof. Root owns actual all128 dispatch and adoption.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestSonicCdAct2PastOrdinaryLoop {
    private static final List<String> ORDER=List.of("BEFORE8","PUBLISH","SONIC_ART","ANIM","COPY_DRAW","SLOW_PLC","HUD",
            "MUSIC","SETTLE","SPAWN","WALK","POST_CAMERA","SAT","PALETTE_IF","SECTION","PREPARE","GLOBAL");

    @Test void constructorKeepsTheActualFramePairAndPerformsNoServiceOrPortWork() throws Exception {
        var pair=retail();var frame=frame(pair.terrain(),pair.player(),1,0,0,false);var actor=frame.captureActor();var shared=frame.captureShared();
        var h=new Harness(pair.terrain(),pair.player(),frame,true);assertTrue(frame.stageSource().ownsAct2Past(pair.terrain(),pair.player()));
        assertEquals(actor,frame.captureActor());assertEquals(shared,frame.captureShared());assertTrue(h.events.isEmpty());assertTrue(h.checks.isEmpty());assertNull(h.primary);
        assertEquals(0,frame.vIntClock().deltaSinceAdmittedCut());assertEquals(0,frame.environment().globals().levelFrames());
    }

    @Test void constructorRejectsNullForeignEqualIdentityLegacyAndClosedFramesBeforePorts() throws Exception {
        var pair=retail();var h=harness(false);var frame=h.frame;var actor=frame.captureActor();var shared=frame.captureShared();
        assertThrows(NullPointerException.class,()->new SonicCdAct2PastOrdinaryLoop(null,pair.terrain(),pair.player(),h));
        assertThrows(NullPointerException.class,()->new SonicCdAct2PastOrdinaryLoop(frame,null,pair.player(),h));
        assertThrows(NullPointerException.class,()->new SonicCdAct2PastOrdinaryLoop(frame,pair.terrain(),null,h));
        assertThrows(NullPointerException.class,()->new SonicCdAct2PastOrdinaryLoop(frame,pair.terrain(),pair.player(),null));
        assertEquals(pair.terrain().identity(),pair.otherTerrain().identity());assertEquals(pair.player().identity(),pair.otherPlayer().identity());
        assertThrows(IllegalArgumentException.class,()->new SonicCdAct2PastOrdinaryLoop(frame,pair.otherTerrain(),pair.player(),h));
        assertThrows(IllegalArgumentException.class,()->new SonicCdAct2PastOrdinaryLoop(frame,pair.terrain(),pair.otherPlayer(),h));
        var old=CdPlayerFrameRuntime.fromPastEntry(pair.past(),state(),environment(0,0,0),art(false),clock());
        assertThrows(IllegalArgumentException.class,()->new SonicCdAct2PastOrdinaryLoop(old,pair.terrain(),pair.player(),h));
        assertEquals(actor,frame.captureActor());assertEquals(shared,frame.captureShared());assertTrue(h.events.isEmpty());assertTrue(h.checks.isEmpty());
        frame.close();assertThrows(IllegalStateException.class,()->new SonicCdAct2PastOrdinaryLoop(frame,pair.terrain(),pair.player(),h));
    }

    @Test void unpausedServiceUsesRealFrameOrderFinishClockMainIncrementAndPrimaryInitExactlyOnce() throws Exception {
        var h=harness(true);var before=h.frame.state();var ingress=h.frame.artState();
        h.onCheck=()->{var cut=h.checks.getLast();if(cut==PUBLISH_LEVEL){assertEquals(new CdPlayerBody.Controls(8,8,0,0,0),h.frame.environment().controls());assertEquals(0,h.frame.vIntClock().deltaSinceAdmittedCut());assertEquals(3,h.frame.environment().movement().globals().boredTimer());}
            if(cut==UPDATE_ANIM_TILES){assertFalse(h.frame.artState().dirty());assertEquals(0,h.frame.vIntClock().deltaSinceAdmittedCut());}
            if(cut==MAIN_MUSIC||cut==SETTLE_SELECTED_CPU){assertEquals(1,h.frame.vIntClock().deltaSinceAdmittedCut());assertEquals(4,h.frame.environment().movement().globals().boredTimer());assertEquals(6,h.frame.environment().globals().timeWarpTimer());assertEquals(0,h.frame.environment().globals().levelFrames());assertNull(h.primary);}};
        h.loop.run(8);
        assertEquals(ORDER,h.events);assertEquals(Arrays.asList(SonicCdAct2PastOrdinaryLoop.Cut.values()),h.checks);
        assertEquals(1,h.engineVBlanks);assertEquals(1,h.frame.vIntClock().deltaSinceAdmittedCut());assertEquals(OptionalLong.of(0xABCDEF01L),h.frame.vIntClock().absoluteValue());
        assertEquals(1,h.frame.environment().globals().levelFrames());assertEquals(4,h.frame.environment().movement().globals().boredTimer());assertEquals(6,h.frame.environment().globals().timeWarpTimer());
        assertEquals(new CdPlayerBody.Controls(8,8,0,0,0),h.frame.environment().controls());assertEquals(CdPlayerBody.Boundary.INIT_RETURN,h.primary.result().boundary());
        assertEquals(2,h.frame.state().motion().routine());assertEquals(new CdPlayerBody.Header(1,0x230F60,0x780,2,0x18),h.frame.state().header());
        assertEquals(before.motion().centreX16_16(),h.frame.state().motion().centreX16_16());assertEquals(before.motion().centreY16_16(),h.frame.state().motion().centreY16_16());
        assertSame(before.history(),h.frame.state().history());assertTrue(h.frame.display().isEmpty());assertFalse(h.frame.artState().dirty());
        assertArrayEquals(ingress.cpuBuffer().copy(),h.frame.artState().cpuBuffer().copy());
        assertArrayEquals(Arrays.copyOfRange(ingress.residentBank().copy(),0x2E0,0x300),Arrays.copyOfRange(h.frame.artState().residentBank().copy(),0x2E0,0x300));
        assertEquals(1,h.published.size());assertSame(h.borrowed,h.published.getFirst());assertSame(h.borrowed,h.settled.getFirst());
        assertNotSame(h.borrowed,h.currentCpu,"The later SAT port owns its distinct next CPU image");assertFalse(h.frame.closed());
        assertThrows(IllegalStateException.class,()->h.frame.runBody(2));assertDoesNotThrow(h.frame::captureShared);
    }

    @Test void wrappedMainFrameAndLongClockComeOnlyFromTheExistingFrameReturn() throws Exception {
        var pair=retail();var frame=CdPlayerFrameRuntime.fromAct2PastEntry(pair.terrain(),pair.player(),state(),environment(1,0,0xFFFF),art(false),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xFFFFFFFFL),0));
        var h=new Harness(pair.terrain(),pair.player(),frame,true);h.expectedMainFrame=0;h.loop.run(8);
        assertEquals(0,frame.environment().globals().levelFrames());assertEquals(0,h.primary.result().environment().globals().levelFrames());assertEquals(OptionalLong.of(0),frame.vIntClock().absoluteValue());assertEquals(1,frame.vIntClock().deltaSinceAdmittedCut());assertEquals(1,h.engineVBlanks);
    }

    @Test void theSameBorrowedPriorCpuReferenceFlowsThroughPublishAndSettleBeforeTheNextSat() throws Exception {
        for(boolean absent:new boolean[]{false,true}){var h=harness(false);if(absent)h.currentCpu=Optional.empty();var old=h.currentCpu;h.loop.run(0);var next=h.currentCpu;
            assertNotSame(old,next);assertSame(old,h.published.getFirst());assertSame(old,h.settled.getFirst());assertSame(old,h.borrowed);
            h.loop.run(0x80);assertTrue(h.frame.paused());assertEquals(2,h.published.size());assertSame(next,h.published.get(1));assertSame(next,h.settled.get(1));
            assertEquals(2,h.frame.vIntClock().deltaSinceAdmittedCut());assertEquals(1,h.frame.environment().globals().levelFrames());
            assertEquals(1,h.events.stream().filter("SAT"::equals).count());assertEquals(2,h.engineVBlanks);
        }
    }

    @Test void pauseHoldReleaseAndUnpauseUseOldPauseFinishAndNeverInventAnotherMainTick() throws Exception {
        var h=harness(false);
        for(int held:new int[]{0x80,0x80,0}){h.loop.run(held);assertTrue(h.frame.paused());assertNull(h.primary);assertEquals(0,h.frame.environment().globals().levelFrames());}
        assertEquals(3,h.frame.vIntClock().deltaSinceAdmittedCut());assertEquals(4,h.frame.environment().movement().globals().boredTimer());assertEquals(6,h.frame.environment().globals().timeWarpTimer());
        h.loop.run(0x80);assertFalse(h.frame.paused());assertNotNull(h.primary);assertEquals(4,h.frame.vIntClock().deltaSinceAdmittedCut());assertEquals(1,h.frame.environment().globals().levelFrames());
        assertEquals(4,h.engineVBlanks);assertEquals(List.of(true,true,true,false),h.musicPauseValues);
        assertEquals(4,h.frame.environment().movement().globals().boredTimer());assertEquals(6,h.frame.environment().globals().timeWarpTimer());
        assertEquals(1,h.events.stream().filter("SPAWN"::equals).count());assertEquals(1,h.events.stream().filter("WALK"::equals).count());
        assertEquals(4,h.settled.size());assertEquals(4,h.checks.stream().filter(RETURN::equals).count());
    }

    @Test void aPublicationFailureKeepsCommittedInputButNeverSettlesOrRunsArtFinishOrTail() throws Exception {
        var h=harness(true);h.stopAt="PUBLISH";var before=h.frame.state();var art=h.frame.artState();
        assertSame(h.fault,assertThrows(IllegalArgumentException.class,()->h.loop.run(8)));
        assertEquals(List.of("BEFORE8","PUBLISH"),h.events);assertTrue(h.published.isEmpty());assertTrue(h.settled.isEmpty());
        assertEquals(8,h.frame.environment().controls().rawHeld());assertEquals(8,h.frame.environment().controls().rawTap());assertSame(before,h.frame.state());assertSame(art,h.frame.artState());
        assertEquals(0,h.frame.vIntClock().deltaSinceAdmittedCut());assertEquals(0,h.engineVBlanks);assertTrue(h.frame.closed());assertTerminal(h);
    }

    @Test void everyReachedVintAndTailPortFailurePreservesItsOriginalExceptionAndStopsLaterCalls() throws Exception {
        for(String stop:ORDER){if(stop.equals("BEFORE8")||stop.equals("PUBLISH")||stop.equals("WALK"))continue;
            var h=harness(true);h.stopAt=stop;assertSame(h.fault,assertThrows(IllegalArgumentException.class,()->h.loop.run(8)),stop);
            assertEquals(ORDER.subList(0,ORDER.indexOf(stop)+1),h.events,stop);assertTerminal(h);
            assertEquals(ORDER.indexOf(stop)>=ORDER.indexOf("MUSIC")?1:0,h.frame.vIntClock().deltaSinceAdmittedCut(),stop);
            assertEquals(ORDER.indexOf(stop)>=ORDER.indexOf("SPAWN")?1:0,h.frame.environment().globals().levelFrames(),stop);
            assertEquals(ORDER.indexOf(stop)>ORDER.indexOf("WALK")?2:0,h.frame.state().motion().routine(),stop);
            if(ORDER.indexOf(stop)<=ORDER.indexOf("MUSIC"))assertTrue(h.settled.isEmpty(),stop);
        }
    }

    @Test void bodyPortFailureAfterActualPrimaryInitRetainsThatPrefixAndBypassesAllLaterTail() throws Exception {
        var h=harness(false);h.afterPrimary=()->{throw h.fault;};assertSame(h.fault,assertThrows(IllegalArgumentException.class,()->h.loop.run(8)));
        assertNotNull(h.primary);assertEquals(CdPlayerBody.Boundary.INIT_RETURN,h.primary.result().boundary());assertEquals(0x230F60,h.frame.state().header().mappingAddress());
        assertEquals(2,h.frame.state().motion().routine());assertEquals(1,h.frame.environment().globals().levelFrames());assertEquals(1,h.frame.vIntClock().deltaSinceAdmittedCut());
        assertEquals(List.of("BEFORE8","PUBLISH","ANIM","COPY_DRAW","SLOW_PLC","HUD","MUSIC","SETTLE","SPAWN","WALK"),h.events);
        assertEquals(1,h.settled.size());assertFalse(h.frame.closed(),"Root owns terminal Frame/Core/Host teardown; the kernel does not roll back this prefix");assertTerminal(h);
        var e=harness(false);var error=new AssertionError("constructed post-primary Error");e.afterPrimary=()->{throw error;};assertSame(error,assertThrows(AssertionError.class,()->e.loop.run(8)));
        assertEquals(2,e.frame.state().motion().routine());assertEquals(1,e.frame.vIntClock().deltaSinceAdmittedCut());assertFalse(e.events.contains("POST_CAMERA"));assertTerminal(e);
    }

    @Test void caughtReentryAtEveryCutFailsBeforeTheOuterCatchAndDoesNotPoisonTheValidRun() throws Exception {
        var h=harness(true);int[] rejected={0};
        h.onCheck=()->{var s=h.frame.state();var e=h.frame.environment();var c=h.frame.vIntClock();
            assertThrows(IllegalStateException.class,()->h.loop.run(0x40));assertSame(s,h.frame.state());assertSame(e,h.frame.environment());assertSame(c,h.frame.vIntClock());rejected[0]++;};
        h.loop.run(8);assertEquals(SonicCdAct2PastOrdinaryLoop.Cut.values().length,rejected[0]);assertEquals(ORDER,h.events);
        assertEquals(1,h.frame.vIntClock().deltaSinceAdmittedCut());assertEquals(1,h.engineVBlanks);assertFalse(h.frame.closed());
    }

    @Test void anUncaughtBeforeEightReentryStopsWithoutFrameServiceAndTheKernelCannotRetry() throws Exception {
        var h=harness(false);RuntimeException[] nested={null};h.before=()->{try{h.loop.run(0);}catch(RuntimeException e){nested[0]=e;throw e;}};
        var state=h.frame.state();var env=h.frame.environment();var clock=h.frame.vIntClock();var error=assertThrows(IllegalStateException.class,()->h.loop.run(8));assertSame(nested[0],error);
        assertSame(state,h.frame.state());assertSame(env,h.frame.environment());assertSame(clock,h.frame.vIntClock());assertEquals(List.of("BEFORE8"),h.events);assertTerminal(h);
    }

    @Test void aStaleRootCutPreventsThatPortAndEveryLaterAdoptionWithoutFabricatedCompletion() throws Exception {
        var h=harness(false);h.staleAt=RUN_OBJECTS;assertSame(h.stale,assertThrows(IllegalStateException.class,()->h.loop.run(8)));
        assertEquals(List.of("BEFORE8","PUBLISH","ANIM","COPY_DRAW","SLOW_PLC","HUD","MUSIC","SETTLE","SPAWN"),h.events);
        assertNull(h.primary);assertEquals(1,h.frame.environment().globals().levelFrames());assertEquals(1,h.frame.vIntClock().deltaSinceAdmittedCut());assertTerminal(h);
    }

    @Test void anUnboundActualArtPublicationNeverSelectsOrSettlesAndAddsNoClockOrInput() throws Exception {
        var pair=retail();var frame=frame(pair.terrain(),pair.player(),1,0,0,true);var a=frame.captureActor();var s=frame.captureShared();
        var h=new Harness(pair.terrain(),pair.player(),frame,false);assertThrows(IllegalStateException.class,()->h.loop.run(8));
        assertEquals(List.of("BEFORE8"),h.events);assertEquals(a,frame.captureActor());assertEquals(s,frame.captureShared());
        assertTrue(h.published.isEmpty());assertTrue(h.settled.isEmpty());assertFalse(frame.closed());assertTerminal(h);
    }

    @Test void pausedResetKeepsTheExistingTypedBoundaryAfterFinishAndRootMusicWithoutAnyMainTail() throws Exception {
        var h=harness(false);var error=assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,()->h.loop.run(0x90));
        assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.PAUSED_RESET,error.boundary());assertEquals(1,h.frame.vIntClock().deltaSinceAdmittedCut());assertEquals(1,h.engineVBlanks);
        assertTrue(h.frame.paused());assertEquals(0,h.frame.environment().globals().levelFrames());assertEquals(List.of("BEFORE8","PUBLISH","ANIM","COPY_DRAW","SLOW_PLC","HUD","MUSIC"),h.events);
        assertEquals(1,h.published.size());assertTrue(h.settled.isEmpty());assertTrue(h.frame.closed());assertTerminal(h);
    }

    @Test void actualFrameNativeActAndPeriodGuardsRemainAheadOfPublicationDespiteTheCorrectPair() throws Exception {
        var pair=retail();
        for(int[] bad:new int[][]{{0,0},{1,1}}){var frame=frame(pair.terrain(),pair.player(),bad[0],bad[1],0,false);var actor=frame.captureActor();var shared=frame.captureShared();
            var h=new Harness(pair.terrain(),pair.player(),frame,true);var error=assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,()->h.loop.run(8));
            assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.SOURCE_FAMILY,error.boundary());assertEquals(actor,frame.captureActor());assertEquals(shared,frame.captureShared());
            assertEquals(List.of("BEFORE8"),h.events);assertTrue(h.published.isEmpty());assertTrue(h.settled.isEmpty());assertTerminal(h);
        }
    }

    @Test void frameClosureAfterConstructionRejectsBeforeAnyPortOrCatchScope() throws Exception {
        var h=harness(false);h.frame.close();assertThrows(IllegalStateException.class,()->h.loop.run(0));assertTrue(h.events.isEmpty());assertTrue(h.checks.isEmpty());
        assertEquals(0,h.frame.vIntClock().deltaSinceAdmittedCut());assertThrows(IllegalStateException.class,()->h.loop.run(0));assertTrue(h.events.isEmpty());
    }

    private static void assertTerminal(Harness h){var events=List.copyOf(h.events);var checks=List.copyOf(h.checks);var clock=h.frame.vIntClock();var state=h.frame.state();var env=h.frame.environment();
        assertThrows(IllegalStateException.class,()->h.loop.run(0));assertEquals(events,h.events);assertEquals(checks,h.checks);assertSame(clock,h.frame.vIntClock());assertSame(state,h.frame.state());assertSame(env,h.frame.environment());}
    private Harness harness(boolean dirty)throws Exception{var pair=retail();return new Harness(pair.terrain(),pair.player(),frame(pair.terrain(),pair.player(),1,0,0,dirty),true);}
    private static CdPlayerFrameRuntime frame(CdR12bTerrainAssets terrain,CdR12bPlayerAssets player,int act,int period,int level,boolean dirty){
        return CdPlayerFrameRuntime.fromAct2PastEntry(terrain,player,state(),environment(act,period,level),art(dirty),clock());}
    private static CdPlayerFrameRuntime.VIntClock clock(){return new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xABCDEF00L),0);}
    private static CdPlayerArtRuntime.State art(boolean dirty){byte[] cpu=new byte[0x300],resident=new byte[0x300];for(int i=0;i<0x300;i++){cpu[i]=(byte)(i*7+3);resident[i]=(byte)(i*11+5);}return new CdPlayerArtRuntime.State(cpu,resident,0,dirty);}
    private static CdPlayerBody.State state(){return new CdPlayerBody.State(new CdPlayerMotionState(0x07001234,0x04205678,-0x123,0x234,-0x345,0,0,0x42,0,0,0,0,0,0,0,0,0,0,0,0),
            new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(1,0,0,0,0),new CdPlayerBody.Timers(0x78,0,0,0),
            new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[0x100]),0),0,new CdPlayerBody.ObjectLinks(0,0),0);}
    private static CdPlayerBody.Environment environment(int act,int period,int level){return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0,0,0),
            new CdPlayerMovement.Bounds(0x10,0x2000,0x700,0),new CdPlayerMovement.Gates(act,period,0,0,0,0,0),new CdPlayerMovement.Globals(0,0x60,0,0,3,0,0x12,0x34,0x56,0x78)),
            new CdPlayerBody.Controls(0,0,0,0,0),new CdPlayerBody.Globals(level,160,0,0,0,5,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);}
    private static Optional<CpuSat> cpu(int fill){byte[] bytes=new byte[640];Arrays.fill(bytes,(byte)fill);
        var slots=new CdDrawSlots(){@Override public LiveDrawSlot read(CdSlotRef ref){throw new AssertionError("Empty component CPU queue has no slots");}@Override public void writeOnScreenBit(CdSlotRef ref,boolean value){throw new AssertionError("Empty queue writes no slot");}};
        try(var queue=new CdObjectDrawQueue(slots,(a,m,f)->{throw new AssertionError("Empty queue reads no mappings");},bytes)){queue.beginPass();return Optional.of(queue.finishAfterCamera(new DrawCameras(new CameraWords(0,0),new CameraWords(0,0),new CameraWords(0,0))));}}

    /** Constructed caller controls prove kernel ordering only; runObjects executes genuine primary Init. */
    private static final class Harness implements SonicCdAct2PastOrdinaryLoop.Ports {
        final CdR12bTerrainAssets terrain;final CdR12bPlayerAssets player;final CdPlayerFrameRuntime frame;final SonicCdAct2PastOrdinaryLoop loop;
        final List<String> events=new ArrayList<>();final List<SonicCdAct2PastOrdinaryLoop.Cut> checks=new ArrayList<>();
        final List<Optional<CpuSat>> published=new ArrayList<>(),settled=new ArrayList<>();final List<Boolean> musicPauseValues=new ArrayList<>();
        final IllegalArgumentException fault=new IllegalArgumentException("constructed source port stop");final IllegalStateException stale=new IllegalStateException("constructed stale Root epoch");
        Optional<CpuSat> currentCpu=cpu(0x43),borrowed;int engineVBlanks,expectedMainFrame=1;String stopAt;SonicCdAct2PastOrdinaryLoop.Cut staleAt;CdPlayerFrameRuntime.BodyPublication primary;
        Runnable onCheck=()->{},before=()->{},afterPrimary=()->{};
        Harness(CdR12bTerrainAssets terrain,CdR12bPlayerAssets player,CdPlayerFrameRuntime frame,boolean bindArt){this.terrain=terrain;this.player=player;this.frame=frame;
            if(bindArt)frame.bindArtPublication((request,actual)->{record("SONIC_ART");assertSame(actual,frame.artState());assertEquals(0x2E0,request.bytes().length());assertFalse(actual.dirty());assertEquals(0,frame.vIntClock().deltaSinceAdmittedCut());});
            loop=new SonicCdAct2PastOrdinaryLoop(frame,terrain,player,this);}
        private void record(String event){events.add(event);if(event.equals(stopAt))throw fault;}
        @Override public void requireCurrent(SonicCdAct2PastOrdinaryLoop.Cut cut,CdPlayerFrameRuntime actual,CdR12bTerrainAssets actualTerrain,CdR12bPlayerAssets actualPlayer){
            assertSame(frame,actual);assertSame(terrain,actualTerrain);assertSame(player,actualPlayer);assertTrue(frame.stageSource().ownsAct2Past(terrain,player));checks.add(cut);
            if(cut==staleAt)throw stale;onCheck.run();}
        @Override public Optional<CpuSat> beforeSelector8(){record("BEFORE8");assertDoesNotThrow(()->{frame.captureShared();},"Old CPU/VScroll observation precedes any pending input sample");before.run();borrowed=currentCpu;return borrowed;}
        @Override public void publishLevel(Optional<CpuSat> cpu){record("PUBLISH");assertSame(borrowed,cpu);published.add(cpu);assertEquals(0,frame.environment().controls().logicalHeld());}
        @Override public void updateAnimTiles(){record("ANIM");}
        @Override public void copyCameraAndDrawLevel(){record("COPY_DRAW");}
        @Override public void serviceSlowPlc(){record("SLOW_PLC");}
        @Override public void updateHud(){record("HUD");}
        @Override public void mainMusic(){record("MUSIC");engineVBlanks++;musicPauseValues.add(frame.paused());assertEquals(engineVBlanks,frame.vIntClock().deltaSinceAdmittedCut());}
        @Override public void settleSelectedCpu(Optional<CpuSat> cpu){record("SETTLE");assertSame(borrowed,cpu);settled.add(cpu);}
        @Override public void spawnObjects(){record("SPAWN");assertEquals(expectedMainFrame,frame.environment().globals().levelFrames());}
        @Override public void runObjects(int actualLevelFrame){record("WALK");assertEquals(frame.environment().globals().levelFrames(),actualLevelFrame);primary=frame.runBody(actualLevelFrame);assertEquals(CdPlayerBody.Boundary.INIT_RETURN,primary.result().boundary());afterPrimary.run();}
        @Override public void postObjectCamera(){record("POST_CAMERA");}
        @Override public void drawObjects(){record("SAT");currentCpu=cpu(0x65);}
        @Override public void paletteCycleIfTimeRunning(){record("PALETTE_IF");}
        @Override public void updateSectionArt(){record("SECTION");}
        @Override public void preparePlc(){record("PREPARE");}
        @Override public void updateGlobalAnimations(){record("GLOBAL");}
    }

    private record Retail(CdR12bTerrainAssets terrain,CdR12bPlayerAssets player,CdR12bTerrainAssets otherTerrain,CdR12bPlayerAssets otherPlayer,CdR11bAssets past){}
    private Retail retained;
    private Retail retail()throws Exception{if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Configure actual original USA media root");assertFalse(root.isBlank());Path cue;
        try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=new Retail(CdR12bTerrainAssets.fromPrograms(source),CdR12bPlayerAssets.fromPrograms(source),CdR12bTerrainAssets.fromPrograms(source),CdR12bPlayerAssets.fromPrograms(source),CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source)));}
        return retained;
    }
}
