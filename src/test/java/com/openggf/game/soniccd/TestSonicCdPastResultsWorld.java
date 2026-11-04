package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import com.openggf.game.soniccd.resources.CdLevelHud;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored source checks only: ALL JAVA UNRUN, including compilation/discovery.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Results/Main.asm:61-343;
 * Functions (General).asm slow PLC; Object Functions.asm:137-196,275-346.
 * Fixed object-only ingress at the genuine healthy8278 cut. No actor, ring/global, terrain,
 * queue, art or clock seed and no failed restore. This does not claim naturally spawned Results,
 * >=50 Big Ring touch, audio synthesis, renderer pixels, a new act, LoadMMD or save completion.
 */
@Isolated
class TestSonicCdPastResultsWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void nativeHighByteDelayPreservesLowByteAndEveryDirtyUnwrittenByteThroughFullAndCourseReplay() throws Exception {
        var p=ordinary(); var entry=fixed(p,127).moveByte(ROUTINE,0).moveWord(RESULTS_TIMER_WORD,0x02A7)
                .moveWord(Y_FRACTION,0x1357).moveByte(FLAGS,0x40).moveByte(ANIMATION,253);
        var control=control(p); seed(p,entry); replay(p);
        assertEquals(entry.moveWord(RESULTS_TIMER_WORD,0x01A7),p.placement().pool().state(127));
        assertEquals(1,calls(p)); assertTrue(resultsSounds(p).isEmpty());control.assertFrameAndScroll(p);
    }

    @Test void actualCue16UsesTheExistingQueueAndFortyThreeTileSlowServicesWithoutSeedingReadiness() throws Exception {
        var p=ordinary(); assertEquals(0,section(p).queue().headSource());
        var entry=fixed(p,127).moveByte(ROUTINE,0).moveWord(RESULTS_TIMER_WORD,0x0100); seed(p,entry); replay(p);
        assertEquals(2,p.placement().pool().state(127).routine()); assertEquals(0,p.placement().pool().state(127).word(RESULTS_TIMER_WORD));
        assertNotEquals(0,section(p).queue().headSource());
        // Native Results art is120tiles; existing unlocked SlowPLC services exactly3tiles per call.
        // Prepare follows Objects in this ordinary pass; forty subsequent services exhaust120tiles.
        assertEquals(0,presentation(p).pastForegroundImage().scrollLock()&0xFF00);
        for(int service=0;service<40;service++){step(p.harness(),neutral());healthy(p);}
        assertEquals(0,section(p).queue().headSource());
        var residency=section(p).residency(); assertNotNull(residency);
        assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_SLOW_PLC));
    }

    @Test void signedMovementAndUnsigned472AdmissionRetainTheNativeFullImage() throws Exception {
        var p=ordinary(); var entry=fixed(p,127).moveByte(ROUTINE,4).moveByte(MAP_FRAME,1)
                .moveWord(X,0x7FFC).moveWord(RESULTS_DEST_X,0x8004).moveWord(RESULTS_TIMER_WORD,473)
                .moveWord(Y,0x2468).moveWord(Y_FRACTION,0x1357).moveByte(FLAGS,0x40);
        seed(p,entry); replay(p);
        assertEquals(entry.moveWord(X,0x7FF4).moveWord(RESULTS_TIMER_WORD,472),p.placement().pool().state(127));
        assertFalse(cpu(p).provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==127));
    }

    @Test void screenModeDrawUsesNativeWordAt0ANotDirtyWorldYAndKeepsAllOtherSourceBytes() throws Exception {
        var p=ordinary(); var entry=fixed(p,127).moveByte(ROUTINE,4).moveByte(MAP_FRAME,0)
                .moveWord(X,288).moveWord(RESULTS_DEST_X,288).moveWord(RESULTS_TIMER_WORD,0)
                .moveWord(RESULTS_SCREEN_Y_WORD,204).moveWord(Y,0x2468).moveWord(Y_FRACTION,0x1357).moveByte(FLAGS,0x40);
        seed(p,entry); replay(p); var source=p.placement().pool().state(127);
        assertEquals(entry.moveByte(ROUTINE,6).moveByte(SPRITE_FLAGS,0x80),source);
        var sat=cpu(p); int ordinal=-1;
        for(int i=0;i<sat.provenance().size();i++)if(sat.provenance().get(i).slotRef().wholeIndex()==127){ordinal=i;break;}
        assertTrue(ordinal>=0); var resolver=sourceMappings(p);
        var piece=resolver.resolve(entry.longBits(MAPPINGS),false,0).pieces().getFirst();
        assertEquals((204+piece.y())&0x1FF,word(sat.bytes(),ordinal*8));
        assertNotEquals((entry.centreY()+piece.y())&0x1FF,word(sat.bytes(),ordinal*8));
    }

    @Test void actualZeroBonusOnlySetsExistingHudFlagAndUsesTheSignedWordCountdown() throws Exception {
        var p=ordinary(); var bonus=bonus(p); assertEquals(0,bonus.timeBonusWord());assertEquals(0,bonus.ringBonusWord());
        var control=control(p);
        var entry=fixed(p,127).moveByte(ROUTINE,6).moveWord(RESULTS_TIMER_WORD,1);seed(p,entry);replay(p);
        assertEquals(entry.moveWord(RESULTS_TIMER_WORD,0).moveByte(SPRITE_FLAGS,0x80),p.placement().pool().state(127));
        assertEquals(1,bonus(p).updateHudBonusByte()); assertEquals(control.score(),actualField(globals(p),"score"));
        assertEquals(control.ring(),p.harness().module().pastLoadState().ring()); assertTrue(resultsSounds(p).isEmpty());control.assertFrameAndScroll(p);
    }

    @Test void nativeSignpostBonusFeedsTheSharedScoreOwnerAndOrderedTallyWithoutGlobalSeeds() throws Exception {
        var p=ordinary(); var source=fixed(p,127).moveByte(ID,0x17).moveByte(ROUTINE,6).moveByte(LEVEL_END_TIMER,1)
                .moveLong(MAPPINGS,p.assets().view(CdR11bAssets.Asset.MAP_SPR_GOAL_SIGNPOST).range().nativeAddress()).moveByte(MAP_FRAME,3);
        seed(p,source);step(p.harness(),neutral());healthy(p);var before=bonus(p);
        assertTrue(before.timeBonusWord()>0 || before.ringBonusWord()>0,"Only real Signpost clock/rings supply this bonus");
        int child=p.placement().pool().capture().slots().stream().filter(s->s.id()==0x3A).findFirst().orElseThrow().identity().wholeIndex();
        var control=control(p);var ring=control.ring();var score=control.score();
        var entry=fixedAt(p,child).moveByte(ROUTINE,6).moveWord(RESULTS_TIMER_WORD,2);seed(p,entry);
        int points=(before.timeBonusWord()!=0?10:0)+(before.ringBonusWord()!=0?10:0);
        var expected=CdPlayerTouchResponse.addPoints(new CdPlayerTouchResponse.WorldGlobals(ring.rings(),ring.lives(),ring.updateHudLives(),
                (int)actualField(score,"score"),(int)actualField(score,"nextLifeScore"),(int)actualField(score,"updateHudScore")),points);
        replay(p);var actual=actualField(globals(p),"score");
        assertEquals(expected.world().score(),actualField(actual,"score"));assertEquals(expected.world().nextLifeScore(),actualField(actual,"nextLifeScore"));
        assertEquals(expected.world().updateHudScore(),actualField(actual,"updateHudScore"));
        assertEquals(expected.world().lives(),p.harness().module().pastLoadState().ring().lives());
        assertEquals(before.timeBonusWord()==0?0:(before.timeBonusWord()-100)&0xFFFF,bonus(p).timeBonusWord());
        assertEquals(before.ringBonusWord()==0?0:(before.ringBonusWord()-100)&0xFFFF,bonus(p).ringBonusWord());
        assertEquals(List.of(CdResults.Sound.FM_TALLY),resultsSounds(p).stream().map(SonicCdGameModule.PastResultsSound::sound).toList());control.assertFrameAndScroll(p);
    }

    @Test void nextLevelProgrammePrefixFencesAfterTheCompletePhysicalWalkBeforeOrdinaryDraw() throws Exception {
        var p=ordinary();var entry=fixed(p,127).moveByte(ROUTINE,8);seed(p,entry);
        var program=p.harness().module().levelProgramState();
        var dev=(CdOpeningObjectRuntime.DevGlobals)actualField(globals(p),"retainedDev");assertEquals(0,dev.goodFuture());
        var failure=assertThrows(SonicCdGameModule.UnsupportedPastLoading.class,()->step(p.harness(),neutral()));
        assertEquals(SonicCdGameModule.PastLoadBoundary.LEVEL_END_PROGRAMME,failure.boundary());
        assertSame(program,p.harness().module().levelProgramState());assertEquals(2,((CdLevelGlobals)actualField(actualField(p.visit(),"values"),"level")).levelRestart());
        assertEquals(1,p.frame().environment().movement().gates().zoneAct());assertEquals(1,p.frame().environment().movement().gates().timeZone());
        assertEquals(entry.moveByte(SPRITE_FLAGS,entry.spriteFlags()&0x7F),p.placement().pool().state(127));
        assertEquals(0,failure.getSuppressed().length);assertTrue(p.frame().closed());retired(p);
        var calls=(List<?>)actualField(actualField(p.visit(),"values"),"calls");
        assertTrue(calls.contains(SonicCdGameModule.PastLoadCall.RESULTS_BODY));assertEquals(SonicCdGameModule.PastLoadCall.DYNAMIC_OBJECTS,calls.getLast());
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.DRAW_OBJECTS));assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        assertEquals(CdObjectDrawQueue.Phase.CLOSED,((CdObjectDrawQueue)actualField(p.visit(),"drawQueue")).phase());
        // A failed Frame is terminal; do not restore it to claim replay or a new visit.
    }

    @Test void finiteMappingFrameReadFailureKeepsCompletedMoveAndDrawClearButClosesTheActualFrame() throws Exception {
        var p=ordinary();var entry=fixed(p,127).moveByte(ROUTINE,4).moveByte(MAP_FRAME,255).moveWord(RESULTS_TIMER_WORD,1);seed(p,entry);
        var failure=assertThrows(RuntimeException.class,()->step(p.harness(),neutral()));assertNotNull(failure);
        assertEquals(entry.moveWord(RESULTS_TIMER_WORD,0),p.placement().pool().state(127));assertTrue(p.frame().closed());retired(p);
        var calls=(List<?>)actualField(actualField(p.visit(),"values"),"calls");assertTrue(calls.contains(SonicCdGameModule.PastLoadCall.RESULTS_BODY));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
    }

    @Test void descriptivePublicReadCallCannotMintThePrivateActualProducerEvenWithTheCurrentFullSource() throws Exception {
        var p=ordinary();var entry=fixed(p,127);seed(p,entry);var before=captureNative(p.harness());
        var call=new CdResults.ReadCall(CdResults.ReadField.PLC_HEAD_LONG,p.placement().capture(),entry);
        var method=SonicCdPastLoadingVisit.class.getDeclaredMethod("readResults",CdResults.ReadCall.class);method.setAccessible(true);
        var failure=assertThrows(InvocationTargetException.class,()->method.invoke(p.visit(),call));assertInstanceOf(IllegalStateException.class,failure.getCause());
        assertNativeCapture(before,p.harness());healthy(p);
    }

    @Test void cachedFiniteMappingsRetainAllFourRootsAndTheUnchangedNativeOrdinalPrefix() throws Exception {
        var p=ordinary();var body=(CdResults)actualField(p.visit(),"results");var data=(CdResults.Assets)actualField(body,"assets");
        assertEquals(24,data.initData().length());assertEquals(622,data.mappings().length());
        assertArrayEquals(p.assets().view(CdR11bAssets.Asset.MAP_SPR_RESULTS).bytes().copy(),data.mappings().copy());
        var resolver=sourceMappings(p);
        int[][] counts={{8,14,10,10,10},{8,14,10,10,10},{14,14,11,11,11},{14,14,11,11,11}};
        for(int root=0;root<4;root++)for(int frame=0;frame<5;frame++)assertEquals(counts[root][frame],resolver.resolve(data.mappingsAddress()+root*10,false,frame).pieces().size());
        assertEquals(81,SonicCdGameModule.PastLoadCall.BIG_RING_BODY.ordinal());assertEquals(82,SonicCdGameModule.PastLoadCall.RESULTS_BODY.ordinal());
        assertEquals(83,SonicCdGameModule.PastLoadCall.values().length);
        for(var sound:CdResults.Sound.values())assertEquals(sound.nativeId(),new SonicCdGameModule.PastResultsSound(sound).nativeId());
    }

    private static Prepared ordinary() throws Exception {var p=ready();for(int call=p.returned()+1;call<=8278;call++){step(p.harness(),route(call));healthy(p);}return p;}
    private static void healthy(Prepared p) throws Exception {assertSame(p.frame(),p.harness().module().requirePlayerFrame());assertFalse(p.frame().closed());assertInstanceOf(CdResults.class,actualField(p.visit(),"results"));
        assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));retired(p);}
    private static void retired(Prepared p) throws Exception {assertNull(actualField(p.visit(),"activeResults"));assertFalse((boolean)actualField(p.visit(),"resultsPort"));assertFalse((boolean)actualField(p.visit(),"drawingResults"));}
    private static void replay(Prepared p) throws Exception {var h=p.harness();var registry=h.mode().getRewindRegistry();var world=h.mode().getWorldSession();long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var source=p.frame().stageSource();var body=actualField(p.visit(),"results");var full=registry.capture();var course=registry.captureCourse();var before=captureNative(h);
        step(h,neutral());healthy(p);var after=captureNative(h);registry.restore(full);assertNativeCapture(before,h);healthy(p);step(h,neutral());healthy(p);assertNativeCapture(after,h);
        registry.restoreCourse(course);assertNativeCapture(before,h);healthy(p);step(h,neutral());healthy(p);assertNativeCapture(after,h);assertSame(world,h.mode().getWorldSession());
        assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());assertSame(source,p.frame().stageSource());assertSame(body,actualField(p.visit(),"results"));}
    private static CdObjectState fixed(Prepared p,int whole){assertEquals(0,p.placement().pool().state(whole).id());return fixedAt(p,whole);}
    private static CdObjectState fixedAt(Prepared p,int whole){return p.placement().pool().state(whole).moveByte(ID,0x3A).moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0)
            .moveWord(X,288).moveWord(RESULTS_DEST_X,288).moveWord(RESULTS_SCREEN_Y_WORD,204).moveWord(Y,0x2468).moveByte(PRIORITY,0)
            .moveByte(MAP_FRAME,0).moveWord(TILE,0x83C4).moveLong(MAPPINGS,p.assets().view(CdR11bAssets.Asset.MAP_SPR_RESULTS).range().nativeAddress());}
    private static void seed(Prepared p,CdObjectState state){var before=p.placement().capture();p.placement().pool().write(state);assertEquals(before.status(),p.placement().status());assertArrayEquals(before.savedFlags().copy(),p.placement().capture().savedFlags().copy());
        for(var old:before.pool().slots())if(old.identity().wholeIndex()!=state.identity().wholeIndex())assertEquals(old,p.placement().pool().state(old.identity().wholeIndex()));}
    private static LogicalInputSnapshot neutral(){return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(),PlayerInputState.neutral());}
    private static long calls(Prepared p){return p.harness().module().pastLoadState().calls().stream().filter(c->c==SonicCdGameModule.PastLoadCall.RESULTS_BODY).count();}
    private static Object globals(Prepared p)throws Exception{return actualField(actualField(p.visit(),"values"),"globals");}
    private static CdLevelHud.BonusValues bonus(Prepared p)throws Exception{return (CdLevelHud.BonusValues)((java.util.Optional<?>)actualField(globals(p),"bonus")).orElseThrow();}
    private static com.openggf.game.soniccd.resources.CdR11bSectionArt.State section(Prepared p)throws Exception{return (com.openggf.game.soniccd.resources.CdR11bSectionArt.State)actualField(actualField(p.visit(),"values"),"section");}
    private static SonicCdPlayerPresentation presentation(Prepared p)throws Exception{return (SonicCdPlayerPresentation)actualField(p.harness().module(),"playerPresentation");}
    private static CdObjectDrawQueue.CpuSat cpu(Prepared p)throws Exception{return ((java.util.Optional<CdObjectDrawQueue.CpuSat>)actualField(actualField(actualField(p.visit(),"values"),"loop"),"completedDraw")).orElseThrow();}
    private static List<SonicCdGameModule.PastResultsSound> resultsSounds(Prepared p){return p.harness().module().pastLoadState().sounds().stream().filter(SonicCdGameModule.PastResultsSound.class::isInstance).map(SonicCdGameModule.PastResultsSound.class::cast).toList();}
    private static int word(byte[] b,int at){return (Byte.toUnsignedInt(b[at])<<8)|Byte.toUnsignedInt(b[at+1]);}
    private static CdObjectMappings sourceMappings(Prepared p) {
        var source=p.assets().view(CdR11bAssets.Asset.MAP_SPR_RESULTS);
        return CdObjectMappings.declaredSourceViews(java.util.stream.IntStream.range(0,4)
                .mapToObj(root->new CdObjectMappings.DeclaredTable(source.range().nativeAddress(),source.bytes(),root*10,5))
                .toList(),List.of());
    }

    /** A real healthy ordinary control row supplies nonwrite expectations; no expected actor is fabricated. */
    private record Control(Object actor,Object shared,Object program,SonicCdPlayerPresentation.ScrollCapture scroll,
                           CdRingGlobals ring,CdLevelGlobals level,Object score) {
        void assertFrameAndScroll(Prepared p)throws Exception {same(actor,p.frame().captureActor());same(shared,p.frame().captureShared());
            assertSame(program,p.harness().module().levelProgramState());same(scroll,presentation(p).capturePastScroll());}
    }
    private static Control control(Prepared p)throws Exception {var registry=p.harness().mode().getRewindRegistry();var full=registry.capture();var before=captureNative(p.harness());
        step(p.harness(),neutral());healthy(p);var after=new Control(p.frame().captureActor(),p.frame().captureShared(),p.harness().module().levelProgramState(),
                presentation(p).capturePastScroll(),p.harness().module().pastLoadState().ring(),p.harness().module().pastLoadState().level(),actualField(globals(p),"score"));
        registry.restore(full);assertNativeCapture(before,p.harness());healthy(p);return after;}
    private static void same(Object expected,Object actual)throws Exception {
        if(expected==actual)return;assertNotNull(expected);assertNotNull(actual);assertEquals(expected.getClass(),actual.getClass());
        if(expected instanceof CdAssetBytes b)assertArrayEquals(b.copy(),((CdAssetBytes)actual).copy());
        else if(expected instanceof Optional<?> o){var a=(Optional<?>)actual;assertEquals(o.isPresent(),a.isPresent());if(o.isPresent())same(o.orElseThrow(),a.orElseThrow());}
        else if(expected instanceof List<?> l){var a=(List<?>)actual;assertEquals(l.size(),a.size());for(int i=0;i<l.size();i++)same(l.get(i),a.get(i));}
        else if(expected instanceof Map<?,?> m){var a=(Map<?,?>)actual;assertEquals(m.keySet(),a.keySet());for(var k:m.keySet())same(m.get(k),a.get(k));}
        else if(expected.getClass().isArray()){assertEquals(Array.getLength(expected),Array.getLength(actual));for(int i=0;i<Array.getLength(expected);i++)same(Array.get(expected,i),Array.get(actual,i));}
        else if(expected.getClass().isRecord())for(var c:expected.getClass().getRecordComponents()){var a=c.getAccessor();a.setAccessible(true);same(a.invoke(expected),a.invoke(actual));}
        else assertEquals(expected,actual);
    }
}
