package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.scroll.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.graphics.RetainedPlaneSceneRenderer;
import com.openggf.graphics.SpritePresentation;
import static com.openggf.game.soniccd.SonicCdAct3FutureLoadingVisit.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/** Tests FIRST, Java UNRUN. Actual configured C/D source and genuine private Core/Host/World stack.
 * Actor/camera ingress below is explicitly CONSTRUCTED component data, never natural-route,
 * Module/FULL authority, trace hydration or a successful restart/GameOver claim.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestSonicCdAct3FutureDeathCameraWorld {
    @Test void nativeDeathCameraFreezesBothBoundsAndKeepsTheOrdinaryTail() throws Exception {
        for(var a:TestCdR13PlaneSources.actual()) for(int lock:new int[]{0,0xAB00}) try(var f=ready(a)) {
            // Explicit isolated component pool: native spawning is already complete at this same chunk.
            assertEquals(f.host.scroll().foreground().cameraX()&0xFF80,f.host.placement().status().previousChunkWord());
            f.host.placement().pool().clearDynamicSlots();
            actor(f.frame,6,0x200,0,0,0);camera(f.host,0x0101BEEF,0x789,lock);
            var fg=f.host.scroll().foreground();var bg=f.host.scroll().background();var pool=f.host.placement().capture();
            var frames=f.frame.environment().globals().levelFrames();var extras=f.host.globals().animations().orElseThrow();
            var oldCpu=f.host.video().completedCpu();var source=f.frame.stageSource();f.core.service(8);
            assertEquals(0x101,f.frame.environment().movement().bounds().bottom());
            assertEquals(withBottom(fg,0x101),f.host.scroll().foreground());assertSame(bg,f.host.scroll().background());
            assertEquals(pool.status(),f.host.placement().status());assertEquals(pool.pool().slots(),f.host.placement().pool().capture().slots());
            assertArrayEquals(pool.savedFlags().copy(),f.host.placement().capture().savedFlags().copy());
            assertEquals((frames+1)&65535,f.frame.environment().globals().levelFrames());
            assertNotSame(extras,f.host.globals().animations().orElseThrow());assertNotSame(oldCpu,f.host.video().completedCpu());
            assertTrue(f.frame.display().isPresent());assertSame(source,f.frame.stageSource());assertFalse(f.core.failed());
        }
    }
    @Test void deadUsesLiteralGravityAndRecordPosOnceBeforeTheFrozenCamera() throws Exception {
        try(var f=ready(TestCdR13PlaneSources.actual().getFirst())) {
            actor(f.frame,6,0x200,0,-0x100,0);camera(f.host,0x0101BEEF,0x789,0);
            int index=f.frame.state().history().index();f.core.service(0);
            assertEquals(0x01FF0000,f.frame.state().motion().centreY16_16());
            assertEquals(-0xC8,f.frame.state().motion().yVelocity());
            assertEquals((index&0xFF00)|((index+4)&255),f.frame.state().history().index());
            assertEquals(6,f.frame.state().motion().routine());assertEquals(0,f.frame.state().timers().reset());
            assertEquals(0x101,f.frame.environment().movement().bounds().bottom());
        }
    }
    @Test void restartZeroAndNonzeroWordsReturnWithoutDrawOrHistoryWork() throws Exception {
        for(int reset:new int[]{0,2,0x8000,0xFFFF}) try(var f=ready(TestCdR13PlaneSources.actual().getFirst())) {
            actor(f.frame,8,0x200,reset,0,0);var before=f.frame.state();camera(f.host,0x0101BEEF,0x789,0);
            f.core.service(0);assertEquals(reset==0?0:reset-1,f.frame.state().timers().reset());
            assertEquals(before.history(),f.frame.state().history());assertEquals(before.animation(),f.frame.state().animation());
            assertTrue(f.frame.display().isEmpty());assertEquals(0,f.core.state().level().levelRestart());
            assertEquals(0x101,f.frame.environment().movement().bounds().bottom());assertFalse(f.core.failed());
        }
    }
    @Test void completedMainContactDeathIsAcceptedByItsIncomingBoundaryAndLaterFailureKeepsIt() throws Exception {
        try(var f=ready(TestCdR13PlaneSources.actual().getFirst())) {
            var m=f.frame.state().motion();var p=f.host.placement().pool();
            // Explicit native hazard geometry on an unbound later object: primary must finish first.
            p.write(p.state(127).moveByte(ID,3).moveByte(SPRITE_FLAGS,0x80).moveByte(COLLISION_TYPE,0x81)
                    .moveWord(X,m.centreX16_16()>>>16).moveWord(Y,m.centreY16_16()>>>16));
            var failure=assertThrows(SonicCdAct3FutureObjectPass.Unowned.class,()->f.core.service(0));
            assertEquals(SonicCdAct3FutureObjectPass.Boundary.OBJECT_FAMILY,failure.boundary());assertEquals(127,failure.wholeIndex());
            assertEquals(6,f.frame.state().motion().routine());assertTrue(f.core.failed());
            assertThrows(IllegalStateException.class,()->f.core.service(0));
        }
    }
    @Test void completedMainContactDeathReachesTheSameCallCameraAndDrawTail() throws Exception {
        try(var f=ready(TestCdR13PlaneSources.actual().getFirst())) {
            var m=f.frame.state().motion();var p=f.host.placement().pool();camera(f.host,0x0101BEEF,0x789,0);
            // Native touch scans dirty ID-zero storage; physical RunObjects skips its ID-zero slot.
            p.write(p.state(127).moveByte(SPRITE_FLAGS,0x80).moveByte(COLLISION_TYPE,0x81)
                    .moveWord(X,m.centreX16_16()>>>16).moveWord(Y,m.centreY16_16()>>>16));
            f.core.service(0);assertEquals(6,f.frame.state().motion().routine());assertEquals(0x101,f.frame.environment().movement().bounds().bottom());
            assertEquals(0x101,f.host.scroll().foreground().destBottomBound());assertEquals(0,p.state(127).id());assertFalse(f.core.failed());
        }
    }
    @Test void exhaustedDeathAllocationKeepsFullPoolAndStopsAtUnownedAliasWithoutDrawTail() throws Exception {
        try(var f=ready(TestCdR13PlaneSources.actual().getFirst())) {
            actor(f.frame,6,0x500,0,0,0);var p=f.host.placement().pool();for(int i=32;i<128;i++)p.write(p.state(i).moveByte(ID,3));
            var pool=p.capture();var oldCpu=f.host.video().completedCpu();
            var failure=assertThrows(CdWorld.Stopped.class,()->f.core.service(0));
            assertEquals(CdWorld.Boundary.PRIMARY_DEATH_ALIAS,failure.boundary());assertEquals(pool.slots(),p.capture().slots());
            assertEquals(8,f.frame.state().motion().routine());assertEquals(2,rawGlobals(f.host).ring().lives());assertEquals(0,f.core.state().level().updateHudTime());
            assertSame(oldCpu,rawVideo(f.host).completedCpu());assertTrue(f.core.failed());assertThrows(IllegalStateException.class,()->f.core.service(0));
        }
    }
    @Test void genuineGoneCreatesThenDeletesNormalTextWithAllOtherSlotsIntact() throws Exception {
        try(var f=ready(TestCdR13PlaneSources.actual().getFirst())) {
            actor(f.frame,6,0x500,0,0,0);var placed=f.host.placement();var pool=placed.pool();
            // Explicit component empty-pool ingress; native spawner returns at its retained camera chunk.
            assertEquals(f.host.scroll().foreground().cameraX()&0xFF80,placed.status().previousChunkWord());pool.clearDynamicSlots();
            // Slot32 is deleted stale storage; only ID is written by the native allocation.
            pool.write(pool.state(32).moveByte(SUBTYPE,0xA5).moveByte(SPRITE_FLAGS,0x24).moveWord(X,0xBEEF));
            var before=pool.capture();var ring=f.host.globals().ring();
            f.core.service(0); // The newly admitted native lives-remain Init consumes its actual ID3B visit and deletes it.
            assertArrayEquals(new byte[64],pool.state(32).copyBytes());
            assertEquals(8,f.core.state().level().powerup());assertEquals(0,f.core.state().level().timeOver());
            assertEquals(new SonicCdGameModule.PastR13GameOverSubCpu(CdGameOver.SubCpuCommand.SCMD_FADEPCM),f.host.sounds().getLast());
            for(int i=1;i<96;i++)assertEquals(before.slots().get(i),pool.capture().slots().get(i));
            assertEquals(ring.lives()-1,rawGlobals(f.host).ring().lives());assertEquals((ring.updateHudLives()+1)&255,rawGlobals(f.host).ring().updateHudLives());
            assertEquals(0,f.core.state().level().updateHudTime());assertEquals(8,f.frame.state().motion().routine());
            assertEquals(60,f.frame.state().timers().reset());assertEquals(0,f.frame.state().motion().yVelocity());
            assertFalse(f.core.failed());assertFalse(f.frame.closed());assertDoesNotThrow(f.core::capture);
        }
    }
    @Test void unsignedGoneComparisonIncludesWordWrapAndEquality() throws Exception {
        for(int[] v:new int[][]{{0xFF80,0x80,6},{0xFF80,0x81,8},{0x310,0x410,6}})try(var f=ready(TestCdR13PlaneSources.actual().getFirst())) {
            actor(f.frame,6,v[1],0,0,0);bounds(f.frame,v[0]);
            f.core.service(0); // Native ID3B's normal lives-remain deletion now completes this same physical walk.
            assertEquals(v[2],f.frame.state().motion().routine());
        }
    }
    @Test void restartOnePublishesSavedFlagResetAndNativeLongClearBeforePreciseLevelStartStop() throws Exception {
        for(var a:TestCdR13PlaneSources.actual())try(var f=ready(a)) {
            actor(f.frame,8,0x200,1,0,0);var placement=f.host.placement();placement.bsetSavedFlag(3,2,CdObjectPlacementRuntime.SavedBit.LOADED);
            // Explicit dirty programme data: CLR.L spans all three flower bytes plus adjacent fade.
            level(f.core,new CdLevelGlobals(0,0,5,7,0xA5,0x123456,0xBB,0,9));
            var before=f.core.state().level();var camera=f.host.scroll().foreground();int sounds=f.host.sounds().size();
            var failure=assertThrows(CdWorld.Stopped.class,()->f.core.service(0));
            assertEquals(CdWorld.Boundary.LEVEL_FLOW,failure.boundary());
            var next=f.core.state().level();assertEquals(1,next.levelRestart());assertEquals(0,next.flowerCount());assertEquals(0,next.fadeEnableDisplay());
            assertEquals(0,next.spawnMode());assertEquals(before.plcLoadFlags(),next.plcLoadFlags());assertEquals(before.checkpoint(),next.checkpoint());
            byte[] flags=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];flags[0]=flags[1]=1;
            assertArrayEquals(flags,placement.capture().savedFlags().copy());assertEquals(0,f.frame.state().timers().reset());
            assertTrue(f.frame.display().isEmpty());assertEquals(camera,rawScroll(f.host).foreground());
            assertTrue(rawSounds(f.host).subList(sounds,rawSounds(f.host).size()).stream().anyMatch(s->s instanceof SonicCdGameModule.PastFrameSound p&&p.sound() instanceof CdPlayerFrameRuntime.BodySound b&&b.sound()==CdPlayerBody.Sound.SCMD_FADECDA));
            assertTrue(f.core.failed());assertThrows(IllegalStateException.class,()->f.core.service(0));
        }
    }
    @Test void usaDeadStartCannotPauseAndAnAlreadyPausedDeadActorDoesNotRunTheCameraOrBody() throws Exception {
        for(boolean paused:new boolean[]{false,true})try(var f=ready(TestCdR13PlaneSources.actual().getFirst())) {
            actor(f.frame,8,0x200,2,0,0);camera(f.host,0x0101BEEF,0x789,0);bounds(f.frame,0x456);
            var shared=f.frame.captureShared();f.frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(),shared.environment(),shared.artState(),paused,shared.vIntClock()));
            int frames=f.frame.environment().globals().levelFrames();f.core.service(0x80);assertEquals(paused,f.frame.paused());
            assertEquals(paused?2:1,f.frame.state().timers().reset());assertEquals(paused?0x456:0x101,f.frame.environment().movement().bounds().bottom());
            assertEquals(paused?0x789:0x101,f.host.scroll().foreground().destBottomBound());assertEquals(paused?frames:(frames+1)&65535,f.frame.environment().globals().levelFrames());
        }
    }
    @Test void nonzeroLowDebugWordSelectsLevelScrollInsteadOfDeathCamera() throws Exception {
        try(var f=ready(TestCdR13PlaneSources.actual().getFirst())) {
            actor(f.frame,8,0x200,0,0,0);camera(f.host,0x0101BEEF,0x789,0x100);bounds(f.frame,0x456);debug(f.frame,1);
            f.core.service(0);assertEquals(0x456,f.frame.environment().movement().bounds().bottom());assertEquals(0x789,f.host.scroll().foreground().destBottomBound());assertFalse(f.core.failed());
        }
    }
    @Test void preparedCameraRetainsEveryOtherFrameFieldAndUsesTheRawUnsignedWord() throws Exception {
        try(var f=ready(TestCdR13PlaneSources.actual().getFirst())) {
            actor(f.frame,8,0x200,0,0,0);var state=f.frame.state();var e=f.frame.environment();var m=e.movement();
            var p=f.frame.prepareAct3FutureLevelMainDeathCamera(f.assets,state,e,0xF123);
            assertSame(state,p.finalState());assertSame(state,f.frame.state());assertSame(e,f.frame.environment());
            f.frame.applyPreparedObjectSolidPublication(p);var n=f.frame.environment();assertEquals(0xF123,n.movement().bounds().bottom());
            assertEquals(m.bounds().left(),n.movement().bounds().left());assertEquals(m.bounds().right(),n.movement().bounds().right());assertEquals(m.bounds().bossFight(),n.movement().bounds().bossFight());
            assertSame(m.physics(),n.movement().physics());assertSame(m.gates(),n.movement().gates());assertSame(m.globals(),n.movement().globals());
            assertSame(e.controls(),n.controls());assertSame(e.globals(),n.globals());assertSame(e.dynamicSlots(),n.dynamicSlots());
            var equal=f.frame.prepareAct3FutureLevelMainDeathCamera(f.assets,state,n,0xF123);assertSame(n,equal.finalEnvironment());
        }
    }
    @Test void malformedForeignStaleAndUnreachedCameraValuesCannotWrite() throws Exception {
        var all=TestCdR13PlaneSources.actual();try(var f=ready(all.getFirst())) {
            var state=f.frame.state();var e=f.frame.environment();
            assertThrows(IllegalStateException.class,()->f.frame.prepareAct3FutureLevelMainDeathCamera(f.assets,state,e,1));
            actor(f.frame,8,0x200,0,0,0);var s=f.frame.state();var env=f.frame.environment();
            for(int word:new int[]{-1,65536})assertThrows(IllegalArgumentException.class,()->f.frame.prepareAct3FutureLevelMainDeathCamera(f.assets,s,env,word));
            assertThrows(NullPointerException.class,()->f.frame.prepareAct3FutureLevelMainDeathCamera(null,s,env,1));
            assertThrows(IllegalArgumentException.class,()->f.frame.prepareAct3FutureLevelMainDeathCamera(all.getLast(),s,env,1));
            var prepared=f.frame.prepareAct3FutureLevelMainDeathCamera(f.assets,s,env,0x101);
            bounds(f.frame,0x456);assertThrows(IllegalArgumentException.class,()->f.frame.applyPreparedObjectSolidPublication(prepared));
            assertThrows(IllegalArgumentException.class,()->f.frame.prepareAct3FutureLevelMainDeathCamera(f.assets,s,env,0x101));
            assertEquals(0x456,f.frame.environment().movement().bounds().bottom());
            assertThrows(IllegalStateException.class,()->f.host.prepareLevelMainDeathCamera(0x101));assertThrows(IllegalStateException.class,()->f.world.requireCut(CdOrdinaryLoop.Cut.POST_OBJECT_CAMERA));
            debug(f.frame,1);assertThrows(IllegalStateException.class,()->f.frame.prepareAct3FutureLevelMainDeathCamera(f.assets,f.frame.state(),f.frame.environment(),1));
        }
    }
    @Test void fullComponentRestoreReplaysOriginalDeathServicesWithoutEffectReplay() throws Exception {
        try(var f=ready(TestCdR13PlaneSources.actual().getFirst())) {
            actor(f.frame,6,0x200,0,-0x100,0);camera(f.host,0x0101BEEF,0x789,0);var cut=f.core.capture();f.core.service(0);
            var actor=f.frame.captureActor();var shared=f.frame.captureShared();var host=f.host.capture();var core=f.core.state();
            f.core.restore(cut);f.core.service(0);assertEquals(actor,f.frame.captureActor());assertEquals(shared.environment(),f.frame.environment());assertEquals(shared.vIntClock(),f.frame.vIntClock());assertEquals(shared.paused(),f.frame.paused());
            assertArrayEquals(shared.artState().cpuBuffer().copy(),f.frame.artState().cpuBuffer().copy());assertArrayEquals(shared.artState().residentBank().copy(),f.frame.artState().residentBank().copy());assertEquals(shared.artState().lastFrame(),f.frame.artState().lastFrame());assertEquals(shared.artState().dirty(),f.frame.artState().dirty());assertEquals(host,f.host.capture());
            assertEquals(core.calls(),f.core.state().calls());assertEquals(core.level(),f.core.state().level());assertSame(core.program(),f.core.state().program());
        }
    }
    @Test void originalCallbackErrorAndCaughtReentryPreserveTheSingleNativePrefix() throws Exception {
        try(var f=new TestSonicCdAct3FutureOldProgrammeExit.Fixture(TestCdR13PlaneSources.actual().getFirst(),3)) {
            f.ready();f.core.service(0);actor(f.frame,8,0x200,2,0,0);var original=new AssertionError("original VInt prefix");
            f.auth.vint=()->{assertThrows(IllegalStateException.class,()->f.core.service(0));assertThrows(IllegalStateException.class,f.core::capture);throw original;};
            int frames=f.frame.environment().globals().levelFrames();assertSame(original,assertThrows(AssertionError.class,()->f.core.service(0)));
            assertEquals(2,f.frame.state().timers().reset());assertEquals(frames,f.frame.environment().globals().levelFrames());assertTrue(f.core.failed());
        }
    }
    @Test void actualPrimaryFullPoolAndFrameCasRejectForeignCallbackWritesBeforeLifecycleAdoption() throws Exception {
        for(boolean frameWrite:new boolean[]{false,true})try(var f=new FaultFixture(TestCdR13PlaneSources.actual().getFirst(),3)) {
            f.ready();f.core.service(0);actor(f.frame,6,0x500,0,0,0);var pool=f.host.placement().pool();var beforePool=pool.capture();boolean[] fired={false};
            f.auth.hook=()->{if(!fired[0]&&f.frame.state().motion().routine()==8){fired[0]=true;if(frameWrite)bounds(f.frame,0x456);else pool.write(pool.state(127).moveByte(SUBTYPE,0xA5));}};
            assertThrows(IllegalStateException.class,()->f.core.service(0));assertTrue(fired[0]);assertTrue(f.core.failed());
            assertEquals(8,f.frame.state().motion().routine());assertEquals(3,rawGlobals(f.host).ring().lives());
            for(int whole=32;whole<128;whole++)if(frameWrite||whole!=127)assertEquals(beforePool.slots().get(whole-32),pool.state(whole),"Unpublished full-pool death allocation at"+whole);
            if(frameWrite)assertEquals(0x456,f.frame.environment().movement().bounds().bottom());else assertEquals(0xA5,pool.state(127).subtype());
        }
    }
    @Test void everyBoundedPostWalkAuthorityFaultSeesEitherBothCameraStoresOrNeither() throws Exception {
        // Deliberate component callback-fault ordinals, never native gameplay/route selectors.
        boolean before=false,after=false;for(int ordinal=1;ordinal<=40;ordinal++)try(var f=new FaultFixture(TestCdR13PlaneSources.actual().getFirst(),3)) {
            f.ready();f.core.service(0);actor(f.frame,6,0x200,0,-0x100,0);camera(f.host,0x0101BEEF,0x789,0);bounds(f.frame,0x310);
            var original=new AssertionError("original post-walk authority fault "+ordinal);int at=ordinal;int[] calls={0};boolean[] fired={false};
            f.auth.hook=()->{if(cutIs(f.world,CdOrdinaryLoop.Cut.POST_OBJECT_CAMERA)&&++calls[0]==at){fired[0]=true;throw original;}};
            try{f.core.service(0);assertFalse(fired[0]);}catch(AssertionError error){assertSame(original,error);assertTrue(f.core.failed());}
            f.auth.hook=()->{};int bottom=f.frame.environment().movement().bounds().bottom();int dest=rawScroll(f.host).foreground().destBottomBound();
            assertEquals(0x01FF0000,f.frame.state().motion().centreY16_16());assertEquals(4,f.frame.state().history().index());
            if(bottom==0x101){assertEquals(0x101,dest);after|=fired[0];}else{assertEquals(0x310,bottom);assertEquals(0x789,dest);before|=fired[0];}
        }
        assertTrue(before);assertTrue(after);
    }
    /** Retired owners intentionally reject all public reads/capture. Inspect only their retained
     * immutable test evidence; this helper cannot reopen or publish any production owner. */
    private static Object rawHost(SonicCdAct3FutureWorldHost host,String member) throws Exception {var field=SonicCdAct3FutureWorldHost.class.getDeclaredField("image");field.setAccessible(true);var image=field.get(host);var read=image.getClass().getDeclaredMethod(member);read.setAccessible(true);return read.invoke(image);}
    private static SonicCdAct3FutureWorldHost.Globals rawGlobals(SonicCdAct3FutureWorldHost h)throws Exception{return (SonicCdAct3FutureWorldHost.Globals)rawHost(h,"globals");}
    private static SonicCdAct3FutureWorldHost.Video rawVideo(SonicCdAct3FutureWorldHost h)throws Exception{return (SonicCdAct3FutureWorldHost.Video)rawHost(h,"video");}
    private static SonicCdAct3FutureLoadingVisit.Scroll rawScroll(SonicCdAct3FutureWorldHost h)throws Exception{return (SonicCdAct3FutureLoadingVisit.Scroll)rawHost(h,"scroll");}
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.PastSound> rawSounds(SonicCdAct3FutureWorldHost h)throws Exception{return (List<SonicCdGameModule.PastSound>)rawHost(h,"sounds");}
    private static boolean cutIs(CdWorld world,CdOrdinaryLoop.Cut cut){try{var f=CdWorld.class.getDeclaredField("cut");f.setAccessible(true);return f.get(world)==cut;}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static TestSonicCdAct3FutureWorldHost.Fixture ready(CdR13FutureAssets a){var f=new TestSonicCdAct3FutureWorldHost.Fixture(a,3);f.ready();f.core.service(0);return f;}
    /** Labelled component actor ingress; legitimate CaptureOwner is reused only inside this test fixture. */
    static void actor(CdPlayerFrameRuntime frame,int routine,int y,int reset,int yVelocity,int control){
        var cut=frame.captureActor();var s=cut.state();var m=s.motion();
        var n=new CdPlayerMotionState(m.centreX16_16(),y<<16,0,yVelocity,0,routine,0,m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),routine==6?0x18:m.animation(),routine==6?0x18:m.previousAnimation(),0,0,0,0,control,m.previousPrimaryAngle(),m.previousSecondaryAngle());
        var next=new CdPlayerBody.State(n,s.animation(),s.header(),new CdPlayerBody.Timers(0,0,0,reset),new CdPlayerBody.History(s.history().bytes(),0),s.rotationDistance(),s.objectLinks(),s.rotationAngle());
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(cut.owner(),next,Optional.empty()));
    }
    static void camera(SonicCdAct3FutureWorldHost host,int y,int dest,int lock) throws Exception {
        var s=host.scroll();var f=s.foreground();var n=new CdR11aForegroundScroll.State(f.cameraX(),y,f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),dest,f.destLeftBound(),f.bottomBoundShift(),lock);
        var store=SonicCdAct3FutureWorldHost.class.getDeclaredMethod("scrollValue",SonicCdAct3FutureLoadingVisit.Scroll.class);store.setAccessible(true);store.invoke(host,new SonicCdAct3FutureLoadingVisit.Scroll(n,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord()));
    }
    static void bounds(CdPlayerFrameRuntime f,int bottom){var b=f.environment().movement().bounds();f.applyScrollFeedback(new CdPlayerMovement.Bounds(b.left(),b.right(),bottom,b.bossFight()),f.environment().movement().globals().cameraYCentre());}
    static void level(SonicCdAct3FutureLoadingVisit core,CdLevelGlobals level) throws Exception {
        var s=core.state();var field=SonicCdAct3FutureLoadingVisit.class.getDeclaredField("values");field.setAccessible(true);
        field.set(core,new SonicCdAct3FutureLoadingVisit.State(s.phase(),s.section(),s.program(),level,s.normalPalette(),s.fadePalette(),s.waterPalette(),s.displayedPalette(),s.hudSlots(),s.blocksRam(),s.layoutRam(),s.initialFadeSteps(),s.fromFadeSteps(),s.displayEnabled(),s.calls()));
    }
    static void debug(CdPlayerFrameRuntime f,int debug){var s=f.captureShared();var e=s.environment();var g=e.globals();var n=new CdPlayerBody.Globals(g.levelFrames(),g.cameraXCentre(),g.timeAttackMode(),debug,g.debugCheat(),g.timeWarpTimer(),g.invincible(),g.bossMusic());f.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(),new CdPlayerBody.Environment(e.movement(),e.controls(),n,e.dynamicSlots()),s.artState(),s.paused(),s.vIntClock()));}
    private static CdR11aForegroundScroll.State withBottom(CdR11aForegroundScroll.State f,int bottom){return new CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),bottom,f.destLeftBound(),f.bottomBoundShift(),f.scrollLock());}
    /** Declared COMPONENT fault authority; constructor inputs match immutable WorldHost.Fixture.
     * It supplies no actual Module/render admission and is never installed by production. */
    static final class FaultAuthority implements SonicCdAct3FutureWorldHost.Authority {
        FaultFixture f;Runnable hook=()->{};boolean invoking;
        public void require(SonicCdAct3FutureWorldHost h,SonicCdAct3FutureLoadingVisit c,CdWorld w,CdPlayerFrameRuntime frame,CdR13FutureAssets a,CdR13SectionArt art){
            if(f!=null&&(f.host!=h||f.core!=null&&f.core!=c||w!=null&&f.world!=null&&f.world!=w||f.frame!=frame||f.assets!=a||f.art!=art))throw new IllegalStateException("Foreign declared fault fixture");
            if(!invoking){invoking=true;try{hook.run();}finally{invoking=false;}}
        }
        public int selectedGoodFutureByte(SonicCdAct3FutureWorldHost h){return f.assets.identity().file()==CdNativeFile.R13C?1:0;}
        public SonicCdGameModule.PastSound levelMusic(SonicCdAct3FutureWorldHost h,SonicCdAct3FutureLoadingVisit.Program p,int period,int attack){return new SonicCdGameModule.PastProgrammeSound(SonicCdGameModule.ProgramSound.STOP_FM);}
        public void serviceVInt(SonicCdAct3FutureWorldHost h){}public void resetGoodFutureFlags(SonicCdAct3FutureWorldHost h){}
    }
 static final class FaultFixture implements AutoCloseable {
  final CdR13FutureAssets assets;final CdR13SectionArt art;final CdPlayerFrameRuntime frame;final SonicCdAct3FutureWorldHost host;final SonicCdAct3FutureLoadingVisit core;final CdWorld world;final FaultAuthority auth=new FaultAuthority();
  FaultFixture(CdR13FutureAssets a,int flags){this(a,flags,OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),true,0,OptionalInt.empty());}
  FaultFixture(CdR13FutureAssets a,int flags,int palFlags,OptionalInt water){this(a,flags,OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),true,palFlags,water);}
  FaultFixture(CdR13FutureAssets a,int flags,boolean bind){this(a,flags,OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),bind,0,OptionalInt.empty());}
  FaultFixture(CdR13FutureAssets a,int flags,OptionalInt projector,OptionalInt layer,OptionalInt boss,OptionalInt event){this(a,flags,projector,layer,boss,event,true,0,OptionalInt.empty());}
  FaultFixture(CdR13FutureAssets a,int flags,OptionalInt projector,OptionalInt layer,OptionalInt boss,OptionalInt event,boolean bind,int palFlags,OptionalInt water){assets=a;art=CdR13SectionArt.fromAssets(a);
   var motion=new CdPlayerMotionState(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0);var actor=new CdPlayerBody.State(motion,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(0,0,0,0,0),new CdPlayerBody.Timers(0,0,0,0),new CdPlayerBody.History(fill(256,0x71),0x2345),0,new CdPlayerBody.ObjectLinks(0,0),0);
   var env=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x345,0x23,0x45),new CdPlayerMovement.Bounds(3,0x1234,0x456,0),new CdPlayerMovement.Gates(2,2,1,0,0,1,0xFF),new CdPlayerMovement.Globals(3,0x78,4,5,0x6789,9,10,11,12,13)),new CdPlayerBody.Controls(0x20,0x20,0x10,0x10,0),new CdPlayerBody.Globals(0x4567,0x89,0,0x2345,0,0x3456,1,1),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
   frame=CdPlayerFrameRuntime.fromAct3FutureEntry(a,actor,env,new CdPlayerArtRuntime.State(fill(768,0x55),fill(768,0x66),0xA1,true),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0x1234),7));
   var section=art.fromValues(fill(512,0x5A),CdR13ArtResidency.unknownForNativeHud(a),0xA55A,CdR11aPlcBuffer.cleared(),Optional.empty(),new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.XOR,0x1234,10,0x89ABCDEF,0x5678,0x9ABC),0,0x2345,0x3456,0x4567);
   var entry=new Entry(section,new Program(0,0,a.identity().file()==CdNativeFile.R13C?1:0,0,0,palFlags,CdProgramInitialization.KnownBit.CLEAR,128,0,0),new CdLevelGlobals(0,0,0,0,flags,0,0,0,0),fill(128,0x22),fill(128,0x44),fill(128,0x66),fill(128,0x88),fill(1984,0xA5),false);
   var fg=new CdR11aForegroundScroll.State(0x1234,0x56789ABC,2,3,4,5,6,7,8,9,10);var bg=new CdR11aBackgroundScroll.State(1,2,3,4,5,6,fill(8,0x71),7,8,9,fill(512,0x72),fill(512,0x73),fill(1024,0x74),0x12345678);var camera=new CdR11aBackgroundPlane.Camera(1,2);var scroll=new Scroll(fg,bg,new Copies(1,2,new CdR11aBackgroundPlane.CopiedCameras(camera,camera,camera),3,new CdR11aBackgroundPlane.FlagWords(4,5,6)),0x3456,0x78);
   var globals=new SonicCdAct3FutureWorldHost.Globals(new CdRingGlobals(16,3,0,0,0,0,0,0),water.isPresent()?CdCheckpointLiveAux.writeWaterFullscreen(CdCheckpointLiveAux.unknown(),water.getAsInt()):CdCheckpointLiveAux.unknown(),new CdPlayerBody.ContactGlobals(0,0,0),new CdOpeningObjectRuntime.ScoreGlobals(0,5000,0),projector,CdCheckpointSaveData.State.unknown(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),Optional.empty(),Optional.empty(),Optional.empty(),fill(7,0x77),fill(7,0x88),layer,boss,event);
   var scene=new RetainedPlaneSceneRenderer.Scene(null,null,new int[256],0,new byte[128],false,SpritePresentation.Frame.empty(),Map.of(),32);
   var video=new SonicCdAct3FutureWorldHost.Video(fill(640,0xA5),Optional.empty(),frame.artState(),section.residency(),new SonicCdAct3FutureWorldHost.Carried(scene));
   host=new SonicCdAct3FutureWorldHost(auth,a,frame,art,new SonicCdAct3FutureWorldHost.Initial(globals,scroll,new CdR11aBackgroundPlane.State(fill(4096,0xA5)),new CdR11aBackgroundPlane.State(fill(4096,0xB6)),video,fill(CdObjectPlacementRuntime.SAVED_BANK_BYTES,0)));
   core=new SonicCdAct3FutureLoadingVisit(host,a,frame,art,entry);auth.f=this;host.install(core);world=bind?new CdWorld(auth,core,host,frame,a,art,CdR13ObjectAssets.fromAssets(a),CdR13PlayerCollisionAssets.fromAssets(a)):null;if(bind){host.bindWorld(world);core.bindOrdinaryOwner(host,world);}
  }
  void ready(){core.begin();for(int i=0;i<256;i++){if(core.state().phase()==Phase.READY)return;core.service(0);}fail("Bounded actual loading component did not return Ready");}
  public void close(){host.close();frame.close();}
 }
 static CdAssetBytes fill(int n,int value){byte[] b=new byte[n];Arrays.fill(b,(byte)value);return CdAssetBytes.copyOf(b);}
}
