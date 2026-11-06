package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdLevelPaletteFade;
import com.openggf.game.soniccd.resources.CdLevelHud;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;
import com.openggf.game.soniccd.resources.CdR12aSectionArt;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source-first regression packet, N1ecc5f3 Main400-430,19-81; Sonic605-609;
 * Palette Fade273-309. Cold/Past/Results/Title ingress is genuine; timer/flag and
 * later-slot controls are explicitly constructed, never natural13507 evidence.
 * Java/engine checks UNRUN by this author under the source-only delegation.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@Isolated
class TestSonicCdAct2TimeWarpExit {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    private byte[] r12bExportRgba;private int r12bExportService;
    @AfterEach void cleanup() throws Exception {fixture.cleanup();writeOptionalR12bSonic();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,CdObjectPlacementRuntime placement){}

    @Test void actualPrimaryRestartWaitsForThePhysicalPassThenAbandonsOnlyItsPendingDrawQueue() throws Exception {
        var s=prepared229(0xA1);setLevel(s,copy((CdLevelGlobals)field(values(s),"level"),"levelRestart",0x37));
        var cpu=image(s,"cpuSat");var completed=field(field(values(s),"loop"),"completedDraw");
        var save=saved(s);var receipt=field(custody(s),"completed");var p=presentation(s);var fg=p.act2ForegroundImage();var bg=p.act2BackgroundImage();
        var before=s.h().module().act2LoadState();long clock=s.frame().vIntClock().absoluteValue().orElseThrow();
        var vintSection=ordinaryVIntSectionPrefix(s);
        var expectedSection=((CdR12aSectionArt)field(s.visit(),"art")).clearPlcs(vintSection);
        int frames=s.frame().environment().globals().levelFrames();
        step(s.h(),neutral());healthy(s,SonicCdGameModule.Act2LoadPhase.OLD_WHITE_FADE);
        var after=s.h().module().act2LoadState();var calls=after.calls();int all=calls.indexOf(SonicCdGameModule.Act2LoadCall.DYNAMIC_OBJECTS);
        assertTrue(all>calls.indexOf(SonicCdGameModule.Act2LoadCall.PRIMARY_MAIN));
        assertEquals(SonicCdGameModule.Act2LoadCall.OLD_EXIT_PRELUDE,calls.get(all+1));
        assertEquals(SonicCdGameModule.Act2LoadCall.OLD_EXIT_CLEAR_PLCS,calls.getLast());
        for(var absent:List.of(SonicCdGameModule.Act2LoadCall.POST_OBJECT_GUARDS,SonicCdGameModule.Act2LoadCall.DRAW_OBJECTS,
                SonicCdGameModule.Act2LoadCall.PALETTE_CYCLE,SonicCdGameModule.Act2LoadCall.UPDATE_SECTION,
                SonicCdGameModule.Act2LoadCall.PREPARE_PLCS,SonicCdGameModule.Act2LoadCall.UPDATE_GLOBAL_ANIMATIONS,
                SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,SonicCdGameModule.Act2LoadCall.OLD_EXIT_FADE_SERVICE))assertFalse(calls.contains(absent),absent.toString());
        assertEquals(0x137,after.level().levelRestart(),"Native HIGH-byte store preserves dirty low byte");assertEquals(2,after.level().spawnMode());assertEquals(0x80,s.frame().environment().movement().gates().timeZone());
        assertEquals(255,s.frame().environment().movement().gates().timeWarpDirection());assertEquals(230,s.frame().environment().globals().timeWarpTimer());
        assertSame(save,saved(s));assertSame(receipt,field(custody(s),"completed"));assertSame(cpu,image(s,"cpuSat"));
        assertSame(completed,field(field(values(s),"loop"),"completedDraw"));assertNull(field(s.visit(),"drawQueue"));
        assertSame(fg,p.act2ForegroundImage());assertSame(bg,p.act2BackgroundImage());
        assertEquals(expectedSection,after.section(),"ClearPLCs preserves the genuine reached V-int current bytes/knowledge and clears only its native queue/decoder fields");
        assertEquals(vintSection.residency(),after.section().residency());assertSame(before.section().codeTable(),after.section().codeTable());
        assertEquals(0,after.section().queue().headSource());assertEquals(0,after.section().remainingTilesWord());
        assertEquals(clock+1,s.frame().vIntClock().absoluteValue().orElseThrow());assertEquals((frames+1)&65535,s.frame().environment().globals().levelFrames());
        var program=s.h().module().levelProgramState();assertEquals(0xA1,program.palFadeFlags());assertEquals(0,program.whiteFadeSteps());
        assertEquals(0,program.demoMode());assertEquals(0,program.levelStarted());assertEquals(0,program.usePlayer2());assertFalse(s.frame().paused());
        assertTrue(after.sounds().stream().anyMatch(x->x instanceof SonicCdGameModule.PastFrameSound f
                && f.sound() instanceof CdPlayerFrameRuntime.BodySound body&&body.sound()==CdPlayerBody.Sound.SCMD_FADECDA));
        assertFalse(after.sounds().stream().anyMatch(x->x.toString().contains("SCMD_FADEPCM")));
        assertArrayEquals(image(s,"cpuPalette").copy(),image(s,"displayedPalette").copy(),"No white colour step before its first actual wait");
    }

    @Test void aLaterUnsupportedPhysicalSlotStillStopsBeforeAnyExitPreludeAfterPrimaryRestart() throws Exception {
        var s=prepared229(1);var later=s.placement().pool().state(127).moveByte(CdObjectState.ByteField.ID,0x7F);s.placement().pool().write(later);
        var failure=assertThrows(SonicCdGameModule.UnsupportedAct2Loading.class,()->step(s.h(),neutral()));
        assertEquals(SonicCdGameModule.Act2Boundary.DYNAMIC_OBJECTS,failure.boundary());assertTrue(s.frame().closed());
        assertEquals(0x100,((CdLevelGlobals)field(values(s),"level")).levelRestart());
        assertEquals(later.identity(),s.placement().pool().state(127).identity());
        assertArrayEquals(later.copyBytes(),s.placement().pool().state(127).copyBytes(),"Unsupported final physical slot preserves all64 preimage bytes");assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.OLD_EXIT_PRELUDE));
        assertNull(field(s.visit(),"activePrimary"));assertFalse((boolean)field(s.visit(),"runningObjects"));
        assertThrows(IllegalStateException.class,s.visit()::capture);
    }

    @Test void oldClearFadeBitSkipsAllWaitsRetainsItsBsetBitAndClearsDirectionBeforeTheWholeRestartWord() throws Exception {
        var s=prepared229(0xA0);var cpu=image(s,"cpuPalette");var water=image(s,"waterPalette");
        setLevel(s,copy((CdLevelGlobals)field(values(s),"level"),"levelRestart",0x37));
        long clock=s.frame().vIntClock().absoluteValue().orElseThrow();step(s.h(),neutral());healthy(s,SonicCdGameModule.Act2LoadPhase.OLD_RETURNED);
        assertEquals(0,((CdLevelGlobals)field(values(s),"level")).levelRestart());assertEquals(0,s.frame().environment().movement().gates().timeWarpDirection());
        assertEquals(0x80,s.frame().environment().movement().gates().timeZone());assertSame(cpu,image(s,"cpuPalette"));assertSame(water,image(s,"waterPalette"));
        assertEquals(0xA1,s.h().module().levelProgramState().palFadeFlags());assertEquals(0,s.h().module().levelProgramState().whiteFadeSteps());
        assertEquals(clock+1,s.frame().vIntClock().absoluteValue().orElseThrow());assertEquals(0,count(s,SonicCdGameModule.Act2LoadCall.OLD_EXIT_FADE_SERVICE));
        assertEquals(SonicCdGameModule.Act2LoadCall.OLD_EXIT_RETURN,calls(s).getLast());
    }

    @Test void twentyTwoActualSelectorTwelveWaitsPublishBeforeColourAndNeverRunTheOrdinaryLoop() throws Exception {
        var s=prepared229(0xA1);step(s.h(),neutral());healthy(s,SonicCdGameModule.Act2LoadPhase.OLD_WHITE_FADE);
        var actor=s.frame().captureActor();var slots=s.placement().capture().pool().slots();var cpu=image(s,"cpuSat");var save=saved(s);
        var fg=presentation(s).act2ForegroundImage();var bg=presentation(s).act2BackgroundImage();var source=s.frame().stageSource();var world=s.h().mode().getWorldSession();
        long generation=s.h().mode().getLevelManager().getCompletedProductionLoadGeneration(),clock=s.frame().vIntClock().absoluteValue().orElseThrow();
        int frames=s.frame().environment().globals().levelFrames(),timer=s.frame().environment().globals().timeWarpTimer();
        Object timers=field(field(values(s),"loop"),"paletteTimers7"),steps=field(field(values(s),"loop"),"paletteSteps7");
        for(int wait=1;wait<=22;wait++){
            var prior=image(s,"cpuPalette");var expected=CdLevelPaletteFade.stepToWhite(new CdLevelPaletteFade.Palettes(prior,image(s,"waterPalette")));
            step(s.h(),neutral());healthy(s,wait==22?SonicCdGameModule.Act2LoadPhase.OLD_RETURNED:SonicCdGameModule.Act2LoadPhase.OLD_WHITE_FADE);
            assertArrayEquals(prior.copy(),image(s,"displayedPalette").copy());assertArrayEquals(expected.palette().copy(),image(s,"cpuPalette").copy());
            assertArrayEquals(expected.waterPalette().copy(),image(s,"waterPalette").copy());assertSame(cpu,image(s,"cpuSat"));assertSame(cpu,image(s,"displayedSat"));
            assertEquals(actor,s.frame().captureActor());assertEquals(slots,s.placement().capture().pool().slots());assertSame(save,saved(s));
            assertSame(fg,presentation(s).act2ForegroundImage());assertSame(bg,presentation(s).act2BackgroundImage());assertSame(source,s.frame().stageSource());assertSame(world,s.h().mode().getWorldSession());
            assertEquals(generation,s.h().mode().getLevelManager().getCompletedProductionLoadGeneration());assertEquals(clock+wait,s.frame().vIntClock().absoluteValue().orElseThrow());
            assertEquals(frames,s.frame().environment().globals().levelFrames());assertEquals((timer+wait)&65535,s.frame().environment().globals().timeWarpTimer());
            assertEquals(wait,count(s,SonicCdGameModule.Act2LoadCall.OLD_EXIT_FADE_SERVICE));assertEquals(wait,count(s,SonicCdGameModule.Act2LoadCall.OLD_EXIT_WHITE_COLOURS));
            assertEquals(1,count(s,SonicCdGameModule.Act2LoadCall.PRIMARY_MAIN));assertSame(timers,field(field(values(s),"loop"),"paletteTimers7"));assertSame(steps,field(field(values(s),"loop"),"paletteSteps7"));
            assertEquals(wait,s.h().module().levelProgramState().whiteFadeSteps());
        }
        assertEquals(0xA0,s.h().module().levelProgramState().palFadeFlags());assertEquals(0,((CdLevelGlobals)field(values(s),"level")).levelRestart());
        assertEquals(0,s.frame().environment().movement().gates().timeWarpDirection());assertEquals(0x80,s.frame().environment().movement().gates().timeZone());
    }

    @Test void fullAndCourseReplayRetainTheSameSourceAndSavedCustodyAcrossPreludeMiddleAndReturn() throws Exception {
        var s=prepared229(1);replayNext(s);for(int i=0;i<10;i++)step(s.h(),neutral());replayNext(s);
        for(int i=11;i<21;i++)step(s.h(),neutral());replayNext(s);healthy(s,SonicCdGameModule.Act2LoadPhase.OLD_RETURNED);
        var capture=s.visit().capture();var programme=s.h().module().levelProgramState();s.frame().close();
        assertThrows(IllegalStateException.class,()->s.visit().validateRestore(capture,programme));assertThrows(IllegalStateException.class,s.visit()::capture);
    }

    @Test void actualFadeReturnRunsTheExistingWarpAndRetainsEveryOldOwnerUntilR12bAdmission() throws Exception {
        assertWarpBridge(1);
    }

    @Test void zeroWaitReturnKeepsItsSetPaletteBitThroughWarpAndBothRewindScopes() throws Exception {
        assertWarpBridge(0xA0);
    }

    private static void assertWarpBridge(int flags) throws Exception {
        var s=prepared229(flags);step(s.h(),neutral());
        for(int n=0;n<((flags&1)==0?0:22);n++)step(s.h(),neutral());
        healthy(s,SonicCdGameModule.Act2LoadPhase.OLD_RETURNED);
        var oldValues=values(s);var actor=s.frame().captureActor();var shared=s.frame().captureShared();
        var placement=s.placement().capture();var saved=saved(s);var source=s.frame().stageSource();
        var world=s.h().mode().getWorldSession();long generation=s.h().mode().getLevelManager().getCompletedProductionLoadGeneration();
        int expectedWaits=(flags&1)==0?0:22;int paletteFlag=s.h().module().levelProgramState().palFadeFlags();
        replayNext(s);assertEquals(SonicCdGameModule.LevelProgramPhase.WARP,s.h().module().levelProgramState().phase());
        assertEquals(com.openggf.game.soniccd.scene.CdWarpScene.Phase.DELAY,s.h().module().warpSceneState().orElseThrow().phase());
        assertEquals(90,s.h().module().warpSceneState().orElseThrow().timerWord());
        assertEquals(List.of(SonicCdGameModule.ProgramSound.STOP_FM),s.h().module().programSoundRequests());
        var kernel=new com.openggf.game.soniccd.scene.CdWarpScene(s.h().module().warpSceneAssets());
        var expected=kernel.initial(0);
        for(int row=1;row<=354;row++){
            expected=kernel.step(expected,0).state();
            if(row==1||row==91||row==115||row==330||row==354)replayNext(s);else step(s.h(),neutral());
            assertEquals(expected,s.h().module().warpSceneState().orElseThrow(),"Actual existing scene owner at row"+row);
            assertEquals(actor,s.frame().captureActor());assertEquals(shared,s.frame().captureShared());
            assertEquals(placement.status(),s.placement().capture().status());assertEquals(placement.pool().slots(),s.placement().capture().pool().slots());assertArrayEquals(placement.savedFlags().copy(),s.placement().capture().savedFlags().copy());assertSame(oldValues,values(s));assertSame(saved,saved(s));
            assertSame(source,s.frame().stageSource());assertSame(world,s.h().mode().getWorldSession());
            assertEquals(generation,s.h().mode().getLevelManager().getCompletedProductionLoadGeneration());
            assertEquals(expectedWaits,s.h().module().levelProgramState().whiteFadeSteps());
            assertEquals(paletteFlag,s.h().module().levelProgramState().palFadeFlags());
            assertEquals(row<=90?List.of(SonicCdGameModule.ProgramSound.STOP_FM):List.of(SonicCdGameModule.ProgramSound.STOP_FM,SonicCdGameModule.ProgramSound.WARP_DIRECT_FM_D8),s.h().module().programSoundRequests());
        }
        replayNext(s);assertEquals(SonicCdGameModule.LevelProgramPhase.PAST_PENDING,s.h().module().levelProgramState().phase());
        assertEquals(List.of(SonicCdGameModule.ProgramSound.STOP_FM,SonicCdGameModule.ProgramSound.WARP_DIRECT_FM_D8,SonicCdGameModule.ProgramSound.STOP_FM),s.h().module().programSoundRequests());
        assertEquals(actor,s.frame().captureActor());assertEquals(shared,s.frame().captureShared());assertSame(oldValues,values(s));
        long retainedLong=s.frame().vIntClock().absoluteValue().orElseThrow();
        step(s.h(),neutral());
        var module=s.h().module();assertTrue(s.frame().closed());assertFalse(module.act2LoadingSelected());assertTrue(module.act2PastLoadingSelected());
        assertSame(world,s.h().mode().getWorldSession());assertEquals(generation+1,s.h().mode().getLevelManager().getCompletedProductionLoadGeneration());
        var next=module.requirePlayerFrame();assertNotSame(s.frame(),next);assertFalse(next.closed());assertNotSame(source,next.stageSource());
        assertEquals(CdStageKey.parse("R12B"),next.stageSource().stage());assertEquals(1,next.environment().movement().gates().zoneAct());
        assertEquals(0x80,next.environment().movement().gates().timeZone());assertEquals(retainedLong,next.vIntClock().absoluteValue().orElseThrow());
        var loading=module.act2PastLoadingVisit();assertSame(saved,loading.saved());assertEquals(expectedWaits,loading.state().oldWhiteSteps());
        assertEquals((paletteFlag&1)==0?SonicCdAct2PastLoadingVisit.Phase.FIRST_PLC:SonicCdAct2PastLoadingVisit.Phase.INITIAL_WHITE,loading.state().phase());
        assertEquals(0,loading.state().initialWhiteSteps());assertEquals(0,loading.state().fromWhiteSteps());assertEquals(0,next.state().header().slotId());
        assertFalse(loading.state().displayEnabled());assertTrue(loading.state().calls().contains(SonicCdAct2PastLoadingVisit.Call.CLEAR_PLCS));
        assertFalse(loading.state().calls().contains(SonicCdAct2PastLoadingVisit.Call.PLAY_LEVEL_MUSIC));
        for(int row=0;row<256&&loading.state().phase()!=SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL;row++)step(s.h(),neutral());
        assertEquals(SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL,loading.state().phase());
        assertEquals((paletteFlag&1)==0?0:22,loading.state().initialWhiteSteps());assertEquals(22,loading.state().fromWhiteSteps());
        assertEquals(20,loading.completedSavedStores().size());assertEquals(0,next.environment().movement().gates().timeZone());
        assertEquals(1,next.state().header().slotId());assertEquals(0,next.state().motion().routine(),"No ordinary Sonic Init during loading");
        assertEquals(0,next.environment().globals().levelFrames());assertTrue(loading.state().displayEnabled());assertFalse(next.closed());
        assertEquals(0,loading.state().section().queue().headSource());assertEquals(0,loading.state().section().remainingTilesWord());
        assertDoesNotThrow(()->step(s.h(),neutral()));assertFalse(next.closed());
        assertEquals(2,next.state().motion().routine());assertEquals(1,next.environment().globals().levelFrames());
        assertTrue(module.act2PastWorldHost().video().completedCpu().isPresent(),"First actual unpaused pass reaches CPU drain and tail");
        assertSame(world,s.h().mode().getWorldSession());assertEquals(generation+1,s.h().mode().getLevelManager().getCompletedProductionLoadGeneration());
    }

    @Test void actualWarpRendererRestoresTheOldSelectionAndRejectsCaughtServiceReentryWithoutClosingTheFrame() throws Exception {
        try(var egl=TestSonicCdPastResultsRoute.Egl.open()){
            var s=prepared229(0);step(s.h(),neutral());var registry=s.h().mode().getRewindRegistry();
            var old=registry.capture();var before=captureNative(s.h());var oldPixels=warpPixels(s);
            assertNativeCapture(before,s.h());step(s.h(),neutral());
            var blank=warpPixels(s);for(int at=0;at<blank.length;at+=4)assertEquals(0,(blank[at]&255)|(blank[at+1]&255)|(blank[at+2]&255));
            for(int row=1;row<=130;row++)step(s.h(),neutral());
            var selected=s.h().module().warpSceneState().orElseThrow();assertTrue(selected.displayEnabled());
            assertTrue(selected.displayedSpriteCount()>0,"The real WARP scene selects its actual native objects");
            var current=registry.capture();var course=registry.captureCourse();var nativeCurrent=captureNative(s.h());
            var pixels=warpPixels(s);assertFalse(Arrays.equals(blank,pixels));assertFalse(Arrays.equals(oldPixels,pixels));
            var graphics=com.openggf.game.GameServices.graphics();
            graphics.registerCommand((x,y,width,height)->{
                assertThrows(IllegalStateException.class,()->s.h().module().serviceAct2Loading(0));
                assertThrows(IllegalStateException.class,()->s.h().module().serviceWarpProgram(0));
                assertThrows(IllegalStateException.class,()->s.h().module().gameplayFrameController().drawOverlay());
                assertThrows(IllegalStateException.class,registry::capture);
            });
            assertArrayEquals(pixels,warpPixels(s));assertFalse(s.frame().closed());assertNativeCapture(nativeCurrent,s.h());
            registry.restore(old);assertNativeCapture(before,s.h());assertArrayEquals(oldPixels,warpPixels(s));
            registry.restore(current);assertNativeCapture(nativeCurrent,s.h());assertArrayEquals(pixels,warpPixels(s));
            registry.restoreCourse(course);assertNativeCapture(nativeCurrent,s.h());assertArrayEquals(pixels,warpPixels(s));
        }
    }

    private static byte[] warpPixels(Scene s){
        org.lwjgl.opengl.GL11.glViewport(0,0,320,224);org.lwjgl.opengl.GL11.glClearColor(1,0,1,1);
        org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT|org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
        s.h().module().gameplayFrameController().drawOverlay();org.lwjgl.opengl.GL11.glFinish();
        var b=org.lwjgl.BufferUtils.createByteBuffer(320*224*4);
        org.lwjgl.opengl.GL11.glReadPixels(0,0,320,224,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,b);
        assertEquals(org.lwjgl.opengl.GL11.GL_NO_ERROR,org.lwjgl.opengl.GL11.glGetError());var bytes=new byte[b.capacity()];b.get(bytes);return bytes;
    }

    @Test void idleAndNestedCallsCannotMintExitStoresOrOrdinaryMutations() throws Exception {
        var s=prepared229(1);var before=captureNative(s.h());
        assertThrows(IllegalStateException.class,()->s.h().module().beginAct2OldProgrammeExit(s.visit()));assertNativeCapture(before,s.h());
        step(s.h(),neutral());healthy(s,SonicCdGameModule.Act2LoadPhase.OLD_WHITE_FADE);var exit=captureNative(s.h());
        assertThrows(IllegalStateException.class,()->s.h().module().completeAct2OldWhiteFadeWait(s.visit()));
        assertThrows(IllegalStateException.class,()->s.h().module().completeAct2LoadingBeginLevel(s.visit()));
        assertThrows(IllegalStateException.class,()->s.h().module().clearAct2PaletteFlag(s.visit()));
        assertThrows(IllegalStateException.class,()->presentation(s).serviceAct2PlaneAtLevelLoad());
        assertThrows(IllegalStateException.class,()->presentation(s).continueAct2CameraAfterObjects(s.frame(),1));assertNativeCapture(exit,s.h());
        set(s.visit(),"oldProgrammeContinuation",true);
        assertThrows(IllegalStateException.class,()->s.h().module().serviceAct2Loading(0));assertFalse(s.frame().closed());
        set(s.visit(),"oldProgrammeContinuation",false);assertNativeCapture(exit,s.h());
    }

    @Test void oldClearBitReturnAlsoReplaysWithoutMintingAFadeWait() throws Exception {
        var s=prepared229(0xA0);replayNext(s);healthy(s,SonicCdGameModule.Act2LoadPhase.OLD_RETURNED);
        assertEquals(0,count(s,SonicCdGameModule.Act2LoadCall.OLD_EXIT_FADE_SERVICE));assertEquals(0xA1,s.h().module().levelProgramState().palFadeFlags());
    }

    @Test void selectorTwelveReadsCurrentRawStartOnceWithoutTogglingPauseOrChangingSaveAuthority() throws Exception {
        var s=prepared229(1);step(s.h(),neutral());var save=saved(s);long clock=s.frame().vIntClock().absoluteValue().orElseThrow();
        step(s.h(),LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0,0,0,0,true,true),PlayerInputState.neutral()));
        healthy(s,SonicCdGameModule.Act2LoadPhase.OLD_WHITE_FADE);assertFalse(s.frame().paused());
        assertEquals(0x80,s.frame().environment().controls().rawHeld());assertEquals(0x80,s.frame().environment().controls().rawTap());
        assertEquals(clock+1,s.frame().vIntClock().absoluteValue().orElseThrow());assertSame(save,saved(s));
        var shared=s.frame().captureShared();var e=shared.environment();var m=e.movement();
        s.frame().restoreShared(copy(shared,"environment",copy(e,"movement",copy(m,"gates",copy(m.gates(),"timeZone",0x81)))));
        assertThrows(IllegalStateException.class,s.visit()::capture);s.frame().restoreShared(shared);s.visit().capture();
    }

    @Test void unownedFirstLoadResetStopsAfterItsEarlierScalarPrefixAndBeforeClearPlcs() throws Exception {
        var s=prepared229(1);setLevel(s,copy((CdLevelGlobals)field(values(s),"level"),"plcLoadFlags",2));
        var section=ordinaryVIntSectionPrefix(s);var saved=saved(s);
        var stop=assertThrows(SonicCdGameModule.UnsupportedAct2Loading.class,()->step(s.h(),neutral()));
        assertEquals(SonicCdGameModule.Act2Boundary.OLD_EXIT_FIRST_LOAD,stop.boundary());assertTrue(s.frame().closed());
        assertEquals(3,((CdLevelGlobals)field(values(s),"level")).plcLoadFlags());assertEquals(section,field(values(s),"section"),"Reached V-int current/PLC/HUD prefix survives; unowned first-load reset has not called ClearPLCs");assertSame(saved,saved(s));
        assertEquals(SonicCdGameModule.Act2LoadCall.OLD_EXIT_PRELUDE,calls(s).getLast());assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.OLD_EXIT_CLEAR_PLCS));
        assertEquals(SonicCdGameModule.LevelProgramPhase.OLD_EXIT_PRELUDE,s.h().module().levelProgramState().phase());
        assertEquals(0,s.h().module().levelProgramState().gameMode()&0x80,"Main56 has not been reached");
    }

    @Test void constructedFastPlcFaultRetainsEarlierRawReadAndPalettePublicationButNoFinishedWait() throws Exception {
        var s=prepared229(1);step(s.h(),neutral());var clock=s.frame().vIntClock();var cpu=image(s,"cpuPalette");var save=saved(s);
        // Explicit malformed immutable-value control at the reached fast-PLC owner, not a native state or injected callback.
        set(s.visit(),"values",copy(values(s),"section",null));
        assertThrows(NullPointerException.class,()->step(s.h(),LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0,0,0,0,true,true),PlayerInputState.neutral())));
        assertTrue(s.frame().closed());assertEquals(0x80,s.frame().environment().controls().rawHeld());assertSame(clock,s.frame().vIntClock());
        assertSame(cpu,image(s,"cpuPalette"));assertSame(cpu,image(s,"displayedPalette"));assertSame(save,saved(s));
        assertEquals(0,s.h().module().levelProgramState().whiteFadeSteps());assertEquals(0,count(s,SonicCdGameModule.Act2LoadCall.OLD_EXIT_FADE_SERVICE));
        assertFalse((boolean)field(s.visit(),"servicing"));assertFalse((boolean)field(s.visit(),"oldProgrammeContinuation"));
    }

    /** Pure component caller-flow oracle for the reached ordinary V-int only.
     * No Visit callback, Frame service, draw, publication or gameplay is executed here.
     * Native Main normal8 calls LoadShieldArt, DrawLevel, DecompPLCSlow, UpdateHUD before Finish/Main.
     * These controls have no active powerup-copy request; plane/Frame art work does not write Section.
     * HUD normal time still writes B220/B260/B2E0 under raw80 except at the max-time early return.
     * Compare complete Section values (including current bytes/knowledge), never a pre-V-int reference. */
    @SuppressWarnings("unchecked")private static CdR12aSectionArt.State ordinaryVIntSectionPrefix(Scene s)throws Exception{
        var before=s.h().module().act2LoadState();var originalValues=values(s);var globals=field(originalValues,"globals");
        var art=(CdR12aSectionArt)field(s.visit(),"art");var hud=(CdLevelHud.Act2)field(s.visit(),"hud");
        var actor=s.frame().state();var env=s.frame().environment();
        assertFalse(s.frame().paused(),"Constructed exit controls admit an ordinary unpaused service");
        var powerup=CdPowerupArtRuntime.gate(before.level().powerup());assertFalse(powerup.copyRequired(),"This caller-flow oracle is bounded to the actual inactive copy branch");
        var level=copy(before.level(),"powerup",powerup.powerup());
        var slow=art.serviceSlow(before.section(),presentation(s).act2ForegroundImage().scrollLock());
        var inputs=new CdLevelHud.Inputs(new CdLevelHud.Globals(env.globals().debugCheat(),
                ((OptionalInt)field(globals,"pauseMusicBit")).orElseThrow()<<8,env.controls().controlLocked(),
                env.movement().gates().timeZone(),actor.motion().centreX16_16()>>>16,actor.motion().centreY16_16()>>>16,
                env.movement().gates().zoneAct()),new CdLevelHud.DebugData(OptionalInt.empty(),
                (Optional<CdAssetBytes>)field(globals,"layoutRam"),(OptionalInt)field(globals,"debugBlockWord")));
        var result=hud.run(new CdLevelHud.Act2State(slow,before.ring(),level,
                (CdOpeningObjectRuntime.ScoreGlobals)field(globals,"score"),(CdCheckpointLiveAux.State)field(globals,"live"),
                (Optional<CdLevelHud.BonusValues>)field(globals,"bonus")),inputs);
        assertTrue(result.calls().contains(CdLevelHud.NativeCall.DRAW_MINS),"Actual non-max-time control reaches normal HUD current writes");
        assertEquals(2,result.calls().stream().filter(c->c==CdLevelHud.NativeCall.DRAW_SECS).count());
        assertSame(originalValues,values(s));assertSame(actor,s.frame().state());assertSame(env,s.frame().environment());
        return result.state().section();
    }

    private static Scene prepared229(int fadeFlags)throws Exception{
        var s=scene();for(int whole=32;whole<128;whole++)s.placement().pool().deleteObject(whole);
        var a=s.frame().captureActor();var motion=copy(a.state().motion(),"playerControl",1);motion=copy(motion,"charge",0);
        s.frame().restoreActor(copy(a,"state",copy(a.state(),"motion",motion)));setTimer(s,209);
        var shared=s.frame().captureShared();var e=shared.environment();var m=e.movement();
        s.frame().restoreShared(copy(shared,"environment",copy(e,"movement",copy(m,"gates",copy(m.gates(),"timeWarpDirection",255)))));
        step(s.h(),neutral());assertEquals(210,s.frame().environment().globals().timeWarpTimer());assertEquals(0x80,s.frame().environment().movement().gates().timeZone());
        assertTrue(((Optional<?>)field(custody(s),"completed")).isPresent());setTimer(s,229);
        set(s.h().module(),"levelProgram",copy(s.h().module().levelProgramState(),"palFadeFlags",fadeFlags));return s;
    }
    private static Scene scene()throws Exception{
        var old=ready();for(int call=old.returned()+1;call<=10419;call++)step(old.harness(),controller(call));
        step(old.harness(),neutral());var h=old.harness();
        for(int service=0;service<64;service++){step(h,neutral());if(h.module().act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN)){
            var v=(SonicCdAct2LoadingVisit)field(h.module(),"act2Loading");var s=new Scene(h,v,h.module().requirePlayerFrame(),(CdObjectPlacementRuntime)field(v,"placement"));
            for(int ordinary=0;ordinary<256;ordinary++){if(s.frame().environment().controls().controlLocked()==0&&h.module().act2LoadState().section().queue().headSource()==0)return s;step(h,neutral());}
            throw new AssertionError("Actual Title/PLCs did not release");}}
        throw new AssertionError("Actual Act2 ordinary cut not reached");
    }
    private static LogicalInputSnapshot controller(int call){
        if(call<=8276)return route(call);int directions=8,actions=call<=8308?2:0;
        if(call>=9001&&call<=9203)directions=4;else if(call>=9206&&call<=9217)directions=0;
        else if(call>=9218&&call<=9263){directions=2;actions=call==9219?2:0;}else if(call>=9336&&call<=9367)actions=2;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions,0,actions,0,false,false),PlayerInputState.neutral());
    }
    private static LogicalInputSnapshot neutral(){return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(),PlayerInputState.neutral());}
    private static void setTimer(Scene s,int timer)throws Exception{var shared=s.frame().captureShared();var env=shared.environment();s.frame().restoreShared(copy(shared,"environment",copy(env,"globals",copy(env.globals(),"timeWarpTimer",timer))));}
    private static void setLevel(Scene s,CdLevelGlobals level)throws Exception{set(s.visit(),"values",copy(values(s),"level",level));}
    private static Object field(Object o,String name)throws Exception{return actualField(o,name);}
    private static Object values(Scene s)throws Exception{return field(s.visit(),"values");}
    private static Object custody(Scene s)throws Exception{return field(field(values(s),"globals"),"timeWarpCustody");}
    private static Object saved(Scene s)throws Exception{return field(custody(s),"saved");}
    private static CdAssetBytes image(Scene s,String name)throws Exception{return (CdAssetBytes)field(values(s),name);}
    private static SonicCdPlayerPresentation presentation(Scene s)throws Exception{return (SonicCdPlayerPresentation)field(s.visit(),"presentation");}
    @SuppressWarnings("unchecked")private static List<SonicCdGameModule.Act2LoadCall> calls(Scene s)throws Exception{return (List<SonicCdGameModule.Act2LoadCall>)field(values(s),"calls");}
    private static long count(Scene s,SonicCdGameModule.Act2LoadCall call)throws Exception{return calls(s).stream().filter(x->x==call).count();}
    private static void healthy(Scene s,SonicCdGameModule.Act2LoadPhase phase){assertFalse(s.frame().closed());assertSame(s.frame(),s.h().module().requirePlayerFrame());assertEquals(phase,s.h().module().act2LoadState().phase());}
    private static void replayNext(Scene s)throws Exception{
        var registry=s.h().mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=captureNative(s.h());
        step(s.h(),neutral());var after=captureNative(s.h());registry.restore(full);assertNativeCapture(before,s.h());step(s.h(),neutral());assertNativeCapture(after,s.h());
        registry.restoreCourse(course);assertNativeCapture(before,s.h());step(s.h(),neutral());assertNativeCapture(after,s.h());
    }
    @SuppressWarnings("unchecked")private static <T>T copy(T value,String field,Object replacement)throws Exception{
        var c=value.getClass().getRecordComponents();var types=new Class<?>[c.length];var args=new Object[c.length];boolean found=false;
        for(int n=0;n<c.length;n++){types[n]=c[n].getType();var getter=c[n].getAccessor();getter.setAccessible(true);args[n]=getter.invoke(value);if(c[n].getName().equals(field)){args[n]=replacement;found=true;}}
        assertTrue(found,field);var ctor=value.getClass().getDeclaredConstructor(types);ctor.setAccessible(true);return (T)ctor.newInstance(args);
    }
    private static void set(Object owner,String name,Object value)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    // BEGIN R12B LIVE WORLD PROOF APPEND — 2026-11-06.
    // Actual prior World/354 WARP/FULL path. Only prepared229's openly constructed
    // timer/flags differ from natural gameplay. No native owner, picture or decoder is mocked.

    @Test void actualR12bFullAndCourseReplayOwnFirstSectionAndFromWhiteUntilTheOrdinaryBoundary() throws Exception {
        var arrival=r12bArrive(prepared229(1));var budget=new R12bServiceBudget();
        assertEquals(SonicCdAct2PastLoadingVisit.Phase.FIRST_PLC,arrival.visit().state().phase());
        assertInstanceOf(SonicCdPastDisplay.Disabled.class,r12bSelected(arrival).projection());
        r12bReplayNext(arrival,budget);
        assertEquals(SonicCdAct2PastLoadingVisit.Phase.SECTION_PLC,arrival.visit().state().phase());
        assertTrue(arrival.visit().state().section().queue().headSource()!=0
                ||arrival.visit().state().section().remainingTilesWord()!=0,"A genuine queued section wait is required");
        r12bAssertGeometry(arrival);r12bReplayNext(arrival,budget);
        r12bDriveTo(arrival,budget,SonicCdAct2PastLoadingVisit.Phase.FROM_WHITE);
        assertInstanceOf(SonicCdPastDisplay.Disabled.class,r12bSelected(arrival).projection());
        assertSame(arrival.saved(),arrival.visit().saved());r12bReplayNext(arrival,budget);
        assertEquals(1,arrival.visit().state().fromWhiteSteps());r12bReady(arrival);
        r12bDriveTo(arrival,budget,SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL);r12bReady(arrival);
        assertEquals(22,arrival.visit().state().fromWhiteSteps());assertFalse(arrival.frame().closed());
        assertEquals(0,arrival.frame().state().motion().routine(),"Loading has not executed ordinary Sonic Init/body");
        assertEquals(1,arrival.frame().state().header().slotId(),"Only genuine LoadPlayer is reached");
        assertTrue(budget.calls<=256,"All physical target services, including replay executions, are bounded");
    }

    @Test void actualR12bFirstUnpausedOrdinaryServiceRunsTheRealPhysicalPassAndRestoresItsCompleteCut() throws Exception {
        var arrival=r12bArrive(prepared229(1));var budget=new R12bServiceBudget();
        r12bDriveTo(arrival,budget,SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL);
        var frame=arrival.frame();var registry=arrival.old().h().mode().getRewindRegistry();
        var before=r12bProof(arrival);var full=registry.capture();var course=registry.captureCourse();
        assertEquals(0,frame.state().motion().routine());int oldFrame=frame.environment().globals().levelFrames();
        assertDoesNotThrow(()->step(arrival.old().h(),neutral()),"First genuine unpaused R12B pass");
        assertFalse(frame.closed());assertEquals((oldFrame+1)&65535,frame.environment().globals().levelFrames());
        assertEquals(2,frame.state().motion().routine(),"Actual physical primary Init must run");
        assertEquals(0x230F60,frame.state().header().mappingAddress());
        assertTrue(arrival.host().video().completedCpu().isPresent(),"Real complete walk and camera must produce CPU SAT");
        var after=r12bProof(arrival);
        registry.restore(full);r12bAssertProof(before,arrival,"ordinary full before");
        step(arrival.old().h(),neutral());r12bAssertProof(after,arrival,"ordinary full forward");
        registry.restoreCourse(course);r12bAssertProof(before,arrival,"ordinary course before");
        step(arrival.old().h(),neutral());r12bAssertProof(after,arrival,"ordinary course forward");
    }

    @Test void actualR12bSelectedPixelsRepeatAcrossRewindAndCaughtReentryLeavesTheHealthyNativeCut() throws Exception {
        assertInstanceOf(com.openggf.audio.NullAudioBackend.class,com.openggf.game.GameServices.audio().getBackend(),
                "Existing fixture must establish no-device audio before real World work");
        try(var egl=TestSonicCdPastResultsRoute.Egl.open()){
            var arrival=r12bArrive(prepared229(1));var budget=new R12bServiceBudget();
            r12bDriveTo(arrival,budget,SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL);
            var ready=r12bReady(arrival);assertTrue(ready.scene().displayEnabled());r12bAssertGeometry(arrival);
            var registry=arrival.old().h().mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
            var nativeBefore=r12bProof(arrival);var pixels=warpPixels(arrival.old());
            r12bAssertPicture(pixels);r12bAssertProof(nativeBefore,arrival,"first real selected draw is observational");
            assertArrayEquals(pixels,warpPixels(arrival.old()));
            var scroll=arrival.old().h().module().getScrollHandlerProvider().getHandler(0);
            assertSame(arrival.host().handler(),scroll);var scrollBinding=scroll.captureRewindState();
            var graphics=com.openggf.game.GameServices.graphics();int[] caught={0};
            graphics.registerCommand((x,y,width,height)->{
                assertThrows(IllegalStateException.class,()->arrival.old().h().module().serviceAct2PastLoading(0));caught[0]++;
                assertThrows(IllegalStateException.class,()->arrival.old().h().module().gameplayFrameController().drawOverlay());caught[0]++;
                assertThrows(IllegalStateException.class,()->arrival.old().h().module().gameplayFrameController().close());caught[0]++;
                assertThrows(IllegalStateException.class,()->arrival.old().h().module().gameplayFrameController().afterTick(null,false));caught[0]++;
                assertThrows(IllegalStateException.class,registry::capture);caught[0]++;
                assertThrows(IllegalStateException.class,()->scroll.restoreRewindState(scrollBinding));caught[0]++;
                assertFalse(arrival.frame().closed(),"Rejected recursive entry cannot retire the healthy Frame");
            });
            assertArrayEquals(pixels,warpPixels(arrival.old()));assertEquals(6,caught[0],"Actual renderer FIFO must execute all rejection controls");
            assertFalse(arrival.frame().closed());r12bAssertProof(nativeBefore,arrival,"caught FIFO reentry preserves all native values");
            registry.restore(full);r12bAssertProof(nativeBefore,arrival,"full restore selected cut");
            assertArrayEquals(pixels,warpPixels(arrival.old()));
            registry.restoreCourse(course);r12bAssertProof(nativeBefore,arrival,"course restore selected cut");
            assertArrayEquals(pixels,warpPixels(arrival.old()));
            assertInstanceOf(com.openggf.audio.NullAudioBackend.class,com.openggf.game.GameServices.audio().getBackend());
        }
    }

    @Test void actualR12bEightUnpausedServicesSelectGenuineSonicPixelsAndReplayWithoutPoseOrArtInjection() throws Exception {
        byte[] export=null;int exportService=0;
        try(var egl=TestSonicCdPastResultsRoute.Egl.open()){
            var arrival=r12bArrive(prepared229(1));var budget=new R12bServiceBudget();
            r12bDriveTo(arrival,budget,SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL);
            var registry=arrival.old().h().mode().getRewindRegistry();boolean selectedSonic=false;
            for(int ordinary=1;ordinary<=8;ordinary++){
                var before=r12bProof(arrival);var full=registry.capture();var course=registry.captureCourse();
                step(arrival.old().h(),neutral());assertFalse(arrival.frame().closed());var after=r12bProof(arrival);
                registry.restore(full);r12bAssertProof(before,arrival,"ordinary full preimage"+ordinary);
                step(arrival.old().h(),neutral());r12bAssertProof(after,arrival,"ordinary full forward"+ordinary);
                registry.restoreCourse(course);r12bAssertProof(before,arrival,"ordinary course preimage"+ordinary);
                step(arrival.old().h(),neutral());r12bAssertProof(after,arrival,"ordinary course forward"+ordinary);
                var selected=r12bSelected(arrival);var cpu=selected.inputs().selectedCpu();
                if(cpu.isPresent()&&cpu.orElseThrow().provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==0)){
                    selectedSonic=true;var ready=r12bReady(arrival);var nativeBefore=r12bProof(arrival);
                    byte[] pixels=warpPixels(arrival.old());r12bAssertPicture(pixels);r12bAssertProof(nativeBefore,arrival,"selected Sonic draw");
                    assertArrayEquals(pixels,warpPixels(arrival.old()));
                    var omitted=TestSonicCdPastResultsRoute.omitWhole(ready.scene(),cpu.orElseThrow(),0);
                    var binding=field(arrival.host(),"render");
                    var renderer=assertInstanceOf(com.openggf.graphics.RetainedPlaneSceneRenderer.class,field(binding,"renderer"));
                    org.lwjgl.opengl.GL11.glClearColor(1,0,1,1);org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT|org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
                    var camera=arrival.old().h().mode().getCamera();renderer.draw(omitted,camera.getX(),camera.getY(),arrival.old().h().module()::validateAct2PastLoadingCut);
                    org.lwjgl.opengl.GL11.glFinish();var raw=org.lwjgl.BufferUtils.createByteBuffer(320*224*4);
                    org.lwjgl.opengl.GL11.glReadPixels(0,0,320,224,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,raw);
                    var contrast=new byte[raw.capacity()];raw.get(contrast);assertEquals(org.lwjgl.opengl.GL11.GL_NO_ERROR,org.lwjgl.opengl.GL11.glGetError());
                    assertFalse(Arrays.equals(pixels,contrast),"Genuine selected Sonic must contribute visible pixels");
                    r12bAssertProof(nativeBefore,arrival,"renderer-only omission cannot change native owners");
                    assertArrayEquals(pixels,warpPixels(arrival.old()));
                    var pixelFull=registry.capture();var pixelCourse=registry.captureCourse();
                    registry.restore(pixelFull);r12bAssertProof(nativeBefore,arrival,"selected Sonic full");assertArrayEquals(pixels,warpPixels(arrival.old()));
                    registry.restoreCourse(pixelCourse);r12bAssertProof(nativeBefore,arrival,"selected Sonic course");assertArrayEquals(pixels,warpPixels(arrival.old()));
                    if(export==null){export=pixels.clone();exportService=ordinary;}
                }
            }
            assertTrue(selectedSonic,"Fixed eight neutral services must expose the source-native blink/draw/DPLC/next8 sequence");
        }
        r12bExportRgba=export;r12bExportService=exportService;
    }
    private void writeOptionalR12bSonic() throws Exception {
        String output=System.getenv("SONICCD_R12B_ENTRY_OUTPUT");if(r12bExportRgba==null||output==null||output.isBlank())return;
        var root=java.nio.file.Path.of(System.getProperty("user.home"),"Downloads","Sonic-CD","captures","engine").toRealPath();
        var directory=java.nio.file.Path.of(output).toAbsolutePath().normalize();assertTrue(directory.startsWith(root)&&!directory.equals(root));
        java.nio.file.Files.createDirectories(directory);assertEquals(directory,directory.toRealPath());
        var image=new java.awt.image.BufferedImage(320,224,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<224;y++)for(int x=0;x<320;x++){int at=((223-y)*320+x)*4;
            image.setRGB(x,y,0xFF000000|(r12bExportRgba[at]&255)<<16|(r12bExportRgba[at+1]&255)<<8|(r12bExportRgba[at+2]&255));}
        if(!javax.imageio.ImageIO.write(image,"png",directory.resolve("ENGINE-R12B-constructed-entry-selected-Sonic.png").toFile()))throw new java.io.IOException("PNG unavailable");
        java.nio.file.Files.writeString(directory.resolve("ENGINE-R12B-constructed-entry-selected-Sonic.txt"),
                "ENGINE OpenGGF original-USA R12B. Constructed old timer229 fixture; actual World WARP/FULL/loading, followed by8 fixed neutral ordinary services. First captured selected Sonic service="+r12bExportService+". Real renderer/omission/full-course pixel replay. No pose/art injection, natural controller endpoint, emulator capture or parity claim. Exported after context/module/session/boot cleanup.\n");
    }

    @Test void actualR12bFullRejectsRetiredOldEpochAndUnscopedEqualPayloadBeforeAnyOwnerMutation() throws Exception {
        var old=prepared229(1);var registry=old.h().mode().getRewindRegistry();
        var oldFull=registry.capture();var oldCourse=registry.captureCourse();var oldNative=old.visit().capture();
        var oldProgramme=old.h().module().levelProgramState();var oldScroll=presentation(old).captureAct2Scroll();
        var arrival=r12bArrive(old);var before=r12bProof(arrival);
        assertThrows(IllegalArgumentException.class,()->registry.restore(oldFull));r12bAssertProof(before,arrival,"old FULL epoch");
        assertThrows(IllegalArgumentException.class,()->registry.restoreCourse(oldCourse));r12bAssertProof(before,arrival,"old course epoch");
        assertThrows(IllegalStateException.class,()->old.visit().validateRestore(oldNative,oldProgramme));
        r12bAssertProof(before,arrival,"retired old native owner");
        assertThrows(IllegalArgumentException.class,()->arrival.host().handler().restoreRewindState(oldScroll));
        r12bAssertProof(before,arrival,"old scroll token cannot adopt native fields");
        var current=registry.capture();
        var unscoped=new com.openggf.game.rewind.CompositeSnapshot(new LinkedHashMap<>(current.entries()));
        assertThrows(IllegalArgumentException.class,()->registry.restore(unscoped));
        assertThrows(IllegalArgumentException.class,()->registry.restoreCourse(unscoped));
        r12bAssertProof(before,arrival,"equal public payload has no World/epoch authority");
        assertFalse(arrival.frame().closed());assertTrue(old.frame().closed());
        r12bReplayNext(arrival,new R12bServiceBudget());r12bAssertGeometry(arrival);
    }

    private record R12bArrival(Scene old,SonicCdAct2PastLoadingVisit visit,SonicCdAct2PastWorldHost host,
                              CdPlayerFrameRuntime frame,CdTimeWarpSaveData.State saved,Object world,Object data,long generation){}
    private static final class R12bServiceBudget {private int calls;}
    private record R12bProof(Object visit,Object host,Object frame,Object source,Object world,Object data,Object level,long generation,
                             Object saved,NativeCapture nativeCapture,long representedClock,int cameraX,int cameraY){}

    private static R12bArrival r12bArrive(Scene old)throws Exception{
        var h=old.h();var world=h.mode().getWorldSession();var data=world.getDataSource();
        var saved=assertInstanceOf(CdTimeWarpSaveData.State.class,saved(old));
        var oldSource=old.frame().stageSource();var oldLevel=h.mode().getLevelManager().getCurrentLevel();
        long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        step(h,neutral());
        for(int wait=0;h.module().levelProgramState().phase()==SonicCdGameModule.LevelProgramPhase.WHITE_FADE;wait++){
            assertTrue(wait<22,"Old source fade may execute at most its genuine22 waits");step(h,neutral());
        }
        healthy(old,SonicCdGameModule.Act2LoadPhase.OLD_RETURNED);step(h,neutral());
        assertEquals(SonicCdGameModule.LevelProgramPhase.WARP,h.module().levelProgramState().phase());
        var kernel=new com.openggf.game.soniccd.scene.CdWarpScene(h.module().warpSceneAssets());var expected=kernel.initial(0);
        for(int service=1;service<=354;service++){
            expected=kernel.step(expected,0).state();step(h,neutral());
            assertEquals(expected,h.module().warpSceneState().orElseThrow(),"Existing actual WARP service"+service);
            assertSame(world,h.mode().getWorldSession());assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
            assertFalse(old.frame().closed());assertSame(saved,saved(old));
        }
        step(h,neutral());assertEquals(SonicCdGameModule.LevelProgramPhase.PAST_PENDING,h.module().levelProgramState().phase());
        assertFalse(old.frame().closed());assertSame(oldLevel,h.mode().getLevelManager().getCurrentLevel());
        step(h,neutral()); // The actual staged target dispatch, true FULL and synchronous begin; no target VSync is manufactured here.
        assertTrue(h.module().act2PastLoadingSelected(),"Actual target dispatch must install the real R12B visit");
        var visit=h.module().act2PastLoadingVisit();var host=h.module().act2PastWorldHost();var frame=h.module().requirePlayerFrame();
        assertNotNull(visit);assertNotNull(host);assertNotSame(old.frame(),frame);assertNotSame(oldSource,frame.stageSource());
        assertTrue(old.frame().closed());assertFalse(frame.closed());assertSame(world,h.mode().getWorldSession());assertSame(data,world.getDataSource());
        assertEquals(generation+1,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertNotSame(oldLevel,h.mode().getLevelManager().getCurrentLevel());assertSame(saved,visit.saved());
        var terrain=assertInstanceOf(CdR12bTerrainAssets.class,field(visit,"terrain"));
        var player=assertInstanceOf(CdR12bPlayerAssets.class,field(visit,"player"));
        assertTrue(frame.stageSource().ownsAct2Past(terrain,player),"Actual new Frame must borrow the bound original R12B pair");
        assertEquals(1,frame.environment().movement().gates().zoneAct());assertEquals(0x80,frame.environment().movement().gates().timeZone());
        assertTrue(h.mode().getSpriteManager().getAllSprites().isEmpty(),"No ordinary Sonic/SAT object is invented during target loading");
        assertEquals(SonicCdAct2PastLoadingVisit.Phase.FIRST_PLC,visit.state().phase(),
                "The real old fade cleared bit0; target begin stops before its first mandatory selectorC");
        return new R12bArrival(old,visit,host,frame,saved,world,data,generation+1);
    }
    private static void r12bStep(R12bArrival arrival,R12bServiceBudget budget){
        assertTrue(budget.calls<256,"R12B loading exceeded256 physical services at "+arrival.visit().state().phase()
                +", source="+arrival.visit().state().section().queue().headSource()+", remaining="+arrival.visit().state().section().remainingTilesWord());
        budget.calls++;step(arrival.old().h(),neutral());
        assertFalse(arrival.visit().failed());assertFalse(arrival.frame().closed());
        assertSame(arrival.world(),arrival.old().h().mode().getWorldSession());
        assertEquals(arrival.generation(),arrival.old().h().mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(arrival.saved(),arrival.visit().saved());
    }
    private static void r12bDriveTo(R12bArrival arrival,R12bServiceBudget budget,SonicCdAct2PastLoadingVisit.Phase phase){
        while(arrival.visit().state().phase()!=phase){
            assertNotEquals(SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL,arrival.visit().state().phase(),
                    "Requested loading cut was skipped; do not service ordinary selector8 to fit it");
            r12bStep(arrival,budget);
        }
    }
    private static void r12bReplayNext(R12bArrival arrival,R12bServiceBudget budget)throws Exception{
        var registry=arrival.old().h().mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
        var before=r12bProof(arrival);r12bStep(arrival,budget);var after=r12bProof(arrival);
        registry.restore(full);r12bAssertProof(before,arrival,"R12B full before");
        r12bStep(arrival,budget);r12bAssertProof(after,arrival,"R12B full identical next service");
        registry.restoreCourse(course);r12bAssertProof(before,arrival,"R12B course before");
        r12bStep(arrival,budget);r12bAssertProof(after,arrival,"R12B course identical next service");
    }
    private static R12bProof r12bProof(R12bArrival arrival){
        var h=arrival.old().h();assertSame(arrival.visit(),h.module().act2PastLoadingVisit());
        assertSame(arrival.host(),h.module().act2PastWorldHost());assertSame(arrival.frame(),h.module().requirePlayerFrame());
        assertSame(arrival.saved(),arrival.visit().saved());var nativeCapture=captureNative(h);
        return new R12bProof(arrival.visit(),arrival.host(),arrival.frame(),arrival.frame().stageSource(),h.mode().getWorldSession(),
                h.mode().getWorldSession().getDataSource(),h.mode().getLevelManager().getCurrentLevel(),
                h.mode().getLevelManager().getCompletedProductionLoadGeneration(),arrival.visit().saved(),nativeCapture,
                com.openggf.game.session.EngineTiming.vIntRunCounter().value(),h.mode().getCamera().getX(),h.mode().getCamera().getY());
    }
    private static SonicCdAct2PastDisplay.Selection r12bSelected(R12bArrival arrival){
        return assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,arrival.host().selected()).selection();
    }
    private static SonicCdPastDisplay.Ready r12bReady(R12bArrival arrival){
        var selected=r12bSelected(arrival);
        if(selected.projection() instanceof SonicCdPastDisplay.Blocked blocked)fail("Authentic R12B selected boundary: "+blocked.failure());
        return assertInstanceOf(SonicCdPastDisplay.Ready.class,selected.projection(),"Actual enabled current pixels must be Ready; do not substitute static source art");
    }
    private static void r12bAssertGeometry(R12bArrival arrival)throws Exception{
        var terrain=assertInstanceOf(CdR12bTerrainAssets.class,field(arrival.visit(),"terrain"));
        assertTrue(arrival.host().geometryReady());assertSame(terrain.blocksDecode().bytes(),arrival.visit().state().blocksRam().orElseThrow());
        assertSame(terrain.terrain().layout().ram(),arrival.visit().state().layoutRam().orElseThrow());
        assertEquals(CdR12bProfile.Asset.LEVEL_COLLISION.nativeAddress(),arrival.host().collisionPointer().orElseThrow());
        assertEquals(Arrays.asList(CdTimeWarpLoadData.NativeStore.values()).subList(0,20),arrival.visit().completedSavedStores());
        assertSame(arrival.visit().state().section().residency(),arrival.host().video().current());
        var fg=arrival.host().scroll().foregroundPlane().nameTable();boolean nonzero=false;
        for(int at=0;at<fg.length();at+=2)nonzero|=fg.unsignedWord(at)!=0;
        assertTrue(nonzero,"Actual InitLevelDraw must produce native foreground descriptors");
    }
    private static void r12bAssertPicture(byte[] rgba){
        assertEquals(320*224*4,rgba.length);var rgb=new HashSet<Integer>();
        for(int at=0;at<rgba.length;at+=4)rgb.add((rgba[at]&255)<<16|(rgba[at+1]&255)<<8|(rgba[at+2]&255));
        assertTrue(rgb.size()>1,"Actual enabled R12B terrain must reject a stale or flat framebuffer");
        assertFalse(rgb.size()==1&&rgb.contains(0xFF00FF),"The deliberate magenta clear cannot serve as selected output");
    }
    /** Uses the existing native comparator, including Root's explicit R12B Snapshot whitelist addition.
     * The registry payload owns Core+Frame+one Host image; no test-side native canonicalization is added. */
    private static void r12bAssertProof(R12bProof expected,R12bArrival arrival,String path)throws ReflectiveOperationException{
        var actual=r12bProof(arrival);
        assertSame(expected.visit(),actual.visit(),path+".visit");assertSame(expected.host(),actual.host(),path+".host");
        assertSame(expected.frame(),actual.frame(),path+".frame");assertSame(expected.source(),actual.source(),path+".source");
        assertSame(expected.world(),actual.world(),path+".World");assertSame(expected.data(),actual.data(),path+".data");
        assertSame(expected.level(),actual.level(),path+".level");assertSame(expected.saved(),actual.saved(),path+".saved owner");
        assertEquals(expected.generation(),actual.generation(),path+".FULL generation");
        assertEquals(expected.representedClock(),actual.representedClock(),path+".represented VBlank");
        assertEquals(expected.cameraX(),actual.cameraX(),path+".cameraX projection");assertEquals(expected.cameraY(),actual.cameraY(),path+".cameraY projection");
        assertNativeCapture(expected.nativeCapture(),arrival.old().h());
    }
    // END R12B LIVE WORLD PROOF APPEND.

}
