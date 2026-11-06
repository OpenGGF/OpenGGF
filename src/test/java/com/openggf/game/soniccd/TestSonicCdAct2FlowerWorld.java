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

/** Genuine programme/Act2/Title-release ingress; only labelled test dynamic64 inputs.
 * N1ecc5f3a Flower32-97/150-162, CollisionCheck159-175, ObjectAnimate14-95.
 * Persistent subtype0 storage remains an honest prefix stop. All Java UNRUN.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@Isolated
class TestSonicCdAct2FlowerWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void cachedFlowerUsesSelectedOriginalRootAndExistingCurrentStdArt() throws Exception {
        var s=scene();var body=flower(s);var before=captureNative(s.h());
        assertSame(s.assets().objects().flower(),body.assets().flower());assertEquals(0x236AB0,body.assets().mappingsAddress());
        assertEquals(130,SonicCdGameModule.Act2LoadCall.values().length);assertEquals(98,SonicCdGameModule.Act2LoadCall.POINTS_BODY.ordinal());
        assertEquals(104,SonicCdGameModule.Act2LoadCall.FLOWER_BODY.ordinal());assertEquals(105,SonicCdGameModule.Act2LoadCall.FLOWER_PRIMARY_ANGLE.ordinal());
        assertArrayEquals(s.assets().objects().decode(CdR12aProfile.Asset.ART_FLOWER).bytes().slice(0,32).copy(),
                CdR12aCameraArtResidency.readTile(s.h().module().act2LoadState().section().residency(),0x6D7).copy());
        assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runFlower",new Class<?>[]{int.class},127));
        assertThrows(IllegalStateException.class,()->body.currentFloorResult(new CdFlower.FloorCall(1,s.placement().capture(),s.placement().pool().state(127))));
        assertNativeCapture(before,s.h());
    }

    @Test void dirtyInitFallsThroughActualFloorAndAnimatesWithEveryOtherByteRetained() throws Exception {
        var s=scene();var source=falling(s,0).moveByte(SUBTYPE2,0x45).moveByte(FLAGS,0x53)
                .moveByte(ANIMATION,0x62).moveByte(PREVIOUS_ANIMATION,0x73).moveByte(ANIMATION_FRAME,0x41).moveByte(ANIMATION_TIME,0x52);
        seed(s,source);var ready=initReady(source);var floor=CdObjectCollisionCheck.objGetFloorDist(terrain(s),ready);assertTrue(floor.distance()>=0);
        replayNext(s);assertScreen(first(ready.addWord(Y,2),0,3,0),live(s));assertEquals(floor.primaryAngle(),s.frame().environment().movement().globals().primaryAngle());
        assertTrue(familyCalls(s).contains(SonicCdGameModule.Act2LoadCall.FLOWER_PRIMARY_ANGLE));assertEquals(SonicCdGameModule.Act2LoadCall.FLOWER_BODY,familyCalls(s).getLast());
    }

    @Test void realPlayerGroundVectorPlantsAndPublishesOnlyTheActualFramePrimaryAngleByte() throws Exception {
        var s=scene();var source=landing(s).moveByte(FLAGS,0x53);seed(s,source);
        var floor=CdObjectCollisionCheck.objGetFloorDist(terrain(s),source);assertTrue(floor.distance()<0,"actual player-ground source vector");
        var body=flower(s);var original=(CdFlower.PrimaryAngleCommit)actualField(body,"angleCommit");var count=new int[1];
        set(body,"angleCommit",(CdFlower.PrimaryAngleCommit)c->{
            var state=s.frame().state();var env=s.frame().environment();original.commit(c);count[0]++;
            assertSame(state,s.frame().state());assertSame(env.controls(),s.frame().environment().controls());assertSame(env.globals(),s.frame().environment().globals());
            assertSame(env.dynamicSlots(),s.frame().environment().dynamicSlots());assertSame(env.movement().physics(),s.frame().environment().movement().physics());
            assertSame(env.movement().bounds(),s.frame().environment().movement().bounds());assertSame(env.movement().gates(),s.frame().environment().movement().gates());
            var a=env.movement().globals();var z=s.frame().environment().movement().globals();assertEquals(c.value(),z.primaryAngle());
            assertEquals(new CdPlayerMovement.Globals(a.lookMode(),a.cameraYCentre(),c.value(),a.secondaryAngle(),a.boredTimer(),a.scoreChain(),a.debugAngle(),a.debugAngleShift(),a.debugQuadrant(),a.debugFloorDistance()),z);
        });
        replayNext(s);assertTrue(count[0]>=3);assertScreen(first(source.addWord(Y,floor.distance()).moveByte(ROUTINE,4).moveByte(ANIMATION,1).moveByte(Y_RADIUS,0x30),1,3,2),live(s));
    }

    @Test void growingPublishesNativeTileAndAnimationPrefixBeforeItsSharedAnimation() throws Exception {
        var s=scene();var source=falling(s,6).moveByte(PREVIOUS_ANIMATION,0x73);seed(s,source);replayNext(s);
        assertScreen(first(source.moveWord(TILE,0x26D7).moveByte(ANIMATION,2),2,1,5),live(s));assertEquals(List.of(SonicCdGameModule.Act2LoadCall.FLOWER_BODY),familyCalls(s));
    }

    @Test void doneReturnsToRoutineFourWithTheNativeMatureLoop() throws Exception {
        var s=scene();var source=falling(s,8).moveByte(PREVIOUS_ANIMATION,0x73);seed(s,source);replayNext(s);
        assertScreen(first(source.moveByte(ROUTINE,4).moveByte(ANIMATION,3),3,0x13,6),live(s));assertEquals(0x1F,live(s).id());
    }

    @Test void actualFcAnimationCompletesThenOnlyTheNextPassRunsGrowing() throws Exception {
        var s=scene();var source=falling(s,4).moveByte(ANIMATION,1).moveByte(PREVIOUS_ANIMATION,1).moveByte(ANIMATION_FRAME,4).moveByte(ANIMATION_TIME,0);seed(s,source);
        replayNext(s);assertScreen(source.moveByte(ROUTINE,6).moveByte(ANIMATION_TIME,3),live(s));var growing=live(s);
        replayNext(s);assertScreen(first(growing.moveWord(TILE,0x26D7).moveByte(ANIMATION,2),2,1,5),live(s));
    }

    @Test void actualFfLoopsToFirstSourceFrameWithoutAnotherBodyOrFloorProbe() throws Exception {
        var s=scene();var source=falling(s,4).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,2).moveByte(ANIMATION_TIME,0);seed(s,source);
        replayNext(s);assertScreen(first(source,0,3,0),live(s));assertEquals(List.of(SonicCdGameModule.Act2LoadCall.FLOWER_BODY),familyCalls(s));
    }

    @Test void culledMatureFlowerStillAnimatesAndDrawClearsBitSevenWithoutDeletion() throws Exception {
        var s=scene();var source=falling(s,4).addWord(X,0x800).moveByte(ANIMATION_TIME,1);seed(s,source);replayNext(s);
        assertEquals(source.moveByte(ANIMATION_TIME,0).moveByte(SPRITE_FLAGS,source.spriteFlags()&0x7F),live(s));assertEquals(0x1F,live(s).id());
    }

    @Test void genuineExplosionDoneConversionStartsFlowerOnlyOnItsNextPhysicalPass() throws Exception {
        var s=scene();var source=falling(s,4).moveByte(ID,0x18).moveByte(ROUTINE_2,0).moveLong(MAPPINGS,CdR12aProfile.Asset.MAP_SPR_EXPLOSION.nativeAddress()).moveWord(TILE,0x8680);seed(s,source);
        replayNext(s);var converted=source.moveByte(ID,0x1F).moveByte(ROUTINE,0);assertEquals(converted,live(s));
        assertEquals(List.of(SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY),familyCalls(s));
        var ready=initReady(converted);assertTrue(CdObjectCollisionCheck.objGetFloorDist(terrain(s),ready).distance()>=0);
        replayNext(s);assertScreen(first(ready.addWord(Y,2),0,3,0),live(s));assertEquals(SonicCdGameModule.Act2LoadCall.FLOWER_BODY,familyCalls(s).getLast());
    }

    @Test void persistentInitStopsAfterItsExactHeaderBeforeAnyFloorOrSavedWrite() throws Exception {
        var s=scene();var source=falling(s,0).moveByte(SUBTYPE,0).moveByte(SUBTYPE2,1);seed(s,source);var saved=s.placement().capture().savedFlags();beforeForward(s);
        var failure=assertThrows(CdFlower.UnsupportedFlower.class,()->step(s.h(),neutral()));assertEquals(CdFlower.UnsupportedBoundary.SAVED_FLAGS_INIT,failure.boundary());
        assertEquals(header(source),live(s));assertArrayEquals(saved.copy(),s.placement().capture().savedFlags().copy());assertTrue(rawFamilyCalls(s).isEmpty());assertEquals(0,failure.getSuppressed().length);retired(s);
    }

    @Test void persistentLandingKeepsTheRealAngleAndYPrefixBeforeItsUnownedStorage() throws Exception {
        var s=scene();var source=landing(s).moveByte(SUBTYPE,0);seed(s,source);var floor=CdObjectCollisionCheck.objGetFloorDist(terrain(s),source);assertTrue(floor.distance()<0);
        var saved=s.placement().capture().savedFlags();beforeForward(s);var failure=assertThrows(CdFlower.UnsupportedFlower.class,()->step(s.h(),neutral()));
        assertEquals(CdFlower.UnsupportedBoundary.SAVED_FLAGS_LANDING,failure.boundary());assertEquals(source.addWord(Y,floor.distance()),live(s));
        assertEquals(floor.primaryAngle(),s.frame().environment().movement().globals().primaryAngle());assertArrayEquals(saved.copy(),s.placement().capture().savedFlags().copy());
        assertFalse(rawFamilyCalls(s).contains(SonicCdGameModule.Act2LoadCall.FLOWER_BODY));assertEquals(0,failure.getSuppressed().length);retired(s);
    }

    @Test void heldUnknownAnimationWaitsUntilTheReachedDirectoryReadAndRetainsItsTimer() throws Exception {
        var s=scene();var source=falling(s,4).moveByte(ANIMATION,4).moveByte(PREVIOUS_ANIMATION,4).moveByte(ANIMATION_TIME,1);seed(s,source);replayNext(s);
        assertScreen(source.moveByte(ANIMATION_TIME,0),live(s));var held=live(s);beforeForward(s);
        var failure=assertThrows(CdFlower.UnsupportedFlower.class,()->step(s.h(),neutral()));assertEquals(CdFlower.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,failure.boundary());
        assertEquals(held.moveByte(ANIMATION_TIME,255),live(s));assertEquals(0,failure.getSuppressed().length);retired(s);
    }

    @Test void reachedFrameFailureRetainsItsNativeTimerAndSpeedStores() throws Exception {
        var s=scene();var source=falling(s,4).moveByte(ANIMATION_FRAME,255).moveByte(ANIMATION_TIME,0);seed(s,source);beforeForward(s);
        var failure=assertThrows(CdFlower.UnsupportedFlower.class,()->step(s.h(),neutral()));assertEquals(CdFlower.UnsupportedBoundary.ANIMATION_FRAME_READ,failure.boundary());
        assertEquals(source.moveByte(ANIMATION_TIME,3),live(s));assertEquals(0,failure.getSuppressed().length);retired(s);
    }

    @Test void noOpBeforePublicationCannotPerformTheInitStore() throws Exception {
        var s=scene();var source=falling(s,0);seed(s,source);var body=flower(s);var delegate=observer(body);
        set(body,"observer",new Forward(delegate){@Override public void beforePublication(CdFlower.Publication c){ }});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source,live(s));retired(s);
    }

    @Test void noOpAfterInitPublicationStopsBeforeTheFloorPrezero() throws Exception {
        var s=scene();var source=falling(s,0);seed(s,source);var body=flower(s);var delegate=observer(body);var env=new AtomicReference<CdPlayerBody.Environment>();
        set(body,"observer",new Forward(delegate){@Override public void afterPublication(CdFlower.Publication c){env.set(s.frame().environment());}});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(initReady(source),live(s));assertSame(env.get(),s.frame().environment());assertTrue(rawFamilyCalls(s).isEmpty());retired(s);
    }

    @Test void noOpSameValueAnglePortCannotBorrowItsCompletionReceipt() throws Exception {
        var s=scene();var source=falling(s,0);seed(s,source);var body=flower(s);var state=new AtomicReference<CdPlayerBody.State>();var env=new AtomicReference<CdPlayerBody.Environment>();
        set(body,"angleCommit",(CdFlower.PrimaryAngleCommit)c->{assertEquals(0,c.value());state.set(s.frame().state());env.set(s.frame().environment());});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(initReady(source),live(s));assertSame(state.get(),s.frame().state());assertSame(env.get(),s.frame().environment());retired(s);
    }

    @Test void clonesAndDuplicatesCannotBorrowPublicationFloorAngleOrDrawAuthority() throws Exception {
        var s=scene();seed(s,falling(s,0));var body=flower(s);var delegate=observer(body);var count=new int[1];
        set(body,"observer",new Forward(delegate){
            @Override public void beforePublication(CdFlower.Publication c){
                assertThrows(IllegalStateException.class,()->delegate.beforePublication(new CdFlower.Publication(c.ordinal(),c.kind(),c.cut(),c.source(),c.output())));
                delegate.beforePublication(c);assertThrows(IllegalStateException.class,()->delegate.beforePublication(c));count[0]++;
            }
            @Override public void afterPublication(CdFlower.Publication c){delegate.afterPublication(c);assertThrows(IllegalStateException.class,()->delegate.afterPublication(c));}
            @Override public void beforeCall(Object c){
                assertThrows(IllegalStateException.class,()->delegate.beforeCall(cloneCall(c)));delegate.beforeCall(c);
                assertThrows(IllegalStateException.class,()->delegate.beforeCall(c));count[0]++;
            }
            @Override public void afterCall(Object c){
                if(c instanceof CdFlower.FloorCall f)assertThrows(IllegalStateException.class,()->body.currentFloorResult((CdFlower.FloorCall)cloneCall(f)));
                delegate.afterCall(c);assertThrows(IllegalStateException.class,()->delegate.afterCall(c));
            }
        });replayNext(s);assertTrue(count[0]>=15);
    }

    @Test void noOpSameValueDrawCannotPretendTheNativeBclrWasPublished() throws Exception {
        var s=scene();var source=falling(s,4).moveByte(SPRITE_FLAGS,4);seed(s,source);set(flower(s),"sourceDraw",(CdFlower.SourceDraw)c->{ });beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source.moveByte(ANIMATION_TIME,0),live(s));retired(s);
    }

    @Test void originalFaultAfterActualPrezeroKeepsItsFramePrefixWithoutTerrainOrMotionReplay() throws Exception {
        var s=scene();var source=falling(s,0);seed(s,source);var body=flower(s);var original=(CdFlower.PrimaryAngleCommit)actualField(body,"angleCommit");var sentinel=new IllegalStateException("after-real-flower-prezero");var count=new int[1];
        set(body,"angleCommit",(CdFlower.PrimaryAngleCommit)c->{original.commit(c);count[0]++;throw sentinel;});beforeForward(s);
        assertSame(sentinel,assertThrows(IllegalStateException.class,()->step(s.h(),neutral())));assertEquals(1,count[0]);assertEquals(initReady(source),live(s));
        assertEquals(0,s.frame().environment().movement().globals().primaryAngle());assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void originalFaultAfterRealDrawRetainsTheCompletedSourceAndOriginalIdentity() throws Exception {
        var s=scene();var source=falling(s,4);seed(s,source);var body=flower(s);var original=(CdFlower.SourceDraw)actualField(body,"sourceDraw");var sentinel=new IllegalStateException("after-real-flower-draw");
        set(body,"sourceDraw",(CdFlower.SourceDraw)c->{original.draw(c);throw sentinel;});beforeForward(s);
        assertSame(sentinel,assertThrows(IllegalStateException.class,()->step(s.h(),neutral())));assertEquals(source.moveByte(ANIMATION_TIME,0).moveByte(SPRITE_FLAGS,source.spriteFlags()&0x7F),live(s));
        assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void actualComponentReentryIsRejectedWhileTheOriginalNativeDispatchCompletes() throws Exception {
        var s=scene();seed(s,falling(s,0));var body=flower(s);var delegate=observer(body);var count=new int[1];
        set(body,"observer",new Forward(delegate){@Override public void beforePublication(CdFlower.Publication c){assertThrows(IllegalStateException.class,()->body.run(127));count[0]++;delegate.beforePublication(c);}});
        replayNext(s);assertTrue(count[0]>=9);
    }

    @Test void unrelatedDynamicMutationDuringThePendingCallCannotBeAdoptedAsFlowerOutput() throws Exception {
        var s=scene();var source=falling(s,0);seed(s,source);var body=flower(s);var delegate=observer(body);var other=s.placement().pool().state(126);
        set(body,"observer",new Forward(delegate){@Override public void beforePublication(CdFlower.Publication c){s.placement().pool().write(other.moveByte(SUBTYPE,0x71));delegate.beforePublication(c);}});beforeForward(s);
        var failure=assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertEquals(source,live(s));assertEquals(other.moveByte(SUBTYPE,0x71),s.placement().pool().state(126));
        assertEquals(1,failure.getSuppressed().length);retired(s);
    }

    private static class Forward implements CdFlower.DispatchObserver {
        final CdFlower.DispatchObserver delegate;
        Forward(CdFlower.DispatchObserver delegate){this.delegate=delegate;}
        @Override public void beforePublication(CdFlower.Publication c){delegate.beforePublication(c);}
        @Override public void afterPublication(CdFlower.Publication c){delegate.afterPublication(c);}
        @Override public void beforeCall(Object c){delegate.beforeCall(c);}
        @Override public void afterCall(Object c){delegate.afterCall(c);}
    }
    private static Object cloneCall(Object c){
        if(c instanceof CdFlower.FloorCall f)return new CdFlower.FloorCall(f.ordinal(),f.cut(),f.source());
        if(c instanceof CdFlower.PrimaryAngleCall a)return new CdFlower.PrimaryAngleCall(a.ordinal(),a.cut(),a.source(),a.value());
        var d=(CdFlower.DrawCall)c;return new CdFlower.DrawCall(d.ordinal(),d.cut(),d.source());
    }
    private static CdFlower flower(Scene s)throws Exception{return (CdFlower)actualField(s.visit(),"flower");}
    private static CdFlower.DispatchObserver observer(CdFlower body)throws Exception{return (CdFlower.DispatchObserver)actualField(body,"observer");}
    private static CdPlayerTerrain terrain(Scene s){return CdPlayerTerrain.fromStageTerrain(s.assets().terrain());}
    private static CdObjectState live(Scene s){return s.placement().pool().state(127);}
    private static List<SonicCdGameModule.Act2LoadCall> familyCalls(Scene s){return calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.FLOWER_BODY || c==SonicCdGameModule.Act2LoadCall.FLOWER_PRIMARY_ANGLE || c==SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY).toList();}
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.Act2LoadCall> rawFamilyCalls(Scene s) throws Exception {
        var calls=(List<SonicCdGameModule.Act2LoadCall>)actualField(actualField(s.visit(),"values"),"calls");
        return calls.stream().filter(c->c==SonicCdGameModule.Act2LoadCall.FLOWER_BODY || c==SonicCdGameModule.Act2LoadCall.FLOWER_PRIMARY_ANGLE || c==SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY).toList();
    }
    private static CdObjectState falling(Scene s,int routine){
        var m=s.frame().state().motion();return s.placement().pool().state(127).moveByte(ID,0x1F).moveByte(ROUTINE,routine).moveByte(ROUTINE_2,0x51)
                .moveByte(SUBTYPE,1).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,0x39).moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0)
                .moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0x50).moveByte(PRIORITY,1).moveByte(WIDTH,16).moveByte(X_RADIUS,16).moveByte(Y_RADIUS,0)
                .moveWord(X,m.centreX16_16()>>>16).moveWord(Y,((m.centreY16_16()>>>16)-64)&65535).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468)
                .moveWord(TILE,0x6D7).moveLong(MAPPINGS,CdR12aProfile.Asset.MAP_SPR_FLOWER.nativeAddress()).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0)
                .moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,1).moveByte(MAP_FRAME,0);
    }
    private static CdObjectState landing(Scene s){
        var m=s.frame().state().motion();var support=falling(s,2).moveWord(Y,((m.centreY16_16()>>>16)+m.yRadius()-16)&65535);
        // One fixed standing column, one real-floor alignment, then native eight-pixel penetration.
        // Only this labelled dynamic object changes; no search, actor/terrain edit or fabricated probe.
        var floor=CdObjectCollisionCheck.objGetFloorDist(terrain(s),support);support=support.addWord(Y,floor.distance());
        assertEquals(0,CdObjectCollisionCheck.objGetFloorDist(terrain(s),support).distance(),"Actual fixed-column floor alignment prerequisite");
        var source=support.addWord(Y,8);assertTrue(CdObjectCollisionCheck.objGetFloorDist(terrain(s),source).distance()<=-8,"Native landing at the fixed real floor");return source;
    }
    private static CdObjectState header(CdObjectState s){return s.moveByte(SPRITE_FLAGS,s.spriteFlags()|4).moveByte(PRIORITY,1).moveByte(Y_RADIUS,0)
            .moveWord(TILE,s.unsignedByte(SUBTYPE2)==0?0xA6D7:0x26D7).moveLong(MAPPINGS,CdR12aProfile.Asset.MAP_SPR_FLOWER.nativeAddress());}
    private static CdObjectState initReady(CdObjectState s){return header(s).moveWord(ANIMATION_WORD,2).moveByte(ROUTINE,2).moveWord(TILE,0x6D7);}
    /** Literal first frames/delays in the selected original four-script table, no body execution. */
    private static CdObjectState first(CdObjectState s,int ani,int timer,int frame){return s.moveByte(ANIMATION,ani).moveByte(PREVIOUS_ANIMATION,ani)
            .moveByte(ANIMATION_FRAME,1).moveByte(ANIMATION_TIME,timer).moveByte(MAP_FRAME,frame).moveByte(SPRITE_FLAGS,(s.spriteFlags()&0xFC)|(s.flags()&3));}
    private static void assertScreen(CdObjectState expected,CdObjectState actual){assertArrayEquals(expected.moveByte(SPRITE_FLAGS,expected.spriteFlags()&0x7F).copyBytes(),actual.moveByte(SPRITE_FLAGS,actual.spriteFlags()&0x7F).copyBytes());}

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
        for(String name:new String[]{"activeFlower","activeChildEffect","activeLogMosqui","activeAnton","activePata","activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        for(String name:new String[]{"flowerPort","drawingFlower","drawingChildEffect","logMosquiPort","drawingLogMosqui","antonPort","drawingAnton","drawingPata","boulderPort","drawingBoulder","platformPort","drawingPlatform","monitorPort","drawingMonitor","runningObjects"})assertFalse((boolean)actualField(s.visit(),name),name);
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
