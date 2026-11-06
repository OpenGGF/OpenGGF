package com.openggf.game.soniccd;

import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual cold/Past/Results/normal R12A/Title ingress; dynamic64 test inputs are labelled.
 * N1ecc5f3a Spin Tunnel/Main (Door)135-177, Object Animate14-95, Object Functions11-33/137-196/228-237.
 * Source authored before either owned production destination existed. Every Java/World/GL test UNRUN.
 * No natural completed13365, pixel, whole-act or native execution claim follows from this packet.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@Isolated
class TestSonicCdAct2DoorSplashWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void actualCachedSourceAndAppendOnlyCallAndSoundContractsRemainBounded() throws Exception {
        var s=scene();var b=splash(s);var before=captureNative(s.h());
        assertSame(s.assets().objects().tunnelDoorSplash(),b.assets().visual());assertEquals(0x21D652,b.assets().mappingsAddress());
        assertEquals(130,SonicCdGameModule.Act2LoadCall.values().length);assertEquals(119,SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_RING_AWARD.ordinal());
        assertEquals(120,SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_BODY.ordinal());assertEquals(121,SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_FM.ordinal());
        assertEquals(22,SonicCdGameModule.PastSound.class.getPermittedSubclasses().length);
        assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runTunnelDoorSplash",new Class<?>[]{int.class},127));assertNativeCapture(before,s.h());
    }

    @Test void dirtyInitOverwritesFlagsAndKeepsEveryNonwrittenByteAcrossFullAndCourseReplay() throws Exception {
        var s=scene();var source=image(s,127,0,0).moveByte(SPRITE_FLAGS,255).moveByte(FLAGS,3)
                .moveByte(PREVIOUS_ANIMATION,0x73).moveByte(ANIMATION_FRAME,0x41).moveByte(ANIMATION_TIME,0x52);seed(s,source);
        var saved=s.placement().capture().savedFlags();replayNext(s);assertScreen(first(source),s.placement().pool().state(127));
        assertArrayEquals(saved.copy(),s.placement().capture().savedFlags().copy());assertEquals(List.of(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_FM,SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_BODY),familyCalls(s));
        assertEquals(List.of(new SonicCdGameModule.PastTunnelDoorSplashFm(CdTunnelDoorSplash.FmSound.FM_A2)),s.h().module().act2LoadState().sounds().stream().filter(v->v instanceof SonicCdGameModule.PastTunnelDoorSplashFm).toList());
    }

    @Test void allThreeNativeSubtypesChooseExactFirstFrameAndTypedFmAfterTileStore() throws Exception {
        var s=scene();var base=s.h().mode().getRewindRegistry().capture();
        for(int subtype=0;subtype<3;subtype++) {
            final int selected=subtype;s.h().mode().getRewindRegistry().restore(base);var source=image(s,127,0,subtype).moveByte(PREVIOUS_ANIMATION,0x7F);seed(s,source);
            var b=splash(s);var actual=(CdTunnelDoorSplash.SourceSound)actualField(b,"sourceSound");var observed=new ArrayList<Object>();
            set(b,"sourceSound",(CdTunnelDoorSplash.SourceSound)c->{assertEquals(header(source).moveWord(TILE,0x03A7),c.source());observed.add(c.sound());actual.play(c);});
            replayNext(s);set(b,"sourceSound",actual);assertScreen(first(source),s.placement().pool().state(127));
            assertEquals(3,observed.size());assertTrue(observed.stream().allMatch(v->v==(selected<2?CdTunnelDoorSplash.FmSound.FM_A2:CdTunnelDoorSplash.FmSound.FM_A1)));
        }
    }

    @Test void samePreviousAnimationPreservesDirtyIndexAndPositiveTimerWithoutAScriptRead() throws Exception {
        var s=scene();var source=image(s,127,0,2).moveByte(PREVIOUS_ANIMATION,2).moveByte(ANIMATION_FRAME,0xF3).moveByte(ANIMATION_TIME,2).moveByte(MAP_FRAME,6);seed(s,source);
        replayNext(s);assertScreen(header(source).moveWord(TILE,0x03A7).moveByte(ANIMATION_TIME,1),s.placement().pool().state(127));
    }

    @Test void heldUnknownSelectorWaitsUntilTheActuallyReachedDirectoryRead() throws Exception {
        var s=scene();var source=image(s,127,2,3).moveByte(ANIMATION,3).moveByte(PREVIOUS_ANIMATION,3).moveByte(ANIMATION_TIME,1);seed(s,source);
        replayNext(s);assertScreen(source.moveByte(ANIMATION_TIME,0),s.placement().pool().state(127));var held=s.placement().pool().state(127);beforeForward(s);
        var failure=assertThrows(CdTunnelDoorSplash.UnsupportedTunnelDoorSplash.class,()->step(s.h(),neutral()));
        assertEquals(CdTunnelDoorSplash.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,failure.boundary());assertEquals(held.moveByte(ANIMATION_TIME,255),s.placement().pool().state(127));assertEquals(0,failure.getSuppressed().length);retired(s);
    }

    @Test void invalidInitSelectorKeepsHeaderTileFmAndAnimationResetTimerPrefixBeforeStopping() throws Exception {
        var s=scene();var source=image(s,127,0,3).moveByte(PREVIOUS_ANIMATION,0x73).moveByte(ANIMATION_FRAME,0x41).moveByte(ANIMATION_TIME,0x52);seed(s,source);beforeForward(s);
        var failure=assertThrows(CdTunnelDoorSplash.UnsupportedTunnelDoorSplash.class,()->step(s.h(),neutral()));
        assertEquals(CdTunnelDoorSplash.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,failure.boundary());
        assertEquals(header(source).moveWord(TILE,0x03A7).moveByte(PREVIOUS_ANIMATION,3).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,255),s.placement().pool().state(127));
        assertEquals(List.of(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_FM),familyCalls(s));assertEquals(0,failure.getSuppressed().length);retired(s);
    }

    @Test void nonzeroSubtype2FailsAtOneWordTileBodyBeforeFmAndPreservesHeaderOnly() throws Exception {
        var s=scene();var source=image(s,127,0,0).moveByte(SUBTYPE2,1).moveByte(SPRITE_FLAGS,255);seed(s,source);beforeForward(s);
        var failure=assertThrows(CdTunnelDoorSplash.UnsupportedTunnelDoorSplash.class,()->step(s.h(),neutral()));
        assertEquals(CdTunnelDoorSplash.UnsupportedBoundary.TILE_ID_SUBTYPE2,failure.boundary());assertEquals(header(source),s.placement().pool().state(127));assertTrue(familyCalls(s).isEmpty());retired(s);
    }

    @Test void dirtyFrameReadFailureKeepsLoadedDelayAndAllOtherBytes() throws Exception {
        var s=scene();var source=image(s,127,2,0).moveByte(ANIMATION_FRAME,255).moveByte(ANIMATION_TIME,0);seed(s,source);beforeForward(s);
        var failure=assertThrows(CdTunnelDoorSplash.UnsupportedTunnelDoorSplash.class,()->step(s.h(),neutral()));
        assertEquals(CdTunnelDoorSplash.UnsupportedBoundary.ANIMATION_FRAME_READ,failure.boundary());assertEquals(source.moveByte(ANIMATION_TIME,3),s.placement().pool().state(127));assertTrue(familyCalls(s).isEmpty());retired(s);
    }

    @Test void eachFcIncrementsRoutineStillDrawsAndDeletesOnlyOnNextDispatch() throws Exception {
        var s=scene();var base=s.h().mode().getRewindRegistry().capture();
        for(int subtype=0;subtype<3;subtype++) {
            s.h().mode().getRewindRegistry().restore(base);int terminal=subtype==0?5:subtype==1?3:2;
            var source=image(s,127,2,subtype).moveByte(ANIMATION_FRAME,terminal).moveByte(ANIMATION_TIME,0).moveByte(SPRITE_FLAGS,0x84);seed(s,source);
            replayNext(s);assertScreen(source.moveByte(ROUTINE,4).moveByte(ANIMATION_TIME,3),s.placement().pool().state(127));
            var b=splash(s);var original=(CdTunnelDoorSplash.SourceDraw)actualField(b,"sourceDraw");set(b,"sourceDraw",(CdTunnelDoorSplash.SourceDraw)c->{throw new AssertionError("Destroy must not Draw");});
            var saved=s.placement().capture().savedFlags();replayNext(s);assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());assertArrayEquals(saved.copy(),s.placement().capture().savedFlags().copy());
            assertEquals(List.of(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_BODY),familyCalls(s));set(b,"sourceDraw",original);
        }
    }

    @Test void genuineDrawUsesRetainedZeroWidthInsteadOfMappingExtentsAndClearsBit7BeforeCull() throws Exception {
        var s=scene();var base=s.h().mode().getRewindRegistry().capture();var presentation=(SonicCdPlayerPresentation)actualField(s.visit(),"presentation");
        var camera=presentation.act2ForegroundImage();var source=image(s,127,2,0).moveWord(X,camera.cameraX()+320).moveWord(Y,camera.cameraYWord()+100)
                .moveByte(WIDTH,0).moveByte(Y_RADIUS,0).moveByte(SPRITE_FLAGS,0x84).moveByte(ANIMATION_TIME,2);seed(s,source);
        replayNext(s);assertEquals(4,s.placement().pool().state(127).spriteFlags());assertFalse(cpu(s).provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==127));
        s.h().mode().getRewindRegistry().restore(base);seed(s,source.moveByte(WIDTH,1));replayNext(s);
        assertEquals(0x84,s.placement().pool().state(127).spriteFlags());assertEquals(2L,cpu(s).provenance().stream().filter(v->v.slotRef().wholeIndex()==127).count());
    }

    @Test void everyDeclaredFrameUsesActualCpuMappingRootAndNativePieceCount() throws Exception {
        var s=scene();var base=s.h().mode().getRewindRegistry().capture();
        for(int frame=0;frame<7;frame++) {
            s.h().mode().getRewindRegistry().restore(base);seed(s,image(s,127,2,0).moveByte(MAP_FRAME,frame).moveByte(ANIMATION_TIME,2));replayNext(s);
            assertEquals(s.assets().objects().tunnelDoorSplash().mappingFrames().get(frame).pieces().size(),cpu(s).provenance().stream().filter(v->v.slotRef().wholeIndex()==127).count());
        }
    }

    @Test void nonzeroNativeParentSubtypeOpensWithoutAllocatingSplashOrPlayingA4() throws Exception {
        var s=scene();var m=s.frame().state().motion();seed(s,parent(s,127).moveByte(SUBTYPE,1).moveWord(X,m.centreX16_16()>>>16).moveWord(Y,m.centreY16_16()>>>16));
        replayNext(s,jump());assertEquals(4,s.placement().pool().state(127).routine());assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_FM));assertTrue(familyCalls(s).isEmpty());
        assertFalse(s.placement().pool().capture().slots().stream().anyMatch(v->v.id()==0x0B));
    }

    @Test void originalParentAllocatesDirtyEarlierChildWhichWaitsUntilNextPhysicalPass() throws Exception {
        var s=scene();var m=s.frame().state().motion();seed(s,parent(s,127).moveWord(X,m.centreX16_16()>>>16).moveWord(Y,m.centreY16_16()>>>16));
        int free=s.placement().pool().findObjSlot().orElseThrow();assertTrue(free<127);var before=dirtyFree(s,free);seed(s,before);
        replayNext(s,jump());var p=s.placement().pool().state(127);var child=before.moveByte(ID,0x0B).moveWord(X,p.centreX()).moveWord(Y,p.centreY()-4);
        assertEquals(child,s.placement().pool().state(free));assertEquals(4,p.routine());assertFalse(familyCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_BODY));
        assertTrue(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_FM));replayNext(s);assertScreen(first(child),s.placement().pool().state(free));
    }

    @Test void originalParentAllocatesLaterChildWhoseInitFollowsA4InTheSamePhysicalPass() throws Exception {
        var s=scene();var m=s.frame().state().motion();seed(s,parent(s,32).moveWord(X,m.centreX16_16()>>>16).moveWord(Y,m.centreY16_16()>>>16));
        int free=s.placement().pool().findObjSlot().orElseThrow();assertTrue(free>32);var before=dirtyFree(s,free);seed(s,before);
        replayNext(s,jump());var p=s.placement().pool().state(32);var child=before.moveByte(ID,0x0B).moveWord(X,p.centreX()).moveWord(Y,p.centreY()-4);assertScreen(first(child),s.placement().pool().state(free));
        var calls=rawCalls(s);assertTrue(calls.indexOf(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_FM)<calls.indexOf(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_FM));
        assertTrue(calls.indexOf(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_BODY)<calls.indexOf(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_BODY));
    }

    @Test void fullPoolOpensParentWithoutChildOrA4AndDoesNotClearDirtyOtherImages() throws Exception {
        var s=scene();for(int whole=32;whole<128;whole++)seed(s,image(s,whole,2,0).moveByte(ANIMATION_TIME,2));
        var m=s.frame().state().motion();seed(s,parent(s,32).moveWord(X,m.centreX16_16()>>>16).moveWord(Y,m.centreY16_16()>>>16));assertTrue(s.placement().pool().findObjSlot().isEmpty());
        replayNext(s,jump());assertEquals(4,s.placement().pool().state(32).routine());assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_FM));
        assertEquals(95,s.placement().pool().capture().slots().stream().filter(v->v.id()==0x0B).count());assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_FM));
    }

    @Test void noOpBeforePublicationCannotReachFirstHeaderStore() throws Exception {
        var s=scene();var source=image(s,127,0,0);seed(s,source);var b=splash(s);set(b,"observer",observer((CdTunnelDoorSplash.DispatchObserver)actualField(b,"observer"),true,false));beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source,s.placement().pool().state(127));assertTrue(familyCalls(s).isEmpty());retired(s);
    }

    @Test void noOpAfterPublicationStopsAtExactlyStoredHeaderBeforeTileAndFm() throws Exception {
        var s=scene();var source=image(s,127,0,0);seed(s,source);var b=splash(s);set(b,"observer",observer((CdTunnelDoorSplash.DispatchObserver)actualField(b,"observer"),false,true));beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(header(source),s.placement().pool().state(127));assertTrue(familyCalls(s).isEmpty());retired(s);
    }

    @Test void equalCloneAndDuplicatePublicationCannotBorrowOriginalProducerReceipt() throws Exception {
        var s=scene();seed(s,image(s,127,0,0));var b=splash(s);var original=(CdTunnelDoorSplash.DispatchObserver)actualField(b,"observer");var seen=new AtomicReference<Object>();
        set(b,"observer",new CdTunnelDoorSplash.DispatchObserver(){
            public void beforePublication(CdTunnelDoorSplash.Publication c){seen.set(c);assertThrows(IllegalStateException.class,()->original.beforePublication(new CdTunnelDoorSplash.Publication(c.ordinal(),c.kind(),c.cut(),c.source(),c.before(),c.output())));original.beforePublication(c);assertThrows(IllegalStateException.class,()->original.beforePublication(c));}
            public void afterPublication(CdTunnelDoorSplash.Publication c){original.afterPublication(c);assertThrows(IllegalStateException.class,()->original.afterPublication(c));}
            public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}
        });replayNext(s);assertFalse(b.isCurrentCall(seen.get()));assertFalse(b.isCurrentCall(null));
    }

    @Test void noOpFmCannotReachAnimationOrDrawAfterActualTileStore() throws Exception {
        var s=scene();var source=image(s,127,0,0);seed(s,source);set(splash(s),"sourceSound",(CdTunnelDoorSplash.SourceSound)c->{});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(header(source).moveWord(TILE,0x03A7),s.placement().pool().state(127));assertTrue(familyCalls(s).isEmpty());retired(s);
    }

    @Test void clonedAndDuplicateFmCannotAppendAnotherTypedLedgerEntry() throws Exception {
        var s=scene();seed(s,image(s,127,0,2));var b=splash(s);var original=(CdTunnelDoorSplash.SourceSound)actualField(b,"sourceSound");
        set(b,"sourceSound",(CdTunnelDoorSplash.SourceSound)c->{assertThrows(IllegalStateException.class,()->original.play(new CdTunnelDoorSplash.FmCall(c.cut(),c.source(),c.sound())));original.play(c);assertThrows(IllegalStateException.class,()->original.play(c));});
        replayNext(s);assertEquals(1L,s.h().module().act2LoadState().sounds().stream().filter(v->v instanceof SonicCdGameModule.PastTunnelDoorSplashFm).count());
    }

    @Test void originalFaultAfterActualFmPreservesTypedLedgerAndDoesNotReachAnimation() throws Exception {
        var s=scene();var source=image(s,127,0,2);seed(s,source);var b=splash(s);var original=(CdTunnelDoorSplash.SourceSound)actualField(b,"sourceSound");var sentinel=new AssertionError("after actual FM_A1");
        set(b,"sourceSound",(CdTunnelDoorSplash.SourceSound)c->{original.play(c);throw sentinel;});beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(header(source).moveWord(TILE,0x03A7),s.placement().pool().state(127));assertEquals(List.of(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_FM),familyCalls(s));assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void noOpDrawRetainsAnimationWithoutInventingQueueReceipt() throws Exception {
        var s=scene();var source=image(s,127,0,0);seed(s,source);set(splash(s),"sourceDraw",(CdTunnelDoorSplash.SourceDraw)c->{});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(first(source),s.placement().pool().state(127));assertEquals(List.of(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_FM),familyCalls(s));retired(s);
    }

    @Test void clonedAndDuplicateDrawCannotPublishAnotherQueueEntry() throws Exception {
        var s=scene();seed(s,image(s,127,2,0).moveByte(ANIMATION_TIME,2));var b=splash(s);var original=(CdTunnelDoorSplash.SourceDraw)actualField(b,"sourceDraw");
        set(b,"sourceDraw",(CdTunnelDoorSplash.SourceDraw)c->{assertThrows(IllegalStateException.class,()->original.draw(new CdTunnelDoorSplash.DrawCall(c.cut(),c.source())));original.draw(c);assertThrows(IllegalStateException.class,()->original.draw(c));});replayNext(s);
    }

    @Test void originalDrawFaultAfterBclrKeepsReachedSourceAndNeverReplaysEffects() throws Exception {
        var s=scene();var source=image(s,127,2,0).moveByte(ANIMATION_TIME,2).moveByte(SPRITE_FLAGS,0x84);seed(s,source);var b=splash(s);var original=(CdTunnelDoorSplash.SourceDraw)actualField(b,"sourceDraw");var sentinel=new AssertionError("after genuine splash Draw");
        set(b,"sourceDraw",(CdTunnelDoorSplash.SourceDraw)c->{original.draw(c);throw sentinel;});beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(source.moveByte(ANIMATION_TIME,1).moveByte(SPRITE_FLAGS,4),s.placement().pool().state(127));assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void originalFaultBeforeHeaderStoreKeepsSourceAndOriginalThrowable() throws Exception {
        var s=scene();var source=image(s,127,0,0);seed(s,source);var b=splash(s);var original=(CdTunnelDoorSplash.DispatchObserver)actualField(b,"observer");var sentinel=new AssertionError("after prepared source before header");
        set(b,"observer",new CdTunnelDoorSplash.DispatchObserver(){public void beforePublication(CdTunnelDoorSplash.Publication c){original.beforePublication(c);throw sentinel;}
            public void afterPublication(CdTunnelDoorSplash.Publication c){original.afterPublication(c);}public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});
        beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(source,s.placement().pool().state(127));assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void adversarialReentryAndUnrelatedFull64MutationCannotReachSourceStore() throws Exception {
        var s=scene();var source=image(s,127,0,0);seed(s,source);var b=splash(s);var original=(CdTunnelDoorSplash.DispatchObserver)actualField(b,"observer");var state=new AtomicReference<CdPlayerBody.State>();
        set(b,"observer",new CdTunnelDoorSplash.DispatchObserver(){public void beforePublication(CdTunnelDoorSplash.Publication c){state.set(s.frame().state());assertThrows(IllegalStateException.class,()->b.run(127));assertThrows(IllegalStateException.class,()->s.h().mode().getRewindRegistry().capture());assertThrows(IllegalStateException.class,()->s.h().mode().getRewindRegistry().captureCourse());original.beforePublication(c);s.placement().pool().write(s.placement().pool().state(126).moveByte(ROUTINE_2,0x5A));}
            public void afterPublication(CdTunnelDoorSplash.Publication c){original.afterPublication(c);}public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});
        beforeForward(s);assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertSame(state.get(),s.frame().state());assertEquals(source,s.placement().pool().state(127));retired(s);
    }

    @Test void foreignPlacementWithEqualSourceBytesCannotPublishIntoActualWorld() throws Exception {
        var s=scene();var source=image(s,127,0,0);seed(s,source);var foreign=CdObjectPlacementRuntime.fromAssets(s.assets(),s.placement().capture().savedFlags());
        var detached=new Scene(s.h(),s.visit(),s.frame(),foreign,s.assets());var row=image(detached,127,0,0);foreign.pool().write(row);assertArrayEquals(source.copyBytes(),row.copyBytes());set(splash(s),"runtime",foreign);beforeForward(s);
        var failure=assertThrows(IllegalArgumentException.class,()->step(s.h(),neutral()));assertEquals("Foreign CD placement source/runtime capture",failure.getMessage());assertEquals(source,s.placement().pool().state(127));assertArrayEquals(source.copyBytes(),foreign.pool().state(127).copyBytes());retired(s);
    }

    private static CdTunnelDoorSplash splash(Scene s) throws Exception{return (CdTunnelDoorSplash)actualField(s.visit(),"tunnelDoorSplash");}
    private static CdObjectState image(Scene s,int whole,int routine,int subtype) {
        var m=s.frame().state().motion();return s.placement().pool().state(whole).moveByte(ID,0x0B).moveByte(ROUTINE,routine).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0)
                .moveWord(X,(m.centreX16_16()>>>16)+64).moveWord(Y,m.centreY16_16()>>>16).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468)
                .moveByte(SPRITE_FLAGS,4).moveByte(FLAGS,0).moveByte(WIDTH,0).moveByte(Y_RADIUS,0).moveByte(PRIORITY,1).moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0)
                .moveByte(SAVED_FLAGS_ID,1).moveByte(ROUTINE_2,0xE3).moveWord(X_VELOCITY,0xCAFE).moveWord(Y_VELOCITY,0xBEEF)
                .moveLong(MAPPINGS,0x21D652).moveWord(TILE,0x03A7).moveByte(MAP_FRAME,0).moveByte(ANIMATION,subtype).moveByte(PREVIOUS_ANIMATION,subtype).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0);
    }
    private static CdObjectState dirtyFree(Scene s,int whole){return image(s,whole,0,0).moveByte(ID,0).moveLong(MAPPINGS,0x12345678).moveWord(TILE,0x5678).moveByte(SPRITE_FLAGS,255).moveByte(PREVIOUS_ANIMATION,0x77).moveByte(ANIMATION_FRAME,0x45).moveByte(ANIMATION_TIME,0x34);}
    private static CdObjectState parent(Scene s,int whole){return image(s,whole,2,0).moveByte(ID,0x0D).moveByte(ROUTINE_2,0).moveByte(SAVED_FLAGS_ID,0).moveByte(SOLID_TYPE,0).moveByte(WIDTH,44).moveByte(Y_RADIUS,8).moveWord(TILE,0x834E).moveLong(MAPPINGS,CdR12aProfile.Asset.MAP_SPR_TUNNEL_DOOR.nativeAddress());}
    private static CdObjectState header(CdObjectState s){return s.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,1).moveLong(MAPPINGS,0x21D652).moveByte(ANIMATION,s.subtype());}
    private static CdObjectState first(CdObjectState s){return header(s).moveWord(TILE,0x03A7).moveByte(PREVIOUS_ANIMATION,s.subtype()).moveByte(ANIMATION_FRAME,1).moveByte(ANIMATION_TIME,3).moveByte(MAP_FRAME,s.subtype()==2?6:0).moveByte(SPRITE_FLAGS,4|(s.flags()&3));}
    private static void assertScreen(CdObjectState expected,CdObjectState actual){assertEquals(expected.moveByte(SPRITE_FLAGS,expected.spriteFlags()&0x7F),actual.moveByte(SPRITE_FLAGS,actual.spriteFlags()&0x7F));}
    private static LogicalInputSnapshot jump(){return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0,0,InputActionMasks.ACTION_B,InputActionMasks.ACTION_B,false,false),PlayerInputState.neutral());}
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.Act2LoadCall> rawCalls(Scene s) throws Exception{return (List<SonicCdGameModule.Act2LoadCall>)actualField(actualField(s.visit(),"values"),"calls");}
    private static List<SonicCdGameModule.Act2LoadCall> familyCalls(Scene s) throws Exception{return rawCalls(s).stream().filter(v->v==SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_BODY||v==SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_FM).toList();}
    private static CdTunnelDoorSplash.DispatchObserver observer(CdTunnelDoorSplash.DispatchObserver original,boolean skipBefore,boolean skipAfter){return new CdTunnelDoorSplash.DispatchObserver(){
        public void beforePublication(CdTunnelDoorSplash.Publication c){if(!skipBefore)original.beforePublication(c);}public void afterPublication(CdTunnelDoorSplash.Publication c){if(!skipAfter)original.afterPublication(c);}
        public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}};}

    private static Scene scene() throws Exception {
        var old=oldReturned(); step(old.harness(),neutral()); var h=old.harness();
        for(int service=0;service<64;service++) {
            step(h,neutral());
            if(h.module().act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN)) {
                var visit=(SonicCdAct2LoadingVisit)actualField(h.module(),"act2Loading");
                var scene=new Scene(h,visit,h.module().requirePlayerFrame(),(CdObjectPlacementRuntime)actualField(visit,"placement"),h.module().act2LoadState().assets());
                assertEquals(0,scene.placement().pool().state(127).id()); healthy(scene);
                for(int ordinary=0;ordinary<256;ordinary++) {
                    if(scene.frame().environment().controls().controlLocked()==0
                            && scene.h().module().act2LoadState().section().queue().headSource()==0) {
                        assertEquals(1,scene.frame().environment().movement().gates().timeZone());return scene;
                    }
                    step(h,neutral());healthy(scene);
                }
                throw new AssertionError("Actual Title/PLC2 did not release within its native finite bound");
            }
        }
        throw new AssertionError("Actual normal Act2 load did not reach its first ordinary cut");
    }
    private static void seed(Scene s,CdObjectState image) throws Exception {
        var before=s.placement().capture(); var actor=s.frame().captureActor(); var shared=s.frame().captureShared();
        s.placement().pool().write(image);
        assertEquals(before.status(),s.placement().status()); assertArrayEquals(before.savedFlags().copy(),s.placement().capture().savedFlags().copy());
        assertEquals(actor,s.frame().captureActor()); assertEquals(shared,s.frame().captureShared());
        for(var old:before.pool().slots()) if(old.identity().wholeIndex()!=image.identity().wholeIndex())
            assertEquals(old,s.placement().pool().state(old.identity().wholeIndex()));
    }
    private static void beforeForward(Scene s) throws Exception {
        var registry=s.h().mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=captureNative(s.h());
        registry.restore(full);assertNativeCapture(before,s.h());registry.restoreCourse(course);assertNativeCapture(before,s.h());
    }
    private static void replayNext(Scene s) throws Exception {replayNext(s,neutral());}
    private static void replayNext(Scene s,LogicalInputSnapshot input) throws Exception {
        var h=s.h(); var registry=h.mode().getRewindRegistry(); var full=registry.capture(); var course=registry.captureCourse();
        var before=captureNative(h); var source=s.frame().stageSource(); var world=h.mode().getWorldSession();
        long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        registry.restore(full); assertNativeCapture(before,h); registry.restoreCourse(course); assertNativeCapture(before,h);
        step(h,input); healthy(s); var after=captureNative(h);
        registry.restore(full); assertNativeCapture(before,h); step(h,input); healthy(s); assertNativeCapture(after,h);
        registry.restoreCourse(course); assertNativeCapture(before,h); step(h,input); healthy(s); assertNativeCapture(after,h);
        assertSame(source,s.frame().stageSource()); assertSame(world,h.mode().getWorldSession());
        assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
    }
    private static void healthy(Scene s) throws Exception {
        assertSame(s.frame(),s.h().module().requirePlayerFrame()); assertFalse(s.frame().closed());
        assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,s.h().module().act2LoadState().calls().getLast()); retired(s);
    }
    private static void retired(Scene s) throws Exception {
        for(String name:new String[]{"activeTunnelDoorSplash","activeDoor","activeChildEffect","activeLogMosqui","activeAnton","activePata","activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        for(String name:new String[]{"splashPort","drawingSplash","doorPort","drawingDoor","drawingChildEffect","logMosquiPort","drawingLogMosqui","antonPort","drawingAnton","drawingPata","boulderPort","drawingBoulder","platformPort","drawingPlatform","monitorPort","drawingMonitor","runningObjects"})assertFalse((boolean)actualField(s.visit(),name),name);
    }
    private static List<SonicCdGameModule.Act2LoadCall> calls(Scene s) { return s.h().module().act2LoadState().calls(); }
    private static CdAssetBytes bytes(Scene s,String name) throws Exception { return (CdAssetBytes)actualField(actualField(s.visit(),"values"),name); }
    @SuppressWarnings("unchecked") private static CdObjectDrawQueue.CpuSat cpu(Scene s) throws Exception {
        return ((Optional<CdObjectDrawQueue.CpuSat>)actualField(actualField(actualField(s.visit(),"values"),"loop"),"completedDraw")).orElseThrow();
    }
    private static Object invoke(Object owner,String name,Class<?>[] types,Object...arguments) throws Exception {
        var method=owner.getClass().getDeclaredMethod(name,types); method.setAccessible(true);
        try { return method.invoke(owner,arguments); }
        catch(InvocationTargetException wrapped) {
            var original=wrapped.getCause(); if(original instanceof Error error) throw error;
            if(original instanceof RuntimeException error) throw error; if(original instanceof Exception error) throw error;
            throw new AssertionError(original);
        }
    }
    private static void set(Object owner,String name,Object value) throws Exception {
        var field=owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner,value);
    }
    private static Prepared oldReturned() throws Exception { return oldReturned(ready()); }
    private static Prepared oldReturned(Prepared p) throws Exception {
        for(int call=p.returned()+1;call<=8278;call++) step(p.harness(),route(call));
        seed(p,fixed(p,127).moveByte(ROUTINE,8)); step(p.harness(),neutral());
        for(int wait=0;wait<22;wait++) step(p.harness(),neutral());
        step(p.harness(),neutral()); step(p.harness(),neutral());
        assertEquals(SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED,p.harness().module().pastLoadState().phase());
        assertFalse(p.frame().closed()); return p;
    }
    private static CdObjectState fixed(Prepared p, int whole) {
        var old = p.placement().pool().state(whole); assertEquals(0, old.id());
        return old.moveByte(ID, 0x3A).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0).moveWord(X, 288)
                .moveWord(RESULTS_DEST_X, 288).moveWord(RESULTS_SCREEN_Y_WORD, 204).moveWord(Y, 0x2468)
                .moveWord(Y_FRACTION, 0x1357).moveByte(PRIORITY, 0).moveByte(MAP_FRAME, 0).moveWord(TILE, 0x83C4)
                .moveLong(MAPPINGS, p.assets().view(CdR11bAssets.Asset.MAP_SPR_RESULTS).range().nativeAddress());
    }
    private static void seed(Prepared p, CdObjectState row) {
        var before = p.placement().capture(); p.placement().pool().write(row);
        assertEquals(before.status(), p.placement().status());
        assertArrayEquals(before.savedFlags().copy(), p.placement().capture().savedFlags().copy());
        for (var other : before.pool().slots()) if (other.identity().wholeIndex() != row.identity().wholeIndex())
            assertEquals(other, p.placement().pool().state(other.identity().wholeIndex()));
    }
    private static LogicalInputSnapshot neutral() { return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral()); }
}
