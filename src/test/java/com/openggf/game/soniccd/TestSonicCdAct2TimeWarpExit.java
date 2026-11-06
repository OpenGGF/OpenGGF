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
    @AfterEach void cleanup(){fixture.cleanup();}
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

    @Test void returnStopsAtTheUnownedIpxWarpBridgeWithoutCreatingDestinationOrServicingAnOldFrame() throws Exception {
        var s=prepared229(0);step(s.h(),neutral());healthy(s,SonicCdGameModule.Act2LoadPhase.OLD_RETURNED);
        var v=values(s);var actor=s.frame().state();var clock=s.frame().vIntClock();var source=s.frame().stageSource();var world=s.h().mode().getWorldSession();
        long generation=s.h().mode().getLevelManager().getCompletedProductionLoadGeneration();
        var stop=assertThrows(SonicCdGameModule.UnsupportedAct2Loading.class,()->step(s.h(),neutral()));
        assertEquals(SonicCdGameModule.Act2Boundary.OLD_EXIT_WARP_PROGRAMME,stop.boundary());assertTrue(s.frame().closed());
        assertSame(v,values(s));assertSame(actor,s.frame().state());assertSame(clock,s.frame().vIntClock());assertSame(source,s.frame().stageSource());
        assertSame(world,s.h().mode().getWorldSession());assertEquals(generation,s.h().mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertTrue(s.h().module().warpSceneState().isEmpty());assertEquals(SonicCdGameModule.Act2LoadCall.OLD_EXIT_RETURN,calls(s).getLast());
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
}
