package com.openggf.game.soniccd;

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
import com.openggf.game.soniccd.resources.CdR12aCameraArtResidency;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine cold/Past/Results/normal Act2/Title release; only explicitly labelled dynamic64 inputs.
 * N1ecc5f3a Explosion14-106, HUD/Points15-69, Object Spawner329-340, Object Animate14-65.
 * This source-only preparatory packet claims no natural child frontier or pixels. All Java UNRUN.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@Isolated
class TestSonicCdAct2ExplosionPointsWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void cachedActualSourcesUseBothNewRootsAndExistingCurrentStdBytesWithoutAnotherOwner() throws Exception {
        var s=scene();var e=explosion(s);var p=points(s);var before=captureNative(s.h());
        assertSame(s.assets().objects().explosion(),e.assets().explosion());assertSame(s.assets().objects().points(),p.assets().points());
        assertEquals(0x21D714,e.assets().mappingsAddress());assertEquals(0x20AA8C,p.assets().mappingsAddress());
        assertEquals(125,SonicCdGameModule.Act2LoadCall.values().length);assertEquals(96,SonicCdGameModule.Act2LoadCall.MOSQUI_FM.ordinal());
        assertEquals(97,SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY.ordinal());assertEquals(98,SonicCdGameModule.Act2LoadCall.POINTS_BODY.ordinal());
        var current=s.h().module().act2LoadState().section().residency();
        assertArrayEquals(s.assets().objects().decode(CdR12aProfile.Asset.ART_EXPLOSIONS).bytes().slice(0,32).copy(),CdR12aCameraArtResidency.readTile(current,0x680).copy());
        assertArrayEquals(s.assets().objects().decode(CdR12aProfile.Asset.ART_POINTS).bytes().slice(0,32).copy(),CdR12aCameraArtResidency.readTile(current,0x6C6).copy());
        assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runExplosion",new Class<?>[]{int.class},127));
        assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runPoints",new Class<?>[]{int.class},127));assertNativeCapture(before,s.h());
    }

    @Test void dirtyNonbadnikInitSkipsPointsAndPreservesEveryOtherSourceByteAcrossFullAndCourseReplay() throws Exception {
        var s=scene();var source=image(s,127,0x18,0).moveByte(ROUTINE_2,1).moveByte(SUBTYPE,1).moveByte(LAYER,1)
                .moveByte(ANIMATION,0x62).moveByte(PREVIOUS_ANIMATION,0x73).moveByte(ANIMATION_FRAME,0x41).moveByte(ANIMATION_TIME,0x52);
        seed(s,source);var saved=s.placement().capture().savedFlags();var children=s.placement().capture().pool().slots().stream().filter(v->v.id()==0x1C).count();
        replayNext(s);assertScreen(firstExplosion(source),s.placement().pool().state(127));
        assertEquals(children,s.placement().capture().pool().slots().stream().filter(v->v.id()==0x1C).count());
        assertArrayEquals(saved.copy(),s.placement().capture().savedFlags().copy());assertEquals(List.of(SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY),familyCalls(s));
    }

    @Test void firstFreeEarlierDirtyPointsChildWaitsForItsOwnNextPhysicalPass() throws Exception {
        var s=scene();var source=image(s,127,0x18,0).moveByte(ROUTINE_2,0).moveWord(EXPLOSION_POINTS_FRAME,0x010A);seed(s,source);
        int child=s.placement().pool().findObjSlot().orElseThrow();assertTrue(child<127);var dirty=dirtyFree(s,child);seed(s,dirty);
        replayNext(s);var spawned=dirty.moveByte(ID,0x1C).moveWord(X,source.centreX()).moveWord(Y,source.centreY()).moveByte(SUBTYPE,0x85);
        assertEquals(spawned,s.placement().pool().state(child));assertEquals(0,s.placement().pool().state(child).routine());
        assertEquals(List.of(SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY),familyCalls(s));
        replayNext(s);assertScreen(pointStep(spawned),s.placement().pool().state(child));assertTrue(familyCalls(s).contains(SonicCdGameModule.Act2LoadCall.POINTS_BODY));
    }

    @Test void firstFreeLaterDirtyPointsChildRunsThroughItsRealInitInTheSamePhysicalPass() throws Exception {
        var s=scene();var source=image(s,32,0x18,0).moveByte(ROUTINE_2,0).moveWord(EXPLOSION_POINTS_FRAME,0x010A);seed(s,source);
        int child=s.placement().pool().findObjSlot().orElseThrow();assertTrue(child>32);var dirty=dirtyFree(s,child);seed(s,dirty);
        replayNext(s);var spawned=dirty.moveByte(ID,0x1C).moveWord(X,source.centreX()).moveWord(Y,source.centreY()).moveByte(SUBTYPE,0x85);
        assertScreen(pointStep(spawned),s.placement().pool().state(child));
        var calls=familyCalls(s);assertTrue(calls.indexOf(SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY)<calls.indexOf(SonicCdGameModule.Act2LoadCall.POINTS_BODY));
    }

    @Test void genuineFullDynamicCapacitySkipsOnlyTheUnallocatedPointsChildAndStillAnimatesParent() throws Exception {
        var s=scene();
        // Labelled dynamic64 capacity input, with real owned Points states; no owner/global/queue/actor seed.
        for(int whole=32;whole<128;whole++)seed(s,image(s,whole,0x1C,2).moveByte(SUBTYPE,0x80).moveByte(POINTS_TIMER,2));
        var source=image(s,32,0x18,0).moveByte(ROUTINE_2,0);seed(s,source);assertTrue(s.placement().pool().findObjSlot().isEmpty());
        replayNext(s);assertScreen(firstExplosion(source),s.placement().pool().state(32));
        assertEquals(95,s.placement().capture().pool().slots().stream().filter(v->v.id()==0x1C).count());
        assertEquals(1L,familyCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY).count());
    }

    @Test void nonbadnikDoneClearsAll64BytesAndDoesNotCallDraw() throws Exception {
        var s=scene();var source=image(s,127,0x18,4).moveByte(ROUTINE_2,1);seed(s,source);
        set(explosion(s),"sourceDraw",(CdExplosion.SourceDraw)c->{throw new AssertionError("Done must not Draw");});
        replayNext(s);assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());assertEquals(List.of(SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY),familyCalls(s));
    }

    @Test void badnikDoneConvertsOnlyIdAndRoutineAndFlowerWaitsUntilNextPass() throws Exception {
        var s=scene();var source=image(s,127,0x18,4).moveByte(ROUTINE_2,0).moveByte(SUBTYPE,1);seed(s,source);
        replayNext(s);assertEquals(source.moveByte(ID,0x1F).moveByte(ROUTINE,0),s.placement().pool().state(127));
        assertEquals(List.of(SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY),familyCalls(s));beforeForward(s);
        replayNext(s);assertEquals(0x1F,s.placement().pool().state(127).id());assertEquals(2,s.placement().pool().state(127).routine());
        assertTrue(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.FLOWER_BODY));assertFalse(familyCalls(s).contains(SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY));retired(s);
    }

    @Test void pointsByteTimerWrapAndCountdownUseOnlyNativeWordYAndKeepEveryFraction() throws Exception {
        var s=scene();var registry=s.h().mode().getRewindRegistry();var base=registry.capture();
        for(int timer:new int[]{0,2,0x80}) {
            registry.restore(base);var source=image(s,127,0x1C,2).moveByte(SUBTYPE,0x82).moveByte(POINTS_TIMER,timer);seed(s,source);
            replayNext(s);assertScreen(source.addByte(POINTS_TIMER,-1).addWord(Y,-2),s.placement().pool().state(127));
        }
    }

    @Test void pointsDeleteStillCallsRealDrawWithTheFullyZeroIdZeroSource() throws Exception {
        var s=scene();var source=image(s,127,0x1C,2).moveByte(SUBTYPE,0x85).moveByte(POINTS_TIMER,1);seed(s,source);
        var body=points(s);var original=(CdPoints.SourceDraw)actualField(body,"sourceDraw");var zero=new AtomicReference<CdObjectState>();
        set(body,"sourceDraw",(CdPoints.SourceDraw)c->{zero.set(c.source());original.draw(c);});
        replayNext(s);assertArrayEquals(new byte[64],zero.get().copyBytes());assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());
        assertEquals(List.of(SonicCdGameModule.Act2LoadCall.POINTS_BODY),familyCalls(s));
    }

    @Test void dynamicId1cWithoutSubtypeBitSevenStopsAtItsActualHudOwnerBeforeAnyObjectWrite() throws Exception {
        var s=scene();var source=image(s,127,0x1C,0).moveByte(SUBTYPE,1);seed(s,source);beforeForward(s);
        var failure=assertThrows(CdPoints.UnsupportedPoints.class,()->step(s.h(),neutral()));assertEquals(CdPoints.UnsupportedBoundary.HUD_OWNER,failure.boundary());
        assertEquals(source,s.placement().pool().state(127));assertTrue(familyCalls(s).isEmpty());retired(s);
    }

    @Test void heldUnknownAnimationDoesNotReadItsDirectoryUntilTheNextReachedNativeRead() throws Exception {
        var s=scene();var source=image(s,127,0x18,2).moveByte(ANIMATION,2).moveByte(PREVIOUS_ANIMATION,2).moveByte(ANIMATION_TIME,1);seed(s,source);
        replayNext(s);assertScreen(source.moveByte(ANIMATION_TIME,0),s.placement().pool().state(127));var held=s.placement().pool().state(127);beforeForward(s);
        var failure=assertThrows(CdExplosion.UnsupportedExplosion.class,()->step(s.h(),neutral()));assertEquals(CdExplosion.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,failure.boundary());
        assertEquals(held.moveByte(ANIMATION_TIME,255),s.placement().pool().state(127));assertEquals(0,failure.getSuppressed().length);retired(s);
    }

    @Test void finiteExplosionDirectoryFailureKeepsItsReachedTimerPrefixWithoutDrawOrOtherWrites() throws Exception {
        var s=scene();var source=image(s,127,0x18,2).moveByte(ANIMATION,2).moveByte(PREVIOUS_ANIMATION,2).moveByte(ANIMATION_TIME,0);seed(s,source);beforeForward(s);
        var failure=assertThrows(CdExplosion.UnsupportedExplosion.class,()->step(s.h(),neutral()));assertEquals(CdExplosion.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,failure.boundary());
        assertEquals(source.moveByte(ANIMATION_TIME,255),s.placement().pool().state(127));assertEquals(0,failure.getSuppressed().length);assertTrue(familyCalls(s).isEmpty());retired(s);
    }

    @Test void finiteExplosionFrameFailureRetainsActualSpeedReadAfterTimerPrefix() throws Exception {
        var s=scene();var source=image(s,127,0x18,2).moveByte(ANIMATION_FRAME,255).moveByte(ANIMATION_TIME,0);seed(s,source);beforeForward(s);
        var failure=assertThrows(CdExplosion.UnsupportedExplosion.class,()->step(s.h(),neutral()));assertEquals(CdExplosion.UnsupportedBoundary.ANIMATION_FRAME_READ,failure.boundary());
        assertEquals(source.moveByte(ANIMATION_TIME,3),s.placement().pool().state(127));assertEquals(0,failure.getSuppressed().length);retired(s);
    }

    @Test void foreignReachedMappingRootStopsAfterNativePointsMoveAndActualDrawPrefix() throws Exception {
        var s=scene();var source=image(s,127,0x1C,2).moveByte(SUBTYPE,0x80).moveByte(POINTS_TIMER,3).moveLong(MAPPINGS,0x7E123456);seed(s,source);beforeForward(s);
        assertThrows(UnsupportedOperationException.class,()->step(s.h(),neutral()));assertScreen(pointStep(source),s.placement().pool().state(127));
        assertTrue(familyCalls(s).contains(SonicCdGameModule.Act2LoadCall.POINTS_BODY));retired(s);
    }

    @Test void noOpBeforePublicationCannotReachEvenTheFirstNativeHeaderStore() throws Exception {
        var s=scene();var source=image(s,127,0x18,0).moveByte(ROUTINE_2,0);seed(s,source);var body=explosion(s);
        set(body,"observer",observer((CdExplosion.DispatchObserver)actualField(body,"observer"),true,false));beforeForward(s);
        var failure=assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertTrue(failure.getMessage().contains("prepared receipt"));
        assertEquals(source,s.placement().pool().state(127));assertEquals(0,failure.getSuppressed().length);retired(s);
    }

    @Test void noOpAfterPublicationStopsBeforeFindObjSlotAndRetainsOnlyItsStoredInitHeader() throws Exception {
        var s=scene();var source=image(s,127,0x18,0).moveByte(ROUTINE_2,0);seed(s,source);var before=s.placement().capture().pool();var body=explosion(s);
        set(body,"observer",observer((CdExplosion.DispatchObserver)actualField(body,"observer"),false,true));beforeForward(s);
        var failure=assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertTrue(failure.getMessage().contains("completed receipt"));
        assertEquals(explosionHeader(source),s.placement().pool().state(127));assertEquals(0,failure.getSuppressed().length);
        for(var old:before.slots())if(old.id()==0)assertEquals(old,s.placement().pool().state(old.identity().wholeIndex()));retired(s);
    }

    @Test void equalAndDuplicatePublicationRecordsCannotBorrowTheActualPendingProducer() throws Exception {
        var s=scene();seed(s,image(s,127,0x18,0).moveByte(ROUTINE_2,1));var body=explosion(s);var original=(CdExplosion.DispatchObserver)actualField(body,"observer");
        var seen=new AtomicReference<CdExplosion.Publication>();
        set(body,"observer",new CdExplosion.DispatchObserver() {
            @Override public void beforePublication(CdExplosion.Publication c){seen.set(c);var clone=new CdExplosion.Publication(c.ordinal(),c.kind(),c.cut(),c.source(),c.before(),c.output());assertThrows(IllegalStateException.class,()->original.beforePublication(clone));original.beforePublication(c);assertThrows(IllegalStateException.class,()->original.beforePublication(c));}
            @Override public void afterPublication(CdExplosion.Publication c){original.afterPublication(c);assertThrows(IllegalStateException.class,()->original.afterPublication(c));}
            @Override public void beforeCall(Object c){original.beforeCall(c);}
            @Override public void afterCall(Object c){original.afterCall(c);}
        });replayNext(s);assertFalse(body.isCurrentCall(seen.get()));assertFalse(body.isCurrentCall(null));
    }

    @Test void noOpPointsDrawCannotBorrowTheSourceReceiptAfterNativeMove() throws Exception {
        var s=scene();var source=image(s,127,0x1C,2).moveByte(SUBTYPE,0x80).moveByte(POINTS_TIMER,3).moveByte(SPRITE_FLAGS,4);seed(s,source);
        set(points(s),"sourceDraw",(CdPoints.SourceDraw)c->{});beforeForward(s);var failure=assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));
        assertTrue(failure.getMessage().contains("once-only receipt"));assertEquals(pointStep(source),s.placement().pool().state(127));assertEquals(0,failure.getSuppressed().length);retired(s);
    }

    @Test void clonedAndDuplicateDrawRecordsCannotCauseAnotherQueuePublication() throws Exception {
        var s=scene();seed(s,image(s,127,0x1C,2).moveByte(SUBTYPE,0x80).moveByte(POINTS_TIMER,3));var body=points(s);var original=(CdPoints.SourceDraw)actualField(body,"sourceDraw");
        set(body,"sourceDraw",(CdPoints.SourceDraw)c->{assertThrows(IllegalStateException.class,()->original.draw(new CdPoints.DrawCall(c.cut(),c.source())));original.draw(c);assertThrows(IllegalStateException.class,()->original.draw(c));});replayNext(s);
    }

    @Test void faultAfterActualDrawPreservesOriginalExceptionAndCompletedSourceWithoutReplay() throws Exception {
        var s=scene();var source=image(s,127,0x1C,2).moveByte(SUBTYPE,0x80).moveByte(POINTS_TIMER,3);seed(s,source);var body=points(s);var original=(CdPoints.SourceDraw)actualField(body,"sourceDraw");
        var sentinel=new AssertionError("after genuine Points Draw");set(body,"sourceDraw",(CdPoints.SourceDraw)c->{original.draw(c);throw sentinel;});beforeForward(s);
        assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertScreen(pointStep(source),s.placement().pool().state(127));assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void otherPoolCachedBodyCannotPublishThroughTheActualWorldEvenWithEqualBytes() throws Exception {
        var s=scene();var source=image(s,127,0x18,0).moveByte(ROUTINE_2,1);seed(s,source);
        var foreign=CdObjectPlacementRuntime.fromAssets(s.assets(),s.placement().capture().savedFlags());
        var foreignScene=new Scene(s.h(),s.visit(),s.frame(),foreign,s.assets());
        var row=image(foreignScene,127,0x18,0).moveByte(ROUTINE_2,1);foreign.pool().write(row);
        assertArrayEquals(source.copyBytes(),row.copyBytes());set(explosion(s),"runtime",foreign);beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source,s.placement().pool().state(127));assertArrayEquals(source.copyBytes(),foreign.pool().state(127).copyBytes());retired(s);
    }

    @Test void reentryAndUnrelatedFull64MutationStopBeforeTheComponentCanContinue() throws Exception {
        var s=scene();var source=image(s,127,0x18,0).moveByte(ROUTINE_2,1);seed(s,source);var body=explosion(s);var original=(CdExplosion.DispatchObserver)actualField(body,"observer");
        var actor=new AtomicReference<CdPlayerBody.State>();var environment=new AtomicReference<CdPlayerBody.Environment>();
        set(body,"observer",new CdExplosion.DispatchObserver() {
            @Override public void beforePublication(CdExplosion.Publication c){actor.set(s.frame().state());environment.set(s.frame().environment());assertThrows(IllegalStateException.class,()->body.run(127));original.beforePublication(c);s.placement().pool().write(s.placement().pool().state(126).moveByte(ROUTINE_2,0x5A));}
            @Override public void afterPublication(CdExplosion.Publication c){original.afterPublication(c);}
            @Override public void beforeCall(Object c){original.beforeCall(c);}
            @Override public void afterCall(Object c){original.afterCall(c);}
        });beforeForward(s);var failure=assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertSame(actor.get(),s.frame().state());assertSame(environment.get(),s.frame().environment());
        assertEquals(source,s.placement().pool().state(127));assertTrue(failure.getSuppressed().length>0);retired(s);
    }

    @Test void originalFaultBeforePreparedPublicationKeepsTheUntouchedSourceAndNoChildAllocation() throws Exception {
        var s=scene();var source=image(s,127,0x18,0).moveByte(ROUTINE_2,0);seed(s,source);var body=explosion(s);var original=(CdExplosion.DispatchObserver)actualField(body,"observer");
        var sentinel=new AssertionError("after real preflight before native header store");
        set(body,"observer",new CdExplosion.DispatchObserver() {
            @Override public void beforePublication(CdExplosion.Publication c){original.beforePublication(c);throw sentinel;}
            @Override public void afterPublication(CdExplosion.Publication c){original.afterPublication(c);}
            @Override public void beforeCall(Object c){original.beforeCall(c);}
            @Override public void afterCall(Object c){original.afterCall(c);}
        });beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(source,s.placement().pool().state(127));assertEquals(0,sentinel.getSuppressed().length);assertTrue(familyCalls(s).isEmpty());retired(s);
    }

    @Test void originalFaultAfterActualHeaderPublicationRetainsItsCompletedPrefixAndNeverReplaysFindObjSlot() throws Exception {
        var s=scene();var source=image(s,127,0x18,0).moveByte(ROUTINE_2,0);seed(s,source);var before=s.placement().capture().pool();var body=explosion(s);var original=(CdExplosion.DispatchObserver)actualField(body,"observer");
        var sentinel=new AssertionError("after native header and genuine World receipt");
        set(body,"observer",new CdExplosion.DispatchObserver() {
            @Override public void beforePublication(CdExplosion.Publication c){original.beforePublication(c);}
            @Override public void afterPublication(CdExplosion.Publication c){original.afterPublication(c);throw sentinel;}
            @Override public void beforeCall(Object c){original.beforeCall(c);}
            @Override public void afterCall(Object c){original.afterCall(c);}
        });beforeForward(s);assertSame(sentinel,assertThrows(AssertionError.class,()->step(s.h(),neutral())));
        assertEquals(explosionHeader(source),s.placement().pool().state(127));assertEquals(0,sentinel.getSuppressed().length);assertTrue(familyCalls(s).isEmpty());
        for(var old:before.slots())if(old.id()==0)assertEquals(old,s.placement().pool().state(old.identity().wholeIndex()));retired(s);
    }

    private static CdExplosion.DispatchObserver observer(CdExplosion.DispatchObserver original,boolean beforeNoOp,boolean afterNoOp) {
        return new CdExplosion.DispatchObserver() {
            @Override public void beforePublication(CdExplosion.Publication c){if(!beforeNoOp)original.beforePublication(c);}
            @Override public void afterPublication(CdExplosion.Publication c){if(!afterNoOp)original.afterPublication(c);}
            @Override public void beforeCall(Object c){original.beforeCall(c);}
            @Override public void afterCall(Object c){original.afterCall(c);}
        };
    }
    private static CdExplosion explosion(Scene s) throws Exception {return (CdExplosion)actualField(s.visit(),"explosion");}
    private static CdPoints points(Scene s) throws Exception {return (CdPoints)actualField(s.visit(),"points");}
    private static List<SonicCdGameModule.Act2LoadCall> familyCalls(Scene s) throws Exception {return rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY || c==SonicCdGameModule.Act2LoadCall.POINTS_BODY).toList();}
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.Act2LoadCall> rawCalls(Scene s) throws Exception {return (List<SonicCdGameModule.Act2LoadCall>)actualField(actualField(s.visit(),"values"),"calls");}
    private static void assertScreen(CdObjectState expected,CdObjectState actual) {assertEquals(expected.spriteFlags()&0x7F,actual.spriteFlags()&0x7F);assertEquals(expected.moveByte(SPRITE_FLAGS,actual.spriteFlags()),actual);}
    private static CdObjectState explosionHeader(CdObjectState s) {return s.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,s.spriteFlags()|4).moveByte(PRIORITY,1).moveWord(TILE,s.unsignedByte(LAYER)==0?0x8680:0x680).moveLong(MAPPINGS,CdR12aProfile.Asset.MAP_SPR_EXPLOSION.nativeAddress());}
    private static CdObjectState firstExplosion(CdObjectState s) {int animation=s.subtype()==0?0:1;return explosionHeader(s).moveByte(COLLISION_TYPE,0).moveByte(ANIMATION,animation).moveByte(PREVIOUS_ANIMATION,animation).moveByte(ANIMATION_FRAME,1).moveByte(ANIMATION_TIME,3).moveByte(MAP_FRAME,0);}
    private static CdObjectState pointStep(CdObjectState s) {if(s.routine()==0)s=s.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,s.spriteFlags()|4).moveWord(TILE,0x6C6).moveLong(MAPPINGS,CdR12aProfile.Asset.MAP_SPR_POINTS.nativeAddress()).moveByte(MAP_FRAME,s.subtype()&0x7F).moveByte(POINTS_TIMER,24);return s.addByte(POINTS_TIMER,-1).addWord(Y,-2);}
    private static CdObjectState image(Scene s,int whole,int id,int routine) {
        var m=s.frame().state().motion();return s.placement().pool().state(whole).moveByte(ID,id).moveByte(ROUTINE,routine).moveByte(ROUTINE_2,1).moveByte(SUBTYPE,0).moveByte(SUBTYPE2,0)
                .moveWord(X,((m.centreX16_16()>>>16)+64)&65535).moveWord(Y,m.centreY16_16()>>>16).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468)
                .moveByte(SAVED_FLAGS_ID,0x39).moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0x50).moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0)
                .moveByte(WIDTH,16).moveByte(Y_RADIUS,16).moveByte(X_RADIUS,16).moveByte(PRIORITY,1).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0)
                .moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,1).moveByte(MAP_FRAME,0).moveWord(EXPLOSION_POINTS_FRAME,0)
                .moveLong(MAPPINGS,id==0x18?CdR12aProfile.Asset.MAP_SPR_EXPLOSION.nativeAddress():CdR12aProfile.Asset.MAP_SPR_POINTS.nativeAddress()).moveWord(TILE,id==0x18?0x8680:0x6C6);
    }
    private static CdObjectState dirtyFree(Scene s,int whole) {return image(s,whole,0,0).moveByte(SPRITE_FLAGS,4).moveByte(POINTS_TIMER,0xA6).moveWord(X_FRACTION,0xABCD).moveWord(Y_FRACTION,0x5678).moveByte(SUBTYPE2,0x44);}

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
    private static void replayNext(Scene s) throws Exception {
        var h=s.h(); var registry=h.mode().getRewindRegistry(); var full=registry.capture(); var course=registry.captureCourse();
        var before=captureNative(h); var source=s.frame().stageSource(); var world=h.mode().getWorldSession();
        long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        registry.restore(full); assertNativeCapture(before,h); registry.restoreCourse(course); assertNativeCapture(before,h);
        step(h,neutral()); healthy(s); var after=captureNative(h);
        registry.restore(full); assertNativeCapture(before,h); step(h,neutral()); healthy(s); assertNativeCapture(after,h);
        registry.restoreCourse(course); assertNativeCapture(before,h); step(h,neutral()); healthy(s); assertNativeCapture(after,h);
        assertSame(source,s.frame().stageSource()); assertSame(world,h.mode().getWorldSession());
        assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
    }
    private static void healthy(Scene s) throws Exception {
        assertSame(s.frame(),s.h().module().requirePlayerFrame()); assertFalse(s.frame().closed());
        assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,s.h().module().act2LoadState().calls().getLast()); retired(s);
    }
    private static void retired(Scene s) throws Exception {
        for(String name:new String[]{"activeChildEffect","activeLogMosqui","activeAnton","activePata","activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        for(String name:new String[]{"drawingChildEffect","logMosquiPort","drawingLogMosqui","antonPort","drawingAnton","drawingPata","boulderPort","drawingBoulder","platformPort","drawingPlatform","monitorPort","drawingMonitor","runningObjects"})assertFalse((boolean)actualField(s.visit(),name),name);
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
