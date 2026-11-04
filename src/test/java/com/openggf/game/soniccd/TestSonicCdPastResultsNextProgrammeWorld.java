package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.level.CdR11bAssets;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.OptionalInt;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Object-only ingress at genuine healthy8278, N1ecc5f3a Results:278-343/Main:405-408.
 * A terminal prefix is never restored. No actor/global/time/queue seed or private producer is supplied.
 * Constructed Robot COLSTATUS1 supplies an owned goodFuture write, not natural scanner contact.
 * Known-flag vectors below are pure value checks only; no World ingress claims that knowledge.
 */
@Isolated
class TestSonicCdPastResultsNextProgrammeWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualNextLevelCompletesItsScalarResetSoundDrawTailBeforeOldBlackExit() throws Exception {
        var p=ordinary(); assertEquals(0,dev(p).goodFuture());
        var oldProgram=p.harness().module().levelProgramState(); assertEquals(OptionalInt.empty(),oldProgram.goodFutureFlags());
        var oldLevel=level(p); var oldTail=actualField(globals(p),"tail"); var oldSaved=p.placement().capture().savedFlags().copy();
        var oldCpu=actualField(values(p),"cpuSat"); var oldCompleted=actualField(actualField(values(p),"loop"),"completedDraw");
        var entry=fixed(p,127); seed(p,entry);
        step(p.harness(),neutral());
        assertEquals(SonicCdGameModule.PastLoadPhase.OLD_BLACK_FADE,p.harness().module().pastLoadState().phase());
        assertFalse(p.frame().closed()); retired(p);
        var l=level(p); assertEquals(new CdLevelGlobals(2,l.updateHudTime(),0,0,oldLevel.plcLoadFlags()&~2,0,0,l.powerup(),l.timeOver()),l);
        assertEquals(1,p.frame().environment().movement().gates().zoneAct()); assertEquals(1,p.frame().environment().movement().gates().timeZone());
        assertEquals(0,section(p).sectionWord()); assertEquals(0,dev(p).projDestroyed()); assertEquals(0,dev(p).goodFuture());
        assertEquals(oldProgram.goodFutureFlags(),p.harness().module().levelProgramState().goodFutureFlags());
        assertEquals(SonicCdGameModule.LevelProgramPhase.OLD_BLACK_FADE,p.harness().module().levelProgramState().phase());
        var tail=((java.util.Optional<?>)actualField(globals(p),"tail")).orElseThrow(); var prior=((java.util.Optional<?>)oldTail).orElseThrow();
        for(String name:List.of("player2ControlsWord","player2BoredWord","demoS1IndexWord")) assertEquals(actualField(prior,name),actualField(tail,name));
        assertEquals(0,actualField(tail,"unknownLevelByte")); assertEquals(0x003F,actualField(tail,"paletteFadeInfoWord")); assertSavedReset(p);
        assertEquals(entry.moveByte(SPRITE_FLAGS,entry.spriteFlags()&0x7F),p.placement().pool().state(127));
        assertSame(oldCpu,actualField(values(p),"cpuSat")); assertSame(oldCompleted,actualField(actualField(values(p),"loop"),"completedDraw"));
        assertFalse(java.util.Arrays.equals(oldSaved,p.placement().capture().savedFlags().copy()),"Genuine route has saved placement bits to reset");
        assertEquals(1,fadeCommands(p)); var calls=calls(p); assertEquals(SonicCdGameModule.PastLoadCall.OLD_EXIT_CLEAR_PLCS,calls.getLast());
        assertTrue(calls.contains(SonicCdGameModule.PastLoadCall.RESULTS_BODY)); assertNoOrdinaryTail(calls);
        assertNull(actualField(p.visit(),"drawQueue"));
    }

    @Test void programmePrefixRetainsTheCompleteOrdinaryPlayerPairExceptItsTwoRawGates() throws Exception {
        var p=ordinary();assertEquals(0,dev(p).goodFuture());var registry=p.harness().mode().getRewindRegistry();
        var goodCut=registry.capture();var goodCourse=registry.captureCourse();var before=captureNative(p.harness());
        var incomingMovement=p.frame().environment().movement();
        var ring=p.harness().module().pastLoadState().ring(); // Main restart branches before UpdateGlobalAnims.
        step(p.harness(),neutral());healthy(p);var actor=p.frame().captureActor();var shared=p.frame().captureShared();
        var score=actualField(globals(p),"score");var contact=actualField(globals(p),"contact");var live=actualField(globals(p),"live");
        registry.restore(goodCut);assertNativeCapture(before,p.harness());healthy(p);
        registry.restoreCourse(goodCourse);assertNativeCapture(before,p.harness());healthy(p);
        var entry=fixed(p,127);seed(p,entry);
        step(p.harness(),neutral());
        assertEquals(SonicCdGameModule.PastLoadPhase.OLD_BLACK_FADE,p.harness().module().pastLoadState().phase());
        assertFalse(p.frame().closed());assertEquals(withoutScreenBit(actor.state()),p.frame().state());
        assertEquals(actor.display().map(d->new CdPlayerBody.DisplayPublication(d.queued(),d.mappingAddress(),d.tile(),d.priority(),d.width(),d.mapFrame(),d.spriteFlags()&0x7F,d.centreX16_16(),d.centreY16_16())),p.frame().display());
        var old=shared.environment();var movement=old.movement();var g=movement.gates();
        assertEquals(incomingMovement.bounds(),movement.bounds(),"This healthy control has no post-scroll bound writer");
        assertEquals(incomingMovement.globals().cameraYCentre(),movement.globals().cameraYCentre(),"This healthy control has no post-scroll camera-centre writer");
        var gates=new CdPlayerMovement.Gates(1,1,g.miniSonic(),g.waterSlide(),g.windTunnel(),g.speedShoes(),g.timeWarpDirection());
        var expected=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(),movement.bounds(),gates,movement.globals()),old.controls(),old.globals(),old.dynamicSlots());
        assertEquals(expected,p.frame().environment());
        var actualArt=p.frame().artState();var expectedArt=shared.artState();
        assertArrayEquals(expectedArt.cpuBuffer().copy(),actualArt.cpuBuffer().copy());
        assertArrayEquals(expectedArt.residentBank().copy(),actualArt.residentBank().copy());
        assertEquals(expectedArt.lastFrame(),actualArt.lastFrame());assertEquals(expectedArt.dirty(),actualArt.dirty());
        assertEquals(shared.paused(),p.frame().paused());assertEquals(shared.vIntClock(),p.frame().vIntClock());
        assertEquals(score,actualField(globals(p),"score"));assertEquals(contact,actualField(globals(p),"contact"));assertEquals(live,actualField(globals(p),"live"));
        assertEquals(ring,(CdRingGlobals)actualField(values(p),"ring"));retired(p);
        // Only healthy completed captures above were restored; this exit is now healthy; the separate bridge tests replay its native continuation.
    }

    @Test void aLaterPhysicalFailureRetainsTheCompleteResultsPrefixAndWinsBeforeTheProgrammeFence() throws Exception {
        var p=ordinary(); assertEquals(0,dev(p).goodFuture()); var result=fixed(p,126); seed(p,result);
        var later=p.placement().pool().state(127);assertEquals(0,later.id()); seed(p,later.moveByte(ID,0x7F));
        var failure=assertThrows(SonicCdGameModule.UnsupportedPastLoading.class,()->step(p.harness(),neutral()));
        assertEquals(SonicCdGameModule.PastLoadBoundary.DYNAMIC_OBJECTS,failure.boundary()); assertEquals(0,failure.getSuppressed().length);
        assertEquals(result.moveByte(SPRITE_FLAGS,0),p.placement().pool().state(126)); assertEquals(later.moveByte(ID,0x7F),p.placement().pool().state(127));
        assertEquals(2,level(p).levelRestart());assertSavedReset(p);assertEquals(1,fadeCommands(p));
        var calls=calls(p);assertTrue(calls.contains(SonicCdGameModule.PastLoadCall.RESULTS_BODY));assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.DYNAMIC_OBJECTS));
        assertNoOrdinaryTail(calls);assertTrue(p.frame().closed());retired(p);
    }

    @Test void anEarlierPhysicalFailureDoesNotExecuteAnyResultsProgrammeStore() throws Exception {
        var p=ordinary();var before=level(p);var program=p.harness().module().levelProgramState();
        var earlier=p.placement().pool().state(126);assertEquals(0,earlier.id());seed(p,earlier.moveByte(ID,0x7F));var result=fixed(p,127);seed(p,result);
        var failure=assertThrows(SonicCdGameModule.UnsupportedPastLoading.class,()->step(p.harness(),neutral()));
        assertEquals(SonicCdGameModule.PastLoadBoundary.DYNAMIC_OBJECTS,failure.boundary());assertEquals(before,level(p));assertSame(program,p.harness().module().levelProgramState());
        assertEquals(result,p.placement().pool().state(127));assertEquals(0,fadeCommands(p));assertFalse(calls(p).contains(SonicCdGameModule.PastLoadCall.RESULTS_BODY));retired(p);
    }

    @Test void unknownGoodFutureFlagsStopsBeforeNativeBsetAfterTheOwnedGoodFutureClear() throws Exception {
        var p=ordinary();assertEquals(0,dev(p).goodFuture());var m=p.frame().state().motion();int x=((m.centreX16_16()>>>16)+96)&0xFFFF,y=m.centreY16_16()>>>16;
        var robot=p.placement().pool().state(126);assertEquals(0,robot.id());
        robot=robot.moveByte(ID,0x2D).moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,4)
                .moveByte(X_RADIUS,0x22).moveByte(WIDTH,0x22).moveByte(Y_RADIUS,0x20).moveWord(X,x).moveWord(Y,y)
                .moveWord(TILE,0x043F).moveLong(MAPPINGS,0x20E816).moveLong(ROBOT_GENERATOR_SCHEDULE,0x20EA54)
                .moveWord(ROBOT_GENERATOR_BASE_Y,y).moveWord(ROBOT_GENERATOR_TIMER,0).moveWord(ROBOT_GENERATOR_FLOAT_STEP,1)
                .moveByte(MAP_FRAME,3).moveByte(COLLISION_TYPE,0xFA).moveByte(COLLISION_STATUS,1);
        // Let the ordinary earlier Mosqui visits finish before the genuine Robot sets goodFuture.
        // Results127 then observes that write in this SAME physical pass, ahead of another Mosqui visit.
        seed(p,robot);
        assertEquals(OptionalInt.empty(),p.harness().module().levelProgramState().goodFutureFlags());
        var entry=fixedAt(p,127);seed(p,entry);
        var failure=assertThrows(SonicCdPastLoadingVisit.UnsupportedResultsRead.class,()->step(p.harness(),neutral()));
        assertEquals(SonicCdPastLoadingVisit.ResultsReadBoundary.GOOD_FUTURE_FLAGS,failure.boundary());assertEquals(0,failure.getSuppressed().length);
        assertTrue(calls(p).contains(SonicCdGameModule.PastLoadCall.ROBOT_GENERATOR_BODY));
        assertEquals(0,dev(p).goodFuture());assertEquals(OptionalInt.empty(),p.harness().module().levelProgramState().goodFutureFlags());
        assertEquals(2,level(p).levelRestart());assertEquals(1,p.frame().environment().movement().gates().zoneAct());assertSavedReset(p);assertEquals(1,fadeCommands(p));
        assertEquals(entry.moveByte(SPRITE_FLAGS,0),p.placement().pool().state(127));assertFalse(calls(p).contains(SonicCdGameModule.PastLoadCall.RESULTS_BODY));
        assertNoOrdinaryTail(calls(p));assertTrue(p.frame().closed());retired(p);
    }

    @Test void publicDescriptiveProgrammeCallsDoNotMintActualStoreOrActionAuthority() throws Exception {
        var p=ordinary();var source=fixed(p,127);seed(p,source);var before=captureNative(p.harness());var cut=p.placement().capture();
        var store=new CdResults.ProgrammeStoreCall(CdResults.ProgrammeField.LEVEL_RESTART_WORD,2,cut,source);
        var action=new CdResults.ProgrammeActionCall(CdResults.ProgrammeAction.RESET_SAVED_OBJECT_FLAGS,cut,source);
        for(var row:List.of(new Object[]{"storeResultsProgramme",CdResults.ProgrammeStoreCall.class,store},new Object[]{"actionResultsProgramme",CdResults.ProgrammeActionCall.class,action})) {
            var method=SonicCdPastLoadingVisit.class.getDeclaredMethod((String)row[0],(Class<?>)row[1]);method.setAccessible(true);
            var failure=assertThrows(InvocationTargetException.class,()->method.invoke(p.visit(),row[2]));assertInstanceOf(IllegalStateException.class,failure.getCause());
            assertNativeCapture(before,p.harness());healthy(p);
        }
    }

    @Test void knownFlagVectorsArePureProgrammeValuesAndHaveNoWorldInvocationAuthority() {
        var unknown=new SonicCdGameModule.LevelProgramState(0,0,0,0,1,0,1,0,SonicCdGameModule.LevelProgramPhase.LEVEL);
        assertEquals(OptionalInt.empty(),unknown.goodFutureFlags());
        for(int bits:new int[]{0,1,3,0x80,0xFF}) {
            var known=new SonicCdGameModule.LevelProgramState(0,0,0,0,1,0,1,0,SonicCdGameModule.LevelProgramPhase.LEVEL,OptionalInt.of(bits));
            assertEquals(bits,known.goodFutureFlags().orElseThrow());assertNotEquals(unknown,known);
        }
        assertThrows(IllegalArgumentException.class,()->new SonicCdGameModule.LevelProgramState(0,0,0,0,1,0,1,0,SonicCdGameModule.LevelProgramPhase.LEVEL,OptionalInt.of(256)));
    }

    @Test void labelledDetachedComponentVectorsEstablishClearKnowledgeAndPreserveKnownBsetBitsWithoutWorldWrites() throws Exception {
        var p=ready();var before=captureNative(p.harness());
        for(int vector=0;vector<3;vector++) {
            // These local component operands are constructed vectors, never runtime actor/global ingress.
            final int inputAct=vector==2?2:0;final OptionalInt inputFlags=vector==2?OptionalInt.empty():OptionalInt.of(vector==0?2:0x80);
            var place=CdObjectPlacementRuntime.fromAssets(p.assets(),p.placement().capture().savedFlags());
            var source=place.pool().state(127).moveByte(ID,0x3A).moveByte(ROUTINE,8).moveByte(SPRITE_FLAGS,0x80);place.pool().write(source);
            int[] zone={inputAct},good={1};OptionalInt[] flags={inputFlags};var order=new java.util.ArrayList<String>();
            var body=new CdResults(place,CdResults.Assets.fromPastAssets(p.assets()),c->{order.add("read:"+c.field());return switch(c.field()) {
                case TIME_ATTACK_MODE_BYTE,TIME_STONES_BYTE -> 0;
                case ZONE_ACT_WORD -> zone[0];case ACT_BYTE -> zone[0]&255;case GOOD_FUTURE_BYTE -> good[0];
                case GOOD_FUTURE_FLAGS_BYTE -> flags[0].orElseThrow();
                default -> throw new AssertionError("Unreached constructed component read "+c.field());};},null,null,null,null,
                    c->{order.add("draw");place.pool().write(c.expectedSource().moveByte(SPRITE_FLAGS,c.expectedSource().spriteFlags()&0x7F));},
                    c->{order.add("store:"+c.field()+":"+c.value());switch(c.field()) {
                        case ZONE_ACT_WORD -> zone[0]=c.value();case GOOD_FUTURE_BYTE -> good[0]=c.value();
                        case GOOD_FUTURE_FLAGS_CLEAR_BYTE -> flags[0]=OptionalInt.of(0);
                        case GOOD_FUTURE_FLAGS_SET_BIT -> flags[0]=OptionalInt.of(flags[0].orElseThrow()|(1<<c.value()));default -> {}}},
                    c->{order.add("action:"+c.action());if(c.action()==CdResults.ProgrammeAction.RESET_SAVED_OBJECT_FLAGS)place.resetSavedObjectFlags();});
            var result=body.run(127);assertEquals(source.moveByte(SPRITE_FLAGS,0),result.state());
            if(vector==2) {assertEquals(OptionalInt.of(0),flags[0]);assertFalse(order.contains("read:GOOD_FUTURE_FLAGS_BYTE"));assertEquals(0x100,zone[0]);}
            else {int expected=inputFlags.orElseThrow()|1;assertEquals(OptionalInt.of(expected),flags[0]);assertEquals(expected==3?1:0,good[0]);
                int clear=order.indexOf("store:GOOD_FUTURE_BYTE:0"),set=order.indexOf("store:GOOD_FUTURE_FLAGS_SET_BIT:0");assertTrue(clear>=0&&set>clear);}
            assertTrue(order.indexOf("draw")>order.indexOf("action:FADE_OUT_MUSIC"));
            assertTrue(order.indexOf("read:ACT_BYTE")>order.indexOf("draw"));assertNativeCapture(before,p.harness());
        }
    }

    private static Prepared ordinary() throws Exception {var p=ready();for(int call=p.returned()+1;call<=8278;call++){step(p.harness(),route(call));healthy(p);}return p;}
    private static void healthy(Prepared p)throws Exception{assertFalse(p.frame().closed());assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));retired(p);}
    private static void retired(Prepared p)throws Exception{assertNull(actualField(p.visit(),"activeResults"));assertFalse((boolean)actualField(p.visit(),"resultsPort"));assertFalse((boolean)actualField(p.visit(),"drawingResults"));}
    private static CdObjectState fixed(Prepared p,int whole){assertEquals(0,p.placement().pool().state(whole).id());return fixedAt(p,whole);}
    private static CdObjectState fixedAt(Prepared p,int whole){return p.placement().pool().state(whole).moveByte(ID,0x3A).moveByte(ROUTINE,8).moveByte(SPRITE_FLAGS,0x80)
            .moveWord(X,288).moveWord(RESULTS_SCREEN_Y_WORD,204).moveWord(Y,0x2468).moveWord(RESULTS_DEST_X,288).moveByte(MAP_FRAME,0).moveByte(PRIORITY,0)
            .moveWord(TILE,0x83C4).moveLong(MAPPINGS,p.assets().view(CdR11bAssets.Asset.MAP_SPR_RESULTS).range().nativeAddress());}
    private static void seed(Prepared p,CdObjectState source){var before=p.placement().capture();p.placement().pool().write(source);assertEquals(before.status(),p.placement().status());assertArrayEquals(before.savedFlags().copy(),p.placement().capture().savedFlags().copy());}
    private static LogicalInputSnapshot neutral(){return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(),PlayerInputState.neutral());}
    private static Object values(Prepared p)throws Exception{return actualField(p.visit(),"values");}
    private static Object globals(Prepared p)throws Exception{return actualField(values(p),"globals");}
    private static CdLevelGlobals level(Prepared p)throws Exception{return (CdLevelGlobals)actualField(values(p),"level");}
    private static com.openggf.game.soniccd.resources.CdR11bSectionArt.State section(Prepared p)throws Exception{return (com.openggf.game.soniccd.resources.CdR11bSectionArt.State)actualField(values(p),"section");}
    private static CdOpeningObjectRuntime.DevGlobals dev(Prepared p)throws Exception{return (CdOpeningObjectRuntime.DevGlobals)actualField(globals(p),"retainedDev");}
    private static List<SonicCdGameModule.PastLoadCall> calls(Prepared p)throws Exception{return (List<SonicCdGameModule.PastLoadCall>)actualField(values(p),"calls");}
    private static long fadeCommands(Prepared p)throws Exception{return ((List<?>)actualField(p.visit(),"sounds")).stream().filter(s->s instanceof SonicCdGameModule.PastFrameSound f && f.sound() instanceof CdPlayerFrameRuntime.BodySound b && b.sound()==CdPlayerBody.Sound.SCMD_FADECDA).count();}
    /** Literal native DrawObject BCLR7; healthy control drained and set7, terminal prefix did not. */
    private static CdPlayerBody.State withoutScreenBit(CdPlayerBody.State s){var m=s.motion();
        var n=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags(),m.spriteFlags()&0x7F,
                m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
        return new CdPlayerBody.State(n,s.animation(),s.header(),s.timers(),s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle());}
    private static void assertSavedReset(Prepared p){var bytes=p.placement().capture().savedFlags().copy();assertEquals(1,bytes[0]);assertEquals(1,bytes[1]);for(int i=2;i<bytes.length;i++)assertEquals(0,bytes[i]);assertEquals(1,p.placement().status().rightSavedId());assertEquals(1,p.placement().status().leftSavedId());}
    private static void assertNoOrdinaryTail(List<SonicCdGameModule.PastLoadCall> calls){for(var c:List.of(SonicCdGameModule.PastLoadCall.POST_OBJECT_GUARDS,SonicCdGameModule.PastLoadCall.LEVEL_SCROLL,SonicCdGameModule.PastLoadCall.DRAW_OBJECTS,SonicCdGameModule.PastLoadCall.PALETTE_CYCLE,SonicCdGameModule.PastLoadCall.UPDATE_SECTION,SonicCdGameModule.PastLoadCall.PREPARE_PLCS,SonicCdGameModule.PastLoadCall.UPDATE_GLOBAL_ANIMATIONS,SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN))assertFalse(calls.contains(c),c.name());}
}
